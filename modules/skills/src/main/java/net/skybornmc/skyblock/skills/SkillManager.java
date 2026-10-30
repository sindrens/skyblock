package net.skybornmc.skyblock.skills;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.skills.SkillLevelUpEvent;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.skills.SkillXpGainEvent;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * The {@link SkillService}: cache of online players, batched saves, XP from kills, action bar, level-ups, audit.
 *
 * <p><b>Data safety.</b> XP is loaded before join (pre-login, off the main thread) and cached; changes are saved in one
 * batch every {@code storage.save-interval-seconds}, on quit and (blocking) on shutdown. All writes of this module go
 * through one ordered chain, so an older write can never land after a newer one, and loads wait for the writes queued
 * before them. A player who quits keeps their entry in {@link #offline} until it is safely written (a quick rejoin
 * reuses it). If data could not be loaded, gains are kept as deltas and added to the stored values later
 * ({@link PlayerSkills}).</p>
 */
final class SkillManager implements SkillService, Listener {

    /** /settings toggle id for the action bar XP line. */
    static final String TOGGLE = "skill-xp-bar";
    private static final long LOGIN_TIMEOUT_SECONDS = 10;

    /** XP read at login for profile {@code key}. */
    private record Preloaded(UUID key, Map<SkillType, Double> xp, long stamp, long time) {
    }

