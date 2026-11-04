package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * Island farming v2 (owner 3 Nov 2026): every crop on an island grows on the island's own clock, one stage every few
 * seconds (the full growth time comes from the island's crop-growth upgrade, {@link HoeFarming#regrowSeconds}, split over
 * the crop's stages), with a small puff and a soft sound for players nearby, instead of vanilla's random growth.
 * <ul>
 *   <li>Vanilla growth of these crops is cancelled in the island world; a crop that tries to grow (random tick) is put on
 *   the clock, so crops planted before a restart join it within a minute. Planting puts a crop on the clock at once.</li>
 *   <li>Only loaded chunks are touched; a crop in an unloaded chunk leaves the clock and comes back the same way.</li>
 *   <li>Tilled dirt on islands is moist farmland at once and never dries out or turns back to dirt (no water needed);
 *   trampling it does nothing.</li>
 *   <li>Looking at a crop within a few blocks shows a small text above it for that player: stage and time to the next.</li>
 * </ul>
 * Melon and pumpkin fruits, sugar cane, cactus and bamboo keep their vanilla growth. An asleep island (no island fuel)
 * grows nothing, as before.
 */
final class IslandGrowth implements Listener {

    /** Crops on the island clock (all have an age). */
    static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH, Material.MELON_STEM, Material.PUMPKIN_STEM,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP);

    private static final Set<Material> TILLABLE = Set.of(Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.DIRT_PATH);

    /** One crop on the clock. */
    record Due(UUID world, int x, int y, int z, Material crop, long at, long stepMillis) {
        String key() {
            return key(world, x, y, z);
        }

        static String key(UUID world, int x, int y, int z) {
            return world + ":" + x + ":" + y + ":" + z;
        }
    }

    private final IslandToolsModule m;
    private final PriorityQueue<Due> queue = new PriorityQueue<>((a, b) -> Long.compare(a.at(), b.at()));
    private final Map<String, Due> byKey = new HashMap<>();
    /** Per player: their look-at text display and the block it is over. */
    private final Map<UUID, TextDisplay> labels = new HashMap<>();
    private final Map<UUID, String> labelAt = new HashMap<>();
    private final Map<UUID, Long> lastSound = new HashMap<>();

    IslandGrowth(IslandToolsModule m) {
        this.m = m;
    }

    private boolean islandWorld(World w) {
        return m.islands().map(i -> i.isIslandWorld(w)).orElse(false);
    }

    /** Pure: milliseconds per stage for a crop whose full growth takes {@code fullSeconds} over {@code stages} steps. */
    static long stepMillis(double fullSeconds, int stages, long floorMillis) {
        if (stages <= 0) {
            return Math.max(floorMillis, (long) (fullSeconds * 1000));
        }
        return Math.max(floorMillis, Math.round(fullSeconds * 1000 / stages));
    }

    /** Full growth seconds on the island at {@code loc} (the island's crop-growth upgrade; no player bonus here). */
    private double fullSeconds(Location loc) {
        IslandToolsConfig c = m.config();
        UUID island = m.islands().flatMap(i -> i.islandAt(loc)).map(IslandService.IslandInfo::id).orElse(null);
        double upgrade = island == null ? 0 : m.core().services().optional(ProgressionService.class)
                .map(s -> s.upgradeEffectValue(island, c.regrowEffect)).orElse(0.0);
        return ToolRules.regrowSeconds(upgrade, 0, c.regrowBase, c.regrowTop, c.regrowFullAt, c.regrowFloor);
    }

    /**
     * Pure: the age at which a clock crop is done. The torchflower crop's age only goes to 1 (its last step turns it into
     * a torchflower, vanilla), so it has one step more than its age property.
     */
    static int doneAge(Material crop, int maximumAge) {
        return crop == Material.TORCHFLOWER_CROP ? maximumAge + 1 : maximumAge;
    }

    /** The upper half of a two-block crop (pitcher) grows with its lower half; it is never on the clock itself. */
    private static boolean upperHalf(org.bukkit.block.data.BlockData data) {
        return data instanceof org.bukkit.block.data.Bisected bi && bi.getHalf() == org.bukkit.block.data.Bisected.Half.TOP;
    }

    /** Puts a crop block on the clock (or keeps its place when it is already on it). */
    void track(Block b) {
        if (!m.config().growthEnabled || !CROPS.contains(b.getType()) || !(b.getBlockData() instanceof Ageable a)
                || a.getAge() >= doneAge(b.getType(), a.getMaximumAge()) || upperHalf(a)) {
            return;
        }
        String key = Due.key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        if (byKey.containsKey(key)) {
            return;
        }
        // Feedback 3 Nov 2026: one stage every growth.stage-seconds (3 s); the crop-growth upgrade scales it down.
        double factor = fullSeconds(b.getLocation()) / Math.max(1.0, m.config().regrowBase);
        long step = Math.max(m.config().growthStepFloorMillis, Math.round(m.config().growthStageSeconds * 1000 * factor));
        Due d = new Due(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), b.getType(), System.currentTimeMillis() + step, step);
        byKey.put(key, d);
        queue.add(d);
        while (byKey.size() > m.config().regrowMax && !queue.isEmpty()) {
            byKey.remove(queue.poll().key());
        }
    }

    /**
     * Feedback 3 Nov 2026 ("make bonemeal work on the crops"): vanilla bone meal growth is part of the growth the island
     * clock cancels, so bone meal is applied here: 1 to growth.bonemeal-stages stages, one bone meal used. Runs after
     * island protection (ignoreCancelled), so only players who may build there can use it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoneMeal(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getItem() == null || e.getItem().getType() != Material.BONE_MEAL
                || !m.config().growthEnabled) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null || !CROPS.contains(b.getType()) || !islandWorld(b.getWorld()) || !(b.getBlockData() instanceof Ageable a)
                || a.getAge() >= a.getMaximumAge()) {
            return;
        }
        e.setCancelled(true);
        if (e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            e.getItem().subtract(1);
        }
        int add = 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1, m.config().growthBonemealStages));
        a.setAge(Math.min(a.getMaximumAge(), a.getAge() + add));
        b.setBlockData(a, false);
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0);
        b.getWorld().playSound(b.getLocation(), Sound.ITEM_BONE_MEAL_USE, 0.8f, 1f);
        byKey.remove(Due.key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()));
        track(b);
    }

    /** Every half second: grows the crops whose time came by one stage (loaded chunks only, a small budget per run). */
    void tick() {
        long now = System.currentTimeMillis();
        int budget = m.config().growthBudget;
        // At most growth.max-ms-per-run per run (D18a: was a fixed 2 ms); the rest waits half a second.
        long deadline = System.nanoTime() + Math.max(1, m.config().growthMaxMillis) * 1_000_000L;
        pausedCache.clear();
        nearCache.clear();
        while (!queue.isEmpty() && queue.peek().at() <= now && budget-- > 0 && System.nanoTime() < deadline) {
            Due d = queue.poll();
            // Feedback 4 Nov 2026 (D18a): an entry that was replaced (bone meal re-tracks the crop) is stale; before, it
            // removed the new entry's key and both kept growing the crop (double speed, and the label lost its time).
            if (byKey.get(d.key()) != d) {
                continue;
            }
            byKey.remove(d.key());
            World w = Bukkit.getWorld(d.world());
            if (w == null || !w.isChunkLoaded(d.x() >> 4, d.z() >> 4)) {
                continue; // the chunk is scanned again when it loads (scanChunk), so the crop comes back on the clock
            }
            Block b = w.getBlockAt(d.x(), d.y(), d.z());
            if (b.getType() != d.crop() || !(b.getBlockData() instanceof Ageable a)
                    || a.getAge() >= doneAge(d.crop(), a.getMaximumAge())) {
                continue;
            }
            // D18a: the next stage is due one step after THIS one was due (not after it ran), so a run that is late (the
            // budget or the 2 ms cap was reached on a big field) doesn't make the whole field slower than configured.
            long nextAt = nextDue(d.at(), d.stepMillis(), now);
            if (paused(b)) {
                // An asleep island (no island fuel, production module) doesn't grow; try again a step later.
                Due later = new Due(d.world(), d.x(), d.y(), d.z(), d.crop(), now + d.stepMillis(), d.stepMillis());
                byKey.put(later.key(), later);
                queue.add(later);
                continue;
            }
            boolean ripe = grow(b, a);
            effects(b, ripe);
            if (!ripe) {
                Due next = new Due(d.world(), d.x(), d.y(), d.z(), d.crop(), nextAt, d.stepMillis());
                byKey.put(next.key(), next);
                queue.add(next);
            }
        }
        scanTick();
        labels();
    }

    /** Pure: when the next stage is due after a stage that was due at {@code dueAt} ran at {@code now} (never in the past). */
    static long nextDue(long dueAt, long stepMillis, long now) {
        return Math.max(now, dueAt + stepMillis);
    }

    /** One stage; true when the crop is done now. */
    private static boolean grow(Block b, Ageable a) {
        Material crop = b.getType();
        if (crop == Material.TORCHFLOWER_CROP && a.getAge() >= a.getMaximumAge()) {
            b.setType(Material.TORCHFLOWER, false); // D18a: vanilla's last torchflower step (it stayed a crop forever)
            return true;
        }
        a.setAge(a.getAge() + 1);
        b.setBlockData(a, false);
        if (crop == Material.PITCHER_CROP && a instanceof org.bukkit.block.data.Bisected && a.getAge() >= PITCHER_DOUBLE_AGE) {
            // D18a: from stage 4 the pitcher crop is two blocks tall (vanilla places the top half; setting only the age
            // left a half plant without its top).
            Block up = b.getRelative(0, 1, 0);
            if (up.getType().isAir() || up.getType() == Material.PITCHER_CROP) {
                org.bukkit.block.data.BlockData top = Material.PITCHER_CROP.createBlockData();
                if (top instanceof org.bukkit.block.data.Bisected half) {
                    half.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
                }
                if (top instanceof Ageable topAge) {
                    topAge.setAge(Math.min(topAge.getMaximumAge(), a.getAge()));
                }
                up.setBlockData(top, false);
            }
        }
        return a.getAge() >= doneAge(crop, a.getMaximumAge());
    }

    /** Vanilla: a pitcher crop of this age (or more) has its top half. */
    static final int PITCHER_DOUBLE_AGE = 3;

    // ------------------------------------------------------------------ chunk scans (D18a)

    /** A chunk whose crops are put on the clock. */
    private record ChunkRef(UUID world, int x, int z) {
    }

    /** Chunks waiting for a scan (loaded in the island world), oldest first. */
    private final java.util.LinkedHashSet<ChunkRef> scans = new java.util.LinkedHashSet<>();
    private boolean scannedLoaded;

    /**
     * Pure: does a clock crop of {@code type} at {@code age} (age property max {@code maximumAge}) still have stages to grow?
     * The upper half of a pitcher crop is never on the clock.
     */
    static boolean needsClock(Material type, int age, int maximumAge, boolean upperHalf) {
        return CROPS.contains(type) && !upperHalf && age < doneAge(type, maximumAge);
    }

    /**
     * Feedback 4 Nov 2026 (D18a, "crops not growing as intended"): before, a crop only came back on the clock through a
     * vanilla random tick (BlockGrowEvent), which in a big field takes many minutes per crop, so after every restart and
     * every time an island's chunks unloaded (everyone left) whole fields stayed green. Now every island chunk that
     * loads is scanned once (a chunk copy, read off the main thread) and its unripe crops go on the clock at once.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (m.config().growthEnabled && islandWorld(event.getWorld())) {
            scans.add(new ChunkRef(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ()));
        }
    }

    /** From the tick: copies a few queued chunks (at most 1 ms) and scans the copies off the main thread. */
    private void scanTick() {
        if (!m.config().growthEnabled) {
            scans.clear();
            return;
        }
        if (!scannedLoaded) { // first run after enable: what is loaded already (a reload or the start)
            scannedLoaded = true;
            for (World w : Bukkit.getWorlds()) {
                if (islandWorld(w)) {
                    for (org.bukkit.Chunk c : w.getLoadedChunks()) {
                        scans.add(new ChunkRef(w.getUID(), c.getX(), c.getZ()));
                    }
                }
            }
        }
        long deadline = System.nanoTime() + 1_000_000L;
        var it = scans.iterator();
        while (it.hasNext() && System.nanoTime() < deadline) {
            ChunkRef ref = it.next();
            it.remove();
            World w = Bukkit.getWorld(ref.world());
            if (w == null || !w.isChunkLoaded(ref.x(), ref.z())) {
                continue; // unloaded again: scanned when it loads next time
            }
            org.bukkit.ChunkSnapshot snapshot = w.getChunkAt(ref.x(), ref.z()).getChunkSnapshot(false, false, false);
            int minY = w.getMinHeight();
            int maxY = w.getMaxHeight();
            m.core().scheduler().async(() -> {
                List<int[]> found = scan(snapshot, minY, maxY);
                if (!found.isEmpty()) {
                    m.core().scheduler().main("islandtools:growth-scan", () -> {
                        if (!w.isChunkLoaded(ref.x(), ref.z())) {
                            return;
                        }
                        for (int[] pos : found) {
                            track(w.getBlockAt((ref.x() << 4) + pos[0], pos[1], (ref.z() << 4) + pos[2]));
                        }
                    });
                }
            });
        }
    }

    /** Off the main thread: chunk-relative x, world y, z of every crop in the copy that still has stages to grow. */
    static List<int[]> scan(org.bukkit.ChunkSnapshot snapshot, int minY, int maxY) {
        List<int[]> out = new ArrayList<>();
        int sections = (maxY - minY) >> 4;
        for (int s = 0; s < sections; s++) {
            if (snapshot.isSectionEmpty(s)) {
                continue;
            }
            int baseY = minY + (s << 4);
            for (int y = baseY; y < baseY + 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Material type = snapshot.getBlockType(x, y, z);
                        if (!CROPS.contains(type)) {
                            continue;
                        }
                        org.bukkit.block.data.BlockData data = snapshot.getBlockData(x, y, z);
                        if (data instanceof Ageable a && needsClock(type, a.getAge(), a.getMaximumAge(), upperHalf(data))) {
                            out.add(new int[]{x, y, z});
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Chunk → its island paused, for one run (one island lookup per chunk instead of per crop). */
    private final java.util.Map<Long, Boolean> pausedCache = new java.util.HashMap<>();
    /** Chunk → players near it, for one run (one entity lookup per chunk instead of per crop; D18a). */
    private final java.util.Map<Long, java.util.Collection<Player>> nearCache = new java.util.HashMap<>();

    private boolean paused(Block b) {
        long area = ((long) (b.getX() >> 4) << 32) ^ ((b.getZ() >> 4) & 0xffffffffL);
        return pausedCache.computeIfAbsent(area, k -> m.islands()
                .map(i -> i.islandAt(b.getLocation()).map(info -> i.islandProductionPaused(info.id())).orElse(false))
                .orElse(false));
    }

    /** A stage step: a small puff (and the ripe cue on the last one) and a soft, rate-limited sound for players nearby. */
    private void effects(Block b, boolean ripe) {
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        double r = m.config().cueFar;
        // One player lookup per chunk and run (a big field grows hundreds of crops per run), then a distance check.
        // Key: the 16x16x16 section (a rare collision only costs a puff).
        long section = (((long) (b.getX() >> 4) << 32) ^ ((b.getZ() >> 4) & 0xffffffffL)) ^ ((b.getY() >> 4) * 0x9E3779B97F4A7C15L);
        java.util.Collection<Player> near = nearCache.computeIfAbsent(section, k -> new Location(b.getWorld(),
                ((b.getX() >> 4) << 4) + 8, ((b.getY() >> 4) << 4) + 8, ((b.getZ() >> 4) << 4) + 8).getNearbyPlayers(r + 14));
        for (Player p : near) {
            if (!p.getWorld().equals(at.getWorld()) || p.getLocation().distanceSquared(at) > r * r) {
                continue;
            }
            p.spawnParticle(Particle.HAPPY_VILLAGER, at, ripe ? 5 : 1, 0.2, 0.2, 0.2, 0);
            if (m.config().growthSound) {
                long now = System.currentTimeMillis();
                Long last = lastSound.get(p.getUniqueId());
                if (last == null || now - last > 2000) {
                    lastSound.put(p.getUniqueId(), now);
                    p.playSound(at, ripe ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.BLOCK_CROP_BREAK, 0.25f, ripe ? 1.6f : 1.8f);
                }
            }
        }
    }

    /** Next stage of a tracked crop, in milliseconds from now (-1 = not on the clock). */
    long nextIn(Block b) {
        Due d = byKey.get(Due.key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()));
        return d == null ? -1 : Math.max(0, d.at() - System.currentTimeMillis());
    }

    /** Disable: nothing half grown stays frozen; the clock is cleared and the labels removed. */
    void stop() {
        queue.clear();
        byKey.clear();
        scans.clear();
        labels.values().forEach(TextDisplay::remove);
        labels.clear();
        labelAt.clear();
    }

    // ------------------------------------------------------------------ events (island world only)

    /** Vanilla growth of clock crops is replaced by the clock; the crop joins the clock instead. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        Block b = event.getBlock();
        if (!m.config().growthEnabled || !CROPS.contains(b.getType()) || event.getNewState().getType() != b.getType()
                || !islandWorld(b.getWorld())) {
            return; // fruits (melon, pumpkin) and tall plants grow the vanilla way
        }
        event.setCancelled(true);
        track(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlant(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        if (CROPS.contains(b.getType()) && islandWorld(b.getWorld())) {
            track(b);
        }
    }

    /** Tilling on an island makes moist farmland at once. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onTill(PlayerInteractEvent event) {
        if (!m.config().farmlandAlwaysWet || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getItem() == null || !org.bukkit.Tag.ITEMS_HOES.isTagged(event.getItem().getType())
                || !TILLABLE.contains(event.getClickedBlock().getType()) || !islandWorld(event.getClickedBlock().getWorld())) {
            return;
        }
        Block b = event.getClickedBlock();
        Bukkit.getScheduler().runTask(m.core().plugin(), () -> wet(b));
    }

    private static void wet(Block b) {
        if (b.getBlockData() instanceof Farmland f && f.getMoisture() < f.getMaximumMoisture()) {
            f.setMoisture(f.getMaximumMoisture());
            b.setBlockData(f, false);
        }
    }

    /** Island farmland never dries. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoisture(MoistureChangeEvent event) {
        if (m.config().farmlandAlwaysWet && islandWorld(event.getBlock().getWorld())
                && event.getNewState().getBlockData() instanceof Farmland next && event.getBlock().getBlockData() instanceof Farmland now
                && next.getMoisture() < now.getMoisture()) {
            event.setCancelled(true);
        }
    }

    /** ...never turns back to dirt... */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (m.config().farmlandAlwaysWet && event.getBlock().getType() == Material.FARMLAND && islandWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
            wet(event.getBlock());
        }
    }

    /** ...and can't be trampled (players or mobs). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (m.config().farmlandAlwaysWet && event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.FARMLAND && islandWorld(event.getClickedBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTrample(EntityChangeBlockEvent event) {
        if (m.config().farmlandAlwaysWet && event.getBlock().getType() == Material.FARMLAND && event.getTo() == Material.DIRT
                && islandWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        TextDisplay d = labels.remove(event.getPlayer().getUniqueId());
        if (d != null) {
            d.remove();
        }
        labelAt.remove(event.getPlayer().getUniqueId());
        lastSound.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ look-at label

    private int labelTick;

    /** Every second (from the half-second tick): the crop a player looks at gets a small text only they see. */
    private void labels() {
        if (!m.config().growthLabel || ++labelTick % 2 != 0) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Block target = islandWorld(p.getWorld()) ? p.getTargetBlockExact(m.config().growthLabelRange) : null;
            if (target == null || !CROPS.contains(target.getType()) || !(target.getBlockData() instanceof Ageable a)) {
                hideLabel(p);
                continue;
            }
            String text = labelText(p, target, a);
            TextDisplay d = labels.get(p.getUniqueId());
            String at = target.getX() + ":" + target.getY() + ":" + target.getZ();
            if (d == null || !d.isValid() || !d.getWorld().equals(target.getWorld()) || !at.equals(labelAt.get(p.getUniqueId()))) {
                hideLabel(p);
                Location loc = target.getLocation().add(0.5, 1.15, 0.5);
                d = loc.getWorld().spawn(loc, TextDisplay.class, t -> {
                    t.setPersistent(false);
                    t.setVisibleByDefault(false);
                    t.setBillboard(Display.Billboard.CENTER);
                    t.setShadowed(true);
                    t.setBackgroundColor(org.bukkit.Color.fromARGB(80, 0, 0, 0));
                    float s = 0.6f;
                    t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                            new org.joml.Vector3f(s, s, s), new org.joml.AxisAngle4f()));
                });
                p.showEntity(m.core().plugin(), d);
                labels.put(p.getUniqueId(), d);
                labelAt.put(p.getUniqueId(), at);
            }
            d.text(m.msg().component(p, "growth.label", Msg.p("crop", name(target.getType())), Msg.p("stage", text)));
        }
    }

    private String labelText(Player p, Block b, Ageable a) {
        if (a.getAge() >= doneAge(b.getType(), a.getMaximumAge())) {
            return m.raw("growth.ripe");
        }
        long next = nextIn(b);
        String stage = (a.getAge() + 1) + "/" + (doneAge(b.getType(), a.getMaximumAge()) + 1);
        return next < 0 ? stage : stage + " · " + Math.max(1, (next + 999) / 1000) + "s";
    }

    private void hideLabel(Player p) {
        TextDisplay d = labels.remove(p.getUniqueId());
        labelAt.remove(p.getUniqueId());
        if (d != null) {
            d.remove();
        }
    }

    /** WHEAT -> Wheat, SWEET_BERRY_BUSH -> Sweet Berry Bush (a crop's name for the label). */
    static String name(Material m) {
        StringBuilder out = new StringBuilder();
        for (String w : m.name().replace("_CROP", "").replace("_STEM", "").split("_")) {
            if (!w.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(w.charAt(0)).append(w.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return out.toString();
    }
}
