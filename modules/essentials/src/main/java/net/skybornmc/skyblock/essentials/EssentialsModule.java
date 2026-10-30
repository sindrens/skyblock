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

    static final String ID = "essentials";

    private final Core core;
    private final EssentialsConfig config = new EssentialsConfig();
    private ModuleMessages msg;
    private TeleportCommands teleportCommands;
    private StaffCommands staff;
    private VanishService vanish;
    private InventoryViews views;
    private JoinFlow join;
    private SpawnProtection protection;
    private EssentialsSettings settings;
    private DropGuard dropGuard;
    private StaffPowers powers;

    public EssentialsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() {
        core.configs().load(config);
        msg = core.messages(id());
        settings = new EssentialsSettings(core);
        teleportCommands = new TeleportCommands(core, config, msg, core.services().get(TeleportService.class), settings);
        vanish = new VanishService(core, config, msg);
        views = new InventoryViews(core, config, msg);
        join = new JoinFlow(core, config, msg, settings);
        protection = new SpawnProtection(core, config, msg, join);
        // Owner answers 331-334: creative only in the creative worlds, god/fly/heal only in staff or build mode, staff items.
        powers = new StaffPowers(core, config, msg, protection::buildModeOn);
        staff = new StaffCommands(core, config, msg, powers);
        dropGuard = new DropGuard(core, config, msg, settings);

        for (CommandGroup group : List.of(teleportCommands, staff, vanish, views, protection)) {
            group.register();
        }
        for (Listener listener : listeners()) {
            core.plugin().getServer().getPluginManager().registerEvents(listener, core.plugin());
        }
        teleportCommands.start();
        powers.start();
        // DEPTHSPROT: /build mode also lifts the worlds module's protection flags in the protected worlds (spawn).
        core.services().optional(net.skybornmc.skyblock.common.region.RegionFlags.class)
                .ifPresent(flags -> flags.addBypass(id(), protection::buildModeIn));
        core.scheduler().later("essentials:settings", 1L, settings::register);
    }

    private List<Listener> listeners() {
        return List.of(teleportCommands, staff, vanish, views, join, protection, powers, dropGuard);
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
        powers.stop();
        core.services().optional(net.skybornmc.skyblock.common.region.RegionFlags.class).ifPresent(flags -> flags.removeBypass(id()));
        settings.unregister();
        views.shutdown();
        vanish.shutdown();
        for (Listener listener : listeners()) {
            HandlerList.unregisterAll(listener);
        }
    }
}
