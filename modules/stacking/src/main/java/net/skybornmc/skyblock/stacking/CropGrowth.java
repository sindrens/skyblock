package net.skybornmc.skyblock.stacking;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owner S25: crops on islands grow without light. Vanilla only grows crops with light 9+, and there is no hook into
 * that check, so this class grows the dark ones itself: positions of planted crops are remembered per chunk (in the
 * chunk's persistent data, no database), and every {@code crops.check-interval-seconds} each dark crop in a loaded
 * chunk gets a {@code crops.grow-chance} to grow one stage (a {@link BlockGrowEvent} is fired first so protection and
 * stats see it). Lit crops are left to vanilla. The work is spread over ticks ({@code crops.per-tick}).
 */
final class CropGrowth implements Listener {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private record Work(UUID world, int chunkX, int chunkZ) {
    }

    private final StackingModule module;
    private final Map<UUID, Map<Long, CropPositions>> loaded = new HashMap<>();
    private final ArrayDeque<Work> round = new ArrayDeque<>();
    private Set<Material> types = EnumSet.noneOf(Material.class);
    private long nextRoundTick;
    /** Position inside the chunk currently being worked through (a big chunk can span several ticks). */
    private Work current;
    private int currentIndex;
    /** Positions of the current chunk that hold no crop any more; removed when the chunk is finished. */
    private final List<Integer> gone = new ArrayList<>();

    CropGrowth(StackingModule module) {
        this.module = module;
    }

    void applyConfig() {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String raw : module.config().cropTypes) {
            Material m = Material.matchMaterial(raw.trim());
            if (m == null || !m.isBlock() || !(m.createBlockData() instanceof Ageable)) {
                module.core().plugin().getLogger().warning("stacking.yml → crops.types: '" + raw + "' is not a growing block, skipped");
            } else {
                set.add(m);
            }
        }
        types = set;
    }

    private boolean tracked(Material material) {
        return types.contains(material)
                || (material == Material.ATTACHED_MELON_STEM && types.contains(Material.MELON_STEM))
                || (material == Material.ATTACHED_PUMPKIN_STEM && types.contains(Material.PUMPKIN_STEM));
    }

    // ------------------------------------------------------------------ chunk data

    void loadChunk(Chunk chunk) {
        int[] stored = chunk.getPersistentDataContainer().get(StackKeys.CROPS, PersistentDataType.INTEGER_ARRAY);
        if (stored != null && stored.length > 0) {
            loaded.computeIfAbsent(chunk.getWorld().getUID(), w -> new HashMap<>())
                    .put(MobIndex.key(chunk.getX(), chunk.getZ()), new CropPositions(stored));
        }
    }

    private void save(Chunk chunk, CropPositions positions) {
        if (!positions.dirty()) {
            return;
        }
        if (positions.isEmpty()) {
            positions.snapshot();
            chunk.getPersistentDataContainer().remove(StackKeys.CROPS);
        } else {
            chunk.getPersistentDataContainer().set(StackKeys.CROPS, PersistentDataType.INTEGER_ARRAY, positions.snapshot());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (module.config().cropsEnabled && module.eligible(event.getWorld())) {
            loadChunk(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Map<Long, CropPositions> chunks = loaded.get(event.getWorld().getUID());
        if (chunks == null) {
            return;
        }
        CropPositions positions = chunks.remove(MobIndex.key(event.getChunk().getX(), event.getChunk().getZ()));
        if (positions != null) {
            save(event.getChunk(), positions);
        }
    }

    /** Writes every changed chunk (module disable). */
    void saveAll() {
        for (Map.Entry<UUID, Map<Long, CropPositions>> w : loaded.entrySet()) {
            World world = Bukkit.getWorld(w.getKey());
            if (world == null) {
                continue;
            }
            for (Map.Entry<Long, CropPositions> c : w.getValue().entrySet()) {
                int x = (int) (c.getKey() & 0xffffffffL);
                int z = (int) (c.getKey() >>> 32);
                if (c.getValue().dirty() && world.isChunkLoaded(x, z)) {
                    save(world.getChunkAt(x, z), c.getValue());
                }
            }
        }
        loaded.clear();
        round.clear();
        current = null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlant(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!module.config().cropsEnabled || !types.contains(block.getType()) || !module.eligible(block.getWorld())) {
            return;
        }
        Chunk chunk = block.getChunk();
        CropPositions positions = loaded.computeIfAbsent(block.getWorld().getUID(), w -> new HashMap<>())
                .computeIfAbsent(MobIndex.key(chunk.getX(), chunk.getZ()), k -> new CropPositions(null));
        if (positions.add(CropPositions.pack(block.getX() & 15, block.getY(), block.getZ() & 15, block.getWorld().getMinHeight()))) {
            save(chunk, positions);
        }
    }

    // ------------------------------------------------------------------ growing

    /** Every tick: works through the current round within the budget; starts a new round every check interval. */
    void tick() {
        if (!module.config().cropsEnabled) {
            return;
        }
        long now = Bukkit.getCurrentTick();
        if (round.isEmpty() && current == null) {
            if (now < nextRoundTick) {
                return;
            }
            nextRoundTick = now + module.config().cropCheckIntervalSeconds * 20L;
            for (Map.Entry<UUID, Map<Long, CropPositions>> w : loaded.entrySet()) {
                for (Long key : w.getValue().keySet()) {
                    round.add(new Work(w.getKey(), (int) (key & 0xffffffffL), (int) (key >>> 32)));
                }
            }
            if (round.isEmpty()) {
                return;
            }
        }
        long start = module.perf().start();
        try {
            int budget = module.config().cropsPerTick;
            while (budget > 0) {
                if (current == null) {
                    current = round.poll();
                    currentIndex = 0;
                    gone.clear();
                    if (current == null) {
                        return;
                    }
                }
                budget = work(current, budget);
            }
        } finally {
            module.perf().end(start);
        }
    }

    /** Processes positions of one chunk; returns the budget left (the chunk is finished when it returns > 0). */
    private int work(Work work, int budget) {
        World world = Bukkit.getWorld(work.world());
        Map<Long, CropPositions> chunks = loaded.get(work.world());
        CropPositions positions = chunks == null ? null : chunks.get(MobIndex.key(work.chunkX(), work.chunkZ()));
        if (world == null || positions == null || !world.isChunkLoaded(work.chunkX(), work.chunkZ())) {
            current = null;
            gone.clear();
            return budget;
        }
        Chunk chunk = world.getChunkAt(work.chunkX(), work.chunkZ());
        int minY = world.getMinHeight();
        double chance = module.config().cropGrowChance;
        int light = module.config().cropLightLevel;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (budget > 0 && currentIndex < positions.size()) {
            int packed = positions.get(currentIndex++);
            budget--;
            Block block = chunk.getBlock(CropPositions.x(packed), CropPositions.y(packed, minY), CropPositions.z(packed));
            if (!tracked(block.getType())) {
                gone.add(packed);
                continue;
            }
            if (Math.max(block.getLightFromSky(), block.getLightFromBlocks()) >= light || random.nextDouble() >= chance) {
                continue;
            }
            grow(block);
        }
        if (currentIndex < positions.size()) {
            return budget; // continues next tick
        }
        if (!gone.isEmpty()) {
            for (int packed : gone) {
                positions.remove(packed);
            }
            gone.clear();
            save(chunk, positions);
            if (positions.isEmpty()) {
                chunks.remove(MobIndex.key(work.chunkX(), work.chunkZ()));
            }
        }
        current = null;
        return budget;
    }

    private void grow(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable age)) {
            return;
        }
        if (age.getAge() < age.getMaximumAge()) {
            age.setAge(age.getAge() + 1);
            BlockState state = block.getState();
            state.setBlockData(age);
            if (new BlockGrowEvent(block, state).callEvent()) {
                state.update(true, false);
            }
            return;
        }
        Material type = block.getType();
        if (type == Material.MELON_STEM || type == Material.PUMPKIN_STEM) {
            growFruit(block, type == Material.MELON_STEM);
        }
    }

    /** A ripe stem in the dark grows its melon or pumpkin on a free side with dirt or farmland below (like vanilla). */
    private void growFruit(Block stem, boolean melon) {
        BlockFace side = SIDES[ThreadLocalRandom.current().nextInt(SIDES.length)];
        Block target = stem.getRelative(side);
        Material below = target.getRelative(BlockFace.DOWN).getType();
        if (!target.getType().isAir() || !(Tag.DIRT.isTagged(below) || below == Material.FARMLAND)) {
            return;
        }
        BlockState fruit = target.getState();
        fruit.setType(melon ? Material.MELON : Material.PUMPKIN);
        if (!new BlockGrowEvent(target, fruit).callEvent()) {
            return;
        }
        fruit.update(true, false);
        BlockData attached = (melon ? Material.ATTACHED_MELON_STEM : Material.ATTACHED_PUMPKIN_STEM).createBlockData();
        if (attached instanceof Directional directional) {
            directional.setFacing(side);
        }
        stem.setBlockData(attached, false);
    }

    int trackedChunks() {
        int n = 0;
        for (Map<Long, CropPositions> chunks : loaded.values()) {
            n += chunks.size();
        }
        return n;
    }
}
