package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.skills.SkillLevelUpEvent;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.stacking.StackKillEvent;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolLevelUpEvent;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Tracks achievement progress of online players (F3). Everything here runs on the main thread; the only exception is
 * the audit listener, which filters on the audit thread in O(1) and hops to the main thread.
 *
 * <h2>Where the values come from (existing events and services only)</h2>
 * <ul>
 *   <li>Skill levels: {@link SkillLevelUpEvent} (instant) and {@link SkillService#level} (gauge poll).</li>
 *   <li>Stacked kills: {@link StackKillEvent} (killed, best batch).</li>
 *   <li>Tool level/prestige: {@link ToolLevelUpEvent} and the hotbar ({@link ToolService#read}) on the gauge poll.</li>
 *   <li>Sets: {@link WardrobeService#ownedSets} / {@link WardrobeService#setLevel}; balance: {@link EconomyService#cached}.</li>
 *   <li>Island stats: {@link IslandStatsService#snapshot}, one read per island every island poll (async).</li>
 *   <li>Sales, trades, applied enchants, island visits: the audit rows those modules write ({@link AuditFeeds}).</li>
 *   <li>Playtime: one minute per online minute.</li>
 * </ul>
 *
 * <h2>Storage</h2>
 * Progress is loaded on join and profile switch, saved in one batch every {@code storage.save-interval-seconds} and on
 * quit, switch and shutdown. Every database call of this module runs in one ordered chain, so a quit save always lands
 * before the next load of the same profile.
 */
final class Tracker implements Listener {

    private final AchievementsModule module;
    private final Core core;
    private final AchievementStore store = new AchievementStore();
    private final Map<UUID, ProfileProgress> online = new HashMap<>();
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    private CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
    /** Seconds counted by the gauge slice timer (main thread). */
    private long gaugeTick;
    /** Join gauge polls are spread over this many ticks. */
    private static final int JOIN_SPREAD_TICKS = 20;
    private Runnable stopAudit;
    private final IslandTracker islands;
    /** True while the season reset runs: no progress is added or saved until it finished. */
    private boolean resetting;

    Tracker(AchievementsModule module) {
        this.module = module;
        this.core = module.core();
        this.islands = new IslandTracker(module, this);
    }

    IslandTracker islands() {
        return islands;
    }

    boolean resetting() {
        return resetting;
    }

    private ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    private AchievementRegistry registry() {
        return module.registry();
    }

    private AchievementsConfig config() {
        return module.config();
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        stopAudit = core.audit().listen(this::onAudit);
        Bukkit.getOnlinePlayers().forEach(p -> attach(p, profiles().profileKey(p.getUniqueId())));
        schedule();
        islands.start();
    }

    /** (Re)starts the timers with the current settings. */
    void schedule() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        Scheduler s = core.scheduler();
        long save = 20L * config().saveIntervalSeconds;
        tasks.add(s.repeat("achievements-save", save, save, () -> {
            saveAll();
            islands.saveAll();
        }));
        // QUICK1 (P-12/P-20): every second one slice of the players, so each player is still polled every
        // gauge-poll-seconds but never all of them on the same tick.
        gaugeTick = 0;
        tasks.add(s.repeat("achievements-gauges", 40L, 20L, this::pollGaugeSlice));
        long islands = 20L * config().islandPollSeconds;
        tasks.add(s.repeat("achievements-islands", 200L, islands, this::pollIslands));
        tasks.add(s.repeat("achievements-playtime", 1200L, 1200L, this::playtimeTick));
    }

    /** Saves everything and waits (shutdown): the database closes right after the modules. */
    void shutdown() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        if (stopAudit != null) {
            stopAudit.run();
            stopAudit = null;
        }
        try {
            chain.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            core.plugin().getLogger().warning("Achievements: earlier writes did not finish in time: " + e.getMessage());
        }
        islands.unhook();
        islands.saveBlocking();
        long now = System.currentTimeMillis();
        for (ProfileProgress p : online.values()) {
            // Also profiles still loading: their values are deltas since join / best values, exactly what is written.
            List<ProfileProgress.Row> rows = p.drain();
            if (rows.isEmpty()) {
                continue;
            }
            try {
                core.database().blockingTransaction(c -> {
                    store.save(c, p.key(), rows, now);
                    return null;
                });
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.SEVERE, "Achievements: could not save the progress of " + p.key(), e);
            }
        }
        online.clear();
    }

    // ------------------------------------------------------------------ queries (main thread)

    Optional<ProfileProgress> progress(UUID player) {
        return Optional.ofNullable(online.get(player));
    }

    /** Hot path (bonus source, per block/kill): no Optional. */
    ProfileProgress progressOrNull(UUID player) {
        return online.get(player);
    }

    int completed(UUID player) {
        ProfileProgress p = online.get(player);
        return p == null || !p.loaded() ? 0 : Progression.completed(p, registry());
    }

    // ------------------------------------------------------------------ join, quit, profiles

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        attach(player, profiles().profileKey(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        detach(event.getPlayer().getUniqueId());
    }

    /** Profiles (PR1): progress follows the profile; the old one is saved, the new one loaded. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        detach(event.getPlayer().getUniqueId());
        attach(event.getPlayer(), event.to().id());
    }

    /** A deleted profile's progress and unlocks go with it; titles stay on the account. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        UUID key = event.profile().id();
        queue(c -> store.delete(c, key)).whenComplete((rows, error) -> {
            if (error != null) {
                core.plugin().getLogger().warning("Achievements: could not remove deleted profile " + key + ": " + Database.cause(error).getMessage());
                return;
            }
            core.audit().log(AuditCategory.ITEM, "achievements_profile_deleted", Actor.system(AchievementsModule.ID), key.toString(),
                    null, (long) rows, null, Map.of("profile", key.toString()));
        });
    }

    private void attach(Player player, UUID key) {
        UUID uuid = player.getUniqueId();
        ProfileProgress p = new ProfileProgress(key, uuid);
        online.put(uuid, p);
        p.loading(true);
        queue(c -> store.load(c, key)).whenComplete((loaded, error) -> core.scheduler().main("achievements-load", () -> {
            if (online.get(uuid) != p) {
                return; // left or switched again; detach saved what was earned meanwhile
            }
            if (error != null) {
                p.loading(false);
                core.plugin().getLogger().log(Level.WARNING, "Achievements: could not load the progress of " + player.getName()
                        + " (kept in memory, retried on the next join)", Database.cause(error));
                return;
            }
            p.load(loaded.values(), loaded.unlocks());
            p.points(registry(), config().perTier());
            Player online = Bukkit.getPlayer(uuid);
            handle(online, Progression.evaluateAll(p, registry()));
            // QUICK1 (PERFORMANCE "achievements-load"): the gauge poll (skills, wardrobe, balance, hotbar tools) runs a
            // little later, spread by player, so a join storm does not stack it on the load tick.
            core.scheduler().later("achievements-join-gauges", 1L + GaugeBuckets.bucket(uuid, JOIN_SPREAD_TICKS), () -> {
                Player now = Bukkit.getPlayer(uuid);
                if (now != null && this.online.get(uuid) == p && p.loaded()) {
                    pollGauges(now, p);
                }
            });
        }));
    }

    private void detach(UUID uuid) {
        ProfileProgress p = online.remove(uuid);
        if (p == null) {
            return;
        }
        // Counters are deltas and gauges maxima, so saving a profile that never finished loading is still correct.
        if (!resetting) { // during a season reset the progress is wiped anyway (a save queued after it would bring it back)
            save(p);
        }
    }

    // ------------------------------------------------------------------ saving

    private void saveAll() {
        if (resetting) {
            return;
        }
        for (ProfileProgress p : online.values()) {
            if (p.loaded()) {
                save(p);
            }
        }
    }

    private void save(ProfileProgress p) {
        save(p, 2);
    }

    /** Writes what changed; a failed write goes back into memory (online) or is tried again a few times (offline). */
    private void save(ProfileProgress p, int attempts) {
        List<ProfileProgress.Row> rows = p.drain();
        if (rows.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        queue(c -> saveRows(c, p.key(), rows, now)).whenComplete((v, error) -> {
            if (error == null) {
                return;
            }
            core.scheduler().main("achievements-retry", () -> {
                ProfileProgress current = null;
                for (ProfileProgress o : online.values()) {
                    if (o.key().equals(p.key())) {
                        current = o;
                    }
                }
                if (current != null) {
                    current.restore(rows); // saved with the next batch
                    core.plugin().getLogger().warning("Achievements: saving " + p.key() + " failed, retrying with the next save: "
                            + Database.cause(error).getMessage());
                } else if (attempts > 1) {
                    p.restore(rows);
                    save(p, attempts - 1);
                } else {
                    core.plugin().getLogger().severe("Achievements: progress of " + p.key() + " could not be saved: " + rows);
                }
            });
        });
    }

    private Void saveRows(java.sql.Connection c, UUID key, List<ProfileProgress.Row> rows, long now) throws java.sql.SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            store.save(c, key, rows, now);
            c.commit();
        } catch (java.sql.SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
        return null;
    }

    /** Runs database work after every earlier piece of this module's database work (one ordered chain). */
    synchronized <T> CompletableFuture<T> queue(Sql.SqlFunction<T> work) {
        CompletableFuture<T> next = chain.handle((v, e) -> null).thenCompose(v -> core.database().async(work));
        chain = next.handle((v, e) -> null);
        return next;
    }

    // ------------------------------------------------------------------ season reset (AC4, owner 354 / RTA-44)

    /** Result of a season reset: history rows written and progress rows wiped; {@code written = -1} = already done. */
    record ResetResult(int written, int wiped) {
    }

    /**
     * Season reset: saves what is in memory, writes the season summary (history) of every profile, then wipes every
     * personal and island progress row in <b>one transaction</b>. Titles stay on the accounts (cosmetics). Refuses to run
     * twice for the same season (the summary of that season exists): then only the pending progress is saved.
     * Main thread; the work runs in the module's ordered database chain.
     */
    CompletableFuture<ResetResult> seasonReset(int season, Actor actor) {
        if (resetting) {
            return CompletableFuture.failedFuture(new IllegalStateException("a season reset is already running"));
        }
        resetting = true;
        long now = System.currentTimeMillis();
        Map<UUID, List<ProfileProgress.Row>> personal = new HashMap<>();
        for (ProfileProgress p : online.values()) {
            List<ProfileProgress.Row> rows = p.drain();
            if (!rows.isEmpty()) {
                personal.put(p.key(), rows);
            }
        }
        Map<UUID, List<IslandProgress.Row>> islandRows = islands.drainAll();
        Map<UUID, UUID> memberIsland = islands.memberIslands();
        Map<UUID, UUID> memberOwner = new HashMap<>();
        ProfileService profiles = profiles();
        memberIsland.keySet().forEach(key -> memberOwner.put(key, profiles.ownerOf(key)));
        java.util.Set<UUID> team = islands.teamIslands();
        AchievementRegistry reg = registry();
        int[] perTier = config().perTier();
        CompletableFuture<ResetResult> result = queue(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                for (Map.Entry<UUID, List<ProfileProgress.Row>> e : personal.entrySet()) {
                    store.save(c, e.getKey(), e.getValue(), now);
                }
                for (Map.Entry<UUID, List<IslandProgress.Row>> e : islandRows.entrySet()) {
                    store.saveIsland(c, e.getKey(), e.getValue(), now);
                }
                if (store.hasHistory(c, season)) {
                    c.commit();
                    return new ResetResult(-1, 0);
                }
                List<SeasonSummary.Entry> entries = SeasonSummary.compute(season, reg, perTier, store.allUnlocks(c),
                        store.allIslandUnlocks(c), memberIsland, memberOwner, team);
                store.insertHistory(c, entries, now);
                int wiped = store.wipeSeason(c);
                c.commit();
                return new ResetResult(entries.size(), wiped);
            } catch (java.sql.SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        });
        CompletableFuture<ResetResult> done = new CompletableFuture<>();
        result.whenComplete((r, error) -> core.scheduler().main("achievements-season-reset", () -> {
            resetting = false;
            if (error != null) {
                // Nothing was wiped (rolled back): give the drained rows back so the next save writes them.
                for (ProfileProgress p : online.values()) {
                    List<ProfileProgress.Row> rows = personal.get(p.key());
                    if (rows != null) {
                        p.restore(rows);
                    }
                }
                islandRows.forEach((id, rows) -> islands.find(id).ifPresent(ip -> ip.restore(rows)));
                core.plugin().getLogger().log(Level.SEVERE, "Achievements: the season reset failed and changed nothing", Database.cause(error));
                done.completeExceptionally(Database.cause(error));
                return;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("season", season);
            details.put("history_rows", r.written());
            details.put("wiped_rows", r.wiped());
            core.audit().log(AuditCategory.ITEM, r.written() < 0 ? "achievements_season_reset_refused" : "achievements_season_reset",
                    actor == null ? Actor.system(AchievementsModule.ID) : actor, details);
            if (r.written() >= 0) {
                for (Map.Entry<UUID, ProfileProgress> e : online.entrySet()) {
                    ProfileProgress fresh = new ProfileProgress(e.getValue().key(), e.getValue().owner());
                    fresh.load(Map.of(), Map.of());
                    fresh.points(registry(), config().perTier());
                    e.setValue(fresh);
                    Player player = Bukkit.getPlayer(e.getKey());
                    if (player != null) {
                        module.msg().send(player, "season.reset", Msg.p("season", season));
                    }
                }
                islands.resetAll();
            }
            done.complete(r);
        }));
        return done;
    }

    // ------------------------------------------------------------------ adding progress

    /** Adds to the active profile of an online player. */
    void add(UUID player, Metric metric, long amount) {
        ProfileProgress p = online.get(player);
        if (p == null || resetting || !registry().usedMetrics().contains(metric)) {
            return;
        }
        handle(Bukkit.getPlayer(player), Progression.apply(p, registry(), metric, amount));
    }

    /** Adds to a named profile only while it is the player's active one (sales name the profile they paid). */
    private void add(UUID player, UUID profileKey, Metric metric, long amount) {
        ProfileProgress p = online.get(player);
        if (p == null || (profileKey != null && !p.key().equals(profileKey))) {
            return;
        }
        add(player, metric, amount);
    }

    private void onAudit(AuditEntry entry) {
        if (!AuditFeeds.relevant(entry)) {
            return;
        }
        UUID liked = AuditFeeds.islandLiked(entry);
        if (liked != null) {
            core.scheduler().ensureMain("achievements-island-like", () -> islands.add(liked, IslandMetric.LIKES, 1));
            return;
        }
        List<AuditFeeds.Feed> feeds = AuditFeeds.map(entry);
        if (feeds.isEmpty()) {
            return;
        }
        core.scheduler().ensureMain("achievements-audit", () -> feeds.forEach(f -> add(f.player(), f.profileKey(), f.metric(), f.amount())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSkillLevel(SkillLevelUpEvent event) {
        Player player = event.getPlayer();
        ProfileProgress p = online.get(player.getUniqueId());
        if (p != null) {
            pollSkills(player, p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStackKill(StackKillEvent event) {
        Player killer = event.killer();
        if (killer == null) {
            return;
        }
        add(killer.getUniqueId(), Metric.STACKED_KILLS, event.killed());
        add(killer.getUniqueId(), Metric.BEST_STACK_KILL, event.killed());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onToolLevel(ToolLevelUpEvent event) {
        ToolInfo tool = event.getTool();
        add(event.getPlayer().getUniqueId(), Metric.TOOL_BEST_LEVEL, tool.level());
        add(event.getPlayer().getUniqueId(), Metric.TOOL_BEST_PRESTIGE, tool.prestige());
    }

    private void playtimeTick() {
        if (!config().playtime) {
            return;
        }
        for (UUID uuid : List.copyOf(online.keySet())) {
            add(uuid, Metric.PLAYTIME_MINUTES, 1);
            ProfileProgress p = online.get(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (p != null && player != null && !resetting) {
                islands.minute(player, p.key());
            }
        }
    }

    // ------------------------------------------------------------------ gauges

    /** One slice per second: the players whose bucket matches this second. */
    private void pollGaugeSlice() {
        int slices = Math.max(1, config().gaugePollSeconds);
        int slice = (int) (gaugeTick++ % slices);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (GaugeBuckets.bucket(player.getUniqueId(), slices) != slice) {
                continue;
            }
            ProfileProgress p = online.get(player.getUniqueId());
            if (p != null && p.loaded()) {
                pollGauges(player, p);
            }
        }
    }

    private void pollGauges(Player player, ProfileProgress p) {
        pollSkills(player, p);
        UUID uuid = player.getUniqueId();
        core.services().optional(WardrobeService.class).ifPresent(w -> {
            var sets = w.ownedSets(p.key());
            add(uuid, Metric.WARDROBE_SETS_OWNED, sets.size());
            int best = 0;
            for (String set : sets) {
                best = Math.max(best, w.setLevel(p.key(), set));
            }
            add(uuid, Metric.WARDROBE_BEST_LEVEL, best);
        });
        core.services().optional(EconomyService.class).ifPresent(e -> {
            OptionalLong balance = e.cached(uuid, Currency.MONEY);
            if (balance.isPresent()) {
                add(uuid, Metric.BALANCE_PEAK, balance.getAsLong());
            }
        });
        if (config().hotbarTools) {
            core.services().optional(ToolService.class).ifPresent(tools -> {
                PlayerInventory inv = player.getInventory();
                int level = 0;
                int prestige = 0;
                for (int slot = 0; slot < 9; slot++) {
                    ItemStack item = inv.getItem(slot);
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }
                    Optional<ToolInfo> info = tools.read(item);
                    if (info.isPresent()) {
                        level = Math.max(level, info.get().level());
                        prestige = Math.max(prestige, info.get().prestige());
                    }
                }
                add(uuid, Metric.TOOL_BEST_LEVEL, level);
                add(uuid, Metric.TOOL_BEST_PRESTIGE, prestige);
            });
        }
    }

    private static final Map<SkillType, Metric> SKILL_METRICS = new EnumMap<>(Map.of(
            SkillType.MINING, Metric.SKILL_MINING,
            SkillType.FORAGING, Metric.SKILL_FORAGING,
            SkillType.COMBAT, Metric.SKILL_COMBAT,
            SkillType.FARMING, Metric.SKILL_FARMING,
            SkillType.FISHING, Metric.SKILL_FISHING));

    private void pollSkills(Player player, ProfileProgress p) {
        Optional<SkillService> skills = core.services().optional(SkillService.class);
        if (skills.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        int total = 0;
        int best = 0;
        for (SkillType skill : SkillType.values()) {
            int level = skills.get().level(uuid, skill);
            add(uuid, SKILL_METRICS.get(skill), level);
            if (skills.get().available(skill)) {
                total += level;
            }
            best = Math.max(best, level);
        }
        add(uuid, Metric.SKILL_TOTAL, total);
        add(uuid, Metric.SKILL_BEST, best);
    }

    private static final Map<IslandStat, Metric> ISLAND_METRICS = new EnumMap<>(Map.of(
            IslandStat.BLOCKS_MINED, Metric.ISLAND_BLOCKS_MINED,
            IslandStat.LOGS_CHOPPED, Metric.ISLAND_LOGS_CHOPPED,
            IslandStat.CROPS_HARVESTED, Metric.ISLAND_CROPS_HARVESTED,
            IslandStat.BLOCKS_PLACED, Metric.ISLAND_BLOCKS_PLACED,
            IslandStat.MOBS_KILLED, Metric.ISLAND_MOBS_KILLED));

    /** One snapshot per island with an online member; values land on the main thread. */
    private void pollIslands() {
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        Optional<IslandStatsService> stats = core.services().optional(IslandStatsService.class);
        if (islands.isEmpty() || stats.isEmpty()) {
            return;
        }
        Map<UUID, List<UUID>> playersByIsland = new LinkedHashMap<>();
        for (Map.Entry<UUID, ProfileProgress> e : online.entrySet()) {
            if (!e.getValue().loaded()) {
                continue;
            }
            islands.get().islandOfProfile(e.getValue().key())
                    .ifPresent(info -> playersByIsland.computeIfAbsent(info.id(), id -> new ArrayList<>()).add(e.getKey()));
        }
        playersByIsland.forEach((islandId, players) -> stats.get().snapshot(islandId).whenComplete((snap, error) -> {
            if (error != null || snap == null) {
                return;
            }
            core.scheduler().main("achievements-island-stats", () -> {
                this.islands.onSnapshot(islandId, snap);
                for (UUID uuid : players) {
                    ProfileProgress p = online.get(uuid);
                    if (p == null) {
                        continue;
                    }
                    ISLAND_METRICS.forEach((stat, metric) -> add(uuid, metric, snap.member(p.key(), stat).allTime()));
                    add(uuid, Metric.ISLAND_VISITORS, snap.totals(IslandStat.VISITORS).allTime());
                }
            });
        }));
    }

    // ------------------------------------------------------------------ unlocks

    /** Re-checks every online profile (after a reload changed goals). */
    void reevaluateAll() {
        for (Map.Entry<UUID, ProfileProgress> e : online.entrySet()) {
            handle(Bukkit.getPlayer(e.getKey()), Progression.evaluateAll(e.getValue(), registry()));
            if (e.getValue().loaded()) {
                e.getValue().points(registry(), config().perTier());
            }
        }
        islands.reload();
    }

    /** Stores, audits and shows unlocks; grants the reward title to the account on the last tier. */
    private void handle(Player player, List<Progression.Unlock> unlocks) {
        if (unlocks.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int[] perTier = config().perTier();
        for (Progression.Unlock u : unlocks) {
            ProfileProgress p = online.get(u.owner());
            if (p != null && p.key().equals(u.profileKey())) {
                p.points(registry(), perTier); // boosts follow at once (one pass over the registry per unlock batch is cheap)
            }
        }
        for (Progression.Unlock u : unlocks) {
            long value = progressValue(u); // main thread (the online map); the rest runs where the write finished
            queue(c -> store.unlock(c, u.profileKey(), u.def().id(), u.tier(), u.owner(), now)).whenComplete((added, error) -> {
                // QUICK1 (PERFORMANCE "achievements-unlock"): the audit log is thread-safe and never blocks, so the row
                // is written from the database thread; only a reward title hops to the main thread.
                if (error != null) {
                    core.plugin().getLogger().warning("Achievements: could not store " + u.def().id() + " tier " + u.tier()
                            + " for " + u.profileKey() + ": " + Database.cause(error).getMessage());
                    return;
                }
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("achievement", u.def().id());
                details.put("tier", u.tier());
                details.put("tiers", u.def().tiers());
                details.put("profile", u.profileKey().toString());
                details.put("value", value);
                details.put("points", BoostMath.tierPoints(perTier, u.tier()));
                details.put("boost", registry().boostOf(u.def()).id());
                details.put("new", added);
                if (u.title() != null) {
                    details.put("title", u.title());
                }
                core.audit().logPlayer(AuditCategory.ITEM, "achievement_unlock", Actor.system(AchievementsModule.ID), u.owner(),
                        null, null, null, details);
                if (u.title() != null) {
                    core.scheduler().main("achievements-unlock", () -> grantTitle(u));
                }
            });
        }
        if (player != null && player.isOnline()) {
            module.toasts().show(player, unlocks);
        }
    }

    private long progressValue(Progression.Unlock u) {
        ProfileProgress p = online.get(u.owner());
        return p != null && p.key().equals(u.profileKey()) ? p.value(u.def().metric()) : -1;
    }

    private void grantTitle(Progression.Unlock u) {
        Optional<Cosmetics> cosmetics = core.services().optional(Cosmetics.class);
        if (cosmetics.isEmpty()) {
            return;
        }
        cosmetics.get().grant(u.owner(), Cosmetics.Kind.TITLE, u.title(), "achievement:" + u.def().id(), false)
                .whenComplete((added, error) -> core.scheduler().ensureMain("achievements-title", () -> {
                    if (error != null) {
                        core.plugin().getLogger().warning("Achievements: reward title '" + u.title() + "' of " + u.def().id()
                                + " could not be granted: " + Database.cause(error).getMessage());
                        return;
                    }
                    Player player = Bukkit.getPlayer(u.owner());
                    if (Boolean.TRUE.equals(added) && player != null) {
                        module.msg().send(player, "unlock.title", Msg.c("display", cosmetics.get().display(player, Cosmetics.Kind.TITLE, u.title())));
                    }
                }));
    }
}
