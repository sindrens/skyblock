package net.skybornmc.skyblock;

import com.mojang.brigadier.Command;
import net.skybornmc.skyblock.common.module.ModuleRegistry;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/** Plugin entrypoint. Owns the module registry; all gameplay lives in modules. */
public final class SkyblockPlugin extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ModuleRegistry modules;

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
        // modules.register(new IslandsModule(this));
    }

    public ModuleRegistry modules() {
        return modules;
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("skyblock")
                        .requires(src -> src.getSender().hasPermission("skyblock.admin.modules")
                                || src.getSender().hasPermission("skyblock.admin.reload"))
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
                                            ? MM.deserialize("<green>All modules reloaded.")
                                            : MM.deserialize("<red>Reload failed for: <white>" + String.join(", ", failed)
                                                    + "<red>. Check the console."));
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(),
                "Skyblock admin commands",
                List.of("sb")));
    }

    private void sendModuleStatus(CommandSender sender) {
        Map<String, ModuleRegistry.State> states = modules.states();
        if (states.isEmpty()) {
            sender.sendMessage(MM.deserialize("<gray>No modules registered yet."));
            return;
        }
        sender.sendMessage(MM.deserialize("<gradient:#C21F2B:#FF3D2E>Phoenix</gradient> <#FF8A1F>»</#FF8A1F> <white>Modules:"));
        states.forEach((id, state) -> {
            String color = switch (state) {
                case ENABLED -> "green";
                case FAILED -> "red";
                default -> "gray";
            };
            sender.sendMessage(MM.deserialize(" <white>" + id + " <" + color + ">" + state));
        });
    }
}
