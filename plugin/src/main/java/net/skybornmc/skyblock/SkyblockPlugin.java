package net.skybornmc.skyblock;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditConfig;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.command.CommandService;
import net.skybornmc.skyblock.common.command.OfflineNames;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.item.DupeDetector;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemsConfig;
import net.skybornmc.skyblock.common.menu.MenuService;
import net.skybornmc.skyblock.common.hook.BedrockInfo;
import net.skybornmc.skyblock.common.hook.PapiHook;
import net.skybornmc.skyblock.common.hook.RankProvider;
import net.skybornmc.skyblock.common.placeholder.BuiltinPlaceholders;
import net.skybornmc.skyblock.common.placeholder.PlaceholderRegistry;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.player.PlayerService;
import net.skybornmc.skyblock.common.service.ServiceRegistry;
import net.skybornmc.skyblock.common.util.ClassPreloader;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.common.util.SlowOperationTimer;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.config.CoreConfig;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.SettingInfo;
import net.skybornmc.skyblock.common.module.ModulePermissions;
import net.skybornmc.skyblock.common.module.ModuleRegistry;
import net.skybornmc.skyblock.common.text.BedrockCheck;
import net.skybornmc.skyblock.common.text.Messages;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.text.Sounds;
import net.skybornmc.skyblock.common.text.Style;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.moderation.ModerationModule;
import net.skybornmc.skyblock.motd.MotdModule;
import net.skybornmc.skyblock.worlds.VoidChunkGenerator;
import net.skybornmc.skyblock.worlds.WorldSettings;
import net.skybornmc.skyblock.worlds.WorldsModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Plugin entrypoint. Owns the shared {@link Core} and the module registry; all gameplay lives in modules. */
public final class SkyblockPlugin extends JavaPlugin implements Listener {

    private ModuleRegistry modules;
    private Core core;
    private ModuleMessages msg;
    private WorldsModule worlds;
    private net.skybornmc.skyblock.npcs.NpcsModule npcs;
    private final List<String> styleProblems = new ArrayList<>();
    private final DatabaseConfig dbConfig = new DatabaseConfig();
    private final ItemsConfig itemsConfig = new ItemsConfig();
    private final AuditConfig auditConfig = new AuditConfig();
    private final net.skybornmc.skyblock.common.teleport.TeleportConfig teleportConfig = new net.skybornmc.skyblock.common.teleport.TeleportConfig();
    private net.skybornmc.skyblock.common.teleport.CoreTeleportService teleports;
    private final net.skybornmc.skyblock.common.visibility.VisibilityConfig visibilityConfig = new net.skybornmc.skyblock.common.visibility.VisibilityConfig();
    private net.skybornmc.skyblock.common.visibility.CoreVisibilityService visibility;

