package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.achievements.AchievementService;
import net.skybornmc.skyblock.common.achievements.IslandBoost;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.production.FuelProvider;
import net.skybornmc.skyblock.common.production.FuelState;
import net.skybornmc.skyblock.common.production.FuelView;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Island Fuel (PHASE-7 M7.1): every island's tank in memory (the authority while the module runs), the state check every
 * {@code fuel.check-seconds}, write-behind every {@code fuel.save-seconds}, grants and Fuel Cells. Implements the
 * {@link FuelProvider} the islands module delegates to.
 *
 * <p><b>Consistency:</b> every database write (saves, grants, cells) goes through one ordered chain, and grants and cells
 * change the memory value right away (taken back if the transaction fails or refuses). So a later save always carries
 * every earlier grant, and the conditional update in the grant's own transaction is the database-side guard.</p>
 *
 * <p>Main thread, except the database work.</p>
 */
final class FuelEngine implements FuelProvider {

    /** Upgrade effect id of the Fuel Tank ({@code upgrades.yml}) and mastery effect id of Fuel Efficiency (MA8). */
    static final String TANK_EFFECT = "fuel-tank";
    static final String EFFICIENCY_EFFECT = "fuel-efficiency";

    /** One island's tank in memory. */
    static final class IslandFuel {
        final UUID island;
        int season;
        long fuelMs;
        int tankLevel;
        FuelState state;
        long stateSince;
        long updatedAt;
        boolean dirty;

        IslandFuel(UUID island, int season, long fuelMs, int tankLevel, FuelState state, long stateSince, long updatedAt) {
            this.island = island;
            this.season = season;
            this.fuelMs = fuelMs;
            this.tankLevel = tankLevel;
            this.state = state;
            this.stateSince = stateSince;
            this.updatedAt = updatedAt;
        }

        FuelStore.Row row() {
            return new FuelStore.Row(island, season, fuelMs, tankLevel, state, stateSince, updatedAt);
        }
    }

    private final ProductionModule m;
    private final FuelStore store = new FuelStore();
    private final Map<UUID, IslandFuel> book = new HashMap<>();
    private final ActivityTracker activity;
    /** Islands with a Fuel Cell or grant in flight (one at a time per island, so a refusal is always right). */
    private final Set<UUID> busy = new HashSet<>();
    private CompletableFuture<?> writes = CompletableFuture.completedFuture(null);
    private volatile boolean loaded;
    private volatile boolean stopped;

    FuelEngine(ProductionModule module, ActivityTracker activity) {
        this.m = module;
        this.activity = activity;
    }

    boolean loaded() {
        return loaded;
    }

    // ================================================================== start, stop

    /** Loads every tank, burns the time since its last save (nobody was active while the server was down). */
    void load() {
        m.core.database().async(store::loadAll).whenCompleteAsync((rows, error) -> {
            if (stopped) {
                return;
            }
            if (error != null) {
                m.core.plugin().getLogger().log(Level.SEVERE, "Production: could not load Island Fuel; fuel stays off until the "
                        + "next start (spawners and crops are not paused)", Database.cause(error));
                return;
            }
            long now = System.currentTimeMillis();
            for (FuelStore.Row r : rows) {
                IslandFuel f = new IslandFuel(r.island(), r.season(), r.fuelMs(), r.tankLevel(), r.state(), r.stateSince(), r.updatedAt());
                book.put(r.island(), f);
                if (f.state != FuelState.ASLEEP) {
                    apply(f, false, now, false);
                }
            }
            loaded = true;
            m.core.plugin().getLogger().info("Production: Island Fuel loaded (" + rows.size() + " tanks).");
        }, m.main());
    }

