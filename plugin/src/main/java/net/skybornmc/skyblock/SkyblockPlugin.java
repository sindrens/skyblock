package net.skybornmc.skyblock;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.config.CoreConfig;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.SettingInfo;
import net.skybornmc.skyblock.common.module.ModuleRegistry;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Plugin entrypoint. Owns the module registry; all gameplay lives in modules. */
public final class SkyblockPlugin extends JavaPlugin implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<gradient:#C21F2B:#FF3D2E>Phoenix</gradient> <#FF8A1F>»</#FF8A1F> ";

    private ModuleRegistry modules;
    private ConfigManager configs;
    private final CoreConfig core = new CoreConfig();
    private WorldsModule worlds;

    @Override
    public void onEnable() {
        configs = new ConfigManager(getDataFolder(), getLogger());
        configs.load(core);
        modules = new ModuleRegistry(getLogger());
        registerModules();
        modules.enableAll();
        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        reportConfigProblemsToConsole();
        getLogger().info("Phoenix Skyblock enabled with " + modules.states().size() + " module(s).");
    }

    @Override
    public void onDisable() {
        if (modules != null) {
            modules.disableAll();
        }
    }

    /** Register every feature module here (see CLAUDE.md "Adding a module"). */
    private void registerModules() {
        worlds = new WorldsModule(this);
        modules.register(worlds);
        modules.register(new MotdModule(this, configs));
        modules.register(new ModerationModule(this));
    }

    public ModuleRegistry modules() {
        return modules;
    }

    public ConfigManager configs() {
        return configs;
    }

    public CoreConfig core() {
        return core;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (core.notifyStaffOfConfigProblems && player.hasPermission("skyblock.admin.reload") && !configs.problems().isEmpty()) {
            getServer().getScheduler().runTaskLater(this, () -> sendConfigProblems(player), 40L);
        }
    }

    private void reportConfigProblemsToConsole() {
        Map<String, List<String>> problems = configs.problems();
        if (!problems.isEmpty()) {
            getLogger().warning("Config problems found in " + problems.size() + " file(s); the old/default values are active. See the errors above.");
        }
    }

    /** Lets bukkit.yml use "PhoenixSkyblock" as a void world generator. */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidChunkGenerator(id == null || id.isBlank() ? "the_void" : id, 0.5, 100, 0.5);
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("skyblock")
                        .requires(src -> src.getSender().hasPermission("skyblock.admin.modules")
                                || src.getSender().hasPermission("skyblock.admin.reload")
                                || src.getSender().hasPermission("skyblock.admin.worlds"))
                        .then(Commands.literal("modules")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.modules"))
                                .executes(ctx -> {
                                    sendModuleStatus(ctx.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("reload")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.reload"))
                                .executes(ctx -> {
                                    reload(ctx.getSource().getSender(), null);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("module", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String typed = builder.getRemainingLowerCase();
                                            for (String id : reloadTargets()) {
                                                if (id.startsWith(typed)) {
                                                    builder.suggest(id);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            reload(ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "module").toLowerCase(Locale.ROOT));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("config")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.reload"))
                                .executes(ctx -> {
                                    sendConfigList(ctx.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("file", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String typed = builder.getRemainingLowerCase();
                                            for (String file : configs.loaded().keySet()) {
                                                String name = file.replace(".yml", "");
                                                if (name.startsWith(typed)) {
                                                    builder.suggest(name);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            sendConfig(ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "file").toLowerCase(Locale.ROOT));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("worlds")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.worlds"))
                                .executes(ctx -> {
                                    sendWorlds(ctx.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("tp")
                                .requires(src -> src.getSender().hasPermission("skyblock.admin.worlds")
                                        && src.getSender() instanceof Player)
                                .then(Commands.argument("world", ArgumentTypes.world())
                                        .executes(ctx -> {
                                            Player player = (Player) ctx.getSource().getSender();
                                            World world = ctx.getArgument("world", World.class);
                                            Location target = worlds.spawnOf(world.getName()).orElse(world.getSpawnLocation());
                                            player.teleportAsync(target).thenAccept(ok -> player.sendMessage(MM.deserialize(ok
                                                            ? PREFIX + "<gray>Teleported to <white><world><gray>."
                                                            : PREFIX + "<red>✘ Teleport to <world> failed. Try again.",
                                                    Placeholder.unparsed("world", world.getName()))));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .build(),
                "Phoenix Skyblock admin commands",
                List.of("sb")));
    }

    private List<String> reloadTargets() {
        List<String> targets = new ArrayList<>();
        targets.add("core");
        targets.addAll(modules.enabledIds());
        return targets;
    }

    /** Reloads core + all modules ({@code target == null}), only core, or one module; then reports config problems. */
    private void reload(CommandSender sender, String target) {
        List<String> failed = new ArrayList<>();
        if (target == null || target.equals("core")) {
            configs.load(core);
        }
        if (target == null) {
            failed.addAll(modules.reloadAll());
        } else if (!target.equals("core")) {
            if (!modules.enabledIds().contains(target)) {
                sender.sendMessage(MM.deserialize(PREFIX + "<red>✘ Unknown module <white><id></white>. Choose one of: <white><list>",
                        Placeholder.unparsed("id", target), Placeholder.unparsed("list", String.join(", ", reloadTargets()))));
                return;
            }
            if (!modules.reload(target)) {
                failed.add(target);
            }
        }
        String what = target == null ? "Everything" : "<white>" + target + "</white>";
        if (failed.isEmpty()) {
            sender.sendMessage(MM.deserialize(PREFIX + "<green>✔ " + what + " <green>reloaded."));
        } else {
            sender.sendMessage(MM.deserialize(PREFIX + "<red>✘ Reload failed for: <white><list><red>. Check the console.",
                    Placeholder.unparsed("list", String.join(", ", failed))));
        }
        sendConfigProblems(sender);
    }

    private void sendConfigProblems(CommandSender sender) {
        Map<String, List<String>> problems = configs.problems();
        if (problems.isEmpty()) {
            return;
        }
        sender.sendMessage(MM.deserialize(PREFIX + "<#FF8A1F>⚠ Config problems <gray>(old values are still active):"));
        problems.values().forEach(list -> list.forEach(problem -> sender.sendMessage(MM.deserialize(
                " <dark_gray>•</dark_gray> <#C9C9C9><problem>", Placeholder.unparsed("problem", problem)))));
    }

    private void sendConfigList(CommandSender sender) {
        sender.sendMessage(MM.deserialize(PREFIX + "<white>Config files <gray>(click to view):"));
        Map<String, List<String>> problems = configs.problems();
        for (String file : configs.loaded().keySet()) {
            String name = file.replace(".yml", "");
            String status = problems.containsKey(file) ? "<#FF8A1F>⚠ " + problems.get(file).size() + " problem(s)" : "<green>✔ ok";
            sender.sendMessage(MM.deserialize(" <#FF8A1F>▸</#FF8A1F> <click:run_command:'/skyblock config <name>'>"
                    + "<hover:show_text:'<gray>Show settings of <name>.yml'><white><name>.yml</white></hover></click> "
                    + "<dark_gray>·</dark_gray> " + status, Placeholder.unparsed("name", name)));
        }
    }

    private void sendConfig(CommandSender sender, String name) {
        ModuleConfig config = configs.loaded().get(name.endsWith(".yml") ? name : name + ".yml");
        if (config == null) {
            sender.sendMessage(MM.deserialize(PREFIX + "<red>✘ No config <white><name></white>. Use <white>/skyblock config</white> to list them.",
                    Placeholder.unparsed("name", name)));
            return;
        }
        sender.sendMessage(MM.deserialize(PREFIX + "<white><file> <gray>(active values):", Placeholder.unparsed("file", config.fileName())));
        for (SettingInfo info : configs.describe(config)) {
            boolean isDefault = ConfigManager.display(info.value()).equals(ConfigManager.display(info.defaultValue()));
            sender.sendMessage(MM.deserialize(
                    " <hover:show_text:'<gray><desc><newline><dark_gray>Default: <default>'><#C9C9C9><path></hover>"
                            + "<dark_gray>:</dark_gray> " + (isDefault ? "<white>" : "<#FFD23F>") + "<value>",
                    Placeholder.unparsed("path", info.path()),
                    Placeholder.unparsed("desc", info.description()),
                    Placeholder.unparsed("default", ConfigManager.display(info.defaultValue())),
                    Placeholder.unparsed("value", ConfigManager.display(info.value()))));
        }
    }

    private void sendWorlds(CommandSender sender) {
        sender.sendMessage(MM.deserialize(PREFIX + "<white>Worlds <gray>(click to teleport):"));
        for (WorldSettings s : worlds.worlds()) {
            World world = Bukkit.getWorld(s.name());
            String status = world == null
                    ? "<red>not loaded"
                    : "<green>loaded</green> <dark_gray>·</dark_gray> <gray>" + world.getPlayers().size() + " players · "
                    + world.getLoadedChunks().length + " chunks";
            sender.sendMessage(MM.deserialize(
                    " <#FF8A1F>▸</#FF8A1F> <click:run_command:'/skyblock tp <name>'><hover:show_text:'<gray>Teleport to <name>'>"
                            + "<white><name></white></hover></click> <dark_gray>·</dark_gray> " + status,
                    Placeholder.unparsed("name", s.name())));
        }
    }

    private void sendModuleStatus(CommandSender sender) {
        Map<String, ModuleRegistry.State> states = modules.states();
        if (states.isEmpty()) {
            sender.sendMessage(MM.deserialize(PREFIX + "<gray>No modules registered yet."));
            return;
        }
        sender.sendMessage(MM.deserialize(PREFIX + "<white>Modules:"));
        states.forEach((id, state) -> {
            String color = switch (state) {
                case ENABLED -> "green";
                case FAILED -> "red";
                default -> "gray";
            };
            sender.sendMessage(MM.deserialize(" <white><id> <" + color + ">" + state,
                    Placeholder.unparsed("id", id)));
        });
    }
}
