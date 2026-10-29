package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Essentials: {@code /spawn}, {@code /tpa} family, staff teleports and homes, and the staff tools
 * ({@code /gm /fly /speed /god /heal /feed /clear /invsee /ecsee /vanish /i /give /time /weather /skull /more}).
 * Settings in {@code essentials.yml}, texts in {@code messages/essentials.yml}.
 */
public final class EssentialsModule implements SkyblockModule {

    private final Core core;
    private final EssentialsConfig config = new EssentialsConfig();
    private ModuleMessages msg;
    private TeleportService teleports;
    private TeleportCommands teleportCommands;
    private StaffCommands staff;
    private VanishService vanish;
    private InventoryViews views;

    public EssentialsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return "essentials";
    }

    @Override
    public void enable() {
        core.configs().load(config);
        msg = core.messages(id());
        teleports = new TeleportService(core, config, msg);
        teleportCommands = new TeleportCommands(core, config, msg, teleports);
        staff = new StaffCommands(core, config, msg);
        vanish = new VanishService(core, config, msg);
        views = new InventoryViews(core, config, msg);

        core.services().register(id(), TeleportService.class, teleports);
        for (CommandGroup group : List.of(teleportCommands, staff, vanish, views)) {
            group.register();
        }
        for (Listener listener : List.of(teleports, teleportCommands, staff, vanish, views)) {
            core.plugin().getServer().getPluginManager().registerEvents(listener, core.plugin());
        }
        teleportCommands.start();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(id());
        teleportCommands.reload();
    }

    @Override
    public void disable() {
        if (teleportCommands == null) {
            return;
        }
        teleportCommands.stop();
        teleports.shutdown();
        views.shutdown();
        vanish.shutdown();
        for (Listener listener : List.of(teleports, teleportCommands, staff, vanish, views)) {
            HandlerList.unregisterAll(listener);
        }
    }
}
