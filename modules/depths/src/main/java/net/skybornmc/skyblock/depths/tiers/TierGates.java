package net.skybornmc.skyblock.depths.tiers;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.skills.SkillLevelUpEvent;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.DepthsPerms;
import net.skybornmc.skyblock.depths.engine.BlockKeys;
import net.skybornmc.skyblock.depths.engine.ResourceBreakEvent;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Hub;
import net.skybornmc.skyblock.depths.zones.Side;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneManager;
import net.skybornmc.skyblock.common.util.Stagger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tier locks of The Depths (M4.6).
 *
 * <ul>
 *   <li><b>Gates:</b> every zone's gate opening shows a door ({@code tiers.gate-block}) only to players who have not
 *   unlocked that tier, by block changes sent to them alone; the real gate stays open. Doors are (re)sent with every
 *   chunk send, after a click on them, at once on a level-up and by a periodic check ({@code tiers.check-ticks}) that
 *   catches level changes without a level-up event (data loaded, staff set XP, reload).</li>
 *   <li><b>Entry:</b> walking into a locked zone (any side), teleporting or throwing a pearl into it is refused with
 *   the requirement on the action bar ("Mining 15 needed · 9/15") and a push back; a player standing in a zone that
 *   became locked is sent to The Gate. Breaking resource blocks in a locked zone is refused (no XP, no drops).</li>
 *   <li><b>Unlocks:</b> a level-up that opens tiers says so in chat; first unlocks are remembered and audited
 *   ({@link TierProgress}); the first visit of a tier shows a title and plays a sound.</li>
 * </ul>
 *
 * <p>Staff with {@link DepthsPerms#BYPASS} are never locked; {@code /depths tp} gives a one-zone pass
 * ({@link #grantPass}) until the staff member enters another zone. Main thread only, except {@link #place} (read by
 * placeholders).</p>
 */
public final class TierGates implements Listener {

    /** A zone's gate as per-chunk block keys, plus the door block shown to locked players. */
    private record Door(int index, Zone zone, BlockData fake, Map<Long, long[]> byChunk) {
    }

    private static final class State {
        final BitSet locked = new BitSet();
        String place;
        String pass;
        long lastMessage;
    }

    private final Core core;
    private final DepthsConfig config;
    private final ZoneManager zones;
    private final ModuleMessages msg;
    private final TierProgress progress;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, String> places = new ConcurrentHashMap<>();
    private List<Door> doors = List.of();
    private Map<Long, List<Door>> doorsByChunk = Map.of();
    private Scheduler.Task task = Scheduler.Task.NONE;

    public TierGates(Core core, DepthsConfig config, ZoneManager zones, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.zones = zones;
        this.msg = msg;
        this.progress = new TierProgress(core, config);
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            progress.load(player);
            updatePlace(player, player.getLocation());
        }
        rebuild();
        task = core.scheduler().repeat("depths-tiers-check", 1L, 1L, this::checkDue); // PERF2 (P-12): one slice per tick
    }

    /** Call after zones.yml / depths.yml were (re)loaded: rebuilds the doors and re-sends them. */
    public void reload() {
        task.cancel();
        rebuild();
        task = core.scheduler().repeat("depths-tiers-check", 1L, 1L, this::checkDue); // PERF2 (P-12): one slice per tick
    }

    public void stop() {
        task.cancel();
        restoreAll();
        progress.flush();
    }

    private void rebuild() {
        restoreAll();
        BlockData base = ZoneManager.blockData(config.gateBlock);
        if (base == null) {
            core.plugin().getLogger().warning("Depths tiers: tiers.gate-block '" + config.gateBlock + "' is not a block, iron_bars is used");
            base = Material.IRON_BARS.createBlockData();
        }
        List<Door> built = new ArrayList<>();
        Map<Long, List<Door>> byChunk = new HashMap<>();
        List<Zone> all = zones.zones();
        for (int i = 0; i < all.size(); i++) {
            Zone zone = all.get(i);
            Map<Long, List<Long>> keys = new LinkedHashMap<>();
            for (long key : GateMath.blocks(zone.gate())) {
                keys.computeIfAbsent(BlockKeys.chunkOf(key), k -> new ArrayList<>()).add(key);
            }
            Map<Long, long[]> parts = new LinkedHashMap<>();
            keys.forEach((chunk, list) -> parts.put(chunk, list.stream().mapToLong(Long::longValue).toArray()));
            Door door = new Door(i, zone, oriented(base, zone), parts);
            built.add(door);
            for (long chunk : parts.keySet()) {
                byChunk.computeIfAbsent(chunk, k -> new ArrayList<>()).add(door);
            }
        }
        doors = List.copyOf(built);
        doorsByChunk = byChunk;
        states.values().forEach(s -> s.locked.clear());
        checkAll();
    }

    /** Bars and panes in the door connect along the gate plane (otherwise they would show as thin posts). */
    private static BlockData oriented(BlockData base, Zone zone) {
        BlockData data = base.clone();
        if (data instanceof MultipleFacing facing) {
            boolean spansZ = GateMath.spansZ(zone.gate());
            for (BlockFace face : facing.getAllowedFaces()) {
                boolean along = spansZ ? face == BlockFace.NORTH || face == BlockFace.SOUTH : face == BlockFace.EAST || face == BlockFace.WEST;
                facing.setFace(face, along);
            }
        }
        return data;
    }

    /** Shows every player the real gates again (disable, before a rebuild). */
    private void restoreAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            State state = states.get(player.getUniqueId());
            if (state == null || state.locked.isEmpty() || !inDepths(player)) {
                continue;
            }
            for (Door door : doors) {
                if (state.locked.get(door.index())) {
                    send(player, door, false, null);
                }
            }
            state.locked.clear();
        }
    }

    // ------------------------------------------------------------------ public API

    /** DV2-SPIKE: also run on every periodic tier check of a player (the palette refreshes active tiers there). */
    private java.util.function.BiConsumer<Player, SkillService> afterCheck = (p, s) -> { };

    public void afterCheck(java.util.function.BiConsumer<Player, SkillService> hook) {
        this.afterCheck = Objects.requireNonNull(hook, "hook");
    }

    /** Lets a staff member stay in {@code zoneId} although it is locked for them (until they enter another zone). */
    public void grantPass(Player player, String zoneId) {
        state(player).pass = zoneId;
    }

    /** True if tier locks keep {@code player} out of {@code zone} (bypass and disabled locks count as open). */
    public boolean locked(Player player, Zone zone) {
        return locked(player, zone, skills(), bypass(player));
    }

    /** The player's level in the zone's skill, or {@code null} when no skill system gives that skill (zones open). */
    public Integer level(Player player, Aspect aspect) {
        return level(skills(), player, aspect);
    }

    public boolean bypass(Player player) {
        return player.hasPermission(DepthsPerms.BYPASS);
    }

    /** Where the player is: a zone id, {@link Hub#ID} or {@code null} (thread-safe, for placeholders). */
    public String place(UUID player) {
        return places.get(player);
    }

    // ------------------------------------------------------------------ rules

    private SkillService skills() {
        return core.services().optional(SkillService.class).orElse(null);
    }

    private static Integer level(SkillService skills, Player player, Aspect aspect) {
        if (skills == null || !skills.available(aspect.skill())) {
            return null;
        }
        return skills.level(player, aspect.skill());
    }

    private boolean locked(Player player, Zone zone, SkillService skills, boolean bypass) {
        if (!config.tierLocks || bypass) {
            return false;
        }
        Integer level = level(skills, player, zone.aspect());
        return level != null && !TierRules.unlocked(level, zone.requiredLevel());
    }

    /** Locked and no staff pass for this zone. */
    private boolean blocked(Player player, Zone zone) {
        return locked(player, zone) && !zone.id().equals(state(player).pass);
    }

    private boolean inDepths(Player player) {
        return player.getWorld().getName().equals(zones.world());
    }

    private State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), k -> new State());
    }

    // ------------------------------------------------------------------ periodic check

    private long checkTick;

    /** Everybody at once (after the doors were rebuilt). */
    private void checkAll() {
        SkillService skills = skills();
        for (Player player : Bukkit.getOnlinePlayers()) {
            check(player, skills);
        }
    }

    /** Every tick: checks the players whose slot is due, so each player is checked once per tier-check-ticks. */
    private void checkDue() {
        long tick = ++checkTick;
        int period = Math.max(1, config.tierCheckTicks);
        SkillService skills = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!Stagger.due(player.getUniqueId(), tick, period)) {
                continue;
            }
            if (skills == null) {
                skills = skills();
            }
            check(player, skills);
        }
    }

    /** Remembers new unlocks, opens/closes this player's doors and moves them out of a zone that became locked. */
    private void check(Player player, SkillService skills) {
        List<Zone> all = zones.zones();
        UUID uuid = player.getUniqueId();
        if (skills != null && progress.loaded(uuid)) {
            for (Zone zone : all) {
                Integer level = level(skills, player, zone.aspect());
                if (level != null && TierRules.unlocked(level, zone.requiredLevel()) && !progress.unlocked(uuid, zone.id())) {
                    progress.recordUnlock(player, zone, level);
                }
            }
        }
        afterCheck.accept(player, skills);
        if (!inDepths(player)) {
            return;
        }
        boolean bypass = bypass(player);
        State state = state(player);
        for (Door door : doors) {
            boolean lock = locked(player, door.zone(), skills, bypass);
            if (lock != state.locked.get(door.index())) {
                state.locked.set(door.index(), lock);
                send(player, door, lock, null);
            }
        }
        Zone here = zones.zoneAt(player.getLocation());
        if (here != null && locked(player, here, skills, bypass) && !here.id().equals(state.pass)) {
            kickOut(player, here);
        }
    }

    private void kickOut(Player player, Zone zone) {
        Hub hub = zones.hub();
        Location target = hub != null ? zones.location(hub.spawn()) : player.getWorld().getSpawnLocation();
        if (target == null) {
            return;
        }
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (ok) {
                msg.send(player, "tiers.kicked", requirement(player, zone));
                sound(player, config.lockedSound);
            }
        });
    }

    // ------------------------------------------------------------------ doors

    /**
     * Sends a door's blocks to the player: the door ({@code lock}) on passable gate blocks, or the real blocks. Only
     * chunks the client has; {@code onlyChunk} limits it to one chunk (chunk send), {@code null} = all.
     */
    private void send(Player player, Door door, boolean lock, Long onlyChunk) {
        World world = player.getWorld();
        Map<Position, BlockData> changes = new HashMap<>();
        for (Map.Entry<Long, long[]> part : door.byChunk().entrySet()) {
            long chunk = part.getKey();
            if ((onlyChunk != null && onlyChunk != chunk) || !player.isChunkSent(chunk)) {
                continue;
            }
            for (long key : part.getValue()) {
                int x = BlockKeys.x(key);
                int y = BlockKeys.y(key);
                int z = BlockKeys.z(key);
                Block block = world.getBlockAt(x, y, z);
                if (lock && !block.isPassable()) {
                    continue; // a built frame stays as it is
                }
                changes.put(Position.block(x, y, z), lock ? door.fake() : block.getBlockData());
            }
        }
        if (!changes.isEmpty()) {
            player.sendMultiBlockChange(changes);
        }
    }

    /** The server just sent a chunk: put this player's locked doors in it back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        if (doorsByChunk.isEmpty()) {
            return;
        }
        Chunk chunk = event.getChunk();
        if (!chunk.getWorld().getName().equals(zones.world())) {
            return;
        }
        long key = BlockKeys.chunk(chunk.getX(), chunk.getZ());
        List<Door> here = doorsByChunk.get(key);
        if (here == null) {
            return;
        }
        Player player = event.getPlayer();
        SkillService skills = skills();
        boolean bypass = bypass(player);
        State state = state(player);
        for (Door door : here) {
            boolean lock = locked(player, door.zone(), skills, bypass);
            state.locked.set(door.index(), lock);
            if (lock) {
                send(player, door, true, key);
            }
        }
    }

    /** Clicking a door makes the server re-send the real (open) block: show the door again next tick. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null || doorsByChunk.isEmpty() || !clicked.getWorld().getName().equals(zones.world())) {
            return;
        }
        List<Door> here = doorsByChunk.get(BlockKeys.chunk(clicked.getX() >> 4, clicked.getZ() >> 4));
        if (here == null) {
            return;
        }
        Player player = event.getPlayer();
        State state = state(player);
        for (Door door : here) {
            if (state.locked.get(door.index()) && door.zone().gate().contains(clicked.getX(), clicked.getY(), clicked.getZ())) {
                core.scheduler().later("depths-tiers-door", 1, () -> {
                    if (player.isOnline() && inDepths(player) && state.locked.get(door.index())) {
                        send(player, door, true, null);
                    }
                });
            }
        }
    }

    // ------------------------------------------------------------------ entry

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !config.tierLocks) {
            return;
        }
        Location to = event.getTo();
        if (to.getWorld() == null || !to.getWorld().getName().equals(zones.world())) {
            return;
        }
        Zone target = zones.zoneAt(to);
        if (target == null || same(target, zones.zoneAt(event.getFrom()))) {
            return;
        }
        Player player = event.getPlayer();
        if (blocked(player, target)) {
            event.setCancelled(true);
            push(player, target, event.getFrom());
            notifyLocked(player, target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoved(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) {
            updatePlace(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!config.tierLocks) {
            return;
        }
        Location to = event.getTo();
        if (to.getWorld() == null || !to.getWorld().getName().equals(zones.world())) {
            return;
        }
        Zone target = zones.zoneAt(to);
        if (target == null) {
            return;
        }
        Location from = event.getFrom();
        Zone origin = from.getWorld() != null && from.getWorld().equals(to.getWorld()) ? zones.zoneAt(from) : null;
        Player player = event.getPlayer();
        if (!same(target, origin) && blocked(player, target)) {
            event.setCancelled(true);
            notifyLocked(player, target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleported(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo().clone();
        core.scheduler().later("depths-tiers-place", 1, () -> {
            if (player.isOnline()) {
                updatePlace(player, player.getWorld().equals(to.getWorld()) ? player.getLocation() : to);
            }
        });
    }

    /** Resource breaks in a zone the player has not unlocked give nothing (e.g. staff pass, lost levels). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onResourceBreak(ResourceBreakEvent event) {
        Block block = event.getBlock();
        Zone zone = zones.zoneAt(block.getLocation());
        if (zone != null && locked(event.getPlayer(), zone)) {
            event.setCancelled(true);
            notifyLocked(event.getPlayer(), zone);
        }
    }

    private static boolean same(Zone a, Zone b) {
        return a != null && b != null && a.id().equals(b.id());
    }

    private void push(Player player, Zone zone, Location from) {
        Side side = GateMath.outside(zone.region(), from.getX(), from.getZ());
        Vector push = new Vector(GateMath.pushX(side) * config.pushStrength, config.pushUp, GateMath.pushZ(side) * config.pushStrength);
        // The cancelled move snaps the player back this tick; the push must come after it.
        core.scheduler().later("depths-tiers-push", 1, () -> {
            if (player.isOnline()) {
                player.setVelocity(push);
            }
        });
    }

    private void notifyLocked(Player player, Zone zone) {
        State state = state(player);
        long now = System.currentTimeMillis();
        if (now - state.lastMessage < config.lockedMessageCooldownMs) {
            return;
        }
        state.lastMessage = now;
        msg.actionBar(player, "tiers.locked", requirement(player, zone));
        sound(player, config.lockedSound);
    }

    /** {@code <skill> <required> <level> <name> <tier> <wing>} for lock texts. */
    TagResolver requirement(Player player, Zone zone) {
        Integer level = level(player, zone.aspect());
        return TagResolver.resolver(
                Msg.c("skill", msg.component(player, "zones.skill." + zone.aspect().skill().id())),
                Msg.p("required", zone.requiredLevel()), Msg.p("level", level == null ? 0 : level),
                Msg.p("missing", TierRules.missing(level == null ? 0 : level, zone.requiredLevel())),
                Msg.p("name", zone.name()), Msg.p("tier", zone.tier()),
                Msg.c("wing", msg.component(player, "tiers.wing." + zone.aspect().id())));
    }

    // ------------------------------------------------------------------ place, first visit, unlock messages

    private void updatePlace(Player player, Location location) {
        State state = state(player);
        Zone zone = null;
        String place = null;
        if (location.getWorld() != null && location.getWorld().getName().equals(zones.world())) {
            zone = zones.zoneAt(location);
            Hub hub = zones.hub();
            if (zone != null) {
                place = zone.id();
            } else if (hub != null && hub.region().contains(location.getX(), location.getY(), location.getZ())) {
                place = Hub.ID;
            }
        }
        if (Objects.equals(place, state.place)) {
            return;
        }
        state.place = place;
        if (place == null) {
            places.remove(player.getUniqueId());
        } else {
            places.put(player.getUniqueId(), place);
        }
        if (zone != null) {
            if (state.pass != null && !state.pass.equals(zone.id())) {
                state.pass = null;
            }
            firstVisit(player, zone);
        }
    }

    private void firstVisit(Player player, Zone zone) {
        Integer level = level(player, zone.aspect());
        if (level != null && !TierRules.unlocked(level, zone.requiredLevel())) {
            return; // staff pass or bypass: not a real visit
        }
        if (progress.recordVisit(player, zone, level)) {
            msg.title(player, "tiers.first-visit", requirement(player, zone));
            sound(player, config.firstVisitSound);
        }
    }

    /** After the skills module's own level-up message: name the tiers it opened, open their doors now. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelUp(SkillLevelUpEvent event) {
        Player player = event.getPlayer();
        List<Zone> opened = TierRules.newlyUnlocked(zones.zones(), event.getSkill(), event.getOldLevel(), event.getNewLevel());
        for (Zone zone : opened) {
            msg.send(player, "tiers.unlocked", requirement(player, zone));
        }
        if (!opened.isEmpty()) {
            sound(player, config.unlockSound);
        }
        check(player, skills());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        state(player);
        progress.load(player);
        updatePlace(player, player.getLocation());
    }

    /** PROF: Depths unlocks are per profile. Reload them for the new profile. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent event) {
        Player player = event.getPlayer();
        progress.unload(player.getUniqueId());
        progress.load(player);
        state(player).locked.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(net.skybornmc.skyblock.common.profile.ProfileDeletedEvent event) {
        progress.deleteProfile(event.profile().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        states.remove(uuid);
        places.remove(uuid);
        progress.unload(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        State state = state(player);
        state.locked.clear(); // the client dropped those chunks; the next chunk sends bring the doors back
        updatePlace(player, player.getLocation());
    }

    private void sound(Player player, String key) {
        if (key != null && !key.isBlank()) {
            core.sounds().play(player, key);
        }
    }
}
