package net.skybornmc.skyblock;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.CommandService;
import net.skybornmc.skyblock.common.command.OfflineNames;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.item.DemoItems;
import net.skybornmc.skyblock.common.item.DupeDetector;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemsConfig;
import net.skybornmc.skyblock.common.menu.DemoMenu;
import net.skybornmc.skyblock.common.menu.MenuService;
import net.skybornmc.skyblock.common.player.PlayerService;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.config.CoreConfig;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.SettingInfo;
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
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
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
    private final List<String> styleProblems = new ArrayList<>();
    private final DatabaseConfig dbConfig = new DatabaseConfig();
    private final ItemsConfig itemsConfig = new ItemsConfig();

    @Override
    public void onEnable() {
        ConfigManager configs = new ConfigManager(getDataFolder(), getLogger());
        CoreConfig coreConfig = new CoreConfig();
        configs.load(coreConfig);
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
        PlayerService players = new PlayerService(this, database, messages, bedrock);                 // M0.4
        CommandService commands = new CommandService(this, messages, sounds);                         // M0.5
        OfflineNames.use(players.nameSuggestions());
        MenuService menus = new MenuService(this, configs, messages, sounds);                         // M0.6
        configs.load(itemsConfig);                                                                    // M0.7
        messages.load("items");
        ItemRegistry items = new ItemRegistry(messages);
        DupeDetector dupes = new DupeDetector(this, itemsConfig, messages.of("items"));

        core = new Core(this, coreConfig, configs, style, messages, sounds, database, players, commands, menus, items, dupes);
        msg = core.messages("core");
        players.start();
        menus.enable();
        DemoItems.register(items, messages);
        dupes.start();

        modules = new ModuleRegistry(getLogger());
        registerModules();
        modules.enableAll();
        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        if (!allProblems().isEmpty()) {
            getLogger().warning("Config/message problems found; the previous or default values are active. See the errors above.");
        }
        getLogger().info("Phoenix Skyblock enabled with " + modules.states().size() + " module(s).");
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.menus().disable();
        }
        if (modules != null) {
            modules.disableAll();
        }
        if (core != null) {
            core.dupes().stop();
            core.players().shutdown();
            core.database().close();
        }
    }

    /** Register every feature module here (see CLAUDE.md "Adding a module"). */
    private void registerModules() {
        worlds = new WorldsModule(this);
        modules.register(worlds);
        modules.register(new MotdModule(core));
        modules.register(new ModerationModule(core));
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
                                || src.getSender().hasPermission("skyblock.admin.demo")
                                || src.getSender().hasPermission("skyblock.admin.items"))
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
                        .then(Commands.literal("demo-menu")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.demo") && src.getSender() instanceof Player)
                                .executes(ctx -> run(() -> core.menus().open((Player) ctx.getSource().getSender(), new DemoMenu()))))
                        .then(Commands.literal("give-item")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.items"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(suggest(() -> core.items().ids()))
                                        .executes(ctx -> run(() -> giveItem(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "id"), null, 1)))
                                        .then(Commands.argument("player", ArgumentTypes.player())
                                                .executes(ctx -> run(() -> giveItem(ctx.getSource().getSender(),
                                                        StringArgumentType.getString(ctx, "id"), target(ctx), 1)))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                        .suggests(suggest(() -> List.of("1", "16", "64")))
                                                        .executes(ctx -> run(() -> giveItem(ctx.getSource().getSender(),
                                                                StringArgumentType.getString(ctx, "id"), target(ctx),
                                                                IntegerArgumentType.getInteger(ctx, "amount"))))))))
                        .then(Commands.literal("worlds")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.worlds"))
                                .executes(ctx -> run(() -> sendWorlds(ctx.getSource().getSender()))))
                        .then(Commands.literal("tp")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.worlds")
                                        && src.getSender() instanceof Player)
                                .then(Commands.argument("world", ArgumentTypes.world())
                                        .executes(ctx -> run(() -> teleport((Player) ctx.getSource().getSender(),
                                                ctx.getArgument("world", World.class))))))
                        .build(),
                "Phoenix Skyblock admin commands",
                List.of("sb")));
    }

    private static Player target(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    private void giveItem(CommandSender sender, String id, Player target, int amount) {
        ModuleMessages im = core.messages().of("items");
        if (target == null) {
            if (!(sender instanceof Player self)) {
                im.send(sender, "give.unknown", Msg.p("id", id));
                return;
            }
            target = self;
        }
        if (core.items().get(id).isEmpty()) {
            im.send(sender, "give.unknown", Msg.p("id", id));
            return;
        }
        Player receiver = target;
        int given = 0;
        boolean full = false;
        // Unique items are always created one at a time.
        while (given < amount) {
            ItemStack stack = core.items().create(id, amount - given, "admin:" + sender.getName(), null);
            if (stack.getAmount() <= 0) {
                break;
            }
            given += stack.getAmount();
            var left = receiver.getInventory().addItem(stack);
            if (!left.isEmpty()) {
                full = true;
                left.values().forEach(rest -> receiver.getWorld().dropItemNaturally(receiver.getLocation(), rest));
            }
        }
        if (full) {
            im.send(sender, "give.inventory-full", Msg.p("player", receiver.getName()));
        }
        im.send(sender, "give.given", Msg.p("amount", given), Msg.p("item", id), Msg.p("player", receiver.getName()));
        if (receiver != sender) {
            im.send(receiver, "give.received", Msg.p("amount", given), Msg.p("item", id));
        }
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
            core.configs().load(itemsConfig);
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
