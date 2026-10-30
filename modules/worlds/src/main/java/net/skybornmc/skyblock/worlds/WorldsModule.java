package net.skybornmc.skyblock.worlds;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.worlds.builds.BuildsService;
import net.skybornmc.skyblock.worlds.portals.PortalService;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Creates and configures every world listed in {@code worlds.yml}: void generation, game rules, difficulty,
 * spawn point, spawn platform for builders, fixed time and world border. Settings are re-applied on every
 * start and on reload, so the config file is the single source of truth.
 */
public final class WorldsModule implements SkyblockModule {

    private static final int PLATFORM_RADIUS = 3; // 7x7

    private final Core core;
    private final JavaPlugin plugin;
    private final Logger log;
    private final Map<String, WorldSettings> settings = new LinkedHashMap<>();
    private final WorldsConfig config = new WorldsConfig();
    private BuildsService builds;
    private PortalService portals;

    public WorldsModule(Core core) {
        this.core = core;
        this.plugin = core.plugin();
        this.log = plugin.getLogger();
    }

    @Override
    public String id() {
        return "worlds";
    }

    @Override
    public void enable() {
        loadConfig();
        settings.values().forEach(this::setUp);
        builds = new BuildsService(core, this, config);
        builds.enable();
        portals = new PortalService(core, this, config, builds.pointsStore());
        portals.enable();
    }

    @Override
    public void reload() {
        loadConfig();
        settings.values().forEach(this::setUp);
        core.messages().load(id());
        if (builds != null) {
            builds.reload();
        }
        if (portals != null) {
            portals.reload();
        }
    }

    /** Build portals (step in → target); {@code null} before the module is enabled. */
    public PortalService portals() {
        return portals;
    }

    /** {@code /skyblock builds} (catalog, paste, apply); {@code null} before the module is enabled. */
    public BuildsService builds() {
        return builds;
    }

    /**
     * Moves a world's spawn point (live and in worlds.yml, re-applied on every start). Used by
     * {@code /skyblock builds apply} for a build's {@code player_spawn}; {@code /spawn} and the first join use the spawn
     * world's spawn point. Returns false if the world isn't configured or the file couldn't be saved.
     */
    public boolean setSpawn(String worldName, double x, double y, double z, float yaw, float pitch) {
        String name = worldName.toLowerCase(Locale.ROOT);
        File file = new File(plugin.getDataFolder(), "worlds.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.contains("worlds." + name)) {
            return false;
        }
        String path = "worlds." + name + ".spawn";
        yaml.set(path + ".x", x);
        yaml.set(path + ".y", y);
        yaml.set(path + ".z", z);
        yaml.set(path + ".yaw", (double) yaw);
        try {
            yaml.save(file);
        } catch (java.io.IOException e) {
            log.warning("Could not save worlds.yml: " + e.getMessage());
            return false;
        }
        loadConfig();
        World world = Bukkit.getWorld(name);
        if (world != null) {
            world.setSpawnLocation(new Location(world, x, y, z, yaw, pitch));
        }
        return true;
    }

    @Override
    public void disable() {
        // Worlds are saved by the server on shutdown.
        if (portals != null) {
            portals.disable();
            portals = null;
        }
    }

    /** Configured worlds in config order. */
    public List<WorldSettings> worlds() {
        return Collections.unmodifiableList(new ArrayList<>(settings.values()));
    }

