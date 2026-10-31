package net.skybornmc.skyblock.quests;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.depths.DepthsZones;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.enchant.EnchantService;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.npc.NpcClickEvent;
import net.skybornmc.skyblock.common.pass.PassXp;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.quest.QuestService;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.tutorial.Gates;
import net.skybornmc.skyblock.common.tutorial.TutorialGate;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.common.island.IslandRemovedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Quests (PHASE-10-REWARDS.md M10.7, P10-21, P10-22, RX23).
 * <ul>
 *   <li><b>Daily</b>: 3 quests per profile per day (reset 00:00 Europe/Oslo), sized to ~12 minutes at your tier, 1 free swap;
 *       each pays 3 minutes of income + crystals + 100 pass XP; all 3 = 1 tradeable Cinder key.</li>
 *   <li><b>Weekly</b>: 5 per profile (reset Monday 00:00), ~45 minutes each, 8 minutes of income + crystals + 400 pass XP;
 *       all 5 = 1 tradeable Blaze key.</li>
 *   <li><b>Island missions</b>: 3 per island per week, shared progress by members (never coop players); every member who
 *       helped gets crystals + 150 pass XP.</li>
 *   <li>Only live content: a quest type is in the pool only while its system runs and you have it unlocked. Progress only
 *       from server events; rewards pay by themselves (no claim buttons), once per quest ({@link Payouts}).</li>
 * </ul>
 */
public final class QuestsModule implements SkyblockModule, Listener {

    static final String ID = "quests";
    static final String PERM_USE = "skyblock.quests.use";
    static final String PERM_ADMIN = "skyblock.quests.admin";
    static final String PLACEHOLDER_NEXT = "quest_next";

    final Core core;
    final ModuleMessages msg;
    final QuestsSettings settings = new QuestsSettings();
    private final QuestEngine engine;
    private final QuestGrants grants;
    private final QuestText text;
    private Executor main;
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    private boolean depthsHooked;

    // Parsed from the settings (replaced as a whole on reload).
    private volatile Map<QuestKind, List<QuestPool.Entry>> pools = Map.of();
    private volatile double[] rateMine = new double[0];
    private volatile double[] rateForest = new double[0];
    private volatile double[] rateCombat = new double[0];
    private volatile long[] refPerMinute = new long[0];
    private volatile int[] tierLevels = {0};
    private volatile Set<String> excludedSell = Set.of();
    private volatile ZoneId zone = Format.DEFAULT_ZONE;

