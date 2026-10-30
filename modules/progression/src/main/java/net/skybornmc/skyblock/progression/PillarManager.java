package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.progression.PillarLevelUpEvent;
import net.skybornmc.skyblock.common.progression.PillarMilestoneEvent;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.skills.SkillXpGainEvent;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;

/**
 * The pillars (PHASE-5 M5.1) and the {@link ProgressionService}. Everything runs on the main thread except the database
 * work. All islands' pillars of the season are loaded at start; XP is added in memory and written as deltas in batches
 * ({@link PillarBuffer}), so a crash loses at most one flush interval and nothing is counted twice.
 *
 * <p>XP sources: the base skill XP of island members ({@link #onSkillXp}) and the island actions of
 * {@link IslandSourcesListener}. Coop players and visitors never count ({@link IslandService#countsForIsland}).</p>
 */
final class PillarManager implements ProgressionService, Listener {

    static final String TOGGLE = "pillar-notices";
    static final String LEDGER_REASON = "pillar_milestone";

    private final Core core;
    private final PillarsConfig config;
    private final ModuleMessages msg;
    private final PillarStore store = new PillarStore();
    private final PillarBuffer buffer = new PillarBuffer();
    private final Map<UUID, IslandPillars> islands = new HashMap<>();
    private final Map<String, ToDoubleFunction<UUID>> boosts = new ConcurrentHashMap<>();
    private final int season;
    private final ZoneId zone;

    private PillarCurve curve;
    private List<Pillar> shown = List.of();
    private Map<SkillType, PillarRules.SkillSource> skillSources = Map.of();
    private Set<String> excludedSources = Set.of();
    private List<Integer> milestones = List.of();
    private Map<Integer, Long> rewards = Map.of();
    private boolean ready;
    private boolean closed;
    private Scheduler.Task flushTask;
    private Scheduler.Task loadRetry;
    private CompletableFuture<?> inFlight = CompletableFuture.completedFuture(null);

    PillarManager(Core core, PillarsConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.season = config.seasonId;
        ZoneId z;
        try {
            z = ZoneId.of(config.weekTimeZone);
        } catch (RuntimeException e) {
            core.plugin().getLogger().warning("pillars.yml → week-time-zone: unknown zone '" + config.weekTimeZone + "', using Europe/Oslo");
            z = ZoneId.of("Europe/Oslo");
        }
        this.zone = z;
        applyConfig();
    }

    // ------------------------------------------------------------------ lifecycle

    /** (Re)reads pillars.yml. A changed curve recomputes the levels (they can rise, never fall). */
    void applyConfig() {
        java.util.function.Consumer<String> problem = p -> core.plugin().getLogger().warning(p);
        PillarCurve next = new PillarCurve(config.curveBase, config.curveGrowth);
        boolean curveChanged = curve != null && (curve.base() != next.base() || curve.growth() != next.growth());
        curve = next;
        shown = PillarRules.shown(config.conquestEnabled);
        skillSources = PillarRules.skillSources(config.skillSources, problem);
        excludedSources = PillarRules.lowerSet(config.excludedSources);
        milestones = PillarRules.levels(config.milestoneLevels, problem);
        rewards = PillarRules.rewards(config.milestoneCrystals, problem);
        if (curveChanged) {
            islands.values().forEach(s -> s.relevel(curve));
        }
        if (flushTask != null) {
            flushTask.cancel();
            scheduleFlush();
        }
    }

    void start() {
        scheduleFlush();
        load();
    }

    private void scheduleFlush() {
        long period = Math.max(2, config.flushSeconds) * 20L;
        flushTask = core.scheduler().repeat("progression-flush", period, period, this::flush);
    }

