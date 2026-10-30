package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.flight.FlightGrants;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Island fly (ISL2, owner I4): everyone inside an island's border may fly (double-tap jump) when they have
 * {@code skyblock.island.fly} or the island has the "Wings" mastery ({@link IslandManager#hasWings}); visitors only while
 * the island's {@code visitor-fly} setting is on. Leaving the border (teleport, being sent off, a setting change) turns
 * fly off at once, and the next fall within {@code fly.landing-protection-seconds} does no damage.
 *
 * <p>Only flight this class granted is ever taken away, and only when no other feature still gives flight
 * ({@link FlightGrants}: {@code /fly}, staff mode, freeze). Creative, spectator and vanished staff are left alone. Border crossings are checked on block moves (one O(1) lookup); permission, role and setting changes
 * by a light periodic check of players in the islands world.</p>
 */
final class IslandFly implements Listener {

    static final String PERM_FLY = "skyblock.island.fly";

    private final IslandsModule module;
    private final IslandManager islands;
    /** Players whose flight this class turned on. */
    private final Set<UUID> granted = new HashSet<>();
    /** Players flying with island fly when they logged out (flying again on join if still allowed; memory only). */
    private final Set<UUID> flyingAtQuit = new HashSet<>();
    /** Players protected from the next fall: until (epoch millis). */
    private final Map<UUID, Long> landing = new HashMap<>();
    private Scheduler.Task task;

    IslandFly(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.islands = islands;
    }

    private IslandsConfig config() {
        return module.config();
    }

    /**
     * The decision (pure): may a player fly at this spot? {@code onIsland} = inside an island's border; {@code member} =
     * stored member or coop player of that island.
     */
    static boolean allowed(boolean onIsland, boolean member, boolean visitorFly, boolean permission, boolean wings) {
        if (!onIsland || (!member && !visitorFly)) {
            return false;
        }
        return permission || wings;
    }

    void start() {
        stop();
        long period = config().flyCheckTicks;
        task = module.core().scheduler().repeat("island-fly", period, period, this::checkAll);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Takes back every flight this class granted (module disable). */
    void revokeAll() {
        for (UUID uuid : List.copyOf(granted)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                revoke(player, false);
            }
        }
        granted.clear();
        landing.clear();
    }

    /** Creative/spectator own their flight; vanished staff (staff mode) keep whatever flight they have. */
    private boolean ignored(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || VisitManager.hidden(player);
    }

    /** Re-checks {@code player} at {@code at}: grants or revokes island fly. */
    void update(Player player, Location at) {
        UUID uuid = player.getUniqueId();
        if (ignored(player)) {
            if (granted.remove(uuid)) { // the game mode or staff mode owns flight now
                FlightGrants.revoke(uuid, FlightGrants.ISLAND);
            }
            return;
        }
        Island island = at != null && islands.isIslandWorld(at.getWorld()) ? islands.islandAt(at.getBlockX(), at.getBlockZ()) : null;
        boolean allowed = island != null && allowed(true, island.roleOrNull(islands.key(player)) != null,
                islands.flagOn(island, IslandFlag.VISITOR_FLY), player.hasPermission(PERM_FLY), islands.hasWings(island.id()));
        if (allowed) {
            if (!granted.contains(uuid) || !player.getAllowFlight()) {
                boolean couldFly = player.getAllowFlight();
                granted.add(uuid);
                FlightGrants.grant(uuid, FlightGrants.ISLAND);
                landing.remove(uuid);
                if (!couldFly) {
                    player.setAllowFlight(true);
                    if (flyingAtQuit.remove(uuid)) {
                        player.setFlying(true);
                    } else {
                        module.msg().actionBar(player, "fly.on");
                    }
                }
            }
        } else if (granted.contains(uuid)) {
            revoke(player, true);
        }
    }

    private void revoke(Player player, boolean tell) {
        UUID uuid = player.getUniqueId();
        granted.remove(uuid);
        if (!FlightGrants.revoke(uuid, FlightGrants.ISLAND) || ignored(player)) {
            return; // /fly, staff mode or freeze still gives flight (or the game mode owns it): leave it
        }
        boolean wasFlying = player.isFlying();
        player.setFlying(false);
        player.setAllowFlight(false);
        if (wasFlying || player.getFallDistance() > 0 || player.getVelocity().getY() < 0) {
            protectLanding(uuid);
        }
        if (tell && wasFlying) {
            module.msg().actionBar(player, "fly.off");
        }
    }

    private void protectLanding(UUID uuid) {
        if (config().flyLandingSeconds > 0) {
            landing.put(uuid, System.currentTimeMillis() + config().flyLandingSeconds * 1000L);
        }
    }

    /** Re-checks everyone standing on {@code island} (a setting changed). */
    void refresh(Island island) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location at = player.getLocation();
            if (islands.isIslandWorld(at.getWorld()) && islands.islandInSlotAt(at.getBlockX(), at.getBlockZ()) == island) {
                update(player, at);
            }
        }
    }

    private void checkAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (granted.contains(player.getUniqueId()) || islands.isIslandWorld(player.getWorld())) {
                update(player, player.getLocation());
            }
        }
        if (!landing.isEmpty()) {
            long now = System.currentTimeMillis();
            landing.values().removeIf(until -> until < now);
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            Player player = event.getPlayer();
            if (granted.contains(player.getUniqueId()) || islands.isIslandWorld(to.getWorld())) {
                update(player, to);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        module.core().scheduler().later("island-fly-teleport", 1L, () -> {
            if (player.isOnline()) {
                update(player, player.getLocation());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        update(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        module.core().scheduler().later("island-fly-respawn", 1L, () -> {
            if (player.isOnline()) {
                update(player, player.getLocation());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        module.core().scheduler().later("island-fly-join", 2L, () -> {
            if (player.isOnline()) {
                update(player, player.getLocation());
                if (flyingAtQuit.remove(player.getUniqueId()) && !player.isFlying()) {
                    protectLanding(player.getUniqueId()); // logged out in the air without fly now: land softly
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (granted.remove(player.getUniqueId())) {
            FlightGrants.revoke(player.getUniqueId(), FlightGrants.ISLAND); // re-granted next tick if still allowed
        }
        module.core().scheduler().later("island-fly-gamemode", 1L, () -> {
            if (player.isOnline()) {
                update(player, player.getLocation());
            }
        });
    }

    /** Stopping to fly in the air: the landing is soft. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggle(PlayerToggleFlightEvent event) {
        if (!event.isFlying() && granted.contains(event.getPlayer().getUniqueId())) {
            protectLanding(event.getPlayer().getUniqueId());
        } else if (event.isFlying()) {
            landing.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player) || landing.isEmpty()) {
            return;
        }
        Long until = landing.remove(player.getUniqueId());
        if (until != null && until >= System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    /** Island fly is never saved with the player: flight granted here is taken back on quit (and given back on join). */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        landing.remove(uuid);
        if (granted.remove(uuid)) {
            if (player.isFlying()) {
                flyingAtQuit.add(uuid);
            }
            if (FlightGrants.revoke(uuid, FlightGrants.ISLAND) && !ignored(player)) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
    }
}