    /** Spawn location of a configured world, if it is loaded. */
    public Optional<Location> spawnOf(String worldName) {
        WorldSettings s = settings.get(worldName.toLowerCase(Locale.ROOT));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }
        if (s == null) {
            return Optional.of(world.getSpawnLocation());
        }
        return Optional.of(new Location(world, s.spawnX(), s.spawnY(), s.spawnZ(), s.spawnYaw(), 0));
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "worlds.yml");
        if (!file.exists()) {
            plugin.saveResource("worlds.yml", false);
        }
        // Typed settings (builds.*) live in the same file, outside the worlds: section.
        core.configs().load(config);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> defaults = WorldSettings.parseDefaults(yaml);
        ConfigurationSection worlds = yaml.getConfigurationSection("worlds");
        settings.clear();
        if (worlds == null) {
            log.warning("worlds.yml has no 'worlds' section; no worlds configured.");
            return;
        }
        for (String name : worlds.getKeys(false)) {
            try {
                settings.put(name, WorldSettings.parse(name, worlds.getConfigurationSection(name), defaults));
            } catch (IllegalArgumentException e) {
                log.severe("worlds.yml: " + e.getMessage() + " (world skipped)");
            }
        }
    }

    private void setUp(WorldSettings s) {
        World world = Bukkit.getWorld(s.name());
        if (world == null) {
            if (!s.create()) {
                log.warning("World '" + s.name() + "' is marked create: false but is not loaded. "
                        + "It must be the main world (server.properties level-name).");
                return;
            }
            world = new WorldCreator(s.name())
                    .environment(World.Environment.NORMAL)
                    .generator(new VoidChunkGenerator(s.biome(), s.spawnX(), s.spawnY(), s.spawnZ()))
                    .generateStructures(false)
                    .createWorld();
            if (world == null) {
                log.severe("Could not create world '" + s.name() + "'.");
                return;
            }
            log.info("World '" + s.name() + "' loaded.");
        }
        apply(world, s);
    }

    private void apply(World world, WorldSettings s) {
        try {
            world.setDifficulty(Difficulty.valueOf(s.difficulty()));
        } catch (IllegalArgumentException e) {
            log.warning("World '" + s.name() + "': unknown difficulty '" + s.difficulty() + "'.");
        }

        s.gamerules().forEach((key, value) -> applyGameRule(world, key, value));

        if (s.time() != null) {
            world.setTime(s.time());
        }

        Location spawn = new Location(world, s.spawnX(), s.spawnY(), s.spawnZ(), s.spawnYaw(), 0);
        world.setSpawnLocation(spawn);

        if (s.border() > 0) {
            WorldBorder border = world.getWorldBorder();
            // Centred on the world origin, not the spawn: builds are planned around 0,0 (BUILDER_BRIEF §3.1, depths
            // layout radius), and moving a spawn with /skyblock builds apply or /setspawn must not move the border.
            border.setCenter(0, 0);
            border.setSize(s.border());
        }

        placePlatformIfEmpty(world, s, spawn);
    }

    @SuppressWarnings("unchecked")
    private void applyGameRule(World world, String key, Object value) {
        GameRule<?> rule = Registry.GAME_RULE.get(NamespacedKey.minecraft(key));
        if (rule == null) {
            log.warning("World '" + world.getName() + "': unknown game rule '" + key + "' in worlds.yml.");
            return;
        }
        try {
            if (rule.getType() == Boolean.class) {
                world.setGameRule((GameRule<Boolean>) rule, toBoolean(value));
            } else if (rule.getType() == Integer.class) {
                world.setGameRule((GameRule<Integer>) rule, toInt(value));
            } else {
                log.warning("World '" + world.getName() + "': game rule '" + key + "' has unsupported type.");
            }
        } catch (IllegalArgumentException e) {
            log.warning("World '" + world.getName() + "': invalid value '" + value + "' for game rule '" + key + "'.");
        }
    }

    /** A small platform under the spawn so builders don't fall into the void. Never overwrites existing blocks. */
    private void placePlatformIfEmpty(World world, WorldSettings s, Location spawn) {
        if (s.platform() == null || s.platform().isBlank()) {
            return;
        }
        Material material = Material.matchMaterial(s.platform());
        if (material == null || !material.isBlock()) {
            log.warning("World '" + s.name() + "': unknown platform block '" + s.platform() + "'.");
            return;
        }
        Block center = spawn.clone().subtract(0, 1, 0).getBlock();
        if (!center.getType().isAir()) {
            return;
        }
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                Block block = center.getRelative(dx, 0, dz);
                if (block.getType().isAir()) {
                    block.setType(material, false);
                }
            }
        }
        log.info("World '" + s.name() + "': placed a spawn platform for builders.");
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "true", "yes", "on" -> true;
            case "false", "no", "off" -> false;
            default -> throw new IllegalArgumentException(text);
        };
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.valueOf(value), e);
        }
    }
}
