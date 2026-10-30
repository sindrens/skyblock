package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every loadout data change as one atomic database transaction (WARDROBE §7, LD4). The swap itself writes the chest
 * escrow rows (outgoing items {@code stored}, incoming items {@code releasing} with the swap's plan id) and the worn rows
 * together, so after a crash every item is in exactly one place the join recovery understands. Futures never complete
 * exceptionally. No Bukkit (unit-tested on H2).
 */
final class LoadoutOps {

    enum Status {
        OK,
        /** The name is used by another preset of this profile. Nothing changed. */
        TAKEN,
        /** No free preset place (or the place is above the player's slots). Nothing changed. */
        NO_SLOT,
        /** The preset, a chest item or a wardrobe entry is gone. Nothing changed. */
        GONE,
        /** The Wardrobe Chest has no room for the outgoing items. Nothing changed. */
        FULL,
        /** An outgoing item is already in the chest (the one in the inventory is a stale copy). Nothing changed. */
        STALE,
        /** Database error. Nothing changed. */
        ERROR
    }

    /**
     * @param taken the chest rows (with the serialized items) handed out by a swap, by item id
     */
    record Result(Status status, Loadout loadout, Map<UUID, WardrobeStore.Stored> taken) {
        static Result of(Status s) {
            return new Result(s, null, Map.of());
        }

        boolean ok() {
            return status == Status.OK;
        }
    }

    /**
     * One swap (LD4 step 4).
     *
     * @param outgoing items leaving the hotbar into the chest (serialized while the inventory is locked)
     * @param incoming chest items going into the hotbar
     * @param wear     worn rows to write (new nonces)
     * @param off      armor slots to empty
     * @param capacity the player's chest size
     */
    record Swap(UUID profile, UUID plan, UUID loadoutId, List<WardrobeStore.Stored> outgoing, Collection<UUID> incoming,
                List<WardrobeStore.Equipped> wear, List<ArmorPiece> off, int capacity) {
    }

    private static final class Refused extends RuntimeException {
        final Status status;

        Refused(Status status) {
            super(status.name(), null, false, false);
            this.status = status;
        }
    }

    private final Database db;
    private final WardrobeStore store;
    private final LoadoutStore loadouts;
    private final LongSupplier clock;
    private final Logger log;

    LoadoutOps(Database db, WardrobeStore store, LoadoutStore loadouts, LongSupplier clock, Logger log) {
        this.db = db;
        this.store = store;
        this.loadouts = loadouts;
        this.clock = clock;
        this.log = log;
    }

    // ------------------------------------------------------------------ presets

    /** Saves a new preset at its place; NO_SLOT if the place is used or not below {@code slots}, TAKEN if the name is. */
    CompletableFuture<Result> create(Loadout l, int slots) {
        long now = clock.getAsLong();
        return plain("create loadout", c -> {
            if (l.index() < 0 || l.index() >= slots) {
                throw new Refused(Status.NO_SLOT);
            }
            for (Loadout other : loadouts.loadouts(c, l.profile())) {
                if (other.index() == l.index()) {
                    throw new Refused(Status.NO_SLOT);
                }
                if (other.nameKey().equals(l.nameKey())) {
                    throw new Refused(Status.TAKEN);
                }
            }
            try {
                loadouts.insert(c, l, now);
            } catch (SQLException e) {
                if (WardrobeStore.duplicate(e)) {
                    throw new Refused(Status.NO_SLOT); // a second save raced for the same place or name
                }
                throw e;
            }
            return new Result(Status.OK, l, Map.of());
        });
    }

    /** Save current over an existing preset (same id, place, name, icon). */
    CompletableFuture<Result> overwrite(Loadout l) {
        long now = clock.getAsLong();
        return plain("save loadout", c -> {
            if (!loadouts.updateContents(c, l, now)) {
                throw new Refused(Status.GONE);
            }
            return new Result(Status.OK, l, Map.of());
        });
    }

    CompletableFuture<Result> rename(UUID id, UUID profile, String name) {
        long now = clock.getAsLong();
        return plain("rename loadout", c -> {
            for (Loadout other : loadouts.loadouts(c, profile)) {
                if (!other.id().equals(id) && other.nameKey().equals(Loadout.key(name))) {
                    throw new Refused(Status.TAKEN);
                }
            }
            try {
                if (!loadouts.rename(c, id, profile, name, now)) {
                    throw new Refused(Status.GONE);
                }
            } catch (SQLException e) {
                if (WardrobeStore.duplicate(e)) {
                    throw new Refused(Status.TAKEN);
                }
                throw e;
            }
            return Result.of(Status.OK);
        });
    }

    CompletableFuture<Result> icon(UUID id, UUID profile, String icon) {
        long now = clock.getAsLong();
        return plain("loadout icon", c -> {
            if (!loadouts.icon(c, id, profile, icon, now)) {
                throw new Refused(Status.GONE);
            }
            return Result.of(Status.OK);
        });
    }

    CompletableFuture<Result> delete(UUID id, UUID profile) {
        return plain("delete loadout", c -> {
            if (!loadouts.delete(c, id, profile)) {
                throw new Refused(Status.GONE);
            }
            return Result.of(Status.OK);
        });
    }

    // ------------------------------------------------------------------ the swap

    /**
     * The swap's one transaction: incoming chest rows {@code stored → releasing} (plan id; GONE if one is not in this
     * profile's chest any more), capacity check (FULL), outgoing items inserted as {@code stored} (STALE if one already
     * is), worn rows written (GONE if an entry is not the profile's active set), preset marked used. All or nothing.
     */
    CompletableFuture<Result> swap(Swap s) {
        long now = clock.getAsLong();
        return plain("loadout swap", c -> {
            Map<UUID, WardrobeStore.Stored> taken = new LinkedHashMap<>();
            for (UUID id : new TreeSet<>(s.incoming())) { // fixed lock order
                if (!store.markReleasing(c, id, s.profile(), s.plan(), now)) {
                    throw new Refused(Status.GONE);
                }
                taken.put(id, store.storedItem(c, id, false).orElseThrow(() -> new Refused(Status.GONE)));
            }
            if (!s.outgoing().isEmpty() && loadouts.chestUsedExcept(c, s.profile(), s.plan()) + s.outgoing().size() > s.capacity()) {
                throw new Refused(Status.FULL);
            }
            for (WardrobeStore.Stored out : s.outgoing()) {
                if (store.put(c, out, Integer.MAX_VALUE, now) != WardrobeStore.PutResult.OK) {
                    throw new Refused(Status.STALE);
                }
            }
            if ((!s.wear().isEmpty() || !s.off().isEmpty()) && !store.writeEquipped(c, s.profile(), s.wear(), s.off(), now)) {
                throw new Refused(Status.GONE);
            }
            if (s.loadoutId() != null) {
                loadouts.touch(c, s.loadoutId(), now);
            }
            return new Result(Status.OK, null, taken);
        });
    }

    /** After the swap: the incoming items are in the inventory ({@code releasing → released}). */
    CompletableFuture<Boolean> released(Collection<UUID> items, UUID plan) {
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        long now = clock.getAsLong();
        try {
            return db.transaction(c -> {
                for (UUID id : new TreeSet<>(items)) {
                    store.markReleased(c, id, plan, now);
                }
                return true;
            }).exceptionally(e -> {
                log.log(Level.WARNING, "Wardrobe: could not mark " + items.size() + " loadout item(s) released (fixed on the next join)",
                        Database.cause(e));
                return false;
            });
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Wardrobe: could not start marking loadout items released", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    // ------------------------------------------------------------------ helpers

    @FunctionalInterface
    private interface Work {
        Result run(Connection c) throws SQLException;
    }

    private CompletableFuture<Result> plain(String what, Work work) {
        try {
            return db.transaction(work::run).exceptionally(e -> {
                Throwable cause = Database.cause(e);
                if (cause instanceof Refused r) {
                    return Result.of(r.status);
                }
                log.log(Level.SEVERE, "Wardrobe: " + what + " failed and was rolled back", cause);
                return Result.of(Status.ERROR);
            });
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Wardrobe: could not start " + what, e);
            return CompletableFuture.completedFuture(Result.of(Status.ERROR));
        }
    }
}
