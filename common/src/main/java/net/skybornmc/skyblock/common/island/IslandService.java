package net.skybornmc.skyblock.common.island;

import net.skybornmc.skyblock.common.audit.Actor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Islands as seen by other modules (published by the islands module: {@code core.services().optional(IslandService.class)}).
 * All methods are for the main thread unless they return a future. Snapshots ({@link IslandInfo}) never change; ask
 * again for fresh values.
 *
 * <p>Grid: the islands world is cut into 512 × 512 slots (one region file each). An island's playable area
 * ({@link IslandInfo#bounds()}) is a square of whole chunks centered in its slot and grows by chunk rings.</p>
 */
public interface IslandService {

    /** Island roles (owner decision: Leader, Mod, Member, Coop). Coop is temporary and never a stored member. */
    enum Role { LEADER, MOD, MEMBER, COOP }

    /** A rectangle of blocks in one world; both corners inclusive. */
    record Bounds(String world, int minX, int minZ, int maxX, int maxZ) {

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public boolean contains(Location location) {
            return location != null && location.getWorld() != null && location.getWorld().getName().equals(world)
                    && contains(location.getBlockX(), location.getBlockZ());
        }

        public int width() {
            return maxX - minX + 1;
        }

        public double centerX() {
            return (minX + maxX + 1) / 2.0;
        }

        public double centerZ() {
            return (minZ + maxZ + 1) / 2.0;
        }
    }

    /**
     * One island.
     *
     * @param name       display name (the owner's default name until renamed in M2.5)
     * @param team       false = solo island; becomes true forever on the first invite (DESIGN §3.2)
     * @param sizeChunks current border width in chunks (odd: 3, 5, … max)
     * @param bounds     the current playable area (inside the border)
     * @param slotBounds the whole 512 × 512 slot
     */
    record IslandInfo(UUID id, int seasonId, UUID owner, String name, boolean team, int slotId, int gridX, int gridZ,
                      int sizeChunks, int maxSizeChunks, Bounds bounds, Bounds slotBounds, long createdAt) {
    }

    /** Name of the islands world. */
    String worldName();

    boolean isIslandWorld(World world);

    /** The island whose playable area (inside its border) contains {@code location}. */
    Optional<IslandInfo> islandAt(Location location);

    /** The island whose slot contains {@code location}, even outside its border. */
    Optional<IslandInfo> islandInSlot(Location location);

    /** The island {@code player} belongs to (any stored role). One island per player. */
    Optional<IslandInfo> islandOf(UUID player);

    Optional<IslandInfo> island(UUID islandId);

    /** Stored members and their roles (the owner is the leader). Empty for an unknown island. */
    Map<UUID, Role> members(UUID islandId);

    Optional<Role> role(UUID islandId, UUID player);

    /** True if {@code player} is a stored member (any role) of the island. */
    default boolean isMember(UUID islandId, UUID player) {
        return role(islandId, player).isPresent();
    }

    /** The island home (where /is home teleports), empty until the island is built. */
    Optional<Location> home(UUID islandId);

    /** Teleports {@code player} to their own island home (warmup, safe-spot check). False if they have no island. */
    boolean teleportHome(Player player);

    /**
     * Admin/upgrade hook (Phase 5 Island Size, M2.7 /isadmin setsize): sets the border width in chunks (odd, between the
     * start size and the max). Saved, logged (category island) and shown to players on the island right away.
     *
     * @return false if the island is unknown or the size is invalid
     */
    CompletableFuture<Boolean> setSize(UUID islandId, int sizeChunks, Actor actor);
}
