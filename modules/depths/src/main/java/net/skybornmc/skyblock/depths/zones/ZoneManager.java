package net.skybornmc.skyblock.depths.zones;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.engine.Bounds;
import net.skybornmc.skyblock.depths.engine.Depths;
import net.skybornmc.skyblock.depths.engine.DepthsRegion;
import net.skybornmc.skyblock.depths.engine.RegionIndex;
import net.skybornmc.skyblock.depths.engine.ResourceBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Loads {@code zones.yml} (copied from the jar on first start), checks it, registers every ore/log field with the
 * per-player blocks engine and answers {@link ZoneService} queries.
 */
public final class ZoneManager implements ZoneService {

    public static final String FILE = "zones.yml";
    /** Region source for the engine (so a reload replaces exactly these). */
    public static final String SOURCE = "depths-zones";

    /** A zone as an engine {@link Bounds} for the zone-at-location index. */
    private record ZoneBounds(Zone zone, String world) implements Bounds {
        public int minX() {
            return zone.region().minX();
        }

        public int minY() {
            return zone.region().minY();
        }

        public int minZ() {
            return zone.region().minZ();
        }

        public int maxX() {
            return zone.region().maxX();
        }

        public int maxY() {
            return zone.region().maxY();
        }

        public int maxZ() {
            return zone.region().maxZ();
        }
    }

    private final Core core;
    private final DepthsConfig config;
    private final Depths engine;
    private final Logger log;
    private ZoneSet set = ZoneSet.empty("depths", 100);
    private RegionIndex<ZoneBounds> index = RegionIndex.empty();
    private List<String> problems = List.of();

    /** DV2-SPIKE: engine region id ({@code <zone>_<field>}) → that zone's tier. */
    private volatile java.util.Map<String, Integer> regionTiers = java.util.Map.of();

    /** DV2-SPIKE: wing tier of the zone a field region belongs to (0 = not a zone field). */
    public int tierOfRegion(String regionId) {
        Integer tier = regionTiers.get(regionId);
        return tier == null ? 0 : tier;
    }

    public ZoneManager(Core core, DepthsConfig config, Depths engine) {
        this.core = core;
        this.config = config;
        this.engine = engine;
        this.log = core.plugin().getLogger();
    }

