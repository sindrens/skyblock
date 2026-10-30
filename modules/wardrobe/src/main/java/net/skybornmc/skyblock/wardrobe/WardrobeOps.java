package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every wardrobe data change as one atomic database transaction (WARDROBE §2.4, §4.4, §8.4). Purchases go through the
 * economy's guarded transaction ({@code EconomyService#executeGuarded}): the new wardrobe row (and Sigil/claim) and the
 * payment commit together or not at all, with an idempotency key per purchase. Futures never complete exceptionally.
 * No Bukkit (unit-tested on H2).
 */
final class WardrobeOps {

    /** Pays together with extra SQL (the economy's guarded transaction). */
    @FunctionalInterface
    interface Payer {
        CompletableFuture<TxResult> run(Transaction transaction, TxGuard guard);
    }

    enum Status {
        OK,
        /** Not enough money/crystals/gems. Nothing changed. */
        NOT_ENOUGH,
        /** The profile already owns this set. Nothing changed. */
        OWNED,
        /** The account has no Sigil for this gem set (claim). Nothing changed. */
        NO_SIGIL,
        /** This profile already claimed the set this season. Nothing changed. */
        CLAIMED,
        /** The account already has this Sigil (buy again refused; claim instead). Nothing changed. */
        HAS_SIGIL,
        /** The token row is not active (a copy) or is for another set. Nothing changed. */
        TOKEN_INVALID,
        /** The entry/item is gone, not active or not the profile's. Nothing changed. */
        GONE,
        /** The Wardrobe Chest is full. Nothing changed. */
        FULL,
        /** The item is already in the chest: the one in hand is a stale copy. Nothing changed. */
        STALE,
        /** Database or economy error. Nothing changed. */
        ERROR
    }

    /**
     * @param entry   the new wardrobe entry (purchases, claims, tokens, staff give)
     * @param stored  the chest row taken out (take)
     * @param balance balance after a payment, when known
     */
    record Result(Status status, WardrobeStore.Entry entry, WardrobeStore.Stored stored, Long balance) {
        static Result of(Status s) {
            return new Result(s, null, null, null);
        }

        boolean ok() {
            return status == Status.OK;
        }
    }

    /** Who acts, for ledger and audit rows. */
    record Who(UUID player, String name, UUID profile) {
        Actor actor() {
            return Actor.player(player, name);
        }
    }

    /** Thrown inside a plain transaction to roll it back with a status. */
    private static final class Refused extends RuntimeException {
        final Status status;

        Refused(Status status) {
            super(status.name(), null, false, false);
            this.status = status;
        }
    }

    /** Why a guard said no (reset on every retry). */
    private static final class Why {
        Status status;
    }

    static final String REASON_BUY = "wardrobe_buy";

    private final Database db;
    private final WardrobeStore store;
    private final Payer payer;
    private final LongSupplier clock;
    private final Logger log;

    WardrobeOps(Database db, WardrobeStore store, Payer payer, LongSupplier clock, Logger log) {
        this.db = db;
        this.store = store;
        this.payer = payer;
        this.clock = clock;
        this.log = log;
    }

    // ------------------------------------------------------------------ buying

    /**
     * Buys a set (money, crystals or gems). Gem purchases also write the account's Sigil and this profile's claim for the
     * season (WD5). Idempotency key {@code wardrobe_buy:<entry id>}: a retried request can never charge twice, and a
     * second purchase of an owned set rolls back before anything is paid.
     */
    CompletableFuture<Result> buy(Who who, SetDef def, int season) {
        Currency currency = def.source().type().currency();
        long price = def.source().price();
        if (currency == null || price <= 0) {
            return CompletableFuture.completedFuture(Result.of(Status.ERROR));
        }
        long now = clock.getAsLong();
        String origin = def.source().type().key();
        WardrobeStore.Entry entry = WardrobeStore.Entry.fresh(who.profile(), def.id(), origin, null, now);
        Why why = new Why();
        TxGuard guard = c -> {
            why.status = null;
            if (currency == Currency.GEMS) {
                if (!store.insertSigil(c, who.player(), def.id(), price, who.profile(), now)) {
                    why.status = Status.HAS_SIGIL;
                    return false;
                }
                if (!store.insertClaim(c, who.profile(), def.id(), season, entry.entryId(), now)) {
                    why.status = Status.CLAIMED;
                    return false;
                }
            }
            if (!store.insertEntry(c, entry, season, who.player())) {
                why.status = Status.OWNED;
                return false;
            }
            return true;
        };
        Transaction tx = Transaction.withdraw(AccountRef.player(who.profile()), currency, price, REASON_BUY)
                .actor(who.actor())
                .reference(def.id())
                .idempotencyKey(REASON_BUY + ":" + entry.entryId())
                .detail("set", def.id())
                .detail("entry", entry.entryId().toString())
                .detail("target_name", who.name());
        return safe("buy " + def.id(), () -> payer.run(tx, guard).thenApply(r -> switch (r.status()) {
            case SUCCESS -> new Result(Status.OK, entry, null, r.fromBalance());
            case INSUFFICIENT_FUNDS -> Result.of(Status.NOT_ENOUGH);
            case DUPLICATE -> Result.of(why.status != null ? why.status : Status.ERROR);
            default -> Result.of(Status.ERROR);
        }));
    }

