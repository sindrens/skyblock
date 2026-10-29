package net.skybornmc.skyblock.depths;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.depths.engine.Depths;
import net.skybornmc.skyblock.depths.engine.DepthsEngine;
import net.skybornmc.skyblock.depths.tiers.Dungeon;
import net.skybornmc.skyblock.depths.tiers.TierGates;
import net.skybornmc.skyblock.depths.zones.Hub;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneCommands;
import net.skybornmc.skyblock.depths.zones.ZoneGenerator;
import net.skybornmc.skyblock.depths.zones.ZoneManager;
import net.skybornmc.skyblock.depths.zones.ZonePortals;
import net.skybornmc.skyblock.depths.zones.ZoneService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.util.List;

/**
 * The Depths (Phase 4). M4.3: the per-player blocks engine ({@link DepthsEngine}), published as the {@link Depths}
 * service, plus {@code /depths debug} for staff. M4.1: the layout from {@code zones.yml} ({@link ZoneManager}, published
 * as {@link ZoneService}; its ore/log fields become engine regions), {@code /depths generate} (placeholder builds) and
 * {@code /depths tp}. M4.6: tier locks and gates ({@link TierGates}), {@code /dungeon} ({@link Dungeon}) and the
 * placeholders {@code depths_zone} / {@code depths_tier}. Settings and regions in {@code depths.yml}, texts in {@code messages/depths.yml}.
 */
public final class DepthsModule implements SkyblockModule {

    static final String ID = "depths";

    private final Core core;
    private final DepthsConfig config = new DepthsConfig();
    private DepthsEngine engine;
    private ZoneManager zones;
    private ZoneGenerator generator;
    private ZonePortals portals;
    private TierGates gates;
    private Dungeon dungeon;
    private ModuleMessages msg;

    public DepthsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        msg = core.messages(ID);
        engine = new DepthsEngine(core, config, msg);
        engine.configRegions(loadRegions());
        engine.start();
        core.services().register(ID, Depths.class, engine);
        core.plugin().getServer().getPluginManager().registerEvents(engine, core.plugin());

        zones = new ZoneManager(core, config, engine);
        loadZones();
        core.services().register(ID, ZoneService.class, zones);
        generator = new ZoneGenerator(core, config, msg);
        portals = new ZonePortals(zones, config, msg);
        core.plugin().getServer().getPluginManager().registerEvents(portals, core.plugin());
        ZoneCommands zoneCommands = new ZoneCommands(zones, generator, config, msg);

        gates = new TierGates(core, config, zones, msg);
        core.plugin().getServer().getPluginManager().registerEvents(gates, core.plugin());
        gates.start();
        zoneCommands.beforeZoneTeleport(gates::grantPass);
        dungeon = new Dungeon(core, config, zones, gates, msg);
        dungeon.start();
        registerPlaceholders();

        Arg<Player> target = Args.onlinePlayer("player");
        Arg<String> generateTarget = Args.oneOf("zone", zoneCommands::generateTargets);
        Arg<String> tpTarget = Args.oneOf("zone", zones::teleportIds);
        core.commands().register(CommandSpec.command("depths")
                .description("The Depths staff tools").category("Staff").permission(DepthsPerms.COMMAND)
                .sub(CommandSpec.sub("debug")
                        .description("Region at your feet, depleted blocks and engine numbers")
                        .permission(DepthsPerms.DEBUG).playerOnly().optional(target).example("depths debug")
                        .executes(ctx -> DepthsDebug.show(engine, msg, ctx.player(), ctx.getOr(target, ctx.player()))))
                .sub(CommandSpec.sub("generate")
                        .description("Build the placeholder layout (The Gate, zones, boss arena); skips zones marked built")
                        .permission(DepthsPerms.GENERATE).optional(generateTarget)
                        .confirmIf(15, ctx -> ZoneCommands.ALL.equalsIgnoreCase(ctx.getOr(generateTarget, ZoneCommands.ALL)))
                        .example("depths generate mine_t01")
                        .executes(ctx -> zoneCommands.generate(ctx.sender(), ctx.getOr(generateTarget, ZoneCommands.ALL))))
                .sub(CommandSpec.sub("tp")
                        .description("Teleport to The Gate, a tier zone or the boss arena (ignores locks)")
                        .permission(DepthsPerms.TP).playerOnly().arg(tpTarget).example("depths tp mine_t01")
                        .executes(ctx -> zoneCommands.teleport(ctx.player(), ctx.get(tpTarget)))));
        core.commands().register(CommandSpec.command("dungeon")
                .description("Teleport to your unlocked Depths tiers; see what the locked ones need").category("Depths")
                .permission(DepthsPerms.DUNGEON).playerOnly().example("dungeon")
                .executes(ctx -> dungeon.open(ctx.player())));
    }

    /** {@code depths_zone}: zone name (or The Gate); {@code depths_tier}: "Mine · Tier 3". Empty dash elsewhere. */
    private void registerPlaceholders() {
        core.placeholders().register(ID, "depths_zone", player -> {
            String place = player == null ? null : gates.place(player.getUniqueId());
            if (place == null) {
                return msg.component(player, "placeholders.none");
            }
            if (Hub.ID.equals(place)) {
                Hub hub = zones.hub();
                return hub == null ? msg.component(player, "placeholders.none") : msg.component(player, "placeholders.zone", Msg.p("name", hub.name()));
            }
            Zone zone = zones.zone(place).orElse(null);
            return zone == null ? msg.component(player, "placeholders.none") : msg.component(player, "placeholders.zone", Msg.p("name", zone.name()));
        });
        core.placeholders().register(ID, "depths_tier", player -> {
            String place = player == null ? null : gates.place(player.getUniqueId());
            Zone zone = place == null ? null : zones.zone(place).orElse(null);
            if (zone == null) {
                return Hub.ID.equals(place) ? msg.component(player, "placeholders.hub") : msg.component(player, "placeholders.none");
            }
            Component wing = msg.component(player, "tiers.wing." + zone.aspect().id());
            return msg.component(player, "placeholders.tier", Msg.c("wing", wing), Msg.p("tier", zone.tier()));
        });
    }

    private void loadZones() {
        zones.load();
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
        loadZones();
        gates.reload();
    }

    @Override
    public void disable() {
        core.placeholders().unregisterAll(ID);
        if (dungeon != null) {
            dungeon.stop();
        }
        if (gates != null) {
            HandlerList.unregisterAll(gates);
            gates.stop();
        }
        if (generator != null) {
            generator.stop();
        }
        if (portals != null) {
            HandlerList.unregisterAll(portals);
        }
        if (zones != null) {
            zones.unload();
        }
        if (engine == null) {
            return;
        }
        HandlerList.unregisterAll(engine);
        engine.stop();
    }
}
