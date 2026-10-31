package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.depths.DepthsZones;
import net.skybornmc.skyblock.common.economy.BankDepositEvent;
import net.skybornmc.skyblock.common.economy.BankService;
import net.skybornmc.skyblock.common.island.IslandJoinedEvent;
import net.skybornmc.skyblock.common.island.IslandRemovedEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.IslandLevelUpEvent;
import net.skybornmc.skyblock.common.progression.LevelSource;
import net.skybornmc.skyblock.common.progression.PillarLevelUpEvent;
import net.skybornmc.skyblock.common.skills.SkillLevelUpEvent;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;

/**
 * The dynamic island level (PHASE-5 M5.2): eight sources, caps, debounced recalculation, storage, notices and the
 * island boards. Main thread, except the database reads (members' skills of offline members, island bank balance,
 * storage) which run off it.
 *
 * <p><b>When it recalculates:</b> a pillar level-up, a member's skill level-up, an island bank deposit, a new member, a
 * block worth change or a finished rescan request a recalculation; requests wait {@code recalc.debounce-seconds} for
 * more (at most {@code recalc.max-wait-seconds}). Islands with an online member are refreshed every
 * {@code recalc.refresh-minutes} (members who left, withdrawals, the 3-day rule).</p>
 *
 * <p><b>Anti-abuse (REVIEW A1/A4):</b> coop players are never members; only members with 3+ days on the island count
 * for skills and Depths; a member who leaves is no longer a member, so their skill and Depths points leave with them;
 * the bank counts its lowest balance of 24 h on a log scale; block worth uses a square root and the 20% cap.</p>
 */
final class LevelManager implements Listener {

    private static final long DAY = 86_400_000L;
    private static final long COMPUTE_TIMEOUT_SECONDS = 30;
    private static final long RETRY_MILLIS = 60_000L;

    /** Everything known about one island's level. */
    static final class State {
        Map<LevelSource, Double> points = new EnumMap<>(LevelSource.class);
        Map<LevelSource, Double> raw = new EnumMap<>(LevelSource.class);
        Map<LevelSource, Double> limits = new EnumMap<>(LevelSource.class);
        double total;
        double earned;
        int level;
        int bestLevel;
        long reachedAt;
        long updatedAt;
        long scannedAt;
        long bankLow;
        boolean bankObserved;
        double blockWorth;
        boolean calculated;
        boolean detailed;
        int countedMembers;
        int members;
        double averageSkill;
        Map<SkillType, Integer> depthsBest = new EnumMap<>(SkillType.class);

        double progress(double divisor) {
            return LevelMath.progress(total, divisor);
        }
    }

    private record CachedXp(long at, Map<SkillType, Double> xp) {
    }

    private record CachedBalance(long at, long balance) {
    }

    private final Core core;
    private final LevelConfig config;
    private final ModuleMessages msg;
    private final PillarManager pillars;
    private final LevelStore store;
    private final BlockWorth blocks;
    private final int season;
    private final ZoneId zone;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, BankSamples> banks = new HashMap<>();
    private final Map<UUID, CachedXp> skillCache = new HashMap<>();
    private final Map<UUID, CachedBalance> bankCache = new HashMap<>();
    private final Map<UUID, Long> rescanCooldown = new HashMap<>();
    private final Set<UUID> computing = new HashSet<>();
    private final Set<UUID> again = new HashSet<>();
    private final Set<UUID> activeToday = new HashSet<>();
    private final LevelDebounce debounce = new LevelDebounce();
    private final Map<LevelSource, ToDoubleFunction<UUID>> providers = new ConcurrentHashMap<>();
    /** Runs on the main thread once the stored levels are loaded (M5.7: the boards rank right away). */
    private Runnable onLoaded = () -> { };
    private LevelMath.Weights weights;
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    /** Finished calculations waiting to be applied on the main thread (PERF7; filled from any thread). */
    private final Queue<Runnable> applies = new ConcurrentLinkedQueue<>();
    private Scheduler.Task loadRetry;
    private long lastNightlyDay = Long.MIN_VALUE;
    private boolean ready;
    private boolean closed;