    private final Core core;
    private final SkillsConfig config;
    private final ModuleMessages msg;
    private final SkillRepository repo = new SkillRepository();
    // Profiles (PROF): skills are per profile. PlayerSkills#uuid() is the PROFILE KEY (the storage key); "online" and
    // "preloaded" are keyed by the online player's UUID, everything else by the profile key.
    private final Map<UUID, PlayerSkills> online = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSkills> offline = new ConcurrentHashMap<>();
    private final Map<UUID, Preloaded> preloaded = new ConcurrentHashMap<>();
    private final Map<UUID, Map<SkillType, Double>> retryDeltas = new HashMap<>();
    private final Map<UUID, Long> lastQuit = new ConcurrentHashMap<>();
    private final AtomicLong clock = new AtomicLong();
    private final GainBuffer gains = new GainBuffer();
    private final int season;
    private volatile CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);
    private volatile SkillCurve curve;
    private volatile SkillTables tables = SkillTables.defaults();
    private volatile Set<SkillType> available = EnumSet.noneOf(SkillType.class);
    private final List<Scheduler.Task> tasks = new ArrayList<>();

    SkillManager(Core core, SkillsConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.season = config.seasonId;
        applyConfig();
    }

    /** Re-reads curve and the available list from the config (start and reload). Levels follow a new curve. */
    void applyConfig() {
        SkillCurve next;
        try {
            next = new SkillCurve(config.curveBase, config.curveEarlyGrowth, config.curveLateGrowth, config.curveBreakLevel,
                    config.curveEndGrowth, config.curveEndBreakLevel, config.maxLevel);
        } catch (IllegalArgumentException e) {
            core.plugin().getLogger().warning("skills.yml → curve: " + e.getMessage() + " (keeping the previous curve)");
            next = curve != null ? curve : new SkillCurve(600, 1.2, 1.13, 35, 1.02, 66, 150);
        }
        curve = next;
        Set<SkillType> set = EnumSet.noneOf(SkillType.class);
        for (String id : config.available) {
            SkillType.byId(id).ifPresentOrElse(set::add,
                    () -> core.plugin().getLogger().warning("skills.yml → available: unknown skill '" + id + "' (skipped)"));
        }
        available = set;
        for (PlayerSkills p : online.values()) {
            p.recompute(curve);
        }
    }

    void tables(SkillTables tables) {
        this.tables = tables;
    }

    SkillTables tables() {
        return tables;
    }

    SkillCurve curve() {
        return curve;
    }

    void start() {
        long saveTicks = config.saveIntervalSeconds * 20L;
        tasks.add(core.scheduler().repeat("skills-save", saveTicks, saveTicks, this::saveCycle));
        tasks.add(core.scheduler().repeat("skills-action-bar", 5, 5, this::flushBars));
        // Players already online (module enabled or reloaded while the server runs): load them now.
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSkills p = new PlayerSkills(profiles().profileKey(player.getUniqueId()));
            online.put(player.getUniqueId(), p);
            startLoad(player.getUniqueId(), p);
        }
    }

    private ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    /** The online entry of a profile key (its player is online on that profile), or null. */
    private PlayerSkills onlineByKey(UUID key) {
        for (PlayerSkills p : online.values()) {
            if (p.uuid().equals(key)) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ SkillService

    @Override
    public boolean available(SkillType skill) {
        return available.contains(skill);
    }

    @Override
    public double addXp(Player player, SkillType skill, double amount, String source) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("SkillService.addXp must be called on the main thread");
        }
        if (player == null || skill == null || !available(skill) || !(amount > 0) || Double.isInfinite(amount)) {
            return 0;
        }
        PlayerSkills p = online.get(player.getUniqueId());
        if (p == null) {
            return 0; // NPCs and players whose join is not processed yet
        }
        SkillXpGainEvent event = new SkillXpGainEvent(player, skill, amount, source == null ? "unknown" : source);
        Bukkit.getPluginManager().callEvent(event);
        double gained = event.getAmount();
        if (event.isCancelled() || !(gained > 0) || Double.isInfinite(gained)) {
            return 0;
        }
        PlayerSkills.LevelChange change = p.add(skill, gained, curve);
        showGain(player, skill, gained);
        if (change != null) {
            levelUp(player, p, change, event.getSource(), Actor.system("skills"));
        }
        return gained;
    }

    @Override
    public int level(UUID player, SkillType skill) {
        PlayerSkills p = online.get(player);
        return p == null ? 0 : p.level(skill);
    }

    @Override
    public double xp(UUID player, SkillType skill) {
        PlayerSkills p = online.get(player);
        return p == null || !p.isLoaded() ? 0 : p.xp(skill);
    }

    @Override
    public double progress(UUID player, SkillType skill) {
        PlayerSkills p = online.get(player);
        return p == null || !p.isLoaded() ? 0 : curve.progress(p.xp(skill));
    }

    @Override
    public int maxLevel() {
        return curve.maxLevel();
    }

    @Override
    public double xpForLevel(int level) {
        return curve.totalFor(level);
    }

    @Override
    public int levelFor(double totalXp) {
        return curve.levelFor(totalXp);
    }

    /** Any player's XP: online from the cache, offline from their last active profile. */
    @Override
    public CompletableFuture<Map<SkillType, Double>> loadXp(UUID player) {
        PlayerSkills p = online.get(player);
        if (p != null && p.isLoaded()) {
            return CompletableFuture.completedFuture(p.totals());
        }
        return profiles().profileKeyAsync(player).thenCompose(key -> {
            PlayerSkills unsaved = offline.get(key);
            if (unsaved != null) {
                return CompletableFuture.completedFuture(unsaved.totals());
            }
            return afterWrites().thenCompose(v -> core.database().async(c -> repo.load(c, key, season)));
        });
    }

    @Override
    public boolean setXp(Player player, SkillType skill, double totalXp, Actor by, String reason) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("SkillService.setXp must be called on the main thread");
        }
        PlayerSkills p = online.get(player.getUniqueId());
        if (p == null || !p.isLoaded() || !(totalXp >= 0) || Double.isInfinite(totalXp)) {
            return false;
        }
        double before = p.xp(skill);
        int levelBefore = p.level(skill);
        PlayerSkills.LevelChange change = p.set(skill, totalXp, curve);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("skill", skill.id());
        details.put("xp_before", before);
        details.put("xp_after", totalXp);
        details.put("level_before", levelBefore);
        details.put("level_after", p.level(skill));
        details.put("reason", reason == null ? "" : reason);
        core.audit().logPlayer(AuditCategory.STAFF, "skill_set", by, player.getUniqueId(), player.getName(),
                (long) p.level(skill), null, details);
        if (change != null && change.to() > change.from()) {
            levelUp(player, p, change, "staff", by);
        }
        return true;
    }

    // ------------------------------------------------------------------ XP sources

    /** Combat XP: kills by a player only; spawner mobs and entities tagged {@link #NO_XP_TAG} give none. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null || entity.getScoreboardTags().contains(NO_XP_TAG)) {
            return;
        }
        if (!config.spawnerMobsGiveXp && entity.fromMobSpawner()) {
            return;
        }
        double xp = tables.xp(SkillType.COMBAT, entity.getType().getKey().getKey());
        if (xp > 0) {
            addXp(killer, SkillType.COMBAT, xp, "mob_kill");
        }
    }

    /** Mining/Foraging XP for a resource block broken in The Depths (called by {@link DepthsXpListener}). */
    void onResourceBreak(Player player, String blockId) {
        double mining = tables.xp(SkillType.MINING, blockId);
        if (mining > 0) {
            addXp(player, SkillType.MINING, mining, "depths_block");
        }
        double foraging = tables.xp(SkillType.FORAGING, blockId);
        if (foraging > 0) {
            addXp(player, SkillType.FORAGING, foraging, "depths_log");
        }
    }

    // ------------------------------------------------------------------ feedback

    private void showGain(Player player, SkillType skill, double amount) {
        if (!config.actionBarEnabled || !toggleOn(player)) {
            return;
        }
        gains.add(player.getUniqueId(), skill, amount);
        flushBar(player, System.currentTimeMillis());
    }

    private boolean toggleOn(Player player) {
        return core.services().optional(PlayerSettings.class).map(s -> s.get(player, TOGGLE)).orElse(true);
    }

    private void flushBars() {
        long now = System.currentTimeMillis();
        for (UUID uuid : gains.waiting()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                gains.remove(uuid);
            } else {
                flushBar(player, now);
            }
        }
    }

    private void flushBar(Player player, long now) {
        Map<SkillType, Double> shown = gains.poll(player.getUniqueId(), now, config.actionBarIntervalMs);
        if (shown.isEmpty()) {
            return;
        }
        Component line = Component.empty();
        boolean first = true;
        for (Map.Entry<SkillType, Double> e : shown.entrySet()) {
            if (!first) {
                line = line.append(msg.component(player, "action-bar.separator"));
            }
            first = false;
            line = line.append(msg.component(player, "action-bar.gain", Msg.c("skill", skillName(player, e.getKey())),
                    Msg.p("amount", XpFormat.xp(e.getValue())),
                    Msg.p("percent", XpFormat.percent(progress(player.getUniqueId(), e.getKey()))),
                    Msg.p("level", level(player, e.getKey()))));
        }
        player.sendActionBar(line);
    }

    Component skillName(Audience viewer, SkillType skill) {
        return msg.component(viewer, "skill." + skill.id() + ".name");
    }

    private void levelUp(Player player, PlayerSkills p, PlayerSkills.LevelChange change, String source, Actor actor) {
        Bukkit.getPluginManager().callEvent(new SkillLevelUpEvent(player, change.skill(), change.from(), change.to()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("skill", change.skill().id());
        details.put("from", change.from());
        details.put("to", change.to());
        details.put("xp", p.xp(change.skill()));
        details.put("source", source);
        core.audit().logPlayer(AuditCategory.WORLD, "skill_level_up", actor, player.getUniqueId(), player.getName(),
                (long) change.to(), player.getLocation(), details);
        if (!player.isOnline()) {
            return;
        }
        Component name = skillName(player, change.skill());
        if (config.levelUpTitle) {
            msg.title(player, "level-up.title", Msg.c("skill", name), Msg.p("level", change.to()), Msg.p("from", change.from()));
        }
        if (!config.levelUpSound.isBlank()) {
            core.sounds().play(player, config.levelUpSound);
        }
        if (config.levelUpChat) {
            msg.send(player, "level-up.chat", Msg.c("skill", name), Msg.p("level", change.to()), Msg.p("from", change.from()));
            int next = tables.nextUnlock(change.skill(), change.to());
            if (next > 0 && next <= curve.maxLevel()) {
                msg.send(player, "level-up.next-unlock", Msg.c("unlock", unlockText(player, change.skill(), next)),
                        Msg.p("level", next));
            }
        }
    }

    /** Text of an unlock level ({@code unlock.<skill>.<level>}), or the generic text if none is written. */
    Component unlockText(Audience viewer, SkillType skill, int level) {
        String key = "unlock." + skill.id() + "." + level;
        if (msg.has(key)) {
            return msg.component(viewer, key);
        }
        return msg.component(viewer, "unlock.generic", Msg.c("skill", skillName(viewer, skill)), Msg.p("level", level));
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        try {
            try {
                afterWrites().get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException ignored) {
                // a failed earlier write is logged and retried; it never blocks a login
            }
            long stamp = clock.incrementAndGet();
            UUID key = profiles().profileKeyAsync(uuid).get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Map<SkillType, Double> xp = core.database().blocking(c -> repo.load(c, key, season));
            preloaded.put(uuid, new Preloaded(key, xp, stamp, System.currentTimeMillis()));
        } catch (TimeoutException e) {
            core.plugin().getLogger().warning("Skills of " + event.getName() + " took longer than " + LOGIN_TIMEOUT_SECONDS
                    + " s to load; asked them to rejoin.");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, msg.component(Audience.empty(), "login.slow"));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            core.plugin().getLogger().log(Level.SEVERE, "Could not load skills of " + event.getName(), Database.cause(e));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, msg.component(Audience.empty(), "login.failed"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // The profiles module's join handler (also LOWEST, registered earlier) has set the active profile already.
        UUID key = profiles().profileKey(uuid);
        Preloaded pre = preloaded.remove(uuid);
        attach(uuid, key, pre != null && pre.key().equals(key) ? pre : null);
    }

    /** Makes profile {@code key} the online player's skills (join, profile switch). */
    private void attach(UUID uuid, UUID key, Preloaded pre) {
        PlayerSkills p = offline.remove(key);
        if (p == null) {
            if (pre != null && pre.stamp() > lastQuit.getOrDefault(key, 0L)) {
                p = PlayerSkills.loaded(key, pre.xp(), curve);
            } else {
                p = new PlayerSkills(key); // stale or missing preload: load after the queued writes
            }
        }
        Map<SkillType, Double> retry = retryDeltas.remove(key);
        if (retry != null) {
            for (Map.Entry<SkillType, Double> e : retry.entrySet()) {
                p.add(e.getKey(), e.getValue(), curve);
            }
        }
        online.put(uuid, p);
        if (!p.isLoaded()) {
            startLoad(uuid, p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        gains.remove(uuid);
        detach(uuid);
    }

    /** Stores the online player's current profile skills (quit, profile switch). */
    private void detach(UUID uuid) {
        PlayerSkills p = online.remove(uuid);
        if (p == null) {
            return;
        }
        UUID key = p.uuid();
        lastQuit.put(key, clock.incrementAndGet());
        if (p.isLoaded()) {
            offline.put(key, p);
            saveRecords(List.of(p), () -> offline.remove(key, p));
        } else {
            saveDeltas(key, p.deltas());
        }
    }

    /** Profiles (PROF): skills follow the profile. The old profile's XP is saved, the new one's loaded. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        gains.remove(uuid);
        detach(uuid);
        attach(uuid, event.to().id(), null);
    }

    /** A deleted profile's skills are gone with it (its rows are removed after the queued writes). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        UUID key = event.profile().id();
        offline.remove(key);
        retryDeltas.remove(key);
        write(c -> repo.delete(c, key)).exceptionally(e -> {
            core.plugin().getLogger().warning("Skills: could not remove the skills of deleted profile " + key + ": " + e.getMessage());
            return null;
        });
    }

    // ------------------------------------------------------------------ loading and saving

    private void startLoad(UUID uuid, PlayerSkills p) {
        if (p.isLoading() || p.isLoaded()) {
            return;
        }
        p.loading(true);
        UUID key = p.uuid();
        afterWrites().thenCompose(v -> core.database().async(c -> repo.load(c, key, season)))
                .whenComplete((stored, error) -> core.scheduler().main("skills-load", () -> {
                    if (online.get(uuid) != p) {
                        return; // left again; quit already handled the deltas
                    }
                    if (error != null) {
                        p.loading(false); // retried by the next save cycle
                        core.plugin().getLogger().log(Level.WARNING, "Could not load skills of " + uuid + "; retrying soon",
                                Database.cause(error));
                        return;
                    }
                    Player player = Bukkit.getPlayer(uuid);
                    for (PlayerSkills.LevelChange change : p.merge(stored, curve)) {
                        if (player != null) {
                            levelUp(player, p, change, "merge", Actor.system("skills"));
                        }
                    }
                }));
    }

    /** The current end of the write chain (never fails). */
    private CompletableFuture<Void> afterWrites() {
        return writes.handle((v, e) -> null);
    }

    /** Appends a write to the ordered chain. Main thread. */
    private CompletableFuture<Void> write(Sql.SqlConsumer work) {
        CompletableFuture<Void> next = afterWrites().thenCompose(v -> core.database().transaction(c -> {
            work.accept(c);
            return null;
        }));
        writes = next;
        return next;
    }

    /** Every save interval: one batch for all changed entries, retries of failed writes and loads. */
    private void saveCycle() {
        long now = System.currentTimeMillis();
        preloaded.values().removeIf(p -> now - p.time() > TimeUnit.MINUTES.toMillis(1));
        List<PlayerSkills> records = new ArrayList<>();
        for (Map.Entry<UUID, PlayerSkills> e : online.entrySet()) {
            PlayerSkills p = e.getValue();
            if (p.isLoaded()) {
                if (p.hasDirty()) {
                    records.add(p);
                }
            } else {
                startLoad(e.getKey(), p);
            }
        }
        for (PlayerSkills p : offline.values()) {
            if (p.hasDirty()) {
                UUID uuid = p.uuid();
                saveRecords(List.of(p), () -> offline.remove(uuid, p));
            }
        }
        if (!records.isEmpty()) {
            saveRecords(records, null);
        }
        if (!retryDeltas.isEmpty()) {
            Map<UUID, Map<SkillType, Double>> copy = new HashMap<>(retryDeltas);
            retryDeltas.clear();
            copy.forEach((uuid, d) -> {
                PlayerSkills p = onlineByKey(uuid);
                if (p != null) {
                    d.forEach((skill, xp) -> p.add(skill, xp, curve));
                } else {
                    saveDeltas(uuid, d);
                }
            });
        }
    }

    /** Saves the dirty rows of these entries in one transaction; on failure they become dirty again. */
    private void saveRecords(List<PlayerSkills> records, Runnable onSuccess) {
        Map<PlayerSkills, List<PlayerSkills.Row>> rows = new LinkedHashMap<>();
        List<PlayerSkills.Row> all = new ArrayList<>();
        for (PlayerSkills p : records) {
            List<PlayerSkills.Row> r = p.drainDirty();
            if (!r.isEmpty()) {
                rows.put(p, r);
                all.addAll(r);
            }
        }
        if (all.isEmpty()) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }
        long now = System.currentTimeMillis();
        write(c -> repo.save(c, season, all, now)).whenComplete((v, error) -> core.scheduler().main("skills-saved", () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Saving skills of " + rows.size() + " player(s) failed; retrying next cycle",
                        Database.cause(error));
                rows.forEach(PlayerSkills::markDirty);
            } else if (onSuccess != null) {
                onSuccess.run();
            }
        }));
    }

    /** Adds XP earned while the data was not loaded to the stored values. */
    private void saveDeltas(UUID uuid, Map<SkillType, Double> deltas) {
        if (deltas.isEmpty()) {
            return;
        }
        SkillCurve c0 = curve;
        long now = System.currentTimeMillis();
        lastQuit.put(uuid, clock.incrementAndGet()); // a preload taken before this write is stale
        write(c -> repo.addDeltas(c, uuid, season, deltas, c0, now)).whenComplete((v, error) -> {
            if (error == null) {
                return;
            }
            core.scheduler().main("skills-delta-retry", () -> {
                core.plugin().getLogger().log(Level.SEVERE, "Saving skill XP of " + uuid + " failed; retrying", Database.cause(error));
                PlayerSkills p = onlineByKey(uuid);
                if (p != null) {
                    deltas.forEach((skill, xp) -> p.add(skill, xp, curve));
                } else {
                    Map<SkillType, Double> retry = retryDeltas.computeIfAbsent(uuid, k -> new EnumMap<>(SkillType.class));
                    deltas.forEach((skill, xp) -> retry.merge(skill, xp, Double::sum));
                }
            });
        });
    }

    /** Stops timers and writes everything <b>blocking</b> (module disable). */
    void shutdown() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        try {
            afterWrites().get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            core.plugin().getLogger().warning("Skills: earlier writes did not finish in time: " + e.getMessage());
        }
        List<PlayerSkills.Row> rows = new ArrayList<>();
        Map<UUID, Map<SkillType, Double>> deltas = new HashMap<>(retryDeltas);
        for (PlayerSkills p : online.values()) {
            if (p.isLoaded()) {
                rows.addAll(p.drainDirty());
            } else if (!p.deltas().isEmpty()) {
                deltas.put(p.uuid(), p.deltas());
            }
        }
        for (PlayerSkills p : offline.values()) {
            rows.addAll(p.drainDirty());
        }
        long now = System.currentTimeMillis();
        SkillCurve c0 = curve;
        try {
            if (!rows.isEmpty() || !deltas.isEmpty()) {
                core.database().blockingTransaction(c -> {
                    repo.save(c, season, rows, now);
                    for (Map.Entry<UUID, Map<SkillType, Double>> e : deltas.entrySet()) {
                        repo.addDeltas(c, e.getKey(), season, e.getValue(), c0, now);
                    }
                    return null;
                });
            }
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.SEVERE, "Could not save skills on shutdown (" + rows.size() + " rows)", Database.cause(e));
        }
        online.clear();
        offline.clear();
        preloaded.clear();
        retryDeltas.clear();
        gains.clear();
    }
}