    /** (Re)loads zones.yml and re-registers the fields with the engine. */
    public void load() {
        File file = new File(core.plugin().getDataFolder(), FILE);
        if (!file.exists()) {
            core.plugin().saveResource(FILE, false);
        }
        List<String> found = new ArrayList<>();
        YamlConfiguration yaml = new YamlConfiguration();
        ZoneSet loaded;
        try {
            yaml.load(file);
            loaded = ZoneParser.parse(yaml, FILE, found::add);
        } catch (IOException | InvalidConfigurationException e) {
            found.add(FILE + ": not valid YAML, no zones loaded. " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            loaded = ZoneSet.empty("depths", 100);
        }
        World world = Bukkit.getWorld(loaded.world());
        double border = world == null ? 0 : world.getWorldBorder().getSize() / 2.0;
        if (world == null) {
            found.add(FILE + ": world '" + loaded.world() + "' is not loaded (zones work once it is)");
        }
        found.addAll(ZoneValidator.validate(loaded, border));
        found.forEach(p -> log.warning("Depths zones: " + p));
        problems = List.copyOf(found);
        set = loaded;
        List<ZoneBounds> bounds = new ArrayList<>();
        for (Zone z : loaded.zones()) {
            bounds.add(new ZoneBounds(z, loaded.world()));
        }
        index = RegionIndex.of(bounds);
        registerFields();
    }

    /** Registers every field as an engine region {@code <zone>_<field>} (replaces the previous ones). */
    private void registerFields() {
        engine.unregisterAll(SOURCE);
        java.util.Map<String, Integer> tiers = new java.util.HashMap<>();
        for (Zone zone : set.zones()) {
            for (Field field : zone.fields()) {
                tiers.put(zone.regionId(field), zone.tier());
            }
        }
        regionTiers = java.util.Map.copyOf(tiers);
        if (!config.registerZoneFields) {
            log.info("Depths zones: " + set.zones().size() + " zones loaded, fields not registered (zones.register-fields: false)");
            return;
        }
        int count = 0;
        for (Zone zone : set.zones()) {
            for (Field field : zone.fields()) {
                String id = zone.regionId(field);
                try {
                    Map<Material, ResourceBlock> resources = new EnumMap<>(Material.class);
                    for (Field.Resource r : field.resources()) {
                        ResourceBlock res = resource(r);
                        if (res != null && resources.putIfAbsent(res.block(), res) != null) {
                            log.warning("Depths zones: " + id + ": " + r.block() + " is listed twice, the first entry is used");
                        }
                    }
                    Box b = field.box();
                    engine.registerRegion(DepthsRegion.of(id, set.world(), b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(),
                            resources, SOURCE));
                    count++;
                } catch (IllegalArgumentException e) {
                    log.warning("Depths zones: field region '" + id + "' not registered: " + e.getMessage());
                }
            }
        }
        log.info("Depths zones: " + set.zones().size() + " zones, " + count + " field regions registered"
                + (problems.isEmpty() ? "" : " (" + problems.size() + " warnings above)"));
    }

    private ResourceBlock resource(Field.Resource r) {
        Material block = Material.matchMaterial(r.block());
        if (block == null || !block.isBlock()) {
            log.warning("Depths zones: '" + r.block() + "' is not a block, skipped");
            return null;
        }
        BlockData depleted = blockData(r.depleted() == null ? config.defaultDepleted : r.depleted());
        if (depleted == null) {
            log.warning("Depths zones: depleted block '" + r.depleted() + "' of " + r.block() + " is not valid, "
                    + config.defaultDepleted + " is used");
            depleted = blockData(config.defaultDepleted);
            if (depleted == null) {
                depleted = Material.TUFF.createBlockData();
            }
        }
        int seconds = r.respawnSeconds() == null ? config.defaultRespawnSeconds : r.respawnSeconds();
        return new ResourceBlock(block, depleted, seconds * 20);
    }

    /** A block id or block state ({@code tuff}, {@code oak_leaves[persistent=true]}), or {@code null} if invalid. */
    public static BlockData blockData(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String id = text.trim().toLowerCase(Locale.ROOT);
        try {
            BlockData data = Bukkit.createBlockData(id.contains(":") ? id : "minecraft:" + id);
            return data.getMaterial().isBlock() ? data : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Removes the field regions from the engine. */
    public void unload() {
        engine.unregisterAll(SOURCE);
    }

    public ZoneSet set() {
        return set;
    }

    /** Warnings of the last load (for staff tools). */
    public List<String> problems() {
        return problems;
    }

    /** Ids accepted by {@code /depths tp}: The Gate, every zone, the boss arena. */
    public List<String> teleportIds() {
        List<String> ids = new ArrayList<>();
        if (set.hub() != null) {
            ids.add(Hub.ID);
        }
        set.zones().forEach(z -> ids.add(z.id()));
        if (set.arena() != null) {
            ids.add(BossArena.ID);
        }
        return ids;
    }

    /** The spawn point of a teleport id, if known. */
    public Optional<Point> spawnOf(String id) {
        if (Hub.ID.equalsIgnoreCase(id) && set.hub() != null) {
            return Optional.of(set.hub().spawn());
        }
        if (BossArena.ID.equalsIgnoreCase(id) && set.arena() != null) {
            return Optional.of(set.arena().spawn());
        }
        return set.zone(id).map(Zone::spawn);
    }

    // ------------------------------------------------------------------ ZoneService

    @Override
    public String world() {
        return set.world();
    }

    @Override
    public List<Zone> zones() {
        return set.zones();
    }

    @Override
    public List<Zone> zones(Aspect aspect) {
        return set.zones(aspect);
    }

    @Override
    public Optional<Zone> zone(String id) {
        return set.zone(id);
    }

    @Override
    public Zone zoneAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        ZoneBounds b = index.at(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return b == null ? null : b.zone();
    }

    @Override
    public Hub hub() {
        return set.hub();
    }

    @Override
    public BossArena bossArena() {
        return set.arena();
    }

    @Override
    public ZoneAccess access(Player player, Zone zone) {
        SkillService skills = core.services().optional(SkillService.class).orElse(null);
        if (skills == null || !skills.available(zone.aspect().skill())) {
            return ZoneAccess.of(zone, null);
        }
        return ZoneAccess.of(zone, skills.level(player, zone.aspect().skill()));
    }

    @Override
    public Location location(Point point) {
        World world = Bukkit.getWorld(set.world());
        return world == null ? null : new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }
}
