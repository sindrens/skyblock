package net.skybornmc.skyblock.afk;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.booster.BoosterService;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.teleport.TeleportOptions;
import net.skybornmc.skyblock.common.teleport.TeleportReason;
import net.skybornmc.skyblock.common.teleport.TeleportService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.common.visibility.VisibilityService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The AFK pool loop. Every {@code time.check-seconds} on the main thread (a box test per online player, nothing
 * else): who is inside, who may earn (permission, daily cap, IP rule), credit the elapsed time, hand out due rewards
 * and show the action bar. Every database write of an account goes through that account's queue (in order, off the
 * main thread); rewards are one transaction each (money + reward number + today's total + fuel/booster), keyed so a
 * replay after a crash can never pay twice. Credited time is saved every {@code time.save-seconds} and on leaving,
 * quitting and shutdown.
 */
final class AfkTracker implements Listener {

    private static final String REASON_KEYS = TxReason.AFK_POOL;
    /** Bank source of AFK pool sell boosters (boosters module, audit {@code bank_add}). */
    static final String BOOSTER_SOURCE = "afk_pool";

    private final AfkModule m;
    private final Core core;
    private final Map<UUID, AfkAccount> accounts = new ConcurrentHashMap<>();
    /** Write queues of accounts that quit; a quick rejoin loads only after the quit save finished (main thread). */
    private final Map<UUID, CompletableFuture<?>> tails = new ConcurrentHashMap<>();
    private final IpGate gate = new IpGate();
    private Scheduler.Task checkTask = Scheduler.Task.NONE;
    private Scheduler.Task saveTask = Scheduler.Task.NONE;
    private volatile Optional<PoolArea> area = Optional.empty();
    private volatile ZoneId zone = ZoneId.of("Europe/Oslo");
    private String lastAreaLog = "";
    /** Bedrock IPs already warned about (once per IP per run). */
    private final Set<String> bedrockWarned = new java.util.HashSet<>();

    AfkTracker(AfkModule module) {
        this.m = module;
        this.core = module.core();
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        schedule();
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate") || Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            core.plugin().getLogger().info("AFK pool: Geyser/Floodgate found; Bedrock players' IPs are checked as they join "
                    + "(one earning account per connection needs their real IP, see docs/commands/afk.md).");
            checkBedrockIps();
        }
    }

    /** (Re)starts the timers and re-reads the area and time zone (after a reload). */
    void schedule() {
        checkTask.cancel();
        saveTask.cancel();
        zone = resolveZone();
        refreshArea();
        long check = m.config().checkSeconds * 20L;
        checkTask = core.scheduler().repeat("afk-check", check, check, this::check);
        long save = m.config().saveSeconds * 20L;
        saveTask = core.scheduler().repeat("afk-save", save, save, this::saveTick);
    }

    void shutdown() {
        checkTask.cancel();
        saveTask.cancel();
        long now = System.currentTimeMillis();
        long deadline = now + 5_000;
        for (AfkAccount a : accounts.values()) {
            try {
                a.chain.get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                core.plugin().getLogger().warning("AFK pool: queued writes of " + a.player + " did not finish before shutdown.");
            }
            if (a.loaded) {
                AfkAccount.Snapshot s = a.snapshot();
                try {
                    core.database().blockingTransaction(c -> {
                        m.store().save(c, s, now);
                        return null;
                    });
                } catch (RuntimeException e) {
                    core.plugin().getLogger().log(Level.WARNING, "AFK pool: could not save " + a.player + " on shutdown", e);
                }
            }
        }
        for (CompletableFuture<?> tail : tails.values()) {
            try {
                tail.get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                core.plugin().getLogger().warning("AFK pool: a quit save did not finish before shutdown.");
            }
        }
        tails.clear();
        accounts.clear();
        gate.clear();
    }

    private ZoneId resolveZone() {
        String id = m.config().timezone;
        if (id == null || id.isBlank()) {
            return net.skybornmc.skyblock.common.util.Format.zone();
        }
        try {
            return ZoneId.of(id.trim());
        } catch (RuntimeException e) {
            core.plugin().getLogger().warning("afk.yml → time.timezone: '" + id + "' is not a time zone (example: Europe/Oslo); using "
                    + net.skybornmc.skyblock.common.util.Format.zone().getId());
            return net.skybornmc.skyblock.common.util.Format.zone();
        }
    }

    private void refreshArea() {
        Optional<PoolArea> found = PoolArea.resolve(m.config(), core.services().optional(net.skybornmc.skyblock.common.builds.BuildPoints.class).orElse(null));
        area = found;
        String log = found.map(a -> "AFK pool: " + a.world() + " " + a.box().minX() + "," + a.box().minY() + "," + a.box().minZ() + " to "
                + a.box().maxX() + "," + a.box().maxY() + "," + a.box().maxZ() + " (from " + a.source() + ").")
                .orElse("AFK pool: closed (no region '" + m.config().buildRegion + "' in the applied builds and pool.use-config-area is off in afk.yml).");
        if (!log.equals(lastAreaLog)) {
            lastAreaLog = log;
            core.plugin().getLogger().info(log);
        }
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
        if (core.bedrock().isBedrock(event.getPlayer().getUniqueId())) {
            checkBedrockIps();
        }
    }

    /**
     * R3-3 / brief §3.9: warns once per IP when Bedrock players seem to share the proxy's IP (Floodgate not passing their
     * real IP). Then the one-account-per-connection rule would pause all but one Bedrock player.
     */
    private void checkBedrockIps() {
        if (!m.config().bedrockIpCheck) {
            return;
        }
        Map<UUID, String> ips = new java.util.HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.bedrock().isBedrock(p.getUniqueId())) {
                ips.put(p.getUniqueId(), ip(p));
            }
        }
        BedrockIpCheck.check(ips, m.config().bedrockSharedIpPlayers).filter(f -> bedrockWarned.add(f.ip())).ifPresent(f ->
                core.plugin().getLogger().warning("AFK pool: " + f.players() + " Bedrock player(s) connect from " + f.ip()
                        + (f.loopback() ? " (a loopback address)" : "") + ". That looks like Geyser's or a proxy's address, not the players' own: "
                        + "the one-earning-account-per-connection rule (and other per-IP rules) would treat them as one connection. "
                        + "Check Floodgate/Geyser IP forwarding (docs/commands/afk.md, Bedrock IPs)."));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        AfkAccount a = accounts.remove(p.getUniqueId());
        if (a != null) {
            if (a.loaded) {
                save(a);
            }
            CompletableFuture<?> tail = a.chain;
            tails.put(p.getUniqueId(), tail);
            tail.whenComplete((v, e) -> tails.remove(p.getUniqueId(), tail));
        }
    }

    private void load(Player p) {
        AfkAccount a = new AfkAccount(p.getUniqueId());
        CompletableFuture<?> tail = tails.remove(p.getUniqueId());
        if (tail != null) {
            a.chain = tail;
        }
        accounts.put(p.getUniqueId(), a);
        reload(a, true);
    }

    /** Reads an account from the database (on join, or when the memory copy turned out stale). */
    private void reload(AfkAccount a, boolean deliver) {
        long now = System.currentTimeMillis();
        int today = AfkLedger.dayKey(now, zone);
        Database db = core.database();
        queue(a, () -> db.async(c -> {
            AfkAccount.Snapshot s = m.store().load(c, a.player, today, now);
            boolean pending = !m.store().openGrants(c, a.player).isEmpty();
            return new Object[]{s, pending};
        })).whenComplete((r, error) -> core.scheduler().ensureMain("afk-load", () -> {
            if (error != null || r == null) {
                core.plugin().getLogger().log(Level.WARNING, "AFK pool: could not load " + a.player, error);
                return;
            }
            a.restore((AfkAccount.Snapshot) r[0]);
            a.dirty = false;
            a.pendingGrants = (Boolean) r[1];
            if (deliver) {
                deliver(a);
            }
        }));
    }

    // ------------------------------------------------------------------ the check

    private void check() {
        long now = System.currentTimeMillis();
        int today = AfkLedger.dayKey(now, zone);
        AfkRules rules = m.rules();
        AfkConfig cfg = m.config();
        PoolArea pool = area.orElse(null);
        long maxStep = cfg.checkSeconds * 2000L;
        List<IpGate.Candidate> candidates = new ArrayList<>();
        Map<UUID, Player> candidatePlayers = new LinkedHashMap<>();
        Map<Player, AfkState> states = new LinkedHashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            AfkAccount a = accounts.get(p.getUniqueId());
            if (a == null) {
                continue;
            }
            boolean in = pool != null && pool.contains(p.getLocation());
            if (!in) {
                if (a.inside) {
                    leave(p, a);
                    visibility(p);
                }
                continue;
            }
            if (!a.inside) {
                a.inside = true;
                a.enteredAt = now;
                a.lastCheck = now;
                a.sessionMillis = 0;
                a.idleSince = 0;
                m.msg().send(p, "entered", Msg.p("minutes", m.config().rewardMinutes));
                visibility(p);
            }
            if (!a.loaded) {
                states.put(p, AfkState.LOADING);
                a.lastCheck = now;
                continue;
            }
            if (!p.hasPermission(AfkModule.PERM_EARN)) {
                states.put(p, AfkState.NO_PERMISSION);
                a.lastCheck = now;
                continue;
            }
            AfkLedger.rollDay(a, today);
            if (AfkLedger.capped(a, today, rules)) {
                states.put(p, AfkState.CAPPED);
                a.lastCheck = now;
                continue;
            }
            String ip = ip(p);
            if (gate.remaining(capKey(p, ip), today, rules.ipDailyCapMillis()) <= 0) {
                states.put(p, AfkState.IP_CAPPED);
                a.lastCheck = now;
                continue;
            }
            candidates.add(new IpGate.Candidate(p.getUniqueId(), ip, a.enteredAt));
            candidatePlayers.put(p.getUniqueId(), p);
        }
        FamilyGroups families = m.families();
        Set<UUID> blocked = cfg.onePerIp ? gate.update(candidates, families::of) : Set.of();
        for (IpGate.Candidate c : candidates) {
            Player p = candidatePlayers.get(c.player());
            AfkAccount a = accounts.get(c.player());
            long dt = Math.max(0, Math.min(maxStep, now - a.lastCheck));
            a.lastCheck = now;
            if (blocked.contains(c.player())) {
                states.put(p, AfkState.IP_BLOCKED);
                continue;
            }
            String capKey = capKey(p, c.ip());
            AfkLedger.Result r = AfkLedger.credit(a, today, dt, gate.remaining(capKey, today, rules.ipDailyCapMillis()), rules);
            gate.add(capKey, today, r.credited());
            for (AfkLedger.Reward reward : r.rewards()) {
                grantReward(p, a, reward, rules, now);
            }
            if (cfg.keysEnabled) {
                for (AfkRules.KeyMilestone k : r.keys()) {
                    grantKey(p, a, k, today, now);
                }
            }
            states.put(p, r.reachedCap() || AfkLedger.capped(a, today, rules) ? AfkState.CAPPED : AfkState.EARNING);
        }
        for (Map.Entry<Player, AfkState> e : states.entrySet()) {
            AfkAccount a = accounts.get(e.getKey().getUniqueId());
            show(e.getKey(), a, e.getValue(), today, now, rules);
            idle(e.getKey(), a, e.getValue(), now);
        }
    }

    /** The per-IP daily cap key (family members each get their own, owner R3-3). */
    private String capKey(Player p, String ip) {
        return IpGate.capKey(ip, p.getUniqueId(), m.families().of(p.getUniqueId()) != null);
    }

    /** R3-36: re-applies who sees whom after {@code p} entered or left the pool. */
    private void visibility(Player p) {
        if (m.config().hidePlayers) {
            VisibilityService.of(core.services()).ifPresent(v -> v.refresh(p));
        }
    }

    /** True while {@code p} stands in the pool (the visibility rule asks this; main thread). */
    boolean inside(Player p) {
        AfkAccount a = accounts.get(p.getUniqueId());
        return a != null && a.inside;
    }

    /** R3-35: no idle kick; a player who is not earning is told once and sent to their island after the delay. */
    private void idle(Player p, AfkAccount a, AfkState state, long now) {
        if (a == null) {
            return;
        }
        AfkConfig cfg = m.config();
        IdleHome.Step step = IdleHome.step(cfg.sendHome, state, a.idleSince, now, cfg.sendHomeSeconds * 1000L);
        a.idleSince = step.since();
        switch (step.action()) {
            case WARN -> m.msg().send(p, "home.soon", Msg.p("seconds", cfg.sendHomeSeconds));
            case SEND -> sendHome(p, a);
            case NONE -> {
            }
        }
    }

    private void sendHome(Player p, AfkAccount a) {
        a.sentHome = true;
        Location from = p.getLocation();
        AfkState why = a.shown;
        boolean island = core.services().optional(IslandService.class).map(s -> s.teleportHome(p)).orElse(false);
        core.audit().logPlayer(AuditCategory.WORLD, "afk_sent_home", Actor.system("afk"), p.getUniqueId(), p.getName(), null, from,
                Map.of("to", island ? "island" : m.config().fallbackWorld, "state", why.name().toLowerCase(java.util.Locale.ROOT),
                        "after_seconds", m.config().sendHomeSeconds));
        if (island) {
            m.msg().send(p, "home.sent");
            return;
        }
        World world = Bukkit.getWorld(m.config().fallbackWorld);
        if (world == null) {
            a.sentHome = false;
            core.plugin().getLogger().warning("AFK pool: could not send " + p.getName() + " away (no island and idle.fallback-world '"
                    + m.config().fallbackWorld + "' is not loaded).");
            return;
        }
        Location spawn = world.getSpawnLocation();
        Optional<TeleportService> teleports = core.services().optional(TeleportService.class);
        if (teleports.isPresent()) {
            teleports.get().now(p, spawn, TeleportReason.SPAWN, TeleportOptions.none());
        } else {
            p.teleportAsync(spawn);
        }
        m.msg().send(p, "home.sent-spawn");
    }

    private void show(Player p, AfkAccount a, AfkState state, int today, long now, AfkRules rules) {
        if (a == null) {
            return;
        }
        AfkState before = a.shown;
        a.shown = state;
        if (state == AfkState.CAPPED && before != AfkState.CAPPED) {
            m.msg().send(p, "capped", Msg.p("cap", AfkText.hoursMinutes(rules.dailyCapMillis())),
                    Msg.p("reset", AfkText.hoursMinutes(AfkLedger.millisToMidnight(now, zone))));
        } else if (state == AfkState.IP_BLOCKED && before != AfkState.IP_BLOCKED) {
            m.msg().send(p, "ip-blocked");
        }
        if (!m.config().actionBar) {
            return;
        }
        String key = switch (state) {
            case EARNING -> "action-bar.earning";
            case CAPPED -> "action-bar.capped";
            case IP_CAPPED -> "action-bar.ip-capped";
            case IP_BLOCKED -> "action-bar.ip-blocked";
            case NO_PERMISSION -> "action-bar.no-permission";
            case LOADING -> "action-bar.loading";
            case OUTSIDE -> null;
        };
        if (key == null) {
            return;
        }
        m.msg().actionBar(p, key,
                Msg.p("next", AfkText.countdown(AfkLedger.untilNextReward(a, rules))),
                Msg.p("today", AfkText.hoursMinutes(AfkLedger.creditedToday(a, today))),
                Msg.p("cap", AfkText.hoursMinutes(rules.dailyCapMillis())),
                Msg.p("percent", rules.stepPercent(a.sessionMillis)),
                Msg.p("reset", AfkText.hoursMinutes(AfkLedger.millisToMidnight(now, zone))));
    }

    private void leave(Player p, AfkAccount a) {
        a.inside = false;
        a.sessionMillis = 0;
        a.shown = AfkState.OUTSIDE;
        a.idleSince = 0;
        boolean quiet = a.sentHome; // R3-35: the "sent home" line already said it
        a.sentHome = false;
        if (a.loaded && quiet) {
            if (a.dirty) {
                save(a);
            }
        } else if (a.loaded) {
            m.msg().send(p, "left", Msg.p("today", AfkText.hoursMinutes(AfkLedger.creditedToday(a, AfkLedger.dayKey(System.currentTimeMillis(), zone)))),
                    Msg.p("cap", AfkText.hoursMinutes(m.rules().dailyCapMillis())));
            if (a.dirty) {
                save(a);
            }
        }
    }

    private static String ip(Player p) {
        return p.getAddress() == null || p.getAddress().getAddress() == null ? "unknown:" + p.getUniqueId()
                : p.getAddress().getAddress().getHostAddress();
    }

    // ------------------------------------------------------------------ rewards

    private int stageTier(Player p, AfkRules rules) {
        Optional<SkillService> skills = core.services().optional(SkillService.class);
        if (skills.isEmpty()) {
            return 1;
        }
        int best = 0;
        for (String id : m.config().stageSkills) {
            Optional<SkillType> type = SkillType.byId(id);
            if (type.isPresent() && skills.get().available(type.get())) {
                best = Math.max(best, skills.get().level(p, type.get()));
            }
        }
        return rules.tierFor(best);
    }

    private void grantReward(Player p, AfkAccount a, AfkLedger.Reward reward, AfkRules rules, long now) {
        AfkConfig cfg = m.config();
        UUID player = p.getUniqueId();
        String name = p.getName();
        UUID profileKey = ProfileService.of(core.services()).profileKey(p);
        int tier = stageTier(p, rules);
        int percent = rules.stepPercent(reward.sessionMillis());
        Optional<EconomyService> eco = core.services().optional(EconomyService.class);
        long money = cfg.moneyEnabled && eco.isPresent() ? rules.money(tier, reward.sessionMillis()) : 0;
        UUID island = null;
        if (cfg.fuelEnabled && cfg.fuelMinutes > 0) {
            island = core.services().optional(IslandService.class).flatMap(s -> s.islandOf(player)).map(IslandService.IslandInfo::id).orElse(null);
        }
        // M10.2: the banked sell booster lives in the boosters module (one place for all boosters); without it, no roll.
        Optional<BoosterService> boosterService = BoosterService.of(core.services());
        boolean booster = cfg.boosterEnabled && boosterService.isPresent()
                && reward.sessionMillis() >= cfg.boosterMinSession * AfkRules.MINUTE
                && ThreadLocalRandom.current().nextDouble(100.0) < cfg.boosterChance;
        AfkAccount.Snapshot snap = a.snapshot();
        long seq = reward.seq();
        UUID fuelIsland = island;
        AtomicReference<Long> banked = new AtomicReference<>();
        TxGuard guard = c -> {
            if (!m.store().reward(c, snap, seq, now)) {
                return false;
            }
            if (fuelIsland != null) {
                m.store().addGrant(c, player, profileKey, AfkStore.KIND_FUEL, fuelIsland.toString(), cfg.fuelMinutes, now);
            }
            if (booster) {
                banked.set(boosterService.get().bank(c, profileKey, BoosterType.SELL, cfg.boosterPercent,
                        cfg.boosterMinutes * AfkRules.MINUTE, cfg.boosterMaxBanked * AfkRules.MINUTE, BOOSTER_SOURCE));
            }
            return true;
        };
        a.dirty = false;
        Supplier<CompletableFuture<TxResult.Status>> work;
        if (money > 0) {
            Transaction tx = Transaction.deposit(AccountRef.player(profileKey), Currency.MONEY, money, TxReason.AFK_POOL)
                    .actor(Actor.SYSTEM)
                    .reference("afk:" + seq)
                    .idempotencyKey("afk:" + player + ":" + seq)
                    .detail("target_name", name)
                    .detail("tier", tier)
                    .detail("step_percent", percent);
            work = () -> eco.get().executeGuarded(tx, guard).thenApply(TxResult::status);
        } else {
            work = () -> core.database().transaction(guard::holds)
                    .thenApply(ok -> ok ? TxResult.Status.SUCCESS : TxResult.Status.DUPLICATE);
        }
        long finalMoney = money;
        queue(a, work).whenComplete((status, error) -> core.scheduler().ensureMain("afk-reward", () -> {
            if (error != null || status != TxResult.Status.SUCCESS) {
                core.plugin().getLogger().warning("AFK pool: reward " + seq + " of " + name + " was not stored (" + (error != null ? error : status)
                        + "); reloading their AFK data.");
                reload(a, false);
                return;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("seq", seq);
            details.put("tier", tier);
            details.put("step_percent", percent);
            details.put("money", finalMoney);
            details.put("profile", profileKey.toString());
            details.put("fuel_minutes", fuelIsland == null ? 0 : cfg.fuelMinutes);
            details.put("island", fuelIsland == null ? "-" : fuelIsland.toString());
            details.put("booster", booster);
            core.audit().logPlayer(AuditCategory.ECONOMY, "afk_reward", Actor.SYSTEM, player, name, finalMoney, null, details);
            if (fuelIsland != null) {
                a.pendingGrants = true;
            }
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                if (cfg.rewardChat) {
                    String key = fuelIsland != null ? "reward.with-fuel" : cfg.fuelEnabled && cfg.fuelMinutes > 0 ? "reward.no-island" : "reward.money";
                    m.msg().send(online, key, Msg.money("money", finalMoney), Msg.p("fuel", cfg.fuelMinutes), Msg.p("percent", percent));
                }
                if (booster && banked.get() != null) {
                    m.msg().send(online, "booster.won", Msg.p("percent", cfg.boosterPercent), Msg.p("minutes", cfg.boosterMinutes),
                            Msg.p("banked", AfkText.hoursMinutes(banked.get())));
                }
                if (!cfg.rewardSound.isBlank()) {
                    core.sounds().play(online, cfg.rewardSound);
                }
            }
            if (booster && banked.get() != null) {
                boosterService.get().banked(profileKey, BoosterType.SELL, cfg.boosterPercent, cfg.boosterMinutes * AfkRules.MINUTE,
                        banked.get(), BOOSTER_SOURCE, Actor.SYSTEM);
            }
            deliver(a);
        }));
    }

    private void grantKey(Player p, AfkAccount a, AfkRules.KeyMilestone k, int day, long now) {
        UUID player = p.getUniqueId();
        String name = p.getName();
        UUID profileKey = ProfileService.of(core.services()).profileKey(p);
        queue(a, () -> core.database().transaction(c -> {
            if (!m.store().milestone(c, player, day, k.minutes(), now)) {
                return false;
            }
            m.store().addGrant(c, player, profileKey, AfkStore.KIND_KEY, k.crate(), k.amount(), now);
            return true;
        })).whenComplete((ok, error) -> core.scheduler().ensureMain("afk-key", () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "AFK pool: key milestone " + k.minutes() + " of " + name + " was not stored", error);
                return;
            }
            if (!Boolean.TRUE.equals(ok)) {
                return;
            }
            core.audit().logPlayer(AuditCategory.ECONOMY, "afk_key", Actor.SYSTEM, player, name, (long) k.amount(), null,
                    Map.of("crate", k.crate(), "milestone_minutes", k.minutes(), "day", day, "profile", profileKey.toString()));
            a.pendingGrants = true;
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                boolean crates = core.services().has(CrateKeys.class);
                m.msg().send(online, crates ? "key.earned" : "key.kept", Msg.p("amount", k.amount()), Msg.mm("crate", m.crateName(k.crate())),
                        Msg.p("time", AfkText.hoursMinutes(k.millis())));
                if (!m.config().keySound.isBlank()) {
                    core.sounds().play(online, m.config().keySound);
                }
            }
            deliver(a);
        }));
    }

    // ------------------------------------------------------------------ delivery of keys and fuel

    /** Hands out open key/fuel grants through the crates and Island Fuel services, when they exist. */
    private void deliver(AfkAccount a) {
        if (!a.pendingGrants) {
            return;
        }
        CrateKeys crates = core.services().optional(CrateKeys.class).orElse(null);
        IslandService islands = m.fuelSupported() ? core.services().optional(IslandService.class).orElse(null) : null;
        if (crates == null && islands == null) {
            return;
        }
        UUID player = a.player;
        queue(a, () -> core.database().async(c -> m.store().openGrants(c, player)).thenCompose(grants -> {
            CompletableFuture<int[]> f = CompletableFuture.completedFuture(new int[]{0, 0, grants.size()});
            for (AfkStore.Grant g : grants) {
                f = f.thenCompose(n -> deliverOne(g, crates, islands).thenApply(ok -> {
                    if (ok) {
                        n[g.kind().equals(AfkStore.KIND_KEY) ? 0 : 1] += (int) g.amount();
                        n[2]--;
                    }
                    return n;
                }));
            }
            return f;
        })).whenComplete((n, error) -> core.scheduler().ensureMain("afk-deliver", () -> {
            if (error != null || n == null) {
                core.plugin().getLogger().log(Level.WARNING, "AFK pool: delivering keys/fuel of " + player + " failed", error);
                return;
            }
            a.pendingGrants = n[2] > 0;
            Player online = Bukkit.getPlayer(player);
            if (online != null && n[0] > 0) {
                m.msg().send(online, "key.delivered", Msg.p("amount", n[0]));
            }
        }));
    }

    private CompletableFuture<Boolean> deliverOne(AfkStore.Grant g, CrateKeys crates, IslandService islands) {
        boolean key = g.kind().equals(AfkStore.KIND_KEY);
        if (key ? crates == null : islands == null || !g.kind().equals(AfkStore.KIND_FUEL)) {
            return CompletableFuture.completedFuture(false);
        }
        long now = System.currentTimeMillis();
        Database db = core.database();
        Actor actor = Actor.system("afk");
        return db.async(c -> m.store().claim(c, g.id(), now)).thenCompose(claimed -> {
            if (!claimed) {
                return CompletableFuture.completedFuture(false);
            }
            CompletableFuture<Boolean> handOut = core.scheduler().supplyMain("afk-handout", () -> {
                if (key) {
                    return crates.exists(g.ref()) ? crates.giveKeys(g.profileKey(), g.ref(), (int) g.amount(), REASON_KEYS, actor)
                            : CompletableFuture.completedFuture(false);
                }
                UUID island = UUID.fromString(g.ref());
                return islands.island(island).isPresent() ? islands.addIslandFuel(island, g.amount(), TxReason.AFK_POOL, actor)
                        : CompletableFuture.completedFuture(false);
            }).thenCompose(x -> x).exceptionally(e -> false);
            return handOut.thenCompose(ok -> {
                core.audit().logPlayer(AuditCategory.ECONOMY, ok ? "afk_grant_delivered" : "afk_grant_retry", Actor.SYSTEM, g.player(), null,
                        g.amount(), null, Map.of("kind", g.kind(), "ref", g.ref(), "grant", g.id(), "profile", g.profileKey().toString()));
                return ok ? CompletableFuture.completedFuture(true)
                        : db.run(c -> m.store().unclaim(c, g, System.currentTimeMillis())).thenApply(v -> false);
            });
        });
    }

    // ------------------------------------------------------------------ boosters

    /**
     * True while the player stands in the AFK pool: the boosters module keeps their banked boosters from starting then,
     * so a won booster is never wasted while AFK (it starts when they leave).
     */
    boolean holdsBoosters(Player p) {
        PoolArea pool = area.orElse(null);
        AfkAccount a = accounts.get(p.getUniqueId());
        return (a != null && a.inside) || (pool != null && pool.contains(p.getLocation()));
    }

    // ------------------------------------------------------------------ saving

    private void saveTick() {
        refreshArea();
        gate.prune(AfkLedger.dayKey(System.currentTimeMillis(), zone));
        for (AfkAccount a : accounts.values()) {
            if (a.loaded && a.dirty) {
                save(a);
            }
        }
    }

    private void save(AfkAccount a) {
        AfkAccount.Snapshot s = a.snapshot();
        a.dirty = false;
        long now = System.currentTimeMillis();
        queue(a, () -> core.database().run(c -> m.store().save(c, s, now))).whenComplete((v, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "AFK pool: could not save " + a.player, error);
                core.scheduler().ensureMain("afk-save-retry", () -> a.dirty = true);
            }
        });
    }

    /** Runs {@code work} after everything queued for this account before it (main thread only). */
    private <T> CompletableFuture<T> queue(AfkAccount a, Supplier<CompletableFuture<T>> work) {
        CompletableFuture<T> next = a.chain.handle((x, e) -> null).thenCompose(v -> work.get());
        a.chain = next;
        return next;
    }

    // ------------------------------------------------------------------ placeholders

    /** Scoreboard text while inside: next reward countdown, or why the player is not earning; empty outside. */
    String poolLine(Player p) {
        AfkAccount a = p == null ? null : accounts.get(p.getUniqueId());
        if (a == null || !a.inside) {
            return "";
        }
        return switch (a.shown) {
            case EARNING -> AfkText.countdown(AfkLedger.untilNextReward(a, m.rules()));
            case CAPPED -> m.plain("scoreboard.capped");
            case IP_CAPPED, IP_BLOCKED -> m.plain("scoreboard.paused");
            case NO_PERMISSION -> m.plain("scoreboard.no-permission");
            case LOADING, OUTSIDE -> m.plain("scoreboard.loading");
        };
    }

    /** Today's credited time, e.g. {@code 3h 15m}. */
    String todayText(Player p) {
        AfkAccount a = p == null ? null : accounts.get(p.getUniqueId());
        if (a == null || !a.loaded) {
            return AfkText.hoursMinutes(0);
        }
        return AfkText.hoursMinutes(AfkLedger.creditedToday(a, AfkLedger.dayKey(System.currentTimeMillis(), zone)));
    }
}