    @Override
    public void onEnable() {
        applyDecisions();
        ConfigManager configs = new ConfigManager(getDataFolder(), getLogger());
        CoreConfig coreConfig = new CoreConfig();
        configs.load(coreConfig);
        applyZone(coreConfig);
        Style style = new Style();
        Sounds sounds = new Sounds(getLogger());
        Messages messages = new Messages(this, style);
        loadStyle(style, sounds);
        BedrockCheck bedrock = BedrockCheck.floodgate();
        messages.bedrockCheck(bedrock);

        // M0.3 database: without data playing is unsafe, so a failed database disables the plugin.
        configs.load(dbConfig);
        Database database = new Database(dbConfig, getDataFolder(), getLogger());
        try {
            database.start();
            new Migrations(database, getLogger()).migrate("core", getClassLoader());
        } catch (SQLException | Migrations.MigrationException e) {
            getLogger().severe("Database unavailable, Phoenix Skyblock is disabled: " + e.getMessage());
            database.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // PERF8: the class preload runs while the modules enable, so it is done before the first tick (it used to start at
        // tick 20, the same tick as boosters:tick, and overlapped every module's first start-up runs).
        if (coreConfig.preloadClasses && coreConfig.preloadAtEnable) {
            startClassPreload();
        }
        configs.load(auditConfig);                                                                    // M0.8
        AuditLog audit = new AuditLog(database, auditConfig, getDataFolder(), getLogger());
        audit.start();
        PlayerService players = new PlayerService(this, database, messages, bedrock);                 // M0.4
        CommandService commands = new CommandService(this, messages, sounds);                         // M0.5
        OfflineNames.use(players.nameSuggestions());
        MenuService menus = new MenuService(this, configs, messages, sounds);                         // M0.6
        configs.load(itemsConfig);                                                                    // M0.7
        messages.load("items");
        ItemRegistry items = new ItemRegistry(messages);
        DupeDetector dupes = new DupeDetector(this, itemsConfig, messages.of("items"));

        ServiceRegistry services = new ServiceRegistry(getLogger());                                  // M0.9
        // ENCHANTS §8.1: bonus families + caps for every module; the sell cap is always the economy's (+300%, #46).
        services.register("core", net.skybornmc.skyblock.common.bonus.BonusService.class,
                new net.skybornmc.skyblock.common.bonus.DefaultBonusService(() -> services
                        .optional(net.skybornmc.skyblock.common.economy.EconomyService.class)
                        .map(net.skybornmc.skyblock.common.economy.EconomyService::sellBonusCapPercent)
                        .orElse(net.skybornmc.skyblock.common.economy.MultiplierCap.DEFAULT_CAP_PERCENT), getLogger()));
        // WARDROBE §7.3: one combat tag for every module (loadouts, wardrobe equips, later profile switches).
        net.skybornmc.skyblock.common.combat.DefaultCombatTagService combat = new net.skybornmc.skyblock.common.combat.DefaultCombatTagService(
                () -> coreConfig.combatTagSeconds, () -> coreConfig.combatMobs, System::currentTimeMillis);
        services.register("core", net.skybornmc.skyblock.common.combat.CombatTagService.class, combat);
        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getScheduler().runTaskTimer(this, combat::prune, 20L * 600, 20L * 600);
        PlaceholderRegistry placeholders = new PlaceholderRegistry(getLogger());
        RankProvider ranks = RankProvider.create(this);
        BedrockInfo bedrockInfo = BedrockInfo.create(this, bedrock);
        Scheduler scheduler = new Scheduler(this, new SlowOperationTimer(getLogger(), () -> coreConfig.slowOperationMs));
        BuiltinPlaceholders.register(placeholders, getServer(), ranks, bedrock,
                uuid -> players.get(uuid).map(PlayerData::playtimeMillis).orElse(0L));
        messages.placeholders(placeholders);

        core = new Core(this, coreConfig, configs, style, messages, sounds, database, players, commands, menus, items, dupes,
                audit, services, placeholders, ranks, bedrockInfo, scheduler);
        PapiHook.registerIfPresent(this, placeholders);
        dupes.addHandler(r -> audit.log(AuditCategory.ITEM, "dupe", Actor.system("dupe-detector"), r.itemUuid().toString(),
                r.itemId(), null, null, Map.of("source", r.source(), "holders", r.holders(),
                        "players", r.playerIds().stream().map(java.util.UUID::toString).toList())));
        msg = core.messages("core");
        // F4: one teleport warmup (guards, cooldowns, cancel rules, /back) for every module (teleport.yml).
        configs.load(teleportConfig);
        teleports = new net.skybornmc.skyblock.common.teleport.CoreTeleportService(core, teleportConfig);
        services.register("core", net.skybornmc.skyblock.common.teleport.TeleportService.class, teleports);
        getServer().getPluginManager().registerEvents(teleports, this);
        // REVIEW3 D4: the only owner of hidePlayer/showPlayer (vanish, AFK pool, the Show players choice) (visibility.yml).
        configs.load(visibilityConfig);
        visibility = new net.skybornmc.skyblock.common.visibility.CoreVisibilityService(this, () -> visibilityConfig, services, scheduler);
        services.register("core", net.skybornmc.skyblock.common.visibility.VisibilityService.class, visibility);
        getServer().getPluginManager().registerEvents(visibility, this);
        players.start();
        menus.timer(scheduler.timer());
        menus.enable();
        dupes.start();

        modules = new ModuleRegistry(getLogger());
        // F2: each module's permissions/<id>.yml is registered right before it enables and removed when it stops.
        ModulePermissions permissions = new ModulePermissions(getServer().getPluginManager(), this::getResource, getLogger());
        modules.beforeEnable(permissions::register);
        modules.afterFailedEnable(permissions::unregister);
        modules.afterDisable(id -> {
            visibility.removeAll(id);
            core.services().unregisterAll(id);
            core.placeholders().unregisterAll(id);
            permissions.unregister(id);
        });
        registerModules();
        modules.enableAll();
        visibility.start(); // after the menus module published /settings
        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        if (!allProblems().isEmpty()) {
            getLogger().warning("Config/message problems found; the previous or default values are active. See the errors above.");
        }
        getLogger().info("Phoenix Skyblock enabled with " + modules.states().size() + " module(s).");
        preloadClasses(coreConfig);
    }

    /** PERF6: loads every plugin class off the main thread once the server runs (see {@link ClassPreloader}). */
    private void preloadClasses(CoreConfig coreConfig) {
        if (!coreConfig.preloadClasses || coreConfig.preloadAtEnable) {
            return; // PERF8: already started while enabling (startClassPreload)
        }
        getServer().getScheduler().runTaskLaterAsynchronously(this, classPreload(), Math.max(0, coreConfig.preloadDelayTicks));
    }

    /**
     * PERF8: starts the class preload on its own low-priority daemon thread right away. Bukkit's async tasks only start
     * once the server ticks, so a scheduler task can't run during start-up. Loading never initializes a class (no
     * static initializer runs), the plugin class loader is parallel capable, and every plugin's class loader exists
     * before any plugin enables, so this is the same work as the PERF6 preload, only finished before the first tick.
     */
    private void startClassPreload() {
        Thread thread = new Thread(classPreload(), "PhoenixSkyblock-ClassPreload");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private Runnable classPreload() {
        java.io.File jar = getFile();
        ClassLoader loader = getClassLoader();
        return () -> {
            try {
                ClassPreloader.Result r = ClassPreloader.preload(jar, loader, "net/skybornmc/skyblock/");
                getLogger().info("Preloaded " + r.loaded() + " plugin classes in " + r.millis() + " ms"
                        + (r.skipped() > 0 ? " (" + r.skipped() + " skipped: optional plugin missing)" : "") + ".");
            } catch (java.io.IOException | RuntimeException e) {
                getLogger().warning("Class preload failed (harmless, classes load on first use): " + e);
            }
        };
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.menus().disable();
        }
        if (modules != null) {
            modules.disableAll();
        }
        if (visibility != null) {
            visibility.shutdown();
        }
        if (teleports != null) {
            teleports.shutdown();
        }
        if (core != null) {
            core.dupes().stop();
            core.ranks().close();
            core.players().shutdown();
            core.audit().close();
            core.database().close();
        }
    }

    /** Register every feature module here (see CLAUDE.md "Adding a module"). */
    private void registerModules() {
        worlds = new WorldsModule(core);
        modules.register(worlds);
        modules.register(new MotdModule(core));
        modules.register(new ModerationModule(core));
        // Profiles early: other modules read the active profile key in their own join handlers.
        modules.register(new net.skybornmc.skyblock.profiles.ProfilesModule(core));
        // Item mailbox (owner answer 7) early: every later module hands overflowing items to ItemBox, and it stops late.
        modules.register(new net.skybornmc.skyblock.box.BoxModule(core));
        modules.register(new net.skybornmc.skyblock.essentials.EssentialsModule(core));
        modules.register(new net.skybornmc.skyblock.hud.HudModule(core));
        modules.register(new net.skybornmc.skyblock.staff.StaffModule(core));
        modules.register(new net.skybornmc.skyblock.social.SocialModule(core));
        modules.register(new net.skybornmc.skyblock.perks.PerksModule(core));
        modules.register(new net.skybornmc.skyblock.chat.ChatModule(core));
        modules.register(new net.skybornmc.skyblock.menus.MenusModule(core));
        modules.register(new net.skybornmc.skyblock.logs.LogsModule(core));
        modules.register(new net.skybornmc.skyblock.cosmetics.CosmeticsModule(core));
        modules.register(new net.skybornmc.skyblock.depths.DepthsModule(core));
        modules.register(new net.skybornmc.skyblock.islands.IslandsModule(core));
        modules.register(new net.skybornmc.skyblock.economy.EconomyModule(core));
        modules.register(new net.skybornmc.skyblock.skills.SkillsModule(core));
        modules.register(new net.skybornmc.skyblock.auction.AuctionModule(core));
        modules.register(new net.skybornmc.skyblock.stacking.StackingModule(core));
        modules.register(new net.skybornmc.skyblock.tools.ToolsModule(core));
        modules.register(new net.skybornmc.skyblock.enchants.EnchantsModule(core));
        modules.register(new net.skybornmc.skyblock.wardrobe.WardrobeModule(core));
        modules.register(new net.skybornmc.skyblock.trade.TradeModule(core));
        modules.register(new net.skybornmc.skyblock.shop.ShopModule(core));
        // Island progression (Phase 5, M5.1 pillars): reads islands, skills, economy, stacking and profiles services (all
        // optional); publishes ProgressionService. Before achievements, which will read it for island achievements.
        modules.register(new net.skybornmc.skyblock.progression.ProgressionModule(core));
        // Last: reads skills, islands, wardrobe, tools, economy and cosmetics services (all optional).
        modules.register(new net.skybornmc.skyblock.achievements.AchievementsModule(core));
        // Boosters (PHASE-10 M10.2): needs economy (thanks gems); publishes BoosterService and a BonusService source. Before the
        // AFK pool, which banks its sell boosters through BoosterService and holds them while a player is in the pool.
        modules.register(new net.skybornmc.skyblock.boosters.BoostersModule(core));
        // AFK pool (O10, RW3): reads economy, islands, skills, profiles, builds and boosters services (all optional).
        modules.register(new net.skybornmc.skyblock.afk.AfkModule(core));
        // NPCs + holograms + leaderboards at the applied builds' points (NPCHOLO); needs the worlds module's BuildPoints.
        npcs = new net.skybornmc.skyblock.npcs.NpcsModule(core);
        modules.register(npcs);
        // Join tutorial (TUTORIAL.md TU1-TU4): reads islands, depths, tools, economy, npcs, cosmetics services (all optional);
        // publishes TutorialGate (feature locks), TutorialService and StarterContents (the islands module asks it at paste time).
        modules.register(new net.skybornmc.skyblock.tutorial.TutorialModule(core));
        // Crates core (PHASE-10 M10.1): needs economy; reads box, enchants, wardrobe, stacking, cosmetics, islands, skills and
        // builds services (optional). Publishes CrateKeys once ready (the AFK pool then hands over its pending keys).
        modules.register(new net.skybornmc.skyblock.crates.CratesModule(core));
        // Rewards (PHASE-10 M10.6 daily calendar + playtime; M10.5 voting later): needs economy; hands keys out through
        // CrateKeys (crates, above) and reads box, boosters, enchants, cosmetics, islands, skills, afk, profiles (optional).
        modules.register(new net.skybornmc.skyblock.rewards.RewardsModule(core));
    }

    public ModuleRegistry modules() {
        return modules;
    }

    public Core core() {
        return core;
    }

    /** Lets bukkit.yml use "PhoenixSkyblock" as a void world generator. */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidChunkGenerator(id == null || id.isBlank() ? "the_void" : id, 0.5, 100, 0.5);
    }

