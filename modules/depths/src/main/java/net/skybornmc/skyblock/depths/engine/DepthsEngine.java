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
 * and whenever a cancelled interaction makes the server re-send the real block. State is dropped on quit and on world
 * change.</p>
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
    private final Int2ObjectOpenHashMap<PlayerDepletion> byHandle = new Int2ObjectOpenHashMap<>();
    private int nextHandle;

    private TimingWheel wheel;
    private final TimingWheel.Sink expireSink = this::expire;
    private final IntArrayList pendingOwners = new IntArrayList();
    private final LongArrayList pendingKeys = new LongArrayList();
    private final Location scratch = new Location(null, 0, 0, 0);
    private Scheduler.Task tickTask = Scheduler.Task.NONE;
    private BiPredicate<Player, Player> sameIsland = (a, b) -> false;

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
        tickTask = core.scheduler().repeat("depths-tick", 1L, 1L, this::tick);
    }

    /** Shows every player their real blocks again and stops the timers. */
    public void stop() {
        tickTask.cancel();
        tickTask = Scheduler.Task.NONE;
        restoreAll();
        players.clear();
        byHandle.clear();
    }

    /** Replaces the regions from depths.yml (restores all depleted blocks first, definitions may have changed). */
    public void configRegions(List<DepthsRegion> regions) {
        restoreAll();
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
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        wheel.advance(expireSink);
        if (!pendingKeys.isEmpty()) {
            flushPending();
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
        send(state, key, world.getBlockData(BlockKeys.x(key), BlockKeys.y(key), BlockKeys.z(key)));
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
        int token = state.nextToken();
        state.depleted.put(key, new PlayerDepletion.Depleted(token, resource.depleted(), regionId));
        wheel.schedule(respawnTicks, state.handle, key, token);
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
                changes.put(Position.block(x, y, z), world.getBlockData(x, y, z));
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
        if (config.fastBreakEnabled && checkFastBreak(state, player, block, world, key, resource)) {
            state.clearDamage();
            return;
        }
        state.clearDamage();
        ItemStack tool = player.getInventory().getItemInMainHand();
        ResourceBreakEvent custom = new ResourceBreakEvent(player, block, region, resource, tool);
        long beforeEvent = System.nanoTime();
        custom.callEvent();
        long eventNanos = System.nanoTime() - beforeEvent;
        if (!custom.isCancelled()) {
            depleteInternal(state, world, key, resource, region.id(), custom.getRespawnTicks());
            queueResend(state, key);
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
        state.damageSpeed = block.getBreakSpeed(player);
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

    /** True (and flagged) if this break was faster than possible. */
    private boolean checkFastBreak(PlayerDepletion state, Player player, Block block, String world, long key,
                                   ResourceBlock resource) {
        boolean started = state.damageMatches(world, key);
        if (started && state.damageInstant) {
            return false;
        }
        float speedNow = block.getBreakSpeed(player);
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
        if (state == null || state.depleted.isEmpty()) {
            return;
        }
        long key = BlockKeys.block(block.getX(), block.getY(), block.getZ());
        if (state.isDepleted(block.getWorld().getName(), key)) {
            queueResend(state, key);
        }
    }

    /** Re-applies the player's depleted blocks in a chunk the server just sent them (fired after the chunk packet). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        if (players.isEmpty()) {
            return;
        }
        PlayerDepletion state = players.get(event.getPlayer().getUniqueId());
        if (state == null || state.depleted.isEmpty()) {
            return;
        }
        Chunk chunk = event.getChunk();
        if (!chunk.getWorld().getName().equals(state.world)) {
            return;
        }
        long chunkKey = BlockKeys.chunk(chunk.getX(), chunk.getZ());
        Map<Position, BlockData> changes = null;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerDepletion state = players.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            byHandle.remove(state.handle);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PlayerDepletion state = players.get(event.getPlayer().getUniqueId());
        if (state != null) {
            state.depleted.clear(); // the client dropped those chunks; timers find nothing and do nothing
            state.world = null;
            state.clearDamage();
        }
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
        depleteInternal(state, world, key, resource, region.id(), Math.max(1, respawnTicks));
        send(state, key, resource.depleted());
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
