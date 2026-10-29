package net.skybornmc.skyblock.depths;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.depths.engine.Depths;
import net.skybornmc.skyblock.depths.engine.DepthsEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.util.List;

/**
 * The Depths (Phase 4). M4.3: the per-player blocks engine ({@link DepthsEngine}), published as the {@link Depths}
 * service, plus {@code /depths debug} for staff. Settings and regions in {@code depths.yml}, texts in
 * {@code messages/depths.yml}.
 */
public final class DepthsModule implements SkyblockModule {

    static final String ID = "depths";

    private final Core core;
    private final DepthsConfig config = new DepthsConfig();
    private DepthsEngine engine;
    private ModuleMessages msg;

    public DepthsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() {
        core.configs().load(config);
        msg = core.messages(ID);
        engine = new DepthsEngine(core, config, msg);
        engine.configRegions(loadRegions());
        engine.start();
        core.services().register(ID, Depths.class, engine);
        core.plugin().getServer().getPluginManager().registerEvents(engine, core.plugin());

        Arg<Player> target = Args.onlinePlayer("player");
        core.commands().register(CommandSpec.command("depths")
                .description("The Depths staff tools").category("Staff").permission(DepthsPerms.DEBUG)
                .sub(CommandSpec.sub("debug")
                        .description("Region at your feet, depleted blocks and engine numbers")
                        .permission(DepthsPerms.DEBUG).playerOnly().optional(target).example("depths debug")
                        .executes(ctx -> DepthsDebug.show(engine, msg, ctx.player(), ctx.getOr(target, ctx.player())))));
    }

    private List<net.skybornmc.skyblock.depths.engine.DepthsRegion> loadRegions() {
        File file = new File(core.plugin().getDataFolder(), config.fileName());
        return RegionLoader.load(file, config, problem -> core.plugin().getLogger().warning(problem));
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        engine.configRegions(loadRegions());
    }

    @Override
    public void disable() {
        if (engine == null) {
            return;
        }
        HandlerList.unregisterAll(engine);
        engine.stop();
    }
}
