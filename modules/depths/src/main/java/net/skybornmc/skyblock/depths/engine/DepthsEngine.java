package net.skybornmc.skyblock.depths.engine;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.math.Position;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.DepthsPerms;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * M4.3 per-player blocks engine.
 *
 * <p>Resource blocks never change server-side. When a player breaks one inside a region the vanilla break is cancelled,
 * {@link ResourceBreakEvent} is fired (drops/XP live in other modules), and only that player is sent the depleted
 * block. A {@link TimingWheel} brings the real block back for them after the respawn time. Depleted blocks are re-sent
 * whenever the server re-sends a chunk to the player ({@link PlayerChunkLoadEvent} fires right after the chunk packet)
 * and whenever a cancelled interaction makes the server re-send the real block. On quit and on world change the
 * running timers are parked in {@link DepletionMemory} and handed back when the player is in that world again (SECFIX1
 * F-11: relogging or hopping worlds no longer resets ores).</p>
 *
 * <p>Cost: a break outside regions is one map miss; inside, a few map operations plus the event. The per-tick task
 * only increments a counter while nothing is pending (no allocation when idle).</p>
 */
public final class DepthsEngine implements Depths, Listener {

    private final Core core;
    private final DepthsConfig config;
    private final ModuleMessages msg;

    private volatile RegionIndex<DepthsRegion> index = RegionIndex.empty();
    private List<DepthsRegion> configRegions = List.of();
    private final Map<String, DepthsRegion> codeRegions = new LinkedHashMap<>();

    private final Map<UUID, PlayerDepletion> players = new HashMap<>();
    /** SECFIX1 F-11: running timers of players who left or changed world. */
    private DepletionMemory<BlockData> memory;
    private final Int2ObjectOpenHashMap<PlayerDepletion> byHandle = new Int2ObjectOpenHashMap<>();
    private int nextHandle;

    private TimingWheel wheel;
    private final TimingWheel.Sink expireSink = this::expire;
    private final IntArrayList pendingOwners = new IntArrayList();
    private final LongArrayList pendingKeys = new LongArrayList();
    private final Location scratch = new Location(null, 0, 0, 0);
    private Scheduler.Task tickTask = Scheduler.Task.NONE;
    private BiPredicate<Player, Player> sameIsland = (a, b) -> false;

    // DV2-SPIKE tier palettes.
    private List<TierPalette> palettes = List.of();
    private final Map<String, TierPalette> paletteById = new HashMap<>();
    private final Map<String, TierPalette> paletteByRegion = new HashMap<>();
    private final Map<String, SlotIndex> slotIndexes = new HashMap<>();
    private final Map<String, DepthsRegion> regionsById = new HashMap<>();
    private final List<PlayerDepletion> swapping = new ArrayList<>();
    private java.util.function.ToIntFunction<String> regionTier = id -> 0;
    private long swaps;
    private int lastSwapBlocks;
    private double lastSwapMillis;
    private double maxSwapMillis;
    private int lastDrainTicks;
    private double lastDrainTotalMillis;

    // Statistics for /depths debug.
    private long breaks;
    private double avgBreakNanos;
    private long maxBreakNanos;
    private long flagsTotal;

    public DepthsEngine(Core core, DepthsConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        wheel = new TimingWheel(config.timerSlots, 1024);
        memory = new DepletionMemory<>(config.rememberedPerPlayer);
        tickTask = core.scheduler().repeat("depths-tick", 1L, 1L, this::tick);
    }

    /** Shows every player their real blocks again and stops the timers. */
    public void stop() {
        tickTask.cancel();
        tickTask = Scheduler.Task.NONE;
        palettes(List.of());
        restoreAll();
        players.clear();
        byHandle.clear();
        if (memory != null) {
            memory.clear();
        }
    }

    /** Replaces the regions from depths.yml (restores all depleted blocks first, definitions may have changed). */
    public void configRegions(List<DepthsRegion> regions) {
        restoreAll();
        if (memory != null) {
            memory.clear(); // definitions may have changed: remembered blocks may no longer be resources
        }
        configRegions = List.copyOf(regions);
        codeRegions.values().removeIf(r -> {
            boolean clash = configRegions.stream().anyMatch(c -> c.id().equals(r.id()));
            if (clash) {
                core.plugin().getLogger().warning("Depths region '" + r.id() + "' from " + r.source()
                        + " was dropped: depths.yml now defines a region with the same id.");
            }
            return clash;
        });
        rebuild();
    }

    private void rebuild() {
        List<DepthsRegion> all = new ArrayList<>(configRegions);
        all.addAll(codeRegions.values());
        index = RegionIndex.of(all);
        regionsById.clear();
        for (DepthsRegion r : index.regions()) {
            regionsById.putIfAbsent(r.id(), r);
        }
        rebuildSlotIndexes();
    }

    private void rebuildSlotIndexes() {
        slotIndexes.clear();
        for (String id : paletteByRegion.keySet()) {
            DepthsRegion r = regionsById.get(id);
            if (r != null) {
                slotIndexes.put(id, new SlotIndex(r));
            }
        }
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        wheel.advance(expireSink);
        if (!pendingKeys.isEmpty()) {
            flushPending();
        }
        if (!swapping.isEmpty()) {
            drainSwaps();
        }
    }

