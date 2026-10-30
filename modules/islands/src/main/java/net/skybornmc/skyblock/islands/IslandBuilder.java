package net.skybornmc.skyblock.islands;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.db.MainThread;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Builds a new island's starter islet: the configured schematic via FastAsyncWorldEdit (async) when the file exists and
 * FAWE is enabled, otherwise the code-built "Ember Meadow" placeholder ({@link StarterIsland}). Then fills the starter
 * chest, writes the sign and works out the home spot. Everything that touches the world runs on the main thread except
 * the FAWE paste itself.
 */
final class IslandBuilder {

    /** What was built. {@code source} is "schematic:&lt;name&gt;" or "placeholder". */
    record Build(Island.Home home, String source, boolean chestFilled, List<String> chestItems) {
    }

    /** Schematic metadata ({@code <name>.yml} next to the .schem, BUILDER_BRIEF §6.6); offsets from the paste origin. */
    record Metadata(int[] home, Float homeYaw, int[] chest, int[] sign) {
        static final Metadata NONE = new Metadata(null, null, null, null);
    }

    private record Pasted(FaweSchematics.Pasted area, Metadata metadata) {
    }

    private final Core core;
    private final IslandsConfig config;
    private final IslandsModule module;
    private final Executor main;
    private final Executor async;

    IslandBuilder(IslandsModule module) {
        this.module = module;
        this.core = module.core();
        this.config = module.config();
        this.main = new MainThread(core.plugin());
        this.async = task -> Bukkit.getScheduler().runTaskAsynchronously(core.plugin(), task);
    }

    static boolean faweAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    File schematicsFolder() {
        return new File(core.plugin().getDataFolder(), "schematics");
    }

    File schematicFile() {
        return new File(schematicsFolder(), config.schematic + ".schem");
    }

