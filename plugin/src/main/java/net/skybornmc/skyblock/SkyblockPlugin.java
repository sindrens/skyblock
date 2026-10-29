package net.skybornmc.skyblock;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.skybornmc.skyblock.common.module.ModuleRegistry;
import net.skybornmc.skyblock.motd.MotdModule;
import net.skybornmc.skyblock.worlds.VoidChunkGenerator;
import net.skybornmc.skyblock.worlds.WorldSettings;
import net.skybornmc.skyblock.worlds.WorldsModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/** Plugin entrypoint. Owns the module registry; all gameplay lives in modules. */
public final class SkyblockPlugin extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<gradient:#C21F2B:#FF3D2E>Phoenix</gradient> <#FF8A1F>»</#FF8A1F> ";

    private ModuleRegistry modules;
    private WorldsModule worlds;

    @Override
    public void onEnable() {
        modules = new ModuleRegistry(getLogger());
        registerModules();
        modules.enableAll();
        registerCommands();
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
        modules.register(new MotdModule(this));
    }

    public ModuleRegistry modules() {
        return modules;
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
                                    List<String> failed = modules.reloadAll();
                                    CommandSender sender = ctx.getSource().getSender();
                                    sender.sendMessage(failed.isEmpty()
                                            ? MM.deserialize(PREFIX + "<green>All modules reloaded.")
                                            : MM.deserialize(PREFIX + "<red>✘ Reload failed for: <white><list><red>. Check the console.",
                                                    Placeholder.unparsed("list", String.join(", ", failed))));
                                    return Command.SINGLE_SUCCESS;
                                }))
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
