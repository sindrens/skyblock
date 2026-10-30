package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.teleport.TeleportService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Essentials: join flow (welcome, join/quit messages; the menu item lives in the menus module), spawn protection ({@code /build}, {@code /setspawn}),
 * {@code /spawn}, {@code /tpa} family, staff teleports and homes (all through the core TeleportService), and the staff tools
 * ({@code /gm /fly /speed /god /heal /feed /clear /invsee /ecsee /vanish /i /give /time /weather /skull /more}).
 * Settings in {@code essentials.yml}, texts in {@code messages/essentials.yml}.
 */
public final class EssentialsModule implements SkyblockModule {

    private final Core core;
    private final EssentialsConfig config = new EssentialsConfig();
    private ModuleMessages msg;
    private TeleportCommands teleportCommands;
    private StaffCommands staff;
    private VanishService vanish;
    private InventoryViews views;
    private JoinFlow join;
    private SpawnProtection protection;

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
        teleportCommands = new TeleportCommands(core, config, msg, core.services().get(TeleportService.class));
        staff = new StaffCommands(core, config, msg);
        vanish = new VanishService(core, config, msg);
        views = new InventoryViews(core, config, msg);
        join = new JoinFlow(core, config, msg);
        protection = new SpawnProtection(core, config, msg, join);

        for (CommandGroup group : List.of(teleportCommands, staff, vanish, views, protection)) {
            group.register();
        }
        for (Listener listener : listeners()) {
            core.plugin().getServer().getPluginManager().registerEvents(listener, core.plugin());
        }
        teleportCommands.start();
    }

    private List<Listener> listeners() {
        return List.of(teleportCommands, staff, vanish, views, join, protection);
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
        views.shutdown();
        vanish.shutdown();
        for (Listener listener : listeners()) {
            HandlerList.unregisterAll(listener);
        }
    }
}
