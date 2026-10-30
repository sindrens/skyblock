package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Staff tools (M1.9): staff mode ({@code /staff}), {@code /freeze}, {@code /inspect}, {@code /report} and {@code /reports}.
 * Settings in {@code staff.yml}, texts in {@code messages/staff.yml}, data in {@code db/staff}.
 */
public final class StaffModule implements SkyblockModule {

    /** What menus and tools need from the module. */
    record Services(Core core, ModuleMessages messages, StaffStore store, FreezeService freeze, ReportService reports) {
    }

    private final Core core;
    private final StaffConfig config = new StaffConfig();
    private FreezeService freeze;
    private ReportService reports;
    private StaffMode mode;

    private ProfileSwitchGuard profileGuard;

    public StaffModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return StaffFeature.MODULE;
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(id(), getClass().getClassLoader());
        core.configs().load(config);
        ModuleMessages msg = core.messages(id());
        StaffStore store = new StaffStore(core.database());
        freeze = new FreezeService(core, config, msg, store);
        reports = new ReportService(core, config, msg, store);
        Services services = new Services(core, msg, store, freeze, reports);
        mode = new StaffMode(core, config, msg, store, freeze, reports, services);

        freeze.register();
        freeze.load();
        // Other modules read this (e.g. /pay refuses frozen players).
        core.placeholders().registerText(id(), "frozen", p -> p != null && freeze.isFrozen(p) ? "true" : "false");
        // M3.7 trade (and others) refuse players in staff mode.
        core.placeholders().registerText(id(), "staff_mode", p -> p != null && mode != null && mode.inStaffMode(p) ? "true" : "false");
        reports.register();
        // Phase P 12.9: moderation closes the punishing staff member's claimed reports through this service
        core.services().register(id(), net.skybornmc.skyblock.common.moderation.ReportLink.class, reports);
        mode.register();
        Arg<String> inspectTarget = Args.offlinePlayer("player");
        core.commands().register(CommandSpec.command("inspect")
                .description("Profile, inventory, reports and logs of a player").category("Staff")
                .permission(StaffPerms.INSPECT).playerOnly().arg(inspectTarget).aliases("check").example("inspect Steve")
                .executes(ctx -> InspectMenu.open(services, ctx.player(), ctx.get(inspectTarget))));

        for (Listener listener : listeners()) {
            core.plugin().getServer().getPluginManager().registerEvents(listener, core.plugin());
        }
        freeze.start();
        reports.start();
    }

    private List<Listener> listeners() {
        if (profileGuard == null) {
            profileGuard = new ProfileSwitchGuard(freeze, mode, core.messages(id()));
        }
        return List.of(freeze, reports, mode, profileGuard);
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(id());
    }

    @Override
    public void disable() {
        if (mode == null) {
            return;
        }
        mode.shutdown();
        freeze.stop();
        for (Listener listener : listeners()) {
            HandlerList.unregisterAll(listener);
        }
        profileGuard = null;
    }
}