    /** Claims a gem set on this profile for free (the account owns its Sigil; once per profile per season). */
    CompletableFuture<Result> claim(Who who, String setId, int season) {
        long now = clock.getAsLong();
        WardrobeStore.Entry entry = WardrobeStore.Entry.fresh(who.profile(), setId, "gems_claim", null, now);
        return plain("claim " + setId, c -> {
            if (!store.hasSigil(c, who.player(), setId)) {
                throw new Refused(Status.NO_SIGIL);
            }
            if (!store.insertClaim(c, who.profile(), setId, season, entry.entryId(), now)) {
                throw new Refused(Status.CLAIMED);
            }
            if (!store.insertEntry(c, entry, season, who.player())) {
                throw new Refused(Status.OWNED);
            }
            return new Result(Status.OK, entry, null, null);
        });
    }

    // ------------------------------------------------------------------ tokens

    /** Writes a new token row (before the item is handed out). */
    CompletableFuture<Boolean> mintToken(UUID tokenId, String setId, String origin, UUID owner) {
        long now = clock.getAsLong();
        return db.transaction(c -> {
            store.insertToken(c, tokenId, setId, origin, owner, now);
            return true;
        }).exceptionally(e -> {
            log.log(Level.SEVERE, "Wardrobe: could not write Set Token " + tokenId, Database.cause(e));
            return false;
        });
    }

    /** A token that was minted but never reached a player: active → void. */
    CompletableFuture<Boolean> voidToken(UUID tokenId, String reason) {
        long now = clock.getAsLong();
        return db.transaction(c -> store.moveToken(c, tokenId, WardrobeStore.TokenState.ACTIVE, WardrobeStore.TokenState.VOID, null, null, now, reason))
                .exceptionally(e -> false);
    }

    /**
     * Redeems a token (the item was already taken from the player): the token row active → redeemed and the set
     * added, in one transaction. TOKEN_INVALID = the item was a copy (keep it taken); OWNED/ERROR = give it back.
     */
    CompletableFuture<Result> redeem(Who who, UUID tokenId, String setId, int season) {
        long now = clock.getAsLong();
        WardrobeStore.Entry entry = WardrobeStore.Entry.fresh(who.profile(), setId, "token", tokenId.toString(), now);
        return plain("redeem " + tokenId, c -> {
            Optional<WardrobeStore.Token> token = store.token(c, tokenId, true);
            if (token.isEmpty() || token.get().state() != WardrobeStore.TokenState.ACTIVE || !token.get().setId().equals(setId)) {
                throw new Refused(Status.TOKEN_INVALID);
            }
            if (!store.insertEntry(c, entry, season, who.player())) {
                throw new Refused(Status.OWNED);
            }
            if (!store.moveToken(c, tokenId, WardrobeStore.TokenState.ACTIVE, WardrobeStore.TokenState.REDEEMED, who.player(), who.profile(),
                    now, "redeem")) {
                throw new Refused(Status.TOKEN_INVALID);
            }
            return new Result(Status.OK, entry, null, null);
        });
    }

    /** A copied token was used: active rows are left alone, the copy is just recorded as taken (audit). */
    CompletableFuture<Optional<WardrobeStore.Token>> tokenRow(UUID tokenId) {
        return db.async(c -> store.token(c, tokenId, false)).exceptionally(e -> Optional.empty());
    }

    // ------------------------------------------------------------------ staff

    CompletableFuture<Result> giveSet(UUID profile, String setId, UUID staff, int season) {
        long now = clock.getAsLong();
        WardrobeStore.Entry entry = WardrobeStore.Entry.fresh(profile, setId, "staff", staff == null ? null : staff.toString(), now);
        return plain("give " + setId, c -> {
            if (!store.insertEntry(c, entry, season, staff)) {
                throw new Refused(Status.OWNED);
            }
            return new Result(Status.OK, entry, null, null);
        });
    }