    LevelManager(Core core, LevelConfig config, ModuleMessages msg, PillarManager pillars, LevelStore store, BlockWorth blocks,
                 int season) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.pillars = pillars;
        this.store = store;
        this.blocks = blocks;
        this.season = season;
        ZoneId z;
        try {
            z = ZoneId.of(config.timeZone);
        } catch (RuntimeException e) {
            core.plugin().getLogger().warning("progression.yml → blocks.time-zone: unknown zone '" + config.timeZone + "', using Europe/Oslo");
            z = ZoneId.of("Europe/Oslo");
        }
        this.zone = z;
        applyConfig();
    }

    // ------------------------------------------------------------------ lifecycle

    void applyConfig() {
        weights = LevelMath.Weights.of(config);
        if (!tasks.isEmpty()) {
            stopTasks();
            startTasks();
        }
    }

    void start() {
        startTasks();
        load();
    }

    private void startTasks() {
        tasks.add(core.scheduler().repeat("progression-level", 2L, 2L, this::tick));
        tasks.add(core.scheduler().repeat("progression-level-apply", 1L, 1L, this::drainApplies));
        long refresh = Math.max(1, config.refreshMinutes) * 60L * 20L;
        tasks.add(core.scheduler().repeat("progression-level-refresh", refresh, refresh, this::refreshOnline));
        tasks.add(core.scheduler().repeat("progression-nightly", 20L * 60, 20L * 60, this::nightly));
    }

    private void stopTasks() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
    }

    private void load() {
        long now = System.currentTimeMillis();
        long window = windowMillis();
        LevelMath.Weights w = weights;
        core.database().async(c -> {
            warmUp(w, zone);
            List<LevelStore.LevelRow> levels = store.loadLevels(c, season);
            List<LevelStore.WorthRow> worth = store.loadWorth(c, season);
            List<LevelStore.BankRow> bank = new ArrayList<>(store.loadLatestBefore(c, season, now - window, 0));
            bank.addAll(store.loadBankSamples(c, season, now - window));
            return new Object[]{levels, worth, bank};
        }).whenComplete((loaded, error) -> core.scheduler().main("progression-level-load", () -> {
            if (closed) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: loading island levels failed; retrying in 30 s", Database.cause(error));
                loadRetry = core.scheduler().later("progression-level-load-retry", 30 * 20L, this::load);
                return;
            }
            @SuppressWarnings("unchecked") List<LevelStore.LevelRow> levels = (List<LevelStore.LevelRow>) loaded[0];
            @SuppressWarnings("unchecked") List<LevelStore.WorthRow> worth = (List<LevelStore.WorthRow>) loaded[1];
            @SuppressWarnings("unchecked") List<LevelStore.BankRow> bank = (List<LevelStore.BankRow>) loaded[2];
            for (LevelStore.LevelRow row : levels) {
                State s = state(row.island());
                s.points = new EnumMap<>(row.points());
                s.total = row.total();
                s.earned = row.earned();
                s.level = row.level();
                s.bestLevel = Math.max(row.bestLevel(), row.level());
                s.reachedAt = row.reachedAt();
                s.updatedAt = row.updatedAt();
                s.scannedAt = row.scannedAt();
                s.bankLow = row.bankLow();
                s.blockWorth = row.blockWorth();
                s.calculated = true;
            }
            blocks.restore(worth);
            for (LevelStore.BankRow row : bank) {
                banks.computeIfAbsent(row.island(), k -> new BankSamples()).restore(row.at(), row.balance());
            }
            ready = true;
            core.plugin().getLogger().info("Progression: loaded " + levels.size() + " island levels (season " + season + ").");
            for (Player p : Bukkit.getOnlinePlayers()) {
                joined(p);
            }
            onLoaded.run();
        }));
    }

    /**
     * PERF7: runs the pure parts of a level calculation once off the main thread at load, so the first real calculation
     * (usually right after the first join) does not pay their first-run cost on the main thread: class initialisation of
     * the level math and its EnumMaps, lambda linking, and the time-zone rules read by the daily history row
     * ({@code LocalDate.now(zone)}). Nothing is stored; a failure only means no warm-up.
     */
    static void warmUp(LevelMath.Weights w, ZoneId zone) {
        try {
            LevelMath.calculate(new LevelMath.Inputs(1, 1, 1, 1, List.of(1, 2), 1, 1, 1), w);
            LevelRules.sum(LevelRules.skillTotals(List.of(new EnumMap<>(Map.of(SkillType.values()[0], 1)))));
            LocalDate.now(zone).toEpochDay();
        } catch (RuntimeException e) {
            // only a warm-up
        }
    }

    void shutdown() {
        closed = true;
        stopTasks();
        if (loadRetry != null) {
            loadRetry.cancel();
        }
    }

    private State state(UUID island) {
        return states.computeIfAbsent(island, k -> new State());
    }

    private long windowMillis() {
        return Math.max(1, config.bankWindowHours) * 3_600_000L;
    }

    // ------------------------------------------------------------------ requests

    /** Asks for a debounced recalculation. Main thread. */
    void request(UUID island) {
        if (island == null || closed) {
            return;
        }
        debounce.request(island, System.currentTimeMillis(), config.debounceSeconds * 1000L, config.maxWaitSeconds * 1000L);
    }

    private void requestNow(UUID island) {
        if (island != null && !closed) {
            debounce.request(island, System.currentTimeMillis(), 0, 0);
        }
    }

    /**
     * Starts the due recalculations: at most {@code recalc.per-tick}, and (PERF5) only while this tick is within
     * {@code recalc.budget-ms}; the rest
     * stay due and start in the next ticks.
     */
    private void tick() {
        if (!ready || closed) {
            return;
        }
        long now = System.currentTimeMillis();
        long start = System.nanoTime();
        long budget = Math.max(1, config.budgetMs) * 1_000_000L;
        int limit = Math.max(1, config.perTick);
        for (int started = 0; started < limit; started++) {
            if (started > 0 && System.nanoTime() - start >= budget) {
                break;
            }
            List<UUID> due = debounce.drainDue(now, 1);
            if (due.isEmpty()) {
                break;
            }
            UUID island = due.getFirst();
            if (computing.contains(island)) {
                again.add(island);
            } else {
                compute(island);
            }
        }
    }

    /**
     * Applies finished calculations (PERF7): at least one per tick, more only while the tick is within
     * {@code recalc.budget-ms} (the same budget the starts use), so a burst after a start is spread over ticks.
     */
    private void drainApplies() {
        if (!applies.isEmpty()) {
            drain(applies, System::nanoTime, Math.max(1, config.budgetMs) * 1_000_000L,
                    e -> core.plugin().getLogger().log(Level.WARNING, "Progression: applying an island level failed", e));
        }
    }

    /**
     * Runs queued work: always the first item, then more while less than {@code budgetNanos} passed since the start. A
     * failing item is reported and the next one still runs. Returns how many ran. Pure, unit-tested.
     */
    static int drain(Queue<Runnable> queue, java.util.function.LongSupplier clock, long budgetNanos,
                     java.util.function.Consumer<RuntimeException> onError) {
        long start = clock.getAsLong();
        int ran = 0;
        while (ran == 0 || clock.getAsLong() - start < budgetNanos) {
            Runnable work = queue.poll();
            if (work == null) {
                break;
            }
            ran++;
            try {
                work.run();
            } catch (RuntimeException e) {
                onError.accept(e);
            }
        }
        return ran;
    }

    private void refreshOnline() {
        if (!ready) {
            return;
        }
        for (UUID island : onlineIslands()) {
            activeToday.add(island);
            requestNow(island);
        }
    }

    private Set<UUID> onlineIslands() {
        Set<UUID> out = new HashSet<>();
        IslandService islands = islands();
        if (islands == null) {
            return out;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            islands.islandOf(p.getUniqueId()).ifPresent(i -> out.add(i.id()));
        }
        return out;
    }

    private IslandService islands() {
        return core.services().optional(IslandService.class).orElse(null);
    }

    // ------------------------------------------------------------------ calculation

    /** Gathers the inputs on the main thread, loads skills and bank off it, then applies the result on the main thread. */
    private void compute(UUID islandId) {
        IslandService islands = islands();
        if (islands == null) {
            return;
        }
        Optional<IslandService.IslandInfo> info = islands.island(islandId);
        if (info.isEmpty()) {
            forget(islandId);
            return;
        }
        long now = System.currentTimeMillis();
        Map<UUID, IslandService.Role> members = islands.members(islandId);
        List<UUID> counted = LevelRules.countedMembers(members.keySet(), m -> islands.memberSince(islandId, m), now,
                (long) (config.memberMinDays * DAY));
        int pillarLevels = pillars.totalPillarLevels(islandId);
        double upgrades = provided(LevelSource.UPGRADES, islandId);
        double achievements = provided(LevelSource.ACHIEVEMENTS, islandId);
        double collections = provided(LevelSource.COLLECTIONS, islandId);
        double worth = blocks.ledger().worth(islandId);

        List<CompletableFuture<Map<SkillType, Double>>> xp = new ArrayList<>(counted.size());
        // PERF5: offline members' XP and the bank balance read from the database are cached in the apply step (already
        // on the main thread) instead of one extra main-thread task per read.
        List<UUID> fetched = new ArrayList<>();
        for (UUID member : counted) {
            xp.add(memberXp(member, now, fetched));
        }
        boolean[] bankFetched = new boolean[1];
        CompletableFuture<OptionalLong> bank = bankBalance(islandId, now, bankFetched);
        computing.add(islandId);
        List<CompletableFuture<?>> all = new ArrayList<>(xp);
        all.add(bank);
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).orTimeout(COMPUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // PERF7: queued for the progression-level-apply task, also when every input was already cached (then the
                // futures are complete and the apply used to run inline, inside the progression-level tick that started it).
                .whenComplete((v, error) -> applies.add(() -> {
                    computing.remove(islandId);
                    if (closed) {
                        return;
                    }
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Progression: calculating the level of island " + islandId
                                + " failed; trying again in a minute", Database.cause(error));
                        debounce.request(islandId, System.currentTimeMillis(), RETRY_MILLIS, RETRY_MILLIS);
                        return;
                    }
                    List<Map<SkillType, Double>> memberXp = new ArrayList<>(xp.size());
                    for (int i = 0; i < xp.size(); i++) {
                        Map<SkillType, Double> value = xp.get(i).join();
                        memberXp.add(value);
                        UUID member = counted.get(i);
                        if (fetched.contains(member)) {
                            skillCache.put(member, new CachedXp(now, value));
                        }
                    }
                    OptionalLong balance = bank.join();
                    if (bankFetched[0] && balance.isPresent()) {
                        bankCache.put(islandId, new CachedBalance(now, balance.getAsLong()));
                    }
                    finish(islandId, members.size(), pillarLevels, upgrades, achievements, collections, memberXp, balance, worth, now);
                    if (again.remove(islandId)) {
                        request(islandId);
                    }
                }));
    }

    private double provided(LevelSource source, UUID islandId) {
        ToDoubleFunction<UUID> f = providers.get(source);
        if (f == null) {
            return 0;
        }
        try {
            // Timed on its own (PERF7): these come from other modules (upgrades, achievements, collections).
            double v = core.scheduler().timer().time("progression-level-source:" + source.id(), () -> f.applyAsDouble(islandId));
            return Double.isFinite(v) && v > 0 ? v : 0;
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Progression: level source '" + source.id() + "' failed", e);
            return 0;
        }
    }

    /** XP of exactly that profile: online = cache (cheap), offline = database at most every skills-cache-minutes. */
    private CompletableFuture<Map<SkillType, Double>> memberXp(UUID profileKey, long now, List<UUID> fetched) {
        SkillService skills = core.services().optional(SkillService.class).orElse(null);
        if (skills == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        if (ProfileService.of(core.services()).onlinePlayer(profileKey).isPresent()) {
            return skills.loadProfileXp(profileKey);
        }
        CachedXp cached = skillCache.get(profileKey);
        if (cached != null && now - cached.at() < config.skillsCacheMinutes * 60_000L) {
            return CompletableFuture.completedFuture(cached.xp());
        }
        fetched.add(profileKey); // cached by the apply step (main thread)
        return skills.loadProfileXp(profileKey).thenApply(Map::copyOf);
    }

    /** The island bank balance (cached for bank.sample-minutes; deposits clear the cache). Empty without a bank service. */
    private CompletableFuture<OptionalLong> bankBalance(UUID islandId, long now, boolean[] fetched) {
        BankService bank = core.services().optional(BankService.class).orElse(null);
        if (bank == null) {
            return CompletableFuture.completedFuture(OptionalLong.empty());
        }
        CachedBalance cached = bankCache.get(islandId);
        if (cached != null && now - cached.at() < config.bankSampleMinutes * 60_000L) {
            return CompletableFuture.completedFuture(OptionalLong.of(cached.balance()));
        }
        fetched[0] = true; // cached by the apply step (main thread)
        return bank.islandBalance(islandId).thenApply(balance -> OptionalLong.of(balance == null ? 0 : balance));
    }

    private void finish(UUID islandId, int memberCount, int pillarLevels, double upgrades, double achievements, double collections,
                        List<Map<SkillType, Double>> memberXp, OptionalLong balance, double worth, long now) {
        IslandService islands = islands();
        if (islands == null || islands.island(islandId).isEmpty()) {
            forget(islandId);
            return;
        }
        SkillService skills = core.services().optional(SkillService.class).orElse(null);
        DepthsZones depths = core.services().optional(DepthsZones.class).orElse(null);
        List<Map<SkillType, Integer>> memberLevels = new ArrayList<>(memberXp.size());
        for (Map<SkillType, Double> xp : memberXp) {
            Map<SkillType, Integer> levels = new EnumMap<>(SkillType.class);
            for (SkillType type : SkillType.values()) {
                levels.put(type, skills == null ? 0 : skills.levelFor(xp.getOrDefault(type, 0.0)));
            }
            memberLevels.add(levels);
        }
        List<Integer> totals = LevelRules.skillTotals(memberLevels);
        Map<SkillType, Integer> best = LevelRules.bestTiers(memberLevels,
                (skill, level) -> depths == null ? 0 : depths.unlockedTiers(skill, level));

        BankSamples samples = banks.computeIfAbsent(islandId, k -> new BankSamples());
        if (balance.isPresent() && samples.add(now, balance.getAsLong(), config.bankSampleMinutes * 60_000L)) {
            long b = balance.getAsLong();
            core.database().run(c -> store.addBankSample(c, season, islandId, now, b)).exceptionally(e -> {
                core.plugin().getLogger().log(Level.FINE, "Progression: saving a bank sample failed", e);
                return null;
            });
        }
        OptionalLong lowest = samples.lowest(now, windowMillis());

        LevelMath.Inputs inputs = new LevelMath.Inputs(pillarLevels, upgrades, achievements, collections, totals,
                LevelRules.sum(best.values()), lowest.orElse(0), worth);
        LevelMath.Result result = LevelMath.calculate(inputs, weights);

        State s = state(islandId);
        s.raw = result.raw();
        s.points = result.points();
        s.limits = result.limits();
        s.total = result.total();
        s.earned = result.earned();
        s.bankLow = lowest.orElse(0);
        s.bankObserved = lowest.isPresent();
        s.blockWorth = worth;
        s.countedMembers = memberLevels.size();
        s.members = memberCount;
        s.averageSkill = totals.isEmpty() ? 0 : LevelRules.sum(totals) / (double) totals.size();
        s.depthsBest = best;
        s.detailed = true;
        s.updatedAt = now;
        apply(islandId, s, result.level(), now);
    }

    /** New level: event, audit and notices on a change; always saved. */
    private void apply(UUID islandId, State s, int level, long now) {
        boolean first = !s.calculated;
        int old = s.level;
        int oldBest = s.bestLevel;
        s.calculated = true;
        if (level != old || first) {
            s.level = level;
            s.reachedAt = now;
        }
        if (level != old && !first) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("level_before", old);
            details.put("level_after", level);
            details.put("total_points", round(s.total));
            details.put("earned_points", round(s.earned));
            for (Map.Entry<LevelSource, Double> e : s.points.entrySet()) {
                details.put(e.getKey().id(), round(e.getValue()));
            }
            core.audit().log(AuditCategory.ISLAND, "island_level_change", Actor.system("progression"), islandId.toString(),
                    (long) level, null, details);
            Bukkit.getPluginManager().callEvent(new IslandLevelUpEvent(islandId, old, level));
        }
        s.bestLevel = Math.max(oldBest, level);
        // Only a new season high is announced (block worth going up and down never spams), never the first calculation.
        if (!first && level > oldBest && config.notifyLevelUp) {
            announce(islandId, oldBest, level);
        }
        save(islandId, s);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private void save(UUID islandId, State s) {
        LevelStore.LevelRow row = new LevelStore.LevelRow(islandId, s.total, s.earned, s.level, s.bestLevel, s.reachedAt,
                new EnumMap<>(s.points), s.blockWorth, s.bankLow, s.scannedAt, s.updatedAt);
        int day = (int) LocalDate.now(zone).toEpochDay();
        core.database().transaction(c -> {
            store.saveLevel(c, season, row, day);
            return null;
        }).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Progression: saving the level of island " + islandId + " failed (saved again "
                    + "with the next change)", Database.cause(e));
            return null;
        });
    }

    // ------------------------------------------------------------------ notices

    private void announce(UUID islandId, int oldBest, int level) {
        IslandService islands = islands();
        if (islands == null) {
            return;
        }
        ProfileService profiles = ProfileService.of(core.services());
        for (UUID member : islands.members(islandId).keySet()) {
            profiles.onlinePlayer(member).ifPresent(p -> {
                msg.title(p, "level.up.title", Msg.p("level", level));
                msg.send(p, "level.up.chat", Msg.p("level", level));
                core.sounds().play(p, config.levelUpSound);
            });
        }
        if (PillarManager.crossedMultiple(oldBest, level, config.globalEvery)) {
            int shown = level / config.globalEvery * config.globalEvery;
            String name = displayName(islandId);
            for (Player p : Bukkit.getOnlinePlayers()) {
                msg.send(p, "level.global", Msg.p("island", name), Msg.p("level", shown));
            }
        }
    }

    /** Island name, or its leader's name when the island has none. */
    String displayName(UUID islandId) {
        IslandService islands = islands();
        IslandService.IslandInfo info = islands == null ? null : islands.island(islandId).orElse(null);
        if (info == null) {
            return "?";
        }
        if (info.name() != null && !info.name().isBlank()) {
            return info.name();
        }
        UUID account = ProfileService.of(core.services()).ownerOf(info.owner());
        String name = account == null ? null : Bukkit.getOfflinePlayer(account).getName();
        return name == null ? "?" : name;
    }

    // ------------------------------------------------------------------ boards (M5.7: ranked by TopBoards)

    /** One calculated island's numbers for the boards. */
    record Snap(UUID island, int level, double total, long reachedAt, double earned, double blockWorth) {
    }

    /** The numbers of every calculated island, for {@link TopBoards} (main thread; ranking happens off it). */
    List<Snap> snapshot() {
        List<Snap> out = new ArrayList<>(states.size());
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            State s = e.getValue();
            if (s.calculated) {
                out.add(new Snap(e.getKey(), s.level, s.total, s.reachedAt, s.earned, blocks.ledger().worth(e.getKey())));
            }
        }
        return out;
    }

    /** Sets what runs once the stored levels are loaded (main thread). */
    void onLoaded(Runnable action) {
        this.onLoaded = action == null ? () -> { } : action;
    }

    // ------------------------------------------------------------------ rescans

    /** What {@link #recalculate} did. */
    enum Rescan { STARTED, RUNNING, COOLDOWN, NOT_MEMBER, NO_ISLAND }

    /** A member's Recalculate button: one full block rescan per island every recalculate-cooldown-minutes. */
    Rescan recalculate(Player player, Consumer<Double> done) {
        IslandService islands = islands();
        if (islands == null) {
            return Rescan.NO_ISLAND;
        }
        Optional<IslandService.IslandInfo> island = islands.islandOf(player.getUniqueId());
        if (island.isEmpty()) {
            return Rescan.NO_ISLAND;
        }
        UUID id = island.get().id();
        UUID key = ProfileService.of(core.services()).profileKey(player);
        if (!islands.countsForIsland(id, key)) {
            return Rescan.NOT_MEMBER;
        }
        if (blocks.scanning(id)) {
            return Rescan.RUNNING;
        }
        long now = System.currentTimeMillis();
        Long last = rescanCooldown.get(id);
        if (last != null && now - last < config.recalculateCooldownMinutes * 60_000L) {
            return Rescan.COOLDOWN;
        }
        rescanCooldown.put(id, now);
        double before = blocks.ledger().worth(id);
        rescan(id, after -> {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("worth_before", Math.round(before));
            details.put("worth_after", Math.round(after));
            core.audit().log(AuditCategory.ISLAND, "island_block_rescan", Actor.player(player), id.toString(), Math.round(after), null, details);
            done.accept(after);
        });
        return Rescan.STARTED;
    }

    /** Minutes until the island may rescan again (0 = now). */
    long cooldownMinutesLeft(UUID islandId) {
        Long last = rescanCooldown.get(islandId);
        if (last == null) {
            return 0;
        }
        long left = config.recalculateCooldownMinutes * 60_000L - (System.currentTimeMillis() - last);
        return left <= 0 ? 0 : (left + 59_999) / 60_000;
    }

    boolean scanning(UUID islandId) {
        return blocks.scanning(islandId);
    }

    /** Queues a full rescan; when it ends the island counts as scanned and its level is recalculated. */
    void rescan(UUID islandId, Consumer<Double> done) {
        blocks.queueRescan(islandId, after -> {
            state(islandId).scannedAt = System.currentTimeMillis();
            requestNow(islandId);
            if (done != null) {
                done.accept(after);
            }
        });
    }

    /** Nightly: full rescan of every island that changed or had a member online today; old bank samples are pruned. */
    private void nightly() {
        if (!ready || config.rescanHour < 0) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        long day = now.toLocalDate().toEpochDay();
        if (now.getHour() != config.rescanHour || day == lastNightlyDay) {
            return;
        }
        lastNightlyDay = day;
        Set<UUID> islands = blocks.ledger().drainTouched();
        islands.addAll(activeToday);
        islands.addAll(onlineIslands());
        activeToday.clear();
        for (UUID island : islands) {
            rescan(island, null);
        }
        pruneBank();
        core.plugin().getLogger().info("Progression: nightly block rescan of " + islands.size() + " islands queued.");
    }

    private void pruneBank() {
        long now = System.currentTimeMillis();
        Map<UUID, Long> keep = new HashMap<>();
        banks.forEach((island, samples) -> {
            if (samples.prune(now, windowMillis()) > 0) {
                keep.put(island, samples.oldest());
            }
        });
        if (!keep.isEmpty()) {
            core.database().transaction(c -> store.pruneBankSamples(c, season, keep)).exceptionally(e -> {
                core.plugin().getLogger().log(Level.WARNING, "Progression: pruning bank samples failed", Database.cause(e));
                return 0;
            });
        }
    }

    // ------------------------------------------------------------------ events

    private void joined(Player player) {
        IslandService islands = islands();
        if (islands == null) {
            return;
        }
        islands.islandOf(player.getUniqueId()).ifPresent(info -> {
            activeToday.add(info.id());
            request(info.id());
            State s = states.get(info.id());
            if (s == null || s.scannedAt == 0) {
                rescan(info.id(), null); // never counted: placed blocks from before tracking started
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (ready) {
            joined(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPillarLevelUp(PillarLevelUpEvent event) {
        if (event.shown()) {
            request(event.islandId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSkillLevelUp(SkillLevelUpEvent event) {
        IslandService islands = islands();
        if (islands != null) {
            islands.islandOf(event.getPlayer().getUniqueId()).ifPresent(i -> request(i.id()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBankDeposit(BankDepositEvent event) {
        if (!event.island()) {
            return;
        }
        IslandService islands = islands();
        if (islands != null) {
            islands.islandOf(event.getPlayer().getUniqueId()).ifPresent(i -> {
                bankCache.remove(i.id());
                request(i.id());
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandJoined(IslandJoinedEvent event) {
        request(event.islandId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandRemoved(IslandRemovedEvent event) {
        forget(event.islandId());
    }

    private void forget(UUID islandId) {
        states.remove(islandId);
        banks.remove(islandId);
        bankCache.remove(islandId);
        rescanCooldown.remove(islandId);
        debounce.forget(islandId);
        again.remove(islandId);
        activeToday.remove(islandId);
        blocks.forget(islandId);
    }

    // ------------------------------------------------------------------ reads (service)

    boolean ready() {
        return ready;
    }

    State view(UUID islandId) {
        return islandId == null ? null : states.get(islandId);
    }

    int level(UUID islandId) {
        State s = view(islandId);
        return s == null ? 0 : s.level;
    }

    boolean hasLevel(UUID islandId) {
        State s = view(islandId);
        return s != null && s.calculated;
    }

    double progress(UUID islandId) {
        State s = view(islandId);
        return s == null ? 0 : s.progress(weights.divisor());
    }

    double points(UUID islandId) {
        State s = view(islandId);
        return s == null ? 0 : s.total;
    }

    Map<LevelSource, Double> breakdown(UUID islandId) {
        State s = view(islandId);
        Map<LevelSource, Double> out = new EnumMap<>(LevelSource.class);
        for (LevelSource source : LevelSource.values()) {
            out.put(source, s == null ? 0.0 : s.points.getOrDefault(source, 0.0));
        }
        return out;
    }

    double divisor() {
        return weights.divisor();
    }

    void provider(LevelSource source, ToDoubleFunction<UUID> provider) {
        if (source == null) {
            return;
        }
        if (provider == null) {
            providers.remove(source);
        } else {
            providers.put(source, provider);
        }
    }

    boolean hasProvider(LevelSource source) {
        return source == LevelSource.PILLARS || source == LevelSource.SKILLS || source == LevelSource.DEPTHS
                || source == LevelSource.BANK || source == LevelSource.BLOCKS || providers.containsKey(source);
    }

    LevelConfig config() {
        return config;
    }
}