    private void expire(int owner, long key, int token) {
        PlayerDepletion state = byHandle.get(owner);
        if (state == null) {
            return;
        }
        PlayerDepletion.Depleted d = state.depleted.get(key);
        if (d == null || d.token() != token) {
            return; // restored early or re-depleted: a newer timer owns it
        }
        state.depleted.remove(key);
        sendReal(state, key);
    }

    private void flushPending() {
        for (int i = 0, n = pendingKeys.size(); i < n; i++) {
            PlayerDepletion state = byHandle.get(pendingOwners.getInt(i));
            if (state == null) {
                continue;
            }
            long key = pendingKeys.getLong(i);
            PlayerDepletion.Depleted d = state.depleted.get(key);
            if (d != null) {
                send(state, key, d.shown());
            } else {
                sendPalette(state, key);
            }
        }
        pendingKeys.clear();
        pendingOwners.clear();
    }

    /** Sends the depleted block next tick: a cancelled break/interaction makes the server re-send the real one now. */
    private void queueResend(PlayerDepletion state, long key) {
        pendingOwners.add(state.handle);
        pendingKeys.add(key);
    }

    private void sendReal(PlayerDepletion state, long key) {
        Player player = state.player;
        World world = player.getWorld();
        if (!world.getName().equals(state.world) || !player.isChunkSent(BlockKeys.chunkOf(key))) {
            return; // the next chunk send shows the real block anyway
        }
        TierPalette.Tier tier = paletteTierAt(state, world, key);
        send(state, key, tier != null ? tier.shown() : world.getBlockData(BlockKeys.x(key), BlockKeys.y(key), BlockKeys.z(key)));
    }

    /** DV2-SPIKE: re-sends the palette block of a slot (a cancelled break or a click made the server send the real one). */
    private void sendPalette(PlayerDepletion state, long key) {
        Player player = state.player;
        World world = player.getWorld();
        TierPalette.Tier tier = paletteTierAt(state, world, key);
        if (tier == null || !player.isChunkSent(BlockKeys.chunkOf(key))) {
            return;
        }
        scratch.setWorld(world);
        scratch.set(BlockKeys.x(key), BlockKeys.y(key), BlockKeys.z(key));
        player.sendBlockChange(scratch, tier.shown());
        scratch.setWorld(null);
    }

    private void send(PlayerDepletion state, long key, BlockData data) {
        Player player = state.player;
        World world = player.getWorld();
        if (!world.getName().equals(state.world) || !player.isChunkSent(BlockKeys.chunkOf(key))) {
            return;
        }
        scratch.setWorld(world);
        scratch.set(BlockKeys.x(key), BlockKeys.y(key), BlockKeys.z(key));
        player.sendBlockChange(scratch, data);
        scratch.setWorld(null);
    }

    // ------------------------------------------------------------------ state

    private PlayerDepletion state(Player player) {
        PlayerDepletion state = players.get(player.getUniqueId());
        if (state == null) {
            state = new PlayerDepletion(player, ++nextHandle);
            players.put(player.getUniqueId(), state);
            byHandle.put(state.handle, state);
        }
        return state;
    }

    private void depleteInternal(PlayerDepletion state, String world, long key, ResourceBlock resource, String regionId,
                                 int respawnTicks) {
        if (!world.equals(state.world)) {
            state.depleted.clear();
            state.world = world;
        }
        depleteRaw(state, key, resource.depleted(), regionId, respawnTicks);
    }

    private void depleteRaw(PlayerDepletion state, long key, BlockData shown, String regionId, int respawnTicks) {
        int token = state.nextToken();
        long endsAt = System.currentTimeMillis() + respawnTicks * 50L;
        state.depleted.put(key, new PlayerDepletion.Depleted(token, shown, regionId, endsAt));
        wheel.schedule(respawnTicks, state.handle, key, token);
    }