    CompletableFuture<Result> removeSet(UUID entryId, UUID staff) {
        long now = clock.getAsLong();
        return plain("remove " + entryId, c -> {
            Optional<WardrobeStore.Entry> entry = store.entry(c, entryId);
            if (entry.isEmpty() || !store.removeEntry(c, entryId, staff, now)) {
                throw new Refused(Status.GONE);
            }
            return new Result(Status.OK, entry.get(), null, null);
        });
    }

    // ------------------------------------------------------------------ equipping

    /** Writes worn slots (new nonces) and emptied slots in one transaction; GONE if an entry is not the profile's active set. */
    CompletableFuture<Result> equip(UUID profile, Collection<WardrobeStore.Equipped> wear, Collection<ArmorPiece> off) {
        long now = clock.getAsLong();
        return plain("equip", c -> {
            if (!store.writeEquipped(c, profile, wear, off, now)) {
                throw new Refused(Status.GONE);
            }
            return Result.of(Status.OK);
        });
    }

    // ------------------------------------------------------------------ storage (escrow)

    /** Store: the item is already out of the inventory (kept by the caller). OK, FULL, STALE or ERROR. */
    CompletableFuture<Result> store(WardrobeStore.Stored item, int capacity) {
        long now = clock.getAsLong();
        return plain("store " + item.itemUuid(), c -> switch (store.put(c, item, capacity, now)) {
            case OK -> new Result(Status.OK, null, item, null);
            case FULL -> throw new Refused(Status.FULL);
            case STALE -> throw new Refused(Status.STALE);
        });
    }

    /** Take, step 1: stored → releasing with the plan id; returns the row (with the serialized item) to hand out. */
    CompletableFuture<Result> take(UUID itemUuid, UUID profile, UUID plan) {
        long now = clock.getAsLong();
        return plain("take " + itemUuid, c -> {
            if (!store.markReleasing(c, itemUuid, profile, plan, now)) {
                throw new Refused(Status.GONE);
            }
            return new Result(Status.OK, null, store.storedItem(c, itemUuid, false).orElseThrow(() -> new Refused(Status.GONE)), null);
        });
    }

    /** Take, step 2: the item is in the inventory. */
    CompletableFuture<Boolean> released(UUID itemUuid, UUID plan) {
        long now = clock.getAsLong();
        return db.transaction(c -> store.markReleased(c, itemUuid, plan, now)).exceptionally(e -> {
            log.log(Level.WARNING, "Wardrobe: could not mark " + itemUuid + " released (fixed on the next join)", Database.cause(e));
            return false;
        });
    }

    /** The item could not be handed out: back in the chest. */
    CompletableFuture<Boolean> backToStored(UUID itemUuid, UUID plan) {
        long now = clock.getAsLong();
        return db.transaction(c -> store.backToStored(c, itemUuid, plan, now)).exceptionally(e -> false);
    }

    /** Rows of the chest being handed out (recovery on join) and chest rows of items that are also in the inventory. */
    record Recovery(List<WardrobeStore.Stored> releasing, java.util.Set<UUID> stale) {
    }

    CompletableFuture<Recovery> recovery(UUID profile, Collection<UUID> inventoryIds) {
        return db.async(c -> new Recovery(store.releasing(c, profile), store.inChest(c, inventoryIds)));
    }

    // ------------------------------------------------------------------ helpers

    @FunctionalInterface
    private interface Work {
        Result run(Connection c) throws SQLException;
    }

    private CompletableFuture<Result> plain(String what, Work work) {
        return safe(what, () -> db.transaction(work::run).exceptionally(e -> {
            Throwable cause = Database.cause(e);
            if (cause instanceof Refused r) {
                return Result.of(r.status);
            }
            log.log(Level.SEVERE, "Wardrobe: " + what + " failed and was rolled back", cause);
            return Result.of(Status.ERROR);
        }));
    }

    private CompletableFuture<Result> safe(String what, java.util.function.Supplier<CompletableFuture<Result>> start) {
        try {
            return start.get().exceptionally(e -> {
                log.log(Level.SEVERE, "Wardrobe: " + what + " failed", Database.cause(e));
                return Result.of(Status.ERROR);
            });
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Wardrobe: could not start " + what, e);
            return CompletableFuture.completedFuture(Result.of(Status.ERROR));
        }
    }
}
