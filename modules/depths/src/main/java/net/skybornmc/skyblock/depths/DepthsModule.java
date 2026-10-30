package net.skybornmc.skyblock.depths;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.region.RegionFlags;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.satchel.SatchelService;
import net.skybornmc.skyblock.depths.combat.AmbientMobs;
import net.skybornmc.skyblock.depths.combat.CombatZones;
import net.skybornmc.skyblock.depths.combat.MobCatalog;
import net.skybornmc.skyblock.depths.combat.MobSections;
import net.skybornmc.skyblock.depths.drops.DepthsDrops;
import net.skybornmc.skyblock.depths.forest.ForestGuard;
import net.skybornmc.skyblock.depths.zones.TreeTemplates;
import net.skybornmc.skyblock.depths.drops.DropItems;
import net.skybornmc.skyblock.depths.drops.DropSections;
import net.skybornmc.skyblock.depths.drops.DropTable;
import net.skybornmc.skyblock.depths.engine.Depths;
import net.skybornmc.skyblock.depths.engine.DepthsEngine;
import net.skybornmc.skyblock.depths.tiers.Dungeon;
import net.skybornmc.skyblock.depths.satchel.SatchelManager;
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
 * placeholders {@code depths_zone} / {@code depths_tier}. M4.4 satchel ({@link SatchelManager}, published as
 * {@link SatchelService}, opened from {@code /dungeon}) and M4.9 drops and crystals ({@link DepthsDrops}). M4.7 forest:
 * tree templates per tier ({@link TreeTemplates}, used by {@code /depths generate}) and decorative leaves
 * ({@link ForestGuard}). M4.8 combat zones ({@link CombatZones}: shared mobs, mini-boss placeholders) and per-player
 * ambient mobs ({@link AmbientMobs}) from {@code mobs.yml}. Settings, regions, drop tables and tree templates in
 * {@code depths.yml}, texts in {@code messages/depths.yml}.
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
    private DropItems dropItems;
    private SatchelManager satchel;
    private DepthsDrops drops;
    private volatile TreeTemplates trees = TreeTemplates.DEFAULTS;
    private ForestGuard forest;
    private CombatZones combat;
    private AmbientMobs ambient;

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
        publishFlagRegions();
        core.services().register(ID, ZoneService.class, zones);
        // BUILDS: /skyblock builds apply merges the builders' layout into zones.yml, then the module reloads.
        core.services().register(ID, net.skybornmc.skyblock.common.builds.DepthsLayoutHook.class,
                new net.skybornmc.skyblock.depths.zones.ZoneBuildHook(core.plugin().getDataFolder(), this::reload, core.plugin().getLogger()));
        generator = new ZoneGenerator(core, config, msg);
        portals = new ZonePortals(zones, config, msg);
        core.plugin().getServer().getPluginManager().registerEvents(portals, core.plugin());
        ZoneCommands zoneCommands = new ZoneCommands(zones, generator, config, msg);
        zoneCommands.trees(() -> trees);

        gates = new TierGates(core, config, zones, msg);
        core.plugin().getServer().getPluginManager().registerEvents(gates, core.plugin());
        gates.start();
        zoneCommands.beforeZoneTeleport(gates::grantPass);
        // M4.4 satchel + M4.9 drops: drop items and tables from depths.yml, satchel per profile, crystal batches.
        dropItems = new DropItems(core.items(), key -> core.messages().raw(ID, key));
        satchel = new SatchelManager(core, config, msg, dropItems);
        drops = new DepthsDrops(core, config, msg, zones, satchel, dropItems);
        // M4.7 forest + M4.8 combat/ambient mobs (mobs.yml); loadDrops() fills their catalogs.
        forest = new ForestGuard(config, zones, msg);
        core.plugin().getServer().getPluginManager().registerEvents(forest, core.plugin());
        net.skybornmc.skyblock.depths.combat.MobFactory factory = net.skybornmc.skyblock.depths.combat.MobFactory.create(core.plugin());
        combat = new CombatZones(core, config, msg, zones, satchel, drops, factory);
        ambient = new AmbientMobs(core, config, msg, zones, factory);
        combat.otherOwned(ambient::owns);
        loadDrops();
        satchel.start();
        drops.start();
        core.plugin().getServer().getPluginManager().registerEvents(combat, core.plugin());
        core.plugin().getServer().getPluginManager().registerEvents(ambient, core.plugin());
        ambient.start();
        combat.start();
        core.services().register(ID, SatchelService.class, satchel);
        core.plugin().getServer().getPluginManager().registerEvents(satchel, core.plugin());
        core.plugin().getServer().getPluginManager().registerEvents(drops, core.plugin());

        dungeon = new Dungeon(core, config, zones, gates, msg);
        dungeon.satchel(satchel, satchel::menu);
        registerPlaceholders();

        Arg<Player> target = Args.onlinePlayer("player");
        Arg<String> generateTarget = Args.oneOf("zone", zoneCommands::generateTargets);
        Arg<String> tpTarget = Args.oneOf("zone", zones::teleportIds);
        core.commands().register(CommandSpec.command("depths")
                .description("The Depths staff tools").category("Staff").permission(DepthsPerms.COMMAND)
                .sub(CommandSpec.sub("debug")
                        .description("Region at your feet, depleted blocks and engine numbers")
                        .permission(DepthsPerms.DEBUG).playerOnly().optional(target).example("depths debug")
                        .executes(ctx -> {
                            Player shown = ctx.getOr(target, ctx.player());
                            DepthsDebug.show(engine, msg, ctx.player(), shown);
                            DepthsDebug.flags(core.services().optional(RegionFlags.class).orElse(null), msg, ctx.player(), shown);
                            combat.debug(ctx.player(), shown);
                            ambient.debug(ctx.player(), shown);
                        }))
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

    /**
     * Reads drop-items, drops and trees from depths.yml (defaults copied in when missing) and mobs.yml (combat drop items,
     * mobs, combat spawning, ambient mobs), registers the drop items and hands the catalogs out.
     */
    private void loadDrops() {
        File file = new File(core.plugin().getDataFolder(), config.fileName());
        java.util.function.Consumer<String> warn = problem -> core.plugin().getLogger().warning("depths.yml → " + problem);
        java.util.function.Consumer<String> warnMobs = problem -> core.plugin().getLogger().warning(MobSections.FILE + " → " + problem);
        org.bukkit.configuration.file.YamlConfiguration yaml = DropSections.load(file, warn);
        org.bukkit.configuration.file.YamlConfiguration mobs = MobSections.load(new File(core.plugin().getDataFolder(), MobSections.FILE), warnMobs);
        dropItems.load(java.util.Arrays.asList(yaml.getConfigurationSection(DropSections.ITEMS),
                mobs.getConfigurationSection(MobSections.ITEMS)), warn);
        drops.table(DropTable.parse(yaml.getConfigurationSection(DropSections.DROPS), dropItems.ids(), warn));
        trees = TreeTemplates.parse(yaml.getConfigurationSection(DropSections.TREES), config.forestMaxTrunkHeight, warn);
        MobCatalog catalog = MobCatalog.parse(mobs, dropItems.ids(), net.skybornmc.skyblock.depths.combat.MobFactory::spawnableMob, warnMobs);
        combat.catalog(catalog);
        ambient.catalog(catalog);
    }

    /**
     * {@code depths_zone}: zone name (or The Gate); {@code depths_tier}: "Mine · Tier 3". Empty dash elsewhere.
     * {@code satchel_used} / {@code satchel_max}: satchel fill of the active profile (scoreboard {@code Satchel 412 / 640}).
     */
    private void registerPlaceholders() {
        core.placeholders().register(ID, "satchel_used", player -> satchel.placeholder(player, false));
        core.placeholders().register(ID, "satchel_max", player -> satchel.placeholder(player, true));
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

    /**
     * DEPTHSPROT: hands the ore/log fields (kind field: the engine decides there), the tier zones, The Gate and the boss
     * arena to the worlds module's region flags, which protect the rest of the Depths world.
     */
    private void publishFlagRegions() {
        core.services().optional(RegionFlags.class).ifPresent(flags -> flags.contribute(DepthsRegionFlags.OWNER,
                DepthsRegionFlags.of(zones.world(), engine.regions(), zones.zones(), zones.hub(), zones.bossArena())));
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
        publishFlagRegions();
        gates.reload();
        loadDrops();
        satchel.reload();
        drops.start();
        combat.start();
        ambient.start();
    }

    @Override
    public void disable() {
        core.placeholders().unregisterAll(ID);
        core.services().optional(RegionFlags.class).ifPresent(flags -> flags.contribute(DepthsRegionFlags.OWNER, List.of()));
        if (combat != null) {
            HandlerList.unregisterAll(combat);
            combat.stop();
        }
        if (ambient != null) {
            HandlerList.unregisterAll(ambient);
            ambient.stop();
        }
        if (forest != null) {
            HandlerList.unregisterAll(forest);
        }
        if (drops != null) {
            HandlerList.unregisterAll(drops);
            drops.stop();
        }
        if (satchel != null) {
            HandlerList.unregisterAll(satchel);
            satchel.stop();
        }
        if (dropItems != null) {
            dropItems.unregister();
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