    /** SECFIX1 F-11: parks the running timers of {@code state}'s current depleted world. */
    private void park(UUID player, PlayerDepletion state) {
        if (memory == null || state.world == null || state.depleted.isEmpty()) {
            return;
        }
        List<DepletionMemory.Entry<BlockData>> entries = new ArrayList<>(state.depleted.size());
        ObjectIterator<Long2ObjectMap.Entry<PlayerDepletion.Depleted>> it = state.depleted.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<PlayerDepletion.Depleted> e = it.next();
            PlayerDepletion.Depleted d = e.getValue();
            entries.add(new DepletionMemory.Entry<>(e.getLongKey(), d.shown(), d.regionId(), d.endsAt()));
        }
        memory.park(player, state.world, entries, System.currentTimeMillis());
    }

    /**
     * SECFIX1 F-11: hands back the parked timers of {@code player} in {@code world} (only blocks that are still resources
     * of a known region). The client gets the depleted blocks with the next chunk send.
     */
    private void unpark(Player player, String world) {
        if (memory == null) {
            return;
        }
        long now = System.currentTimeMillis();
        List<DepletionMemory.Entry<BlockData>> back = memory.take(player.getUniqueId(), world, now);
        if (back.isEmpty()) {
            return;
        }
        PlayerDepletion state = state(player);
        if (!world.equals(state.world)) {
            state.depleted.clear();
            state.world = world;
        }
        RegionIndex<DepthsRegion> idx = index;
        for (DepletionMemory.Entry<BlockData> e : back) {
            long key = e.key();
            DepthsRegion region = idx.at(world, BlockKeys.x(key), BlockKeys.y(key), BlockKeys.z(key));
            if (region == null || !region.id().equals(e.regionId()) || state.depleted.containsKey(key)) {
                continue;
            }
            depleteRaw(state, key, e.shown(), e.regionId(), DepletionMemory.ticksLeft(e.endsAt(), now));
            queueResend(state, key); // chunks already sent get it next tick; later ones on their chunk send
        }
    }

    private void restoreAll() {
        for (PlayerDepletion state : players.values()) {
            restoreMatching(state, null);
        }
        if (wheel != null) {
            wheel.clear();
        }
        pendingKeys.clear();
        pendingOwners.clear();
    }

    /** Restores one region's blocks ({@code regionId == null}: all) for every player. */
    private void restoreRegion(String regionId) {
        for (PlayerDepletion state : players.values()) {
            restoreMatching(state, regionId);
        }
    }

    private void restoreMatching(PlayerDepletion state, String regionId) {
        if (state.depleted.isEmpty()) {
            return;
        }
        Player player = state.player;
        World world = player.getWorld();
        boolean sameWorld = world.getName().equals(state.world);
        Map<Position, BlockData> changes = new HashMap<>();
        ObjectIterator<Long2ObjectMap.Entry<PlayerDepletion.Depleted>> it = state.depleted.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<PlayerDepletion.Depleted> e = it.next();
            if (regionId != null && !regionId.equals(e.getValue().regionId())) {
                continue;
            }
            long key = e.getLongKey();
            it.remove();
            if (sameWorld && player.isChunkSent(BlockKeys.chunkOf(key))) {
                int x = BlockKeys.x(key);
                int y = BlockKeys.y(key);
                int z = BlockKeys.z(key);
                TierPalette.Tier tier = paletteTierAt(state, world, key);
                changes.put(Position.block(x, y, z), tier != null ? tier.shown() : world.getBlockData(x, y, z));
            }
        }
        if (!changes.isEmpty() && player.isOnline()) {
            player.sendMultiBlockChange(changes);
        }
    }

    private boolean isBuilder(Player player) {
        return config.builderBypass && player.getGameMode() == GameMode.CREATIVE && player.hasPermission(DepthsPerms.BUILD);
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        RegionIndex<DepthsRegion> idx = index;
        if (idx.isEmpty()) {
            return;
        }
        long start = System.nanoTime();
        Block block = event.getBlock();
        String world = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        DepthsRegion region = idx.at(world, x, y, z);
        if (region == null) {
            return;
        }
        Player player = event.getPlayer();
        if (isBuilder(player)) {
            return;
        }
        ResourceBlock resource = region.resource(block.getType());
        if (resource == null) {
            if (config.protectRegions) {
                event.setCancelled(true);
                msg.actionBar(player, "protected");
            }
            return;
        }
        event.setCancelled(true); // the real block never changes
        PlayerDepletion state = state(player);
        long key = BlockKeys.block(x, y, z);
        if (state.isDepleted(world, key)) {
            queueResend(state, key);
            return;
        }
        // DV2-SPIKE: in a palette zone the player broke the block of their active tier (rewards, look, respawn, timing).
        TierPalette.Tier slot = slotTier(state, region, resource.block());
        ResourceBlock shown = slot != null ? slot.resource() : resource;
        BlockData shownData = slot != null && !slot.shown().getMaterial().equals(resource.block()) ? slot.shown() : null;
        if (config.fastBreakEnabled && checkFastBreak(state, player, block, world, key, shown, shownData)) {
            state.clearDamage();
            if (shownData != null) {
                queueResend(state, key);
            }
            return;
        }
        state.clearDamage();
        ItemStack tool = player.getInventory().getItemInMainHand();
        int tier = slot != null ? slot.wingTier() : regionTier.applyAsInt(region.id());
        ResourceBreakEvent custom = new ResourceBreakEvent(player, block, region, resource, tool, shown.block(), tier,
                shown.respawnTicks());
        long beforeEvent = System.nanoTime();
        custom.callEvent();
        long eventNanos = System.nanoTime() - beforeEvent;
        if (!custom.isCancelled()) {
            depleteInternal(state, world, key, shown, region.id(), custom.getRespawnTicks());
            queueResend(state, key);
        } else if (shownData != null) {
            queueResend(state, key); // the server re-sends the real (tier 1) block: show the tier block again
        }
        record(System.nanoTime() - start - eventNanos);
    }

    private void record(long nanos) {
        breaks++;
        avgBreakNanos = breaks == 1 ? nanos : avgBreakNanos * 0.98 + nanos * 0.02;
        if (nanos > maxBreakNanos) {
            maxBreakNanos = nanos;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        RegionIndex<DepthsRegion> idx = index;
        if (idx.isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        String world = block.getWorld().getName();
        DepthsRegion region = idx.at(world, block.getX(), block.getY(), block.getZ());
        if (region == null || region.resource(block.getType()) == null) {
            return;
        }
        Player player = event.getPlayer();
        if (isBuilder(player)) {
            return;
        }
        PlayerDepletion state = state(player);
        long key = BlockKeys.block(block.getX(), block.getY(), block.getZ());
        if (state.isDepleted(world, key)) {
            event.setCancelled(true);
            queueResend(state, key);
            return;
        }
        state.damageKey = key;
        state.damageWorld = world;
        state.damageStartNanos = System.nanoTime();
        TierPalette.Tier slot = slotTier(state, region, block.getType());
        state.damageSpeed = breakSpeed(player, block, slot == null ? null : slot.shown());
        state.damageInstant = event.getInstaBreak();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageAbort(BlockDamageAbortEvent event) {
        PlayerDepletion state = players.get(event.getPlayer().getUniqueId());
        if (state != null && state.damageKey != PlayerDepletion.NO_BLOCK) {
            Block block = event.getBlock();
            if (state.damageMatches(block.getWorld().getName(), BlockKeys.block(block.getX(), block.getY(), block.getZ()))) {
                state.clearDamage();
            }
        }
    }

    /**
     * DV2-SPIKE hardness fix: Paper's break speed on the real block, scaled to the block the player sees ({@code shown},
     * {@code null} = the real block). Coal, copper and iron ore share hardness 3 but copper and iron need a stone
     * pickaxe, so a wooden pickaxe breaks the shown copper 3.3x slower than the real coal: the check uses the slower one.
     */
    private static float breakSpeed(Player player, Block block, BlockData shown) {
        float real = block.getBreakSpeed(player);
        if (shown == null || shown.getMaterial() == block.getType()) {
            return real;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        BlockData realData = block.getBlockData();
        return BreakMath.scaledSpeed(real, block.getType().getHardness(), realData.getDestroySpeed(tool, true),
                realData.isPreferredTool(tool), shown.getMaterial().getHardness(), shown.getDestroySpeed(tool, true),
                shown.isPreferredTool(tool));
    }

    /** True (and flagged) if this break was faster than possible. */
    private boolean checkFastBreak(PlayerDepletion state, Player player, Block block, String world, long key,
                                   ResourceBlock resource, BlockData shown) {
        boolean started = state.damageMatches(world, key);
        if (started && state.damageInstant) {
            return false;
        }
        float speedNow = breakSpeed(player, block, shown);
        float speed = started ? Math.max(speedNow, state.damageSpeed) : speedNow;
        int expectedTicks = BreakMath.expectedTicks(speed);
        long actualMillis = started ? (System.nanoTime() - state.damageStartNanos) / 1_000_000L : 0L;
        if (!BreakMath.tooFast(actualMillis, expectedTicks, config.fastBreakMinRatio, config.fastBreakGraceMs)) {
            return false;
        }
        flag(state, player, block, resource, expectedTicks, actualMillis, started);
        return true;
    }

    private void flag(PlayerDepletion state, Player player, Block block, ResourceBlock resource, int expectedTicks,
                      long actualMillis, boolean started) {
        flagsTotal++;
        state.flagsTotal++;
        long now = System.currentTimeMillis();
        if (now - state.flagWindowStartMillis > config.fastBreakWindowSeconds * 1000L) {
            state.flagWindowStartMillis = now;
            state.flagsInWindow = 0;
        }
        state.flagsInWindow++;
        if (state.flagsInWindow < config.fastBreakAlertThreshold
                || now - state.lastAlertMillis < config.fastBreakAlertCooldownSeconds * 1000L) {
            return;
        }
        state.lastAlertMillis = now;
        long expectedMillis = BreakMath.expectedMillis(expectedTicks);
        long minMillis = BreakMath.minAllowedMillis(expectedTicks, config.fastBreakMinRatio, config.fastBreakGraceMs);
        String blockName = resource.block().name().toLowerCase(Locale.ROOT);
        String toolName = player.getInventory().getItemInMainHand().getType().name().toLowerCase(Locale.ROOT);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(DepthsPerms.ALERTS)) {
                msg.send(staff, started ? "alert.fast-break" : "alert.no-start",
                        Msg.p("player", player.getName()), Msg.p("block", blockName), Msg.p("tool", toolName),
                        Msg.p("actual", actualMillis), Msg.p("expected", expectedMillis), Msg.p("min", minMillis),
                        Msg.p("count", state.flagsInWindow), Msg.p("seconds", config.fastBreakWindowSeconds));
            }
        }
        core.audit().logPlayer(AuditCategory.WORLD, "depths_fast_break", Actor.system("depths"), player.getUniqueId(),
                player.getName(), null, block.getLocation(), Map.of(
                        "block", blockName, "tool", toolName, "actual_ms", actualMillis, "expected_ms", expectedMillis,
                        "min_allowed_ms", minMillis, "damage_started", started, "flags_in_window", state.flagsInWindow,
                        "window_seconds", config.fastBreakWindowSeconds));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        RegionIndex<DepthsRegion> idx = index;
        if (idx.isEmpty() || !config.protectRegions) {
            return;
        }
        Block block = event.getBlockPlaced();
        String world = block.getWorld().getName();
        if (idx.at(world, block.getX(), block.getY(), block.getZ()) == null || isBuilder(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        msg.actionBar(event.getPlayer(), "protected");
        resendIfDepleted(event.getPlayer(), event.getBlockAgainst());
    }

    /** Any click on a depleted block may make the server re-send the real one: show the depleted one again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked != null && !players.isEmpty()) {
            resendIfDepleted(event.getPlayer(), clicked);
        }
    }

    private void resendIfDepleted(Player player, Block block) {
        PlayerDepletion state = players.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        long key = BlockKeys.block(block.getX(), block.getY(), block.getZ());
        if (state.isDepleted(block.getWorld().getName(), key)) {
            queueResend(state, key);
        } else if (state.anyPaletteTier() && !paletteByRegion.isEmpty()) {
            DepthsRegion region = index.at(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
            TierPalette.Tier slot = region == null ? null : slotTier(state, region, block.getType());
            if (slot != null && PaletteMath.needsPackets(state.paletteTiers.getInt(paletteByRegion.get(region.id()).id()))) {
                queueResend(state, key);
            }
        }
    }

    /** DV2-SPIKE: a builder really changed a block in a palette region: that chunk's slots are scanned again. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBuilderBreak(BlockBreakEvent event) {
        invalidateSlots(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBuilderPlace(BlockPlaceEvent event) {
        invalidateSlots(event.getBlockPlaced());
    }

    private void invalidateSlots(Block block) {
        if (slotIndexes.isEmpty()) {
            return;
        }
        DepthsRegion region = index.at(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        SlotIndex slots = region == null ? null : slotIndexes.get(region.id());
        if (slots != null) {
            slots.invalidate(block.getX() >> 4, block.getZ() >> 4);
        }
    }

    /** Re-applies the player's depleted blocks in a chunk the server just sent them (fired after the chunk packet). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        if (players.isEmpty()) {
            return;
        }
        PlayerDepletion state = players.get(event.getPlayer().getUniqueId());
        if (state == null) {
            return;
        }
        Chunk chunk = event.getChunk();
        boolean palette = state.anyPaletteTier() && !slotIndexes.isEmpty();
        boolean depleted = !state.depleted.isEmpty() && chunk.getWorld().getName().equals(state.world);
        if (!palette && !depleted) {
            return;
        }
        long chunkKey = BlockKeys.chunk(chunk.getX(), chunk.getZ());
        Map<Position, BlockData> changes = palette ? paletteChanges(state, chunk) : null;
        if (!depleted) {
            if (changes != null) {
                event.getPlayer().sendMultiBlockChange(changes);
            }
            return;
        }
        ObjectIterator<Long2ObjectMap.Entry<PlayerDepletion.Depleted>> it = state.depleted.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<PlayerDepletion.Depleted> e = it.next();
            long key = e.getLongKey();
            if (BlockKeys.chunkOf(key) == chunkKey) {
                if (changes == null) {
                    changes = new HashMap<>();
                }
                changes.put(Position.block(BlockKeys.x(key), BlockKeys.y(key), BlockKeys.z(key)), e.getValue().shown());
            }
        }
        if (changes != null) {
            event.getPlayer().sendMultiBlockChange(changes);
        }
    }

    /** DV2-SPIKE: the palette blocks of a just-sent chunk for a player at a higher tier ({@code null} = none). */
    private Map<Position, BlockData> paletteChanges(PlayerDepletion state, Chunk chunk) {
        Map<Position, BlockData> changes = null;
        World world = chunk.getWorld();
        String worldName = world.getName();
        for (TierPalette palette : palettes) {
            int tier = state.paletteTiers.getInt(palette.id());
            if (!PaletteMath.needsPackets(tier)) {
                continue;
            }
            BlockData shown = palette.tier(tier).shown();
            for (String regionId : palette.regionIds()) {
                DepthsRegion region = regionsById.get(regionId);
                SlotIndex slots = slotIndexes.get(regionId);
                if (region == null || slots == null || !region.world().equals(worldName) || !slots.covers(chunk.getX(), chunk.getZ())) {
                    continue;
                }
                for (long key : slots.slots(chunk.getX(), chunk.getZ(), probe(world, palette.base()))) {
                    if (changes == null) {
                        changes = new HashMap<>();
                    }
                    changes.put(Position.block(BlockKeys.x(key), BlockKeys.y(key), BlockKeys.z(key)), shown);
                }
            }
        }
        return changes;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerDepletion state = players.remove(uuid);
        if (state != null) {
            park(uuid, state); // SECFIX1 F-11: running timers come back on the next join
            byHandle.remove(state.handle);
        }
    }

    /** SECFIX1 F-11: a returning player gets their running timers back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        unpark(player, player.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PlayerDepletion state = players.get(player.getUniqueId());
        if (state != null) {
            park(player.getUniqueId(), state); // SECFIX1 F-11: kept until they are back in that world
            state.depleted.clear(); // the client dropped those chunks; timers find nothing and do nothing
            state.world = null;
            state.clearDamage();
            state.swap = null; // palette blocks come back with the chunk sends (the tier is kept)
        }
        unpark(player, player.getWorld().getName());
    }

    // ------------------------------------------------------------------ Depths API

    @Override
    public DepthsRegion regionAt(String world, int x, int y, int z) {
        return index.at(world, x, y, z);
    }

    @Override
    public List<DepthsRegion> regions() {
        return index.regions();
    }

    @Override
    public void registerRegion(DepthsRegion region) {
        if (configRegions.stream().anyMatch(r -> r.id().equals(region.id()))) {
            throw new IllegalArgumentException("Region id '" + region.id() + "' is already defined in depths.yml");
        }
        DepthsRegion old = codeRegions.get(region.id());
        if (old != null && !old.source().equals(region.source())) {
            throw new IllegalArgumentException("Region id '" + region.id() + "' already belongs to " + old.source());
        }
        RegionIndex.of(List.of(region)); // size check before touching state
        if (old != null) {
            restoreRegion(region.id());
        }
        codeRegions.put(region.id(), region);
        rebuild();
    }

    @Override
    public boolean unregisterRegion(String id) {
        if (codeRegions.remove(id) == null) {
            return false;
        }
        restoreRegion(id);
        rebuild();
        return true;
    }

    @Override
    public void unregisterAll(String source) {
        List<String> ids = codeRegions.values().stream().filter(r -> r.source().equals(source)).map(DepthsRegion::id).toList();
        ids.forEach(id -> {
            codeRegions.remove(id);
            restoreRegion(id);
        });
        if (!ids.isEmpty()) {
            rebuild();
        }
    }

    @Override
    public boolean isDepleted(Player player, Block block) {
        PlayerDepletion state = players.get(player.getUniqueId());
        return state != null && state.isDepleted(block.getWorld().getName(),
                BlockKeys.block(block.getX(), block.getY(), block.getZ()));
    }

    @Override
    public int depletedCount(Player player) {
        PlayerDepletion state = players.get(player.getUniqueId());
        return state == null ? 0 : state.depleted.size();
    }

    @Override
    public boolean deplete(Player player, Block block, int respawnTicks) {
        String world = block.getWorld().getName();
        DepthsRegion region = index.at(world, block.getX(), block.getY(), block.getZ());
        ResourceBlock resource = region == null ? null : region.resource(block.getType());
        if (resource == null) {
            return false;
        }
        PlayerDepletion state = state(player);
        long key = BlockKeys.block(block.getX(), block.getY(), block.getZ());
        if (state.isDepleted(world, key)) {
            return false;
        }
        TierPalette.Tier slot = slotTier(state, region, resource.block());
        ResourceBlock shown = slot != null ? slot.resource() : resource;
        depleteInternal(state, world, key, shown, region.id(), Math.max(1, respawnTicks));
        send(state, key, shown.depleted());
        return true;
    }

    @Override
    public boolean restore(Player player, Block block) {
        PlayerDepletion state = players.get(player.getUniqueId());
        long key = BlockKeys.block(block.getX(), block.getY(), block.getZ());
        if (state == null || !state.isDepleted(block.getWorld().getName(), key)) {
            return false;
        }
        state.depleted.remove(key);
        sendReal(state, key);
        return true;
    }

    @Override
    public Visibility visibility(Player player) {
        PlayerDepletion state = players.get(player.getUniqueId());
        return state == null ? Visibility.EVERYONE : state.visibility;
    }

    @Override
    public void visibility(Player player, Visibility visibility) {
        state(player).visibility = java.util.Objects.requireNonNull(visibility, "visibility");
    }

    @Override
    public void islandCheck(BiPredicate<Player, Player> sameIsland) {
        this.sameIsland = java.util.Objects.requireNonNull(sameIsland, "sameIsland");
    }

    @Override
    public boolean shouldSee(Player viewer, Player other) {
        return switch (visibility(viewer)) {
            case EVERYONE -> true;
            case ISLAND -> sameIsland.test(viewer, other);
            case NONE -> false;
        };
    }

    // ------------------------------------------------------------------ DV2-SPIKE tier palettes

    /** Wing tier of the zone holding a region (ResourceBreakEvent#getTier outside palettes; 0 = none). */
    public void regionTier(java.util.function.ToIntFunction<String> regionTier) {
        this.regionTier = java.util.Objects.requireNonNull(regionTier, "regionTier");
    }

    /**
     * Replaces the tier palettes. Every player at a higher tier first gets the real blocks back (at once, no throttle);
     * the caller then sets the tiers again with {@link #paletteTier}.
     */
    public void palettes(List<TierPalette> list) {
        for (PlayerDepletion state : new ArrayList<>(players.values())) {
            for (String id : new ArrayList<>(state.paletteTiers.keySet())) {
                TierPalette old = paletteById.get(id);
                if (old != null) {
                    swap(state, old, 1, false);
                }
            }
            state.paletteTiers.clear();
            state.swap = null;
        }
        swapping.clear();
        palettes = List.copyOf(list);
        paletteById.clear();
        paletteByRegion.clear();
        for (TierPalette palette : palettes) {
            paletteById.put(palette.id(), palette);
            for (String regionId : palette.regionIds()) {
                paletteByRegion.putIfAbsent(regionId, palette);
            }
        }
        rebuildSlotIndexes();
    }

    public List<TierPalette> palettes() {
        return palettes;
    }

    /** The player's active tier in a palette (1 = the real block). */
    public int paletteTier(Player player, String paletteId) {
        PlayerDepletion state = players.get(player.getUniqueId());
        return state == null ? 1 : state.paletteTiers.getInt(paletteId);
    }

    /**
     * Sets the player's active tier in a palette and swaps every slot block in the chunks their client has, nearest
     * first, in about one tick (Bedrock: {@code palette.bedrock-blocks-per-tick} per tick). Returns {@code null} when
     * nothing changed or the palette is unknown.
     */
    public SwapResult paletteTier(Player player, String paletteId, int tier) {
        TierPalette palette = paletteById.get(paletteId);
        if (palette == null) {
            return null;
        }
        return swap(state(player), palette, tier, true);
    }

    /**
     * One swap. {@code keys} are the swapped slots nearest first with their squared distances {@code dist2} (for the
     * unlock particle wave); {@code sentNow} went out at once, the rest is queued.
     */
    public record SwapResult(String palette, int oldTier, int newTier, int blocks, int sentNow, int perTick,
                             boolean bedrock, long nanos, long[] keys, int[] dist2) {
    }

    private SwapResult swap(PlayerDepletion state, TierPalette palette, int tier, boolean throttle) {
        int target = Math.max(1, Math.min(tier, palette.size()));
        int old = state.paletteTiers.getInt(palette.id());
        if (old == target) {
            return null;
        }
        long start = System.nanoTime();
        if (target == 1) {
            state.paletteTiers.removeInt(palette.id());
        } else {
            state.paletteTiers.put(palette.id(), target);
        }
        relookDepleted(state, palette, target);
        Player player = state.player;
        World world = player.getWorld();
        LongArrayList found = new LongArrayList();
        for (String regionId : palette.regionIds()) {
            DepthsRegion region = regionsById.get(regionId);
            SlotIndex slots = slotIndexes.get(regionId);
            if (region == null || slots == null || !region.world().equals(world.getName())) {
                continue;
            }
            SlotIndex.Probe probe = probe(world, palette.base());
            for (long chunkKey : slots.chunkKeys()) {
                int cx = (int) chunkKey;
                int cz = (int) (chunkKey >> 32);
                if (player.isChunkSent(chunkKey) && world.isChunkLoaded(cx, cz)) {
                    found.addElements(found.size(), slots.slots(cx, cz, probe));
                }
            }
        }
        long[] keys = found.toLongArray();
        int[] dist2 = sortNearest(keys, player.getLocation());
        boolean bedrock = core.bedrock().isBedrock(player.getUniqueId());
        int perTick = throttle ? PaletteMath.perTick(bedrock, config.paletteBedrockBlocksPerTick, config.paletteJavaBlocksPerTick)
                : Integer.MAX_VALUE;
        int now = Math.min(perTick, keys.length);
        sendSlots(state, palette, keys, 0, now);
        if (now < keys.length) {
            state.swap = new SwapQueue(keys, now, start);
            state.swapPalette = palette.id();
            if (!swapping.contains(state)) {
                swapping.add(state);
            }
        } else if (palette.id().equals(state.swapPalette)) {
            state.swap = null; // a newer swap replaces a running one
        }
        long nanos = System.nanoTime() - start;
        core.scheduler().timer().record("depths-palette-swap", nanos);
        swaps++;
        lastSwapBlocks = keys.length;
        lastSwapMillis = nanos / 1e6;
        maxSwapMillis = Math.max(maxSwapMillis, lastSwapMillis);
        if (config.paletteLogSwaps) {
            core.plugin().getLogger().info(String.format(Locale.ROOT,
                    "Depths palette swap (timer depths-palette-swap): %s %s tier %d -> %d, %d blocks (%d now%s)%s in %.2f ms",
                    player.getName(), palette.id(), old, target, keys.length, now,
                    now < keys.length ? ", " + (keys.length - now) + " queued at " + perTick + "/tick" : "",
                    bedrock ? ", Bedrock" : "", nanos / 1e6));
        }
        return new SwapResult(palette.id(), old, target, keys.length, now, perTick, bedrock, nanos, keys, dist2);
    }

    /** Sorts {@code keys} nearest to {@code at} first; returns the squared distances in the same order. */
    static int[] sortNearest(long[] keys, Location at) {
        return PaletteMath.sortNearest(keys, at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    /** Sends slots {@code from..to} of {@code keys} as the player should see them now (one multi-block change). */
    private void sendSlots(PlayerDepletion state, TierPalette palette, long[] keys, int from, int to) {
        if (from >= to) {
            return;
        }
        Player player = state.player;
        World world = player.getWorld();
        int tier = state.paletteTiers.getInt(palette.id());
        BlockData shown = PaletteMath.needsPackets(tier) ? palette.tier(tier).shown() : null;
        boolean depletedHere = !state.depleted.isEmpty() && world.getName().equals(state.world);
        Map<Position, BlockData> changes = new HashMap<>(Math.max(16, (to - from) * 2));
        for (int i = from; i < to; i++) {
            long key = keys[i];
            if (!player.isChunkSent(BlockKeys.chunkOf(key))) {
                continue;
            }
            int x = BlockKeys.x(key);
            int y = BlockKeys.y(key);
            int z = BlockKeys.z(key);
            PlayerDepletion.Depleted d = depletedHere ? state.depleted.get(key) : null;
            changes.put(Position.block(x, y, z), d != null ? d.shown() : shown != null ? shown : world.getBlockData(x, y, z));
        }
        if (!changes.isEmpty()) {
            player.sendMultiBlockChange(changes);
        }
    }

    /** Throttled swaps: each player gets their per-tick share. */
    private void drainSwaps() {
        long start = System.nanoTime();
        for (java.util.Iterator<PlayerDepletion> it = swapping.iterator(); it.hasNext(); ) {
            PlayerDepletion state = it.next();
            SwapQueue queue = state.swap;
            TierPalette palette = state.swapPalette == null ? null : paletteById.get(state.swapPalette);
            if (queue == null || palette == null || byHandle.get(state.handle) != state || !state.player.isOnline()) {
                state.swap = null;
                it.remove();
                continue;
            }
            boolean bedrock = core.bedrock().isBedrock(state.player.getUniqueId());
            int per = PaletteMath.perTick(bedrock, config.paletteBedrockBlocksPerTick, config.paletteJavaBlocksPerTick);
            LongArrayList batch = new LongArrayList(Math.min(per, queue.remaining()));
            queue.drain(per, batch::add);
            sendSlots(state, palette, batch.elements(), 0, batch.size());
            if (queue.done()) {
                state.swap = null;
                it.remove();
                lastDrainTicks = queue.ticks();
                lastDrainTotalMillis = (System.nanoTime() - queue.startedNanos()) / 1e6;
                if (config.paletteLogSwaps) {
                    core.plugin().getLogger().info(String.format(Locale.ROOT,
                            "Depths palette swap done (timer depths-palette-drain): %s %s, %d blocks, %d extra ticks, %.0f ms since start",
                            state.player.getName(), palette.id(), queue.total(), queue.ticks(), lastDrainTotalMillis));
                }
            }
        }
        core.scheduler().timer().record("depths-palette-drain", System.nanoTime() - start);
    }

    /** Depleted slots of a palette take the new tier's depleted look (timers unchanged). */
    private void relookDepleted(PlayerDepletion state, TierPalette palette, int tier) {
        if (state.depleted.isEmpty() || state.world == null) {
            return;
        }
        World world = Bukkit.getWorld(state.world);
        if (world == null) {
            return;
        }
        BlockData look = palette.tier(tier).resource().depleted();
        LongArrayList keys = new LongArrayList();
        ObjectIterator<Long2ObjectMap.Entry<PlayerDepletion.Depleted>> it = state.depleted.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<PlayerDepletion.Depleted> e = it.next();
            if (palette.regionIds().contains(e.getValue().regionId())) {
                keys.add(e.getLongKey());
            }
        }
        for (int i = 0; i < keys.size(); i++) {
            long key = keys.getLong(i);
            int x = BlockKeys.x(key);
            int z = BlockKeys.z(key);
            if (!world.isChunkLoaded(x >> 4, z >> 4) || world.getType(x, BlockKeys.y(key), z) != palette.base()) {
                continue;
            }
            PlayerDepletion.Depleted d = state.depleted.get(key);
            state.depleted.put(key, new PlayerDepletion.Depleted(d.token(), look, d.regionId(), d.endsAt()));
        }
    }

    /** The palette tier of a slot for this player ({@code null}: no palette here or not a slot block). Tier 1 included. */
    private TierPalette.Tier slotTier(PlayerDepletion state, DepthsRegion region, Material type) {
        if (paletteByRegion.isEmpty() || region == null) {
            return null;
        }
        TierPalette palette = paletteByRegion.get(region.id());
        if (palette == null || type != palette.base()) {
            return null;
        }
        return palette.tier(state.paletteTiers.getInt(palette.id()));
    }

    /** The palette tier shown at {@code key} when it differs from the real block, else {@code null}. */
    private TierPalette.Tier paletteTierAt(PlayerDepletion state, World world, long key) {
        if (paletteByRegion.isEmpty() || !state.anyPaletteTier()) {
            return null;
        }
        int x = BlockKeys.x(key);
        int y = BlockKeys.y(key);
        int z = BlockKeys.z(key);
        DepthsRegion region = index.at(world.getName(), x, y, z);
        TierPalette palette = region == null ? null : paletteByRegion.get(region.id());
        if (palette == null) {
            return null;
        }
        int tier = state.paletteTiers.getInt(palette.id());
        SlotIndex slots = slotIndexes.get(region.id());
        if (!PaletteMath.needsPackets(tier) || slots == null || !world.isChunkLoaded(x >> 4, z >> 4)
                || !slots.contains(key, probe(world, palette.base()))) {
            return null;
        }
        return palette.tier(tier);
    }

    /** Slot test for loaded chunks only (callers check). */
    private static SlotIndex.Probe probe(World world, Material base) {
        return (x, y, z) -> world.getType(x, y, z) == base;
    }

    /** Numbers for {@code /depths palette}. */
    public record PaletteStats(int palettes, long swaps, int lastBlocks, double lastMillis, double maxMillis,
                               int lastDrainTicks, double lastDrainTotalMillis, int swapping, int indexedChunks, int scans) {
    }

    public PaletteStats paletteStats() {
        int indexed = 0;
        int scans = 0;
        for (SlotIndex slots : slotIndexes.values()) {
            indexed += slots.indexed();
            scans += slots.scans();
        }
        return new PaletteStats(palettes.size(), swaps, lastSwapBlocks, lastSwapMillis, maxSwapMillis, lastDrainTicks,
                lastDrainTotalMillis, swapping.size(), indexed, scans);
    }

    // ------------------------------------------------------------------ debug

    /** Numbers for {@code /depths debug}. */
    public record Stats(int players, int depletedTotal, int timers, int timerCapacity, int timerSlots, long breaks,
                        double avgBreakMillis, double maxBreakMillis, long flagsTotal, int regions) {
    }

    public Stats stats() {
        int total = 0;
        for (PlayerDepletion state : players.values()) {
            total += state.depleted.size();
        }
        return new Stats(players.size(), total, wheel == null ? 0 : wheel.size(), wheel == null ? 0 : wheel.capacity(),
                wheel == null ? 0 : wheel.slots(), breaks, avgBreakNanos / 1e6, maxBreakNanos / 1e6, flagsTotal,
                index.regions().size());
    }

    /** Flags of one player: {in window, total}. */
    public int[] flags(Player player) {
        PlayerDepletion state = players.get(player.getUniqueId());
        return state == null ? new int[]{0, 0} : new int[]{state.flagsInWindow, state.flagsTotal};
    }
}