    /** Loads every stored pillar of the season; gains made meanwhile are kept and added on top. Retries on failure. */
    private void load() {
        core.database().async(c -> store.load(c, season)).whenComplete((loaded, error) -> core.scheduler().main("progression-load", () -> {
            if (closed) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: loading the island pillars failed; retrying in 30 s",
                        Database.cause(error));
                loadRetry = core.scheduler().later("progression-load-retry", 30 * 20L, this::load);
                return;
            }
            for (PillarStore.Row row : loaded.rows()) {
                state(row.island()).addStored(row.pillar(), row.xp(), row.level(), curve);
            }
            loaded.milestones().forEach((island, reached) -> state(island).milestones().addAll(reached));
            ready = true;
            core.plugin().getLogger().info("Progression: loaded the pillars of " + islands.size() + " islands (season " + season + ").");
        }));
    }

    /** Shutdown: waits for the running write, then writes the rest blocking. */
    void shutdown() {
        closed = true;
        if (flushTask != null) {
            flushTask.cancel();
        }
        if (loadRetry != null) {
            loadRetry.cancel();
        }
        try {
            inFlight.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // a failed write merged its batch back; it is written below
        }
        PillarBuffer.Batch batch = buffer.drain(this::levelOf);
        if (batch.isEmpty()) {
            return;
        }
        try {
            core.database().blockingTransaction(c -> store.write(c, season, batch, System.currentTimeMillis()));
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.SEVERE, "Progression: " + batch.xp().size()
                    + " pillar XP rows could not be saved on shutdown", e);
        }
    }

    /** Writes the buffered XP (every flush-seconds). Waits for the stored pillars first, so a load never misses a write. */
    void flush() {
        if (!ready || closed || !inFlight.isDone() || buffer.isEmpty()) {
            return;
        }
        PillarBuffer.Batch batch = buffer.drain(this::levelOf);
        long now = System.currentTimeMillis();
        inFlight = core.database().transaction(c -> store.write(c, season, batch, now)).whenComplete((rows, error) -> {
            if (error != null) {
                buffer.mergeBack(batch);
                core.plugin().getLogger().log(Level.WARNING, "Progression: writing pillar XP failed; retrying with the next flush",
                        Database.cause(error));
            }
        });
    }

    private int levelOf(PillarBuffer.Key key) {
        IslandPillars s = islands.get(key.island());
        return s == null ? 0 : s.level(key.pillar());
    }

    private IslandPillars state(UUID island) {
        return islands.computeIfAbsent(island, k -> new IslandPillars());
    }

    int weekStart() {
        return PillarRules.weekStart(LocalDate.now(zone));
    }

    // ------------------------------------------------------------------ sources

    /**
     * Skill XP of island members → their island's pillar (base XP only, RK20). MONITOR + not cancelled: the XP was
     * really given. Excluded sources (stack kills) and skills without a pillar give nothing.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkillXp(SkillXpGainEvent event) {
        PillarRules.SkillSource source = skillSources.get(event.getSkill());
        if (source == null || PillarRules.excluded(event.getSource(), excludedSources)) {
            return;
        }
        double base = PillarRules.baseXp(event.getBaseAmount(), event.getAmount()) * source.multiplier();
        if (base > 0) {
            gainFor(event.getPlayer(), source.pillar(), base, event.getSource());
        }
    }

    /** Adds base XP to the island of {@code player} if they count for it (stored member, never coop). */
    void gainFor(Player player, Pillar pillar, double baseXp, String source) {
        IslandService islandService = core.services().optional(IslandService.class).orElse(null);
        if (islandService == null || player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Optional<IslandService.IslandInfo> island = islandService.islandOf(uuid);
        if (island.isEmpty() || !islandService.countsForIsland(island.get().id(), uuid)) {
            return;
        }
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        gain(island.get().id(), pillar, baseXp, profile, source, null);
    }

    /**
     * The one place pillar XP is added: applies the island's ISLAND_XP family, buffers the delta and handles level-ups.
     *
     * @param cause member profile key whose action it was (nullable)
     * @param staff staff actor for audited manual adds (nullable = no per-gain audit)
     * @return the XP added
     */
    double gain(UUID islandId, Pillar pillar, double baseXp, UUID cause, String source, Actor staff) {
        if (closed || islandId == null || pillar == null || !(baseXp > 0) || Double.isInfinite(baseXp)) {
            return 0;
        }
        IslandPillars s = state(islandId);
        double amount = baseXp * PillarRules.multiplier(islandXpBonusPercent(islandId));
        IslandPillars.LevelChange change = s.add(pillar, amount, curve);
        int levelsGained = ready && change != null ? change.to() - change.from() : 0;
        buffer.add(islandId, pillar, weekStart(), amount, levelsGained);
        if (staff != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("pillar", pillar.id());
            details.put("base_xp", baseXp);
            details.put("xp", amount);
            details.put("source", source == null ? "" : source);
            core.audit().log(AuditCategory.ISLAND, "pillar_xp_add", staff, islandId.toString(), Math.round(amount), null, details);
        }
        if (ready && change != null) {
            levelUp(islandId, s, change, cause, source);
        }
        return amount;
    }

    // ------------------------------------------------------------------ level-ups and milestones

    private void levelUp(UUID islandId, IslandPillars s, IslandPillars.LevelChange change, UUID cause, String source) {
        boolean isShown = shown.contains(change.pillar());
        Bukkit.getPluginManager().callEvent(new PillarLevelUpEvent(islandId, change.pillar(), change.from(), change.to(), cause, isShown));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("pillar", change.pillar().id());
        details.put("level_before", change.from());
        details.put("level_after", change.to());
        details.put("source", source == null ? "" : source);
        if (cause != null) {
            details.put("cause", cause.toString());
        }
        core.audit().log(AuditCategory.ISLAND, "pillar_level_up", Actor.system("progression"), islandId.toString(),
                (long) change.to(), null, details);
        if (!isShown) {
            return;
        }
        notifyLevelUp(islandId, change, cause);
        for (int m : PillarRules.newlyReached(milestones, PillarRules.lowest(s.levels(), shown), s.milestones())) {
            s.milestones().add(m); // guard in memory at once; removed again if saving fails
            reachMilestone(islandId, m);
        }
    }

    /** Saves the milestone and pays the members' crystals in one database transaction, then tells the island. */
    private void reachMilestone(UUID islandId, int milestone) {
        IslandService islandService = core.services().optional(IslandService.class).orElse(null);
        List<UUID> members = islandService == null ? List.of() : new ArrayList<>(islandService.members(islandId).keySet());
        long crystals = rewards.getOrDefault(milestone, 0L);
        EconomyService economy = core.services().optional(EconomyService.class).orElse(null);
        long now = System.currentTimeMillis();
        CompletableFuture<Boolean> saved;
        if (crystals > 0 && economy != null && !members.isEmpty()) {
            List<Transaction> txs = new ArrayList<>();
            for (UUID member : members) {
                txs.add(Transaction.deposit(AccountRef.player(member), Currency.CRYSTALS, crystals, LEDGER_REASON)
                        .actor(Actor.system("progression"))
                        .reference("pillars:" + milestone)
                        .idempotencyKey(idempotencyKey(season, islandId, milestone, member))
                        .detail("island", islandId.toString()));
            }
            saved = economy.executeBatch(txs, c -> store.markMilestone(c, islandId, season, milestone, now))
                    .thenApply(PillarManager::outcome);
        } else {
            if (crystals > 0 && economy == null) {
                core.plugin().getLogger().warning("Progression: no economy service; milestone " + milestone + " crystals not paid");
            }
            saved = core.database().transaction(c -> store.markMilestone(c, islandId, season, milestone, now));
        }
        long paid = crystals > 0 && economy != null ? crystals : 0;
        saved.whenComplete((ok, error) -> core.scheduler().main("progression-milestone", () -> {
            if (error != null || ok == null) {
                state(islandId).milestones().remove(milestone);
                core.plugin().getLogger().log(Level.WARNING, "Progression: saving milestone " + milestone + " of island " + islandId
                        + " failed; it is tried again on the next pillar level-up", error == null ? null : Database.cause(error));
                return;
            }
            if (!ok) {
                return; // already reached (saved before): nothing paid twice, nothing shown twice
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("milestone", milestone);
            details.put("crystals_per_member", paid);
            details.put("members", members.size());
            core.audit().log(AuditCategory.ISLAND, "pillar_milestone", Actor.system("progression"), islandId.toString(),
                    (long) milestone, null, details);
            Bukkit.getPluginManager().callEvent(new PillarMilestoneEvent(islandId, milestone));
            notifyMilestone(islandId, milestone, paid);
        }));
    }

    /** True = paid and saved; false = it was already saved; null = failed (retry later). */
    private static Boolean outcome(BatchResult result) {
        if (result.ok()) {
            return true;
        }
        return result.status() == TxResult.Status.DUPLICATE ? Boolean.FALSE : null;
    }

    /** {@code pillar_ms:<season>:<island hex>:<milestone>:<member hex>}, at most ~85 characters (limit 96). */
    static String idempotencyKey(int season, UUID islandId, int milestone, UUID member) {
        return "pillar_ms:" + season + ":" + hex(islandId) + ":" + milestone + ":" + hex(member);
    }

    private static String hex(UUID id) {
        return id.toString().replace("-", "");
    }

    // ------------------------------------------------------------------ notices

    private List<Player> onlineMembers(UUID islandId) {
        IslandService islandService = core.services().optional(IslandService.class).orElse(null);
        if (islandService == null) {
            return List.of();
        }
        ProfileService profiles = ProfileService.of(core.services());
        List<Player> out = new ArrayList<>();
        for (UUID key : islandService.members(islandId).keySet()) {
            profiles.onlinePlayer(key).ifPresent(out::add);
        }
        return out;
    }

    private boolean wantsNotices(Player player) {
        return core.services().optional(PlayerSettings.class).map(s -> s.get(player, TOGGLE)).orElse(true);
    }

    private void notifyLevelUp(UUID islandId, IslandPillars.LevelChange change, UUID cause) {
        String causeName = cause == null ? null
                : ProfileService.of(core.services()).onlinePlayer(cause).map(Player::getName).orElse(null);
        boolean title = config.notifyTitleEvery > 0 && crossedMultiple(change.from(), change.to(), config.notifyTitleEvery);
        for (Player p : onlineMembers(islandId)) {
            if (!wantsNotices(p)) {
                continue;
            }
            Component name = msg.component(p, "pillar." + change.pillar().id() + ".name");
            if (config.notifyLevelUpChat) {
                Component by = causeName == null ? Component.empty() : msg.component(p, "level-up.by", Msg.p("player", causeName));
                msg.send(p, "level-up.chat", Msg.c("pillar", name), Msg.p("level", change.to()), Msg.c("by", by),
                        Msg.c("summary", summary(p, islandId)));
                core.sounds().play(p, config.levelUpSound);
            }
            if (title) {
                msg.title(p, "level-up.title", Msg.c("pillar", name), Msg.p("level", change.to()));
            }
        }
    }

    private void notifyMilestone(UUID islandId, int milestone, long crystals) {
        for (Player p : onlineMembers(islandId)) {
            msg.send(p, "milestone.chat", Msg.p("level", milestone), Msg.c("summary", summary(p, islandId)));
            if (crystals > 0) {
                msg.send(p, "milestone.reward", Msg.number("crystals", crystals));
            }
            msg.title(p, "milestone.title", Msg.p("level", milestone));
            core.sounds().play(p, config.milestoneSound);
        }
    }

    /** True if a multiple of {@code every} lies in (from, to]. */
    static boolean crossedMultiple(int from, int to, int every) {
        return every > 0 && to / every > from / every;
    }

    /** Hover text: every shown pillar with level and progress, plus the island XP bonus. */
    Component summary(Player viewer, UUID islandId) {
        List<Component> lines = new ArrayList<>();
        lines.add(msg.component(viewer, "summary.header"));
        for (Pillar p : shown) {
            lines.add(msg.component(viewer, "summary.line", Msg.c("pillar", msg.component(viewer, "pillar." + p.id() + ".name")),
                    Msg.p("level", pillarLevel(islandId, p)), Msg.p("percent", percent(pillarProgress(islandId, p)))));
        }
        lines.add(msg.component(viewer, "summary.bonus", Msg.p("bonus", trim(islandXpBonusPercent(islandId)))));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    static int percent(double progress) {
        return (int) Math.floor(Math.max(0, Math.min(1, progress)) * 100);
    }

    static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    // ------------------------------------------------------------------ ProgressionService

    @Override
    public List<Pillar> shownPillars() {
        return shown;
    }

    @Override
    public boolean ready() {
        return ready;
    }

    @Override
    public int pillarLevel(UUID islandId, Pillar pillar) {
        IslandPillars s = islandId == null ? null : islands.get(islandId);
        return s == null || pillar == null ? 0 : s.level(pillar);
    }

    @Override
    public double pillarXp(UUID islandId, Pillar pillar) {
        IslandPillars s = islandId == null ? null : islands.get(islandId);
        return s == null || pillar == null ? 0 : s.xp(pillar);
    }

    @Override
    public double pillarProgress(UUID islandId, Pillar pillar) {
        IslandPillars s = islandId == null ? null : islands.get(islandId);
        if (s == null || pillar == null) {
            return 0;
        }
        // The level may sit above what the XP gives after a curve change (levels never fall): no progress shown then.
        return curve.levelFor(s.xp(pillar)) < s.level(pillar) ? 0 : curve.progress(s.xp(pillar));
    }

    @Override
    public Map<Pillar, Integer> pillarLevels(UUID islandId) {
        IslandPillars s = islandId == null ? null : islands.get(islandId);
        return s == null ? new IslandPillars().levels() : s.levels();
    }

    @Override
    public int lowestPillarLevel(UUID islandId) {
        return PillarRules.lowest(pillarLevels(islandId), shown);
    }

    @Override
    public int totalPillarLevels(UUID islandId) {
        return PillarRules.total(pillarLevels(islandId), shown);
    }

    @Override
    public double xpForPillarLevel(int level) {
        return curve.totalFor(level);
    }

    @Override
    public int pillarLevelFor(double totalXp) {
        return curve.levelFor(totalXp);
    }

    @Override
    public double balanceBonusPercent(UUID islandId) {
        return PillarRules.balanceBonus(pillarLevels(islandId), shown, config.balanceWindowLevels,
                config.balancePerPillarPercent, config.balanceMaxPercent);
    }

    @Override
    public double islandXpBonusPercent(UUID islandId) {
        double external = 0;
        for (Map.Entry<String, ToDoubleFunction<UUID>> e : boosts.entrySet()) {
            try {
                double v = e.getValue().applyAsDouble(islandId);
                if (Double.isFinite(v)) {
                    external += v;
                }
            } catch (RuntimeException ex) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: island XP boost '" + e.getKey() + "' failed", ex);
            }
        }
        return PillarRules.familyPercent(balanceBonusPercent(islandId), external, config.islandXpCapPercent);
    }

    @Override
    public Set<Integer> milestonesReached(UUID islandId) {
        IslandPillars s = islandId == null ? null : islands.get(islandId);
        return s == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(s.milestones()));
    }

    @Override
    public List<Integer> milestones() {
        return milestones;
    }

    @Override
    public double addPillarXp(UUID islandId, Pillar pillar, double baseXp, String source, Actor actor) {
        IslandService islandService = core.services().optional(IslandService.class).orElse(null);
        if (islandService == null || islandId == null || islandService.island(islandId).isEmpty()) {
            return 0;
        }
        Actor staff = actor != null && (actor.isPlayer() || Actor.CONSOLE.equals(actor)) ? actor : null;
        return gain(islandId, pillar, baseXp, null, source, staff);
    }

    @Override
    public void islandXpBoost(String id, ToDoubleFunction<UUID> percentByIsland) {
        if (id == null) {
            return;
        }
        if (percentByIsland == null) {
            boosts.remove(id);
        } else {
            boosts.put(id, percentByIsland);
        }
    }
}