    /**
     * REVIEW3 §6 decision ledger: owner answers (docs/decisions.yml, bundled as decisions.yml) are migrated once into the
     * existing live configs before any config loads; only values that still equal the old default change (logged).
     */
    private void applyDecisions() {
        java.io.InputStream in = getResource("decisions.yml");
        if (in == null) {
            getLogger().warning("decisions.yml is missing from the jar; no decision migrations ran");
            return;
        }
        try (java.io.Reader reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            net.skybornmc.skyblock.common.config.DecisionMigrations migrations =
                    new net.skybornmc.skyblock.common.config.DecisionMigrations(getDataFolder(), getLogger());
            List<String> ran = migrations.apply(migrations.parse(reader));
            if (!ran.isEmpty()) {
                getLogger().info("Decision ledger: migrations " + ran + " applied (see the lines above for what changed).");
            }
        } catch (java.io.IOException | RuntimeException e) {
            getLogger().log(java.util.logging.Level.WARNING, "Decision migrations failed; configs keep their values", e);
        }
    }

    /** core.yml → display-timezone (owner #16: Europe/Oslo); a wrong zone keeps the current one and is logged. */
    private void applyZone(CoreConfig config) {
        if (!Format.zone(config.displayTimezone)) {
            getLogger().warning("core.yml → display-timezone: '" + config.displayTimezone + "' is not a time zone (example: Europe/Oslo), using "
                    + Format.zone().getId());
        }
    }