    /** Final step and a blocking save of every changed tank (plugin stop or switch-off). */
    void stopBlocking() {
        if (stopped) {
            return;
        }
        stopped = true;
        if (!loaded) {
            return;
        }
        tick();
        try {
            writes.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            m.core.plugin().getLogger().log(Level.WARNING, "Production: earlier fuel writes did not finish in time", e);
        }
        List<FuelStore.Row> rows = dirtyRows();
        try {
            m.core.database().blockingTransaction(c -> {
                store.save(c, rows);
                return null;
            });
        } catch (RuntimeException e) {
            m.core.plugin().getLogger().log(Level.SEVERE, "Production: could not save Island Fuel on stop", Database.cause(e));
        }
        loaded = false;
    }

    // ================================================================== the state check

    /** Every island's step: active islands earn, the others burn, empty ones fall asleep. */
    void tick() {
        if (!loaded) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<UUID> active = activeIslands(now);
        for (UUID id : active) {
            if (!book.containsKey(id)) {
                book.put(id, fresh(id, now, FuelState.ASLEEP));
            }
        }
        for (IslandFuel f : book.values()) {
            boolean on = active.contains(f.island);
            if (!on && f.state == FuelState.ASLEEP && f.fuelMs <= 0) {
                f.updatedAt = now; // nothing to burn (not saved: an empty asleep tank never changes)
                continue;
            }
            if (islands().flatMap(i -> i.island(f.island)).isEmpty()) {
                continue; // unknown island (not loaded or removed): left as it is
            }
            apply(f, on, now, true);
        }
    }

    /** One state step of one island up to {@code now}; tells members when it just fell asleep. */
    private void apply(IslandFuel f, boolean active, long now, boolean notify) {
        int level = tankLevel(f.island);
        FuelMath.Step s = FuelMath.step(f.fuelMs, capacity(level), f.state, active, burnRate(f.island), now - f.updatedAt);
        if (s.state() != f.state) {
            f.stateSince = now;
        }
        f.fuelMs = s.fuelMs();
        f.state = s.state();
        f.tankLevel = level;
        f.updatedAt = now;
        f.dirty = true;
        if (s.fellAsleep() && notify && loaded) {
            fellAsleep(f);
        }
    }

    private IslandFuel fresh(UUID island, long now, FuelState state) {
        int season = islands().flatMap(i -> i.island(island)).map(IslandService.IslandInfo::seasonId).orElse(0);
        return new IslandFuel(island, season, 0, tankLevel(island), state, now, now);
    }