    public QuestsModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
        this.engine = new QuestEngine(this);
        this.grants = new QuestGrants(this);
        this.text = new QuestText(this);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(settings);
        main = new MainThread(core.plugin());
        parse();
        engine.updateWindows();
        registerCommands();
        core.services().register(ID, QuestService.class, new QuestService() {
            @Override
            public void open(Player player, Menu parent) {
                QuestsModule.this.open(player, QuestKind.DAILY, parent);
            }

            @Override
            public int dailyDone(Player player) {
                return engine.set(profileKey(player), QuestKind.DAILY).map(QuestSet::doneCount).orElse(0);
            }
        });
        core.placeholders().registerText(ID, PLACEHOLDER_NEXT, text::nextLine);
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        Bukkit.getPluginManager().registerEvents(new QuestSources(this), core.plugin());
        try {
            Bukkit.getPluginManager().registerEvents(new DepthsQuestListener(this), core.plugin());
            depthsHooked = true;
        } catch (NoClassDefFoundError e) {
            depthsHooked = false;
            core.plugin().getLogger().info("Quests: the depths module is not in this jar; mine, chop and kill quests are left out.");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            engine.load(p);
        }
        long save = 20L * settings.saveSeconds;
        tasks.add(core.scheduler().repeat("quests:windows", 20L, 20L, engine::updateWindows));
        tasks.add(core.scheduler().repeat("quests:tick", save, save, engine::tick));
        long retry = 20L * settings.retrySeconds;
        // The first run also recovers hand-outs a crash interrupted (after every module, crates included, is up).
        tasks.add(core.scheduler().repeat("quests:grants", 200L, retry, grants::retry));
    }

    @Override
    public void reload() {
        core.configs().load(settings);
        core.messages().load(ID);
        parse();
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof QuestsMenu) {
                p.closeInventory();
            }
        }
        engine.saveAllBlocking();
        core.placeholders().unregisterAll(ID);
    }

    private void parse() {
        List<String> problems = new ArrayList<>();
        Map<QuestKind, List<QuestPool.Entry>> p = new LinkedHashMap<>();
        p.put(QuestKind.DAILY, QuestPool.parse(settings.dailyPool, "quests.yml -> daily.pool", problems::add));
        p.put(QuestKind.WEEKLY, QuestPool.parse(settings.weeklyPool, "quests.yml -> weekly.pool", problems::add));
        p.put(QuestKind.ISLAND, QuestPool.parse(settings.islandPool, "quests.yml -> island.pool", problems::add));
        pools = p;
        rateMine = doubles(settings.rateMine, "quests.yml -> rates.mine", problems);
        rateForest = doubles(settings.rateForest, "quests.yml -> rates.forest", problems);
        rateCombat = doubles(settings.rateCombat, "quests.yml -> rates.combat", problems);
        double[] ref = doubles(settings.refPerMinute, "quests.yml -> pouch.ref-per-minute", problems);
        long[] refLong = new long[ref.length];
        for (int i = 0; i < ref.length; i++) {
            refLong[i] = Math.round(ref[i]);
        }
        refPerMinute = refLong;
        double[] levels = doubles(settings.tierLevels, "quests.yml -> tier.tier-levels", problems);
        int[] lv = new int[levels.length];
        for (int i = 0; i < levels.length; i++) {
            lv[i] = (int) levels[i];
        }
        tierLevels = lv.length == 0 ? new int[]{0} : lv;
        Set<String> excluded = new HashSet<>();
        for (String s : settings.excludedSellSources) {
            excluded.add(s.trim().toLowerCase(Locale.ROOT));
        }
        excludedSell = Set.copyOf(excluded);
        ZoneId z = Format.parseZone(settings.zone);
        if (z == null) {
            problems.add("quests.yml -> zone '" + settings.zone + "' is not a time zone; using Europe/Oslo");
            z = Format.DEFAULT_ZONE;
        }
        zone = z;
        for (String problem : problems) {
            core.plugin().getLogger().warning("Quests: " + problem);
        }
    }

    private static double[] doubles(List<String> raw, String where, List<String> problems) {
        List<Double> out = new ArrayList<>();
        for (String s : raw) {
            try {
                double v = Double.parseDouble(s.trim().replace("_", "").replace(",", ""));
                if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) {
                    throw new NumberFormatException();
                }
                out.add(v);
            } catch (NumberFormatException e) {
                problems.add(where + ": '" + s + "' is not a number (skipped)");
            }
        }
        if (out.isEmpty()) {
            problems.add(where + " is empty: those quests are sized 1");
        }
        double[] arr = new double[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    // ================================================================== commands

    private void registerCommands() {
        Arg<Player> player = Args.onlinePlayer("player");
        Arg<QuestKind> board = Args.choice("board", QuestKind.class);
        Arg<Integer> slot = Args.integer("quest", 1, 7, "1", "2", "3", "4", "5");
        core.commands().register(CommandSpec.command("quests")
                .description("Your daily and weekly quests and your island's missions")
                .category("Rewards")
                .permission(PERM_USE)
                .playerOnly()
                .example("quests")
                .executes(ctx -> {
                    if (!settings.enabled) {
                        throw new CommandFailure(ID, "disabled");
                    }
                    open(ctx.player(), QuestKind.DAILY, null);
                })
                .sub(CommandSpec.sub("admin")
                        .description("Staff tools for quests: view, reset and complete (P10-41)")
                        .category("Staff")
                        .permission(PERM_ADMIN)
                        .example("quests admin view Steve")
                        .sub(CommandSpec.sub("view")
                                .description("A player's quests on their active profile and their island's missions")
                                .permission(PERM_ADMIN)
                                .arg(player)
                                .example("quests admin view Steve")
                                .executes(ctx -> view(ctx.sender(), ctx.get(player))))
                        .sub(CommandSpec.sub("reset")
                                .description("Throw away a player's current daily, weekly or island quests and roll new ones")
                                .permission(PERM_ADMIN)
                                .arg(player).arg(board)
                                .example("quests admin reset Steve daily")
                                .executes(ctx -> reset(ctx.sender(), ctx.get(player), ctx.get(board))))
                        .sub(CommandSpec.sub("complete")
                                .description("Finish one quest of a player now (the normal rewards are paid)")
                                .permission(PERM_ADMIN)
                                .arg(player).arg(board).arg(slot)
                                .example("quests admin complete Steve daily 1")
                                .executes(ctx -> complete(ctx.sender(), ctx.get(player), ctx.get(board), ctx.get(slot))))));
    }

    void open(Player player, QuestKind tab, Menu parent) {
        engine.load(player);
        QuestsMenu menu = new QuestsMenu(this, tab);
        if (parent != null) {
            menu.parent(parent);
        }
        core.menus().open(player, menu);
    }

    private void view(org.bukkit.command.CommandSender sender, Player target) {
        msg.send(sender, "admin.view.header", Msg.p("player", target.getName()),
                Msg.p("profile", profiles().activeName(target)));
        UUID profile = profileKey(target);
        for (QuestKind kind : QuestKind.values()) {
            Optional<QuestSet> set = kind == QuestKind.ISLAND ? engine.island(countingIsland(target)) : engine.set(profile, kind);
            if (set.isEmpty()) {
                msg.send(sender, "admin.view.none", Msg.p("board", kind.id()));
                continue;
            }
            msg.send(sender, "admin.view.board", Msg.p("board", kind.id()), Msg.number("done", set.get().doneCount()),
                    Msg.number("total", set.get().quests.size()), Msg.number("swaps", set.get().rerollsUsed),
                    Msg.p("bonus", set.get().bonusPaid ? "yes" : "no"));
            for (Quest q : set.get().quests) {
                msg.send(sender, q.done() ? "admin.view.quest-done" : "admin.view.quest", Msg.number("slot", q.slot + 1),
                        Msg.c("quest", text.name(target, q)), Msg.p("progress", text.amount(q, q.progress)),
                        Msg.p("target", text.amount(q, q.target)));
            }
        }
    }

    private void reset(org.bukkit.command.CommandSender sender, Player target, QuestKind kind) {
        engine.reset(target, kind).whenComplete((n, error) -> core.scheduler().ensureMain("quests:admin-reset", () -> {
            if (error != null) {
                msg.send(sender, "error");
                return;
            }
            if (n == -1) {
                msg.send(sender, "admin.no-island", Msg.p("player", target.getName()));
                return;
            }
            if (n == -2) {
                msg.send(sender, "busy");
                return;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("board", kind.id());
            d.put("profile", profileKey(target).toString());
            d.put("removed", n);
            audit("staff_reset", Actor.of(sender), target.getUniqueId(), target.getName(), null, d);
            msg.send(sender, "admin.reset", Msg.p("player", target.getName()), Msg.p("board", kind.id()));
        }));
    }

    private void complete(org.bukkit.command.CommandSender sender, Player target, QuestKind kind, int slot) {
        if (!engine.forceComplete(target, kind, slot - 1)) {
            msg.send(sender, "admin.no-quest", Msg.p("player", target.getName()), Msg.p("board", kind.id()), Msg.number("slot", slot));
            return;
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("board", kind.id());
        d.put("slot", slot);
        d.put("profile", profileKey(target).toString());
        audit("staff_complete", Actor.of(sender), target.getUniqueId(), target.getName(), null, d);
        msg.send(sender, "admin.completed", Msg.p("player", target.getName()), Msg.p("board", kind.id()), Msg.number("slot", slot));
    }

    // ================================================================== eligibility (live content only)

    /** Pool entries of a board that are live for this player right now. Main thread. */
    List<QuestPool.Entry> eligible(Player player, QuestKind kind) {
        return QuestPool.eligible(pools.getOrDefault(kind, List.of()), unlocks(player));
    }

    /** What the player has unlocked right now, from the live services. Main thread. */
    QuestPool.Unlocks unlocks(Player player) {
        boolean depths = depthsHooked && gate(player, Gates.DEPTHS);
        boolean islands = islands().isPresent() && gate(player, Gates.ISLAND_TEAM);
        boolean tool = holdsTool(player);
        return new QuestPool.Unlocks(depths,
                depths ? unlockedTiers(player, SkillType.MINING) : 0,
                depths ? unlockedTiers(player, SkillType.FORAGING) : 0,
                depths ? unlockedTiers(player, SkillType.COMBAT) : 0,
                core.services().optional(SellService.class).isPresent(), tool,
                core.services().optional(EnchantService.class).isPresent(), islands,
                playtimeMinutes(player) >= settings.likeMinPlaytimeMinutes, farming(player));
    }

    /** PHASE-7 M7.3: harvest quests while island farming is on and the player has an island to farm on. */
    private boolean farming(Player player) {
        return core.services().has(net.skybornmc.skyblock.common.production.FarmingService.class)
                && islands().map(i -> i.islandOf(player.getUniqueId()).isPresent()).orElse(false);
    }

    private boolean gate(Player player, String gate) {
        return TutorialGate.allows(core.services(), player, gate);
    }

    private boolean holdsTool(Player player) {
        Optional<ToolService> tools = core.services().optional(ToolService.class);
        if (tools.isEmpty()) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && tools.get().isTool(stack)) {
                return true;
            }
        }
        return false;
    }

    private long playtimeMinutes(Player player) {
        return core.players().get(player.getUniqueId()).map(d -> d.playtimeMillis()).orElse(0L) / 60_000L;
    }

    /** Open tiers of a Depths wing for this player (0 = none or no Depths). */
    int unlockedTiers(Player player, SkillType skill) {
        Optional<DepthsZones> zones = core.services().optional(DepthsZones.class);
        Optional<SkillService> skills = core.services().optional(SkillService.class);
        if (zones.isEmpty() || skill == null) {
            return 0;
        }
        int level = skills.filter(s -> s.available(skill)).map(s -> s.level(player, skill)).orElse(0);
        return zones.get().unlockedTiers(skill, level);
    }

    /** The player's stage tier: the highest level of the stage skills (same rule as crates, the calendar and the AFK pool). */
    int tier(Player player) {
        Optional<SkillService> skills = core.services().optional(SkillService.class);
        if (skills.isEmpty()) {
            return 1;
        }
        int best = 0;
        for (String id : settings.stageSkills) {
            Optional<SkillType> type = SkillType.byId(id);
            if (type.isPresent() && skills.get().available(type.get())) {
                best = Math.max(best, skills.get().level(player, type.get()));
            }
        }
        return QuestMath.tierFor(best, tierLevels);
    }

    // ================================================================== numbers per board

    int count(QuestKind kind) {
        return switch (kind) {
            case DAILY -> settings.dailyCount;
            case WEEKLY -> settings.weeklyCount;
            case ISLAND -> settings.islandCount;
        };
    }

    int minutes(QuestKind kind) {
        return switch (kind) {
            case DAILY -> settings.dailyMinutes;
            case WEEKLY -> settings.weeklyMinutes;
            case ISLAND -> settings.islandMinutes;
        };
    }

    double moneyMinutes(QuestKind kind) {
        return switch (kind) {
            case DAILY -> settings.dailyMoneyMinutes;
            case WEEKLY -> settings.weeklyMoneyMinutes;
            case ISLAND -> 0;
        };
    }

    long crystalBase(QuestKind kind) {
        return switch (kind) {
            case DAILY -> settings.dailyCrystals;
            case WEEKLY -> settings.weeklyCrystals;
            case ISLAND -> settings.islandCrystals;
        };
    }

    int passXp(QuestKind kind) {
        return switch (kind) {
            case DAILY -> settings.dailyPassXp;
            case WEEKLY -> settings.weeklyPassXp;
            case ISLAND -> settings.islandPassXp;
        };
    }

    double[] rates(QuestType type) {
        return switch (type) {
            case MINE -> rateMine;
            case CHOP -> rateForest;
            case KILL -> rateCombat;
            default -> new double[0];
        };
    }

    long[] refPerMinute() {
        return refPerMinute;
    }

    Set<String> excludedSellSources() {
        return excludedSell;
    }

    /** The all-done bonus of a board (null = none): tradeable keys (sources quest_daily / quest_weekly, P10-6). */
    Payouts.Bonus bonus(QuestKind kind) {
        String crate = switch (kind) {
            case DAILY -> settings.dailyBonusCrate;
            case WEEKLY -> settings.weeklyBonusCrate;
            case ISLAND -> "";
        };
        if (crate == null || crate.isBlank()) {
            return null;
        }
        String c = crate.trim().toLowerCase(Locale.ROOT);
        Optional<CrateKeys> keys = core.services().optional(CrateKeys.class);
        if (keys.isPresent() && !keys.get().exists(c)) {
            return null;
        }
        return new Payouts.Bonus(c, 1, "quest_" + kind.id());
    }

    /** True while the battle pass takes XP (no XP is saved up before it exists, like playtime rewards). */
    boolean passXpLive() {
        return PassXp.of(core.services()).isPresent();
    }

    // ================================================================== accessors

    Executor main() {
        return main;
    }

    ZoneId zone() {
        return zone;
    }

    QuestEngine engine() {
        return engine;
    }

    QuestGrants grants() {
        return grants;
    }

    QuestText text() {
        return text;
    }

    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    UUID profileKey(Player player) {
        return profiles().profileKey(player);
    }

    UUID ownerOf(UUID profile) {
        return profiles().ownerOf(profile);
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    Optional<EconomyService> economy() {
        return core.services().optional(EconomyService.class);
    }

    Payouts.Runner runner() {
        return Payouts.runner(core.database(), economy());
    }

    /** The island the player's actions count for: their own island as a stored member, never as coop. Null = none. */
    UUID countingIsland(Player player) {
        Optional<IslandService> islands = islands();
        if (islands.isEmpty()) {
            return null;
        }
        UUID island = islands.get().islandOf(player.getUniqueId()).map(IslandService.IslandInfo::id).orElse(null);
        return island != null && islands.get().countsForIsland(island, player.getUniqueId()) ? island : null;
    }

    /** True if the profile is still a stored member of the island (not coop). */
    boolean member(UUID island, UUID profile) {
        return islands().map(i -> i.members(island).containsKey(profile)).orElse(false);
    }

    /** The online player who is on this profile right now, or null. */
    Player receiver(UUID profile) {
        return profiles().onlinePlayer(profile).filter(p -> profiles().profileKey(p).equals(profile)).orElse(null);
    }

    String raw(String key, String fallback) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? fallback : raw;
    }

    void fail(Player player, String key, TagResolver... placeholders) {
        msg.send(player, key, placeholders);
        sound(player, "error");
    }

    void sound(Player player, String key) {
        if (player != null && key != null && !key.isBlank()) {
            core.sounds().play(player, key);
        }
    }

    void audit(String action, Actor actor, UUID player, String name, Long amount, Map<String, Object> details) {
        core.audit().logPlayer(AuditCategory.QUESTS, action, actor, player, name, amount, null, details);
    }

    void refreshMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof QuestsMenu menu) {
                menu.refresh();
            }
        }
    }

    // ================================================================== events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // The profile and island load first (same delay as the daily reminder).
        core.scheduler().later("quests:join", 40L, () -> {
            if (p.isOnline()) {
                engine.load(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        engine.quit(event.getPlayer());
        text.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitched(ProfileSwitchedEvent event) {
        engine.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        UUID profile = event.profile().id();
        engine.forget(profile);
        deleteOwner(profile, "profile_reset", event.profile().owner());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandRemoved(IslandRemovedEvent event) {
        engine.forget(event.islandId());
        deleteOwner(event.islandId(), "island_reset", null);
    }

    private void deleteOwner(UUID owner, String action, UUID player) {
        core.database().transaction(c -> {
            QuestStore.deleteOwner(c, owner);
            return true;
        }).whenComplete((v, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Quests: could not remove the quests of " + owner, e);
            } else {
                audit(action, Actor.SYSTEM, player, null, null, Map.of("owner", owner.toString()));
            }
        });
    }

    /** The Quest Master NPC opens /quests (sneak-click keeps its help text). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpc(NpcClickEvent event) {
        if (event.help() || event.role() == null || !settings.enabled || !event.role().equalsIgnoreCase(settings.npcRole.strip())) {
            return;
        }
        Player p = event.getPlayer();
        if (!p.hasPermission(PERM_USE)) {
            return;
        }
        event.setCancelled(true);
        sound(p, "click");
        open(p, QuestKind.DAILY, null);
    }
}