    /** Builds the starter islet of {@code island}; completes on the main thread. */
    CompletableFuture<Build> build(Island island) {
        World world = Bukkit.getWorld(island.world());
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("World '" + island.world() + "' is not loaded"));
        }
        int x = island.centerX();
        int y = config.surfaceY;
        int z = island.centerZ();
        List<Chunk> held = new ArrayList<>();
        return loadChunks(world, x, z, held)
                .thenComposeAsync(ignored -> paste(world, x, y, z), main)
                .thenApplyAsync(pasted -> finish(island, world, x, y, z, pasted), main)
                .whenCompleteAsync((build, error) -> held.forEach(chunk -> chunk.removePluginChunkTicket(core.plugin())), main);
    }

    /** Loads (and holds with a plugin ticket) the 3×3 chunks around the island center. */
    private CompletableFuture<Void> loadChunks(World world, int x, int z, List<Chunk> held) {
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                futures.add(world.getChunkAtAsync((x >> 4) + dx, (z >> 4) + dz, true).thenApplyAsync(chunk -> {
                    chunk.addPluginChunkTicket(core.plugin());
                    held.add(chunk);
                    return chunk;
                }, main));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /** Schematic paste (async) or {@code null} when the placeholder must be used. Completes on the main thread. */
    private CompletableFuture<Pasted> paste(World world, int x, int y, int z) {
        File file = schematicFile();
        if (!faweAvailable() || !file.isFile()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        Metadata metadata = readMetadata(new File(schematicsFolder(), config.schematic + ".yml"));
                        return new Pasted(FaweSchematics.paste(file, world, x, y, z), metadata);
                    } catch (Exception e) {
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                }, async)
                .orTimeout(config.pasteTimeoutSeconds, TimeUnit.SECONDS)
                .handleAsync((pasted, error) -> {
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Islands: pasting " + file.getName()
                                + " failed, using the placeholder islet instead", error);
                        return null;
                    }
                    return pasted;
                }, main);
    }

    /** Reads the optional metadata file (off the main thread). */
    static Metadata readMetadata(File file) {
        if (!file.isFile()) {
            return Metadata.NONE;
        }
        return readMetadata(YamlConfiguration.loadConfiguration(file));
    }

    /**
     * Two formats: the builders' file (BUILDER_BRIEF §3.6, has {@code origin}: world coordinates, {@code home},
     * {@code containers} with the starter chest, {@code signs} with the menu sign) is turned into offsets from the
     * paste origin; the old format already holds offsets ({@code home/chest/sign: {x, y, z}}).
     */
    static Metadata readMetadata(ConfigurationSection yaml) {
        int[] origin = point(yaml, "origin");
        if (origin == null) {
            return new Metadata(point(yaml, "home"), yaml.isSet("home.yaw") ? (float) yaml.getDouble("home.yaw") : null,
                    point(yaml, "chest"), point(yaml, "sign"));
        }
        int[] home = relative(point(yaml, "home"), origin);
        Float yaw = yaml.isSet("home.yaw") ? (float) yaml.getDouble("home.yaw") : null;
        int[] chest = relative(listPoint(yaml, "containers", "starter_chest"), origin);
        int[] sign = relative(listPoint(yaml, "signs", "menu_sign"), origin);
        return new Metadata(home, yaw, chest, sign);
    }

    /** Block position {x, y, z} of a section (decimals rounded down: 0.5 is block 0). */
    private static int[] point(ConfigurationSection yaml, String key) {
        if (!yaml.isSet(key + ".x") || !yaml.isSet(key + ".y") || !yaml.isSet(key + ".z")) {
            return null;
        }
        return new int[]{(int) Math.floor(yaml.getDouble(key + ".x")), (int) Math.floor(yaml.getDouble(key + ".y")),
                (int) Math.floor(yaml.getDouble(key + ".z"))};
    }

    /** The entry with {@code id} in a list of {x, y, z} entries (else the first one), or {@code null}. */
    private static int[] listPoint(ConfigurationSection yaml, String list, String id) {
        int[] first = null;
        for (Map<?, ?> entry : yaml.getMapList(list)) {
            if (!(entry.get("x") instanceof Number x) || !(entry.get("y") instanceof Number y) || !(entry.get("z") instanceof Number z)) {
                continue;
            }
            int[] p = {(int) Math.floor(x.doubleValue()), (int) Math.floor(y.doubleValue()), (int) Math.floor(z.doubleValue())};
            if (id.equals(String.valueOf(entry.get("id")))) {
                return p;
            }
            if (first == null) {
                first = p;
            }
        }
        return first;
    }

    private static int[] relative(int[] point, int[] origin) {
        return point == null ? null : new int[]{point[0] - origin[0], point[1] - origin[1], point[2] - origin[2]};
    }

    private Build finish(Island island, World world, int x, int y, int z, Pasted pasted) {
        Location home;
        Block chest = null;
        String source;
        float yaw = (float) config.homeYaw;
        if (pasted == null) {
            placePlaceholder(world, x, y, z);
            source = "placeholder";
            chest = world.getBlockAt(x + StarterIsland.CHEST.x(), y + StarterIsland.CHEST.y(), z + StarterIsland.CHEST.z());
            writeSign(world.getBlockAt(x + StarterIsland.SIGN.x(), y + StarterIsland.SIGN.y(), z + StarterIsland.SIGN.z()));
            home = new Location(world, x + StarterIsland.HOME.x() + 0.5, y + StarterIsland.HOME.y(), z + StarterIsland.HOME.z() + 0.5, yaw, 0);
        } else {
            source = "schematic:" + config.schematic;
            Metadata meta = pasted.metadata();
            if (meta.homeYaw() != null) {
                yaw = meta.homeYaw();
            }
            if (meta.chest() != null) {
                chest = world.getBlockAt(x + meta.chest()[0], y + meta.chest()[1], z + meta.chest()[2]);
            } else {
                chest = findChest(world, x, y, z, pasted.area()).orElse(null);
            }
            if (meta.sign() != null) {
                writeSign(world.getBlockAt(x + meta.sign()[0], y + meta.sign()[1], z + meta.sign()[2]));
            }
            home = meta.home() != null
                    ? new Location(world, x + meta.home()[0] + 0.5, y + meta.home()[1], z + meta.home()[2] + 0.5, yaw, 0)
                    : new Location(world, x + 0.5, y + 1, z + 0.5, yaw, 0);
        }
        if (!SafeSpots.isSafe(home)) {
            home = SafeSpots.nearest(home, island.bounds(), config.safeSearchRadius, config.minBuildY, config.maxBuildY).orElse(home);
        }
        List<String> filled = fillChest(chest, island.owner());
        return new Build(new Island.Home(home.getX(), home.getY(), home.getZ(), home.getYaw(), home.getPitch()), source,
                !filled.isEmpty(), filled);
    }

    // ------------------------------------------------------------------ placeholder islet

    private void placePlaceholder(World world, int x, int y, int z) {
        Map<StarterIsland.Pos, StarterIsland.Piece> blocks = StarterIsland.blocks();
        Map<StarterIsland.Pos, Integer> leaves = StarterIsland.leafDistances(blocks);
        blocks.forEach((pos, piece) -> {
            Material material = Material.getMaterial(piece.name());
            if (material == null) {
                return;
            }
            Block block = world.getBlockAt(x + pos.x(), y + pos.y(), z + pos.z());
            BlockData data = material.createBlockData();
            if (data instanceof Leaves leaf) {
                leaf.setDistance(Math.max(1, Math.min(leaf.getMaximumDistance(), leaves.getOrDefault(pos, 7))));
                leaf.setPersistent(false);
            } else if (data instanceof Farmland farmland) {
                farmland.setMoisture(farmland.getMaximumMoisture());
            } else if (data instanceof Rotatable rotatable) {
                rotatable.setRotation(BlockFace.SOUTH); // the sign faces the home spot
            } else if (data instanceof Directional directional && directional.getFaces().contains(BlockFace.SOUTH)) {
                directional.setFacing(BlockFace.SOUTH); // the chest opens toward the home spot
            }
            block.setBlockData(data, false);
        });
    }

    private void writeSign(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            return;
        }
        List<String> lines = module.rawLines("starter.sign");
        for (int i = 0; i < 4; i++) {
            Component line = i < lines.size() ? core.messages().parse(Audience.empty(), lines.get(i)) : Component.empty();
            sign.getSide(Side.FRONT).line(i, line);
        }
        sign.setWaxed(true);
        sign.update(true, false);
    }

    // ------------------------------------------------------------------ starter chest

    /** The first chest inside the pasted area (schematics without metadata). */
    private static Optional<Block> findChest(World world, int x, int y, int z, FaweSchematics.Pasted area) {
        int minX = x + area.minX();
        int maxX = x + area.maxX();
        int minZ = z + area.minZ();
        int maxZ = z + area.maxZ();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (BlockState state : world.getChunkAt(cx, cz).getTileEntities()) {
                    Location l = state.getLocation();
                    if (state instanceof Chest && l.getBlockX() >= minX && l.getBlockX() <= maxX && l.getBlockZ() >= minZ
                            && l.getBlockZ() <= maxZ && l.getBlockY() >= y + area.minY() && l.getBlockY() <= y + area.maxY()) {
                        return Optional.of(state.getBlock());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Puts the starter items into the chest. With the tutorial module running its custom starter chest is used
     * (TUTORIAL §6, owner answer 158: starter kit, seeds, Field Guide...); otherwise the plain {@code starter-chest.items}
     * list. Returns what was added ("material:amount"), empty if no chest.
     */
    private List<String> fillChest(Block block, java.util.UUID ownerProfile) {
        java.util.Optional<net.skybornmc.skyblock.common.tutorial.StarterContents> custom =
                core.services().optional(net.skybornmc.skyblock.common.tutorial.StarterContents.class);
        if (custom.isPresent()) {
            try {
                Block chestBlock = block != null && block.getState() instanceof Chest ? block : null;
                return custom.get().fillChest(chestBlock, ownerProfile);
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Islands: the tutorial's starter chest failed; using starter-chest.items", e);
            }
        }
        List<String> added = new ArrayList<>();
        if (block == null || !(block.getState() instanceof Chest chest)) {
            if (block != null || !config.starterItems.isEmpty()) {
                core.plugin().getLogger().warning("Islands: no starter chest found on the new island; starter items not given.");
            }
            return added;
        }
        Inventory inventory = chest.getBlockInventory();
        for (StarterItems.Entry entry : module.starterItems()) {
            Material material = Material.matchMaterial(entry.material());
            if (material == null || !material.isItem()) {
                continue;
            }
            Map<Integer, ItemStack> rest = inventory.addItem(new ItemStack(material, entry.amount()));
            if (rest.isEmpty()) {
                added.add(entry.material() + ":" + entry.amount());
            }
        }
        return added;
    }
}