    // ------------------------------------------------------------------ style, messages, problems

    private void loadStyle(Style style, Sounds sounds) {
        styleProblems.clear();
        for (String name : List.of("style.yml", "sounds.yml")) {
            if (!new File(getDataFolder(), name).exists()) {
                saveResource(name, false);
            }
        }
        styleProblems.addAll(style.load(new File(getDataFolder(), "style.yml")));
        styleProblems.addAll(sounds.load(new File(getDataFolder(), "sounds.yml")));
        styleProblems.forEach(getLogger()::warning);
    }

    /** Every current problem in configs, style, sounds and messages. */
    private List<String> allProblems() {
        List<String> all = new ArrayList<>(styleProblems);
        core.configs().problems().values().forEach(all::addAll);
        core.messages().problems().values().forEach(all::addAll);
        all.addAll(core.commands().problems());
        return all;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (core.config().notifyStaffOfConfigProblems && player.hasPermission("skyblock.admin.reload")) {
            int count = allProblems().size();
            if (count > 0) {
                getServer().getScheduler().runTaskLater(this,
                        () -> msg.send(player, "config.join-notice", Msg.p("count", count)), 40L);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void auditJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        core.audit().logPlayer(AuditCategory.AUTH, "join", Actor.player(p), p.getUniqueId(), p.getName(), null, p.getLocation(),
                Map.of("ip", p.getAddress() == null ? "?" : p.getAddress().getAddress().getHostAddress(),
                        "bedrock", core.players().get(p.getUniqueId()).map(PlayerData::bedrock).orElse(false)));
    }

    /** Every command (ours, vanilla and other plugins) goes to the audit log (category command, kept 30 days). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void auditCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        core.audit().log(AuditCategory.COMMAND, "command", Actor.player(p), null, null, null, p.getLocation(),
                Map.of("line", event.getMessage(), "cancelled", event.isCancelled()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void auditConsoleCommand(ServerCommandEvent event) {
        core.audit().log(AuditCategory.COMMAND, "command", Actor.of(event.getSender()), null, null, null, null,
                Map.of("line", event.getCommand()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void auditQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        core.audit().logPlayer(AuditCategory.AUTH, "quit", Actor.player(p), p.getUniqueId(), p.getName(), null, p.getLocation(), Map.of());
    }

    // ------------------------------------------------------------------ commands

    /** Suggestions filtered by what was typed so far (every argument must have one: owner rule). */
    private static SuggestionProvider<CommandSourceStack> suggest(Supplier<? extends Iterable<String>> values) {
        return (CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (String value : values.get()) {
                if (value.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    builder.suggest(value);
                }
            }
            return builder.buildFuture();
        };
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("skyblock")
                        .requires(src -> src.getSender().hasPermission("skyblock.admin.modules")
                                || src.getSender().hasPermission("skyblock.admin.reload")
                                || src.getSender().hasPermission("skyblock.admin.worlds")
                                || src.getSender().hasPermission(BUILDS_PERMISSION)
                                || src.getSender().hasPermission(net.skybornmc.skyblock.npcs.NpcsModule.PERM_ADMIN))
                        .then(Commands.literal("modules")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.modules"))
                                .executes(ctx -> run(() -> sendModuleStatus(ctx.getSource().getSender()))))
                        .then(Commands.literal("reload")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.reload"))
                                .executes(ctx -> run(() -> reload(ctx.getSource().getSender(), null)))
                                .then(Commands.argument("module", StringArgumentType.word())
                                        .suggests(suggest(this::reloadTargets))
                                        .executes(ctx -> run(() -> reload(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "module").toLowerCase(Locale.ROOT))))))
                        .then(Commands.literal("config")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.reload"))
                                .executes(ctx -> run(() -> sendConfigList(ctx.getSource().getSender())))
                                .then(Commands.argument("file", StringArgumentType.word())
                                        .suggests(suggest(() -> core.configs().loaded().keySet().stream()
                                                .map(f -> f.replace(".yml", "")).toList()))
                                        .executes(ctx -> run(() -> sendConfig(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "file").toLowerCase(Locale.ROOT))))))
                        .then(Commands.literal("style")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.reload"))
                                .executes(ctx -> run(() -> sendStylePreview(ctx.getSource().getSender()))))
                        .then(Commands.literal("worlds")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.worlds"))
                                .executes(ctx -> run(() -> sendWorlds(ctx.getSource().getSender()))))
                        .then(Commands.literal("tp")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.worlds")
                                        && src.getSender() instanceof Player)
                                .then(Commands.argument("world", ArgumentTypes.world())
                                        .executes(ctx -> run(() -> teleport((Player) ctx.getSource().getSender(),
                                                ctx.getArgument("world", World.class))))))
                        .then(buildsCommand())
                        .then(npcsCommand())
                        .build(),
                "Phoenix Skyblock admin commands",
                List.of("sb")));
    }

    /** Owner/admin only: {@code /skyblock builds} (worlds module, BUILDER_BRIEF builds). */
    private static final String BUILDS_PERMISSION = "skyblock.admin.builds";

    /** {@code /skyblock builds [list|info <id>|paste <id> [confirm]|apply <id>|paste-all [confirm]]}. */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildsCommand() {
        return Commands.literal("builds")
                .requires(src -> src.getSender().hasPermission(BUILDS_PERMISSION))
                .executes(ctx -> run(() -> withBuilds(ctx, b -> b.list(ctx.getSource().getSender()))))
                .then(Commands.literal("list")
                        .executes(ctx -> run(() -> withBuilds(ctx, b -> b.list(ctx.getSource().getSender())))))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(suggest(() -> worlds.builds() == null ? List.of() : worlds.builds().ids()))
                                .executes(ctx -> run(() -> withBuilds(ctx, b -> b.info(ctx.getSource().getSender(), buildId(ctx)))))))
                .then(Commands.literal("paste")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(suggest(() -> worlds.builds() == null ? List.of() : worlds.builds().pasteIds()))
                                .executes(ctx -> run(() -> withBuilds(ctx, b -> b.paste(ctx.getSource().getSender(), buildId(ctx), false))))
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> run(() -> withBuilds(ctx, b -> b.paste(ctx.getSource().getSender(), buildId(ctx), true)))))))
                .then(Commands.literal("apply")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(suggest(() -> worlds.builds() == null ? List.of() : worlds.builds().applyIds()))
                                .executes(ctx -> run(() -> withBuilds(ctx, b -> b.apply(ctx.getSource().getSender(), buildId(ctx)))))))
                .then(Commands.literal("paste-all")
                        .executes(ctx -> run(() -> withBuilds(ctx, b -> b.pasteAll(ctx.getSource().getSender(), false))))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> run(() -> withBuilds(ctx, b -> b.pasteAll(ctx.getSource().getSender(), true))))));
    }

    /**
     * Staff: {@code /skyblock npcs [list|tp <id>|respawn]} (npcs module, NPCHOLO). Console-safe except {@code tp};
     * {@code /skyblock reload npcs} also respawns everything.
     */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> npcsCommand() {
        return Commands.literal("npcs")
                .requires(src -> src.getSender().hasPermission(net.skybornmc.skyblock.npcs.NpcsModule.PERM_ADMIN))
                .executes(ctx -> run(() -> withNpcs(ctx, n -> n.list(ctx.getSource().getSender()))))
                .then(Commands.literal("list")
                        .executes(ctx -> run(() -> withNpcs(ctx, n -> n.list(ctx.getSource().getSender())))))
                .then(Commands.literal("tp")
                        .requires(src -> src.getSender() instanceof Player)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(suggest(() -> npcs == null ? List.of() : npcs.ids()))
                                .executes(ctx -> run(() -> withNpcs(ctx, n -> n.teleport(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "id")))))))
                .then(Commands.literal("respawn")
                        .executes(ctx -> run(() -> withNpcs(ctx, n -> n.respawn(ctx.getSource().getSender())))));
    }

    private void withNpcs(CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<net.skybornmc.skyblock.npcs.NpcsModule> action) {
        if (npcs == null || modules.state("npcs") != ModuleRegistry.State.ENABLED) {
            msg.send(ctx.getSource().getSender(), "worlds.npcs-unavailable");
            return;
        }
        action.accept(npcs);
    }

    private static String buildId(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
    }

    private void withBuilds(CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<net.skybornmc.skyblock.worlds.builds.BuildsService> action) {
        net.skybornmc.skyblock.worlds.builds.BuildsService builds = worlds.builds();
        if (builds == null) {
            msg.send(ctx.getSource().getSender(), "worlds.builds-unavailable");
            return;
        }
        action.accept(builds);
    }

    private static int run(Runnable action) {
        action.run();
        return Command.SINGLE_SUCCESS;
    }

    private List<String> reloadTargets() {
        List<String> targets = new ArrayList<>();
        targets.add("core");
        targets.addAll(modules.enabledIds());
        return targets;
    }

    /** Reloads everything ({@code target == null}), only core (core.yml, style, sounds, core messages), or one module. */
    private void reload(CommandSender sender, String target) {
        List<String> failed = new ArrayList<>();
        if (target == null || target.equals("core")) {
            core.configs().load(core.config());
            applyZone(core.config());
            core.configs().load(itemsConfig);
            core.configs().load(auditConfig);
            core.configs().load(teleportConfig);
            core.configs().load(visibilityConfig);
            visibility.reload();
            loadStyle(core.style(), core.sounds());
            core.messages().load("core");
            core.messages().load("items");
            core.commands().reload();
            core.menus().reload();
            core.dupes().reload();
        }
        if (target == null) {
            core.messages().reloadAll();
            failed.addAll(modules.reloadAll());
        } else if (!target.equals("core")) {
            if (!modules.enabledIds().contains(target)) {
                msg.send(sender, "reload.unknown", Msg.p("target", target), Msg.p("list", String.join(", ", reloadTargets())));
                return;
            }
            if (!modules.reload(target)) {
                failed.add(target);
            }
        }
        if (!failed.isEmpty()) {
            msg.send(sender, "reload.failed", Msg.p("list", String.join(", ", failed)));
            if (sender instanceof Player p) {
                core.sounds().play(p, "error");
            }
        } else {
            if (target == null) {
                msg.send(sender, "reload.done-all");
            } else {
                msg.send(sender, "reload.done-one", Msg.p("target", target));
            }
            if (sender instanceof Player p) {
                core.sounds().play(p, "success");
            }
        }
        core.audit().log(AuditCategory.CONFIG, "reload", Actor.of(sender), target == null ? "all" : target, null, null, null,
                Map.of("problems", allProblems().size(), "failed", failed));
        sendProblems(sender);
    }

    private void sendProblems(CommandSender sender) {
        List<String> problems = allProblems();
        if (problems.isEmpty()) {
            return;
        }
        msg.send(sender, "config.problems-header");
        problems.forEach(problem -> msg.send(sender, "config.problem", Msg.p("problem", problem)));
    }

    private void sendConfigList(CommandSender sender) {
        msg.send(sender, "config.list-header");
        Map<String, List<String>> problems = new LinkedHashMap<>(core.configs().problems());
        for (String file : core.configs().loaded().keySet()) {
            String name = file.replace(".yml", "");
            List<String> list = problems.get(file);
            if (list == null) {
                msg.send(sender, "config.list-ok", Msg.p("name", name));
            } else {
                msg.send(sender, "config.list-problems", Msg.p("name", name), Msg.p("count", list.size()));
            }
        }
        sendProblems(sender);
    }

    private void sendConfig(CommandSender sender, String name) {
        ModuleConfig config = core.configs().loaded().get(name.endsWith(".yml") ? name : name + ".yml");
        if (config == null) {
            msg.send(sender, "config.unknown", Msg.p("name", name));
            return;
        }
        msg.send(sender, "config.header", Msg.p("file", config.fileName()));
        for (SettingInfo info : core.configs().describe(config)) {
            String value = ConfigManager.display(info.value());
            String def = ConfigManager.display(info.defaultValue());
            msg.send(sender, value.equals(def) ? "config.line-default" : "config.line-changed",
                    Msg.p("path", info.path()), Msg.p("desc", info.description()), Msg.p("default", def), Msg.p("value", value));
        }
    }

    private void sendStylePreview(CommandSender sender) {
        msg.send(sender, "style.header");
        Style style = core.style();
        style.colors().forEach((section, colors) -> {
            StringBuilder line = new StringBuilder();
            colors.forEach((name, color) -> line.append("<hover:show_text:'<white>").append(section).append(':').append(name)
                    .append("<newline><gray>").append(color.hex().asHexString()).append(" · Bedrock: ").append(color.legacy())
                    .append("'><").append(section).append(':').append(name).append(">■</").append(section).append(":")
                    .append(name).append("></hover>"));
            sender.sendMessage(core.messages().component(sender, "core", "style.section",
                    Msg.p("name", Style.sectionKey(section)), Msg.mm("colors", line.toString())));
        });
        msg.send(sender, "style.symbols", Msg.p("symbols", String.join(" ", style.symbols().values())));
        msg.send(sender, "style.gradients");
        msg.send(sender, "style.formats", Msg.p("money", Format.money(12_500)), Msg.p("big-money", Format.money(1_234_567_890L)),
                Msg.number("number", 4_250), Msg.p("duration", Format.duration(Duration.ofMinutes(135))));
        msg.send(sender, "style.samples-header");
        for (String sample : List.of("success", "error", "warning", "info", "action")) {
            msg.send(sender, "style.sample-" + sample);
        }
        if (sender instanceof Player player) {
            msg.title(player, "style.title");
            msg.actionBar(player, "style.action-bar");
            core.sounds().play(player, "unlock");
        }
    }

    private void teleport(Player player, World world) {
        Location target = worlds.spawnOf(world.getName()).orElse(world.getSpawnLocation());
        player.teleportAsync(target).thenAccept(ok -> {
            msg.send(player, ok ? "worlds.teleported" : "worlds.teleport-failed", Msg.p("world", world.getName()));
            if (ok) {
                core.sounds().play(player, "teleport");
            }
        });
    }

    private void sendWorlds(CommandSender sender) {
        msg.send(sender, "worlds.header");
        for (WorldSettings s : worlds.worlds()) {
            World world = Bukkit.getWorld(s.name());
            if (world == null) {
                msg.send(sender, "worlds.not-loaded", Msg.p("name", s.name()));
            } else {
                msg.send(sender, "worlds.loaded", Msg.p("name", s.name()), Msg.number("players", world.getPlayers().size()),
                        Msg.number("chunks", world.getLoadedChunks().length));
            }
        }
    }

    private void sendModuleStatus(CommandSender sender) {
        Map<String, ModuleRegistry.State> states = modules.states();
        if (states.isEmpty()) {
            msg.send(sender, "modules.none");
            return;
        }
        msg.send(sender, "modules.header");
        states.forEach((id, state) -> {
            String key = switch (state) {
                case ENABLED -> "modules.enabled";
                case FAILED -> "modules.failed";
                default -> "modules.other";
            };
            msg.send(sender, key, Msg.p("id", id), Msg.p("state", state));
        });
    }
}