    /** Islands with a member on them who did something real within {@code fuel.active-minutes}. */
    private Set<UUID> activeIslands(long now) {
        Set<UUID> out = new HashSet<>();
        long window = m.settings.activeMinutes * FuelMath.MINUTE;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (activity.activeWithin(p.getUniqueId(), now, window)) {
                ownIslandHere(p).ifPresent(out::add);
            }
        }
        return out;
    }

    /**
     * The island of the player's <b>active profile</b> when they stand on it as a stored member (coop players and
     * visitors never count, REVIEW A4).
     */
    Optional<UUID> ownIslandHere(Player p) {
        Optional<IslandService> islands = islands();
        if (islands.isEmpty()) {
            return Optional.empty();
        }
        Optional<UUID> own = islands.get().islandOf(p.getUniqueId()).map(IslandService.IslandInfo::id);
        if (own.isEmpty()) {
            return Optional.empty();
        }
        UUID profile = ProfileService.of(m.core.services()).profileKey(p);
        if (!islands.get().countsForIsland(own.get(), profile)) {
            return Optional.empty();
        }
        Optional<UUID> here = islands.get().islandAt(p.getLocation()).map(IslandService.IslandInfo::id);
        return here.filter(own.get()::equals);
    }

    /** A real action: an Asleep or Fueled island of a member standing on it turns Active right away (no waiting). */
    void action(Player p) {
        if (!loaded || stopped || !p.isOnline()) {
            return;
        }
        Optional<UUID> island = ownIslandHere(p);
        if (island.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        IslandFuel f = book.computeIfAbsent(island.get(), id -> fresh(id, now, FuelState.ASLEEP));
        if (f.state == FuelState.ACTIVE) {
            return;
        }
        apply(f, false, now, false); // settle the burn up to now first (a member is here: no "fell asleep" line)
        f.state = FuelState.ACTIVE;
        f.stateSince = now;
        f.dirty = true;
    }

    private void fellAsleep(IslandFuel f) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("island", f.island.toString());
        m.core.audit().log(AuditCategory.PRODUCTION, "fuel_asleep", Actor.system(ProductionModule.ID), f.island.toString(), null, 0L, null, d);
        if (!m.settings.asleepMessage) {
            return;
        }
        for (Player p : onlineMembers(f.island)) {
            m.msg.send(p, "fuel.asleep-now");
            m.sound(p, m.settings.asleepSound);
        }
    }

    /** Online players whose active profile is a stored member of the island. */
    List<Player> onlineMembers(UUID island) {
        List<Player> out = new ArrayList<>();
        ProfileService profiles = ProfileService.of(m.core.services());
        islands().ifPresent(i -> {
            for (UUID member : i.members(island).keySet()) {
                profiles.onlinePlayer(member).ifPresent(out::add);
            }
        });
        return out;
    }

    // ================================================================== saving

    private List<FuelStore.Row> dirtyRows() {
        List<FuelStore.Row> rows = new ArrayList<>();
        for (IslandFuel f : book.values()) {
            if (f.dirty) {
                rows.add(f.row());
                f.dirty = false;
            }
        }
        return rows;
    }

    /** Write-behind of every changed tank (one batch, in the write chain). */
    void flush() {
        if (!loaded || stopped) {
            return;
        }
        List<FuelStore.Row> rows = dirtyRows();
        if (rows.isEmpty()) {
            return;
        }
        chain(() -> m.core.database().transaction(c -> {
            store.save(c, rows);
            return null;
        })).whenCompleteAsync((v, error) -> {
            if (error != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Production: could not save Island Fuel (retried at the next save)",
                        Database.cause(error));
                for (FuelStore.Row r : rows) {
                    IslandFuel f = book.get(r.island());
                    if (f != null) {
                        f.dirty = true;
                    }
                }
            }
        }, m.main());
    }

    /** Runs database work after every earlier write (main thread call). */
    private <T> CompletableFuture<T> chain(java.util.function.Supplier<CompletableFuture<T>> work) {
        CompletableFuture<T> next = writes.handle((v, e) -> null).thenCompose(v -> work.get());
        writes = next;
        return next;
    }

    /** The island was disbanded, deleted or reset. */
    void removed(UUID island) {
        book.remove(island);
        chain(() -> m.core.database().run(c -> store.delete(c, island))).exceptionally(e -> {
            m.core.plugin().getLogger().log(Level.WARNING, "Production: could not remove the fuel of island " + island, Database.cause(e));
            return null;
        });
    }

    // ================================================================== grants (FuelProvider) and cells

    @Override
    public boolean hasFuel(UUID islandId) {
        IslandFuel f = loaded ? book.get(islandId) : null;
        return f != null && f.state.running();
    }

    @Override
    public boolean paused(UUID islandId) {
        if (!loaded || !m.settings.pauseWhenAsleep) {
            return false;
        }
        IslandFuel f = book.get(islandId);
        return f == null || f.state == FuelState.ASLEEP;
    }

    @Override
    public CompletableFuture<Boolean> addFuel(UUID islandId, long minutes, String reason, Actor actor) {
        if (!loaded || stopped || minutes <= 0 || islands().flatMap(i -> i.island(islandId)).isEmpty() || busy.contains(islandId)) {
            return CompletableFuture.completedFuture(false);
        }
        long now = System.currentTimeMillis();
        IslandFuel f = book.computeIfAbsent(islandId, id -> fresh(id, now, FuelState.ASLEEP));
        int level = tankLevel(islandId);
        long cap = capacity(level);
        long addMs = minutes * FuelMath.MINUTE;
        long add = FuelMath.grantable(f.fuelMs, cap, addMs);
        if (add <= 0) {
            return CompletableFuture.completedFuture(false); // full tank: refused, nothing is used up
        }
        FuelStore.Row before = f.row();
        FuelState stateBefore = f.state;
        busy.add(islandId);
        added(f, add, now);
        return chain(() -> m.core.database().transaction(c -> store.grant(c, before, addMs, cap, now)))
                .handleAsync((result, error) -> {
                    busy.remove(islandId);
                    if (error != null || result == null || result.addedMs() <= 0) {
                        undo(islandId, add, stateBefore);
                        if (error != null) {
                            m.core.plugin().getLogger().log(Level.WARNING, "Production: could not add fuel to island " + islandId,
                                    Database.cause(error));
                        }
                        return false;
                    }
                    audit(islandId, result.addedMs(), result.fuelAfterMs(), reason, actor, null);
                    return true;
                }, m.main());
    }

    /** Memory side of a grant or cell: the fuel is in, an asleep island wakes (Fueled, or Active when a member plays). */
    private void added(IslandFuel f, long addMs, long now) {
        f.fuelMs += addMs;
        if (f.state == FuelState.ASLEEP) {
            f.state = FuelState.FUELED;
            f.stateSince = now;
        }
        f.dirty = true;
    }

    private void undo(UUID island, long addMs, FuelState stateBefore) {
        IslandFuel f = book.get(island);
        if (f == null) {
            return;
        }
        f.fuelMs = Math.max(0, f.fuelMs - addMs);
        if (stateBefore == FuelState.ASLEEP && f.state == FuelState.FUELED && f.fuelMs <= 0) {
            f.state = FuelState.ASLEEP;
        }
        f.dirty = true;
    }

    /** Why a Fuel Cell can't be used on this island now (message key), or empty. */
    Optional<String> cellProblem(UUID island, long cellMs) {
        if (!loaded) {
            return Optional.of("cell.loading");
        }
        if (busy.contains(island)) {
            return Optional.of("cell.busy");
        }
        IslandFuel f = book.get(island);
        long fuel = f == null ? 0 : f.fuelMs;
        return FuelMath.cellFits(fuel, capacity(tankLevel(island)), cellMs) ? Optional.empty() : Optional.of("cell.no-room");
    }

    /** Room left in the tank (ms). */
    long room(UUID island) {
        IslandFuel f = book.get(island);
        return Math.max(0, capacity(tankLevel(island)) - (f == null ? 0 : f.fuelMs));
    }

    /**
     * Adds a Fuel Cell whose item was already taken (the caller gives it back for NO_ROOM and ERROR): marks the cell used
     * and adds the fuel in one transaction.
     */
    CompletableFuture<CellUse.Outcome> addCell(UUID island, UUID itemUuid, UUID profileKey, long cellMs, String origin, Actor actor) {
        if (cellProblem(island, cellMs).isPresent()) {
            return CompletableFuture.completedFuture(CellUse.Outcome.NO_ROOM);
        }
        long now = System.currentTimeMillis();
        IslandFuel f = book.computeIfAbsent(island, id -> fresh(id, now, FuelState.ASLEEP));
        long cap = capacity(tankLevel(island));
        FuelStore.Row before = f.row();
        FuelState stateBefore = f.state;
        busy.add(island);
        added(f, cellMs, now);
        return chain(() -> m.core.database().transaction(c -> {
            if (store.cellUsed(c, itemUuid)) {
                return null; // a copy of a used cell
            }
            return store.useCell(c, before, itemUuid, profileKey, cellMs, cap, now);
        })).handleAsync((result, error) -> {
            busy.remove(island);
            if (error != null || result == null) {
                undo(island, cellMs, stateBefore);
                if (error == null) {
                    return CellUse.Outcome.REUSED;
                }
                if (Database.cause(error) instanceof FuelStore.NoRoom) {
                    return CellUse.Outcome.NO_ROOM;
                }
                m.core.plugin().getLogger().log(Level.WARNING, "Production: could not use a Fuel Cell on island " + island, Database.cause(error));
                return CellUse.Outcome.ERROR;
            }
            audit(island, result.addedMs(), result.fuelAfterMs(), "fuel_cell", actor, itemUuid);
            return CellUse.Outcome.ADDED;
        }, m.main());
    }

    private void audit(UUID island, long addedMs, long afterMs, String source, Actor actor, UUID item) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("island", island.toString());
        d.put("minutes", addedMs / FuelMath.MINUTE);
        d.put("source", source == null ? "" : source);
        d.put("tank_after_minutes", afterMs / FuelMath.MINUTE);
        if (item != null) {
            d.put("item", item.toString());
        }
        m.core.audit().log(AuditCategory.PRODUCTION, "fuel_add", actor == null ? Actor.SYSTEM : actor, island.toString(), null,
                addedMs / FuelMath.MINUTE, null, d);
    }

    // ================================================================== views

    FuelView view(UUID island) {
        int level = tankLevel(island);
        double efficiency = efficiency(island);
        double rate = FuelMath.burnRate(m.settings.burnRate, efficiency);
        IslandFuel f = loaded ? book.get(island) : null;
        if (f == null) {
            return new FuelView(FuelState.ASLEEP, 0, capacity(level), level, rate, efficiency, 0);
        }
        long fuel = f.fuelMs;
        if (f.state == FuelState.FUELED) {
            // between checks: show what is left right now
            fuel = Math.max(0, fuel - (long) Math.ceil((System.currentTimeMillis() - f.updatedAt) * rate));
        }
        return new FuelView(f.state, fuel, capacity(level), level, rate, efficiency, f.state == FuelState.ASLEEP ? f.stateSince : 0);
    }

    /** True when the island ran out of fuel (had a tank) and is asleep. */
    boolean ranOut(UUID island) {
        IslandFuel f = loaded ? book.get(island) : null;
        return f != null && f.state == FuelState.ASLEEP && f.fuelMs <= 0;
    }

    // ================================================================== tank and efficiency

    int tankLevel(UUID island) {
        double v = m.core.services().optional(ProgressionService.class).map(p -> p.upgradeEffectValue(island, TANK_EFFECT)).orElse(0.0);
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(v)));
    }

    long capacity(int level) {
        return FuelMath.capacityMs(m.settings.tankStartHours, m.settings.tankHoursPerLevel, level);
    }

    /** The Fuel Efficiency family of the island after its cap: MA8 mastery + island achievements. */
    double efficiency(UUID island) {
        double mastery = m.core.services().optional(ProgressionService.class).map(p -> p.masteryEffectValue(island, EFFICIENCY_EFFECT)).orElse(0.0);
        double achievements = m.core.services().optional(AchievementService.class)
                .map(a -> a.islandBoostPercent(island, IslandBoost.FUEL)).orElse(0.0);
        double cap = m.core.services().optional(BonusService.class).map(b -> b.cap(BonusFamily.FUEL_EFFICIENCY))
                .orElse(BonusFamily.FUEL_EFFICIENCY.defaultCap());
        return FuelMath.efficiency(mastery + achievements, cap);
    }

    double burnRate(UUID island) {
        return FuelMath.burnRate(m.settings.burnRate, efficiency(island));
    }

    private Optional<IslandService> islands() {
        return m.core.services().optional(IslandService.class);
    }

    void tellJoin(Player p) {
        if (!loaded || !m.settings.joinMessage) {
            return;
        }
        islands().flatMap(i -> i.islandOf(p.getUniqueId())).map(IslandService.IslandInfo::id).filter(this::ranOut).ifPresent(island -> {
            IslandFuel f = book.get(island);
            String ago = FuelMath.time(System.currentTimeMillis() - f.stateSince);
            m.msg.send(p, "fuel.asleep-join", Msg.p("time", ago));
        });
    }
}
