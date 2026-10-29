package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Bounds;
import net.skybornmc.skyblock.common.island.IslandService.IslandInfo;
import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One cached island (main thread only). Changes go through {@link IslandManager}, which saves them first and updates
 * this object after the database confirmed.
 */
public final class Island {

    /** A saved home position inside the island. */
    public record Home(double x, double y, double z, float yaw, float pitch) {
    }

    private final UUID id;
    private final int seasonId;
    private final UUID owner;
    private final boolean team;
    private final int slotId;
    private final int gridX;
    private final int gridZ;
    private final long createdAt;
    private final String world;
    private final Map<UUID, Role> members = new LinkedHashMap<>();
    private int sizeChunks;
    private Home home;
    private String name;
    private String ownerName;

    public Island(UUID id, int seasonId, UUID owner, boolean team, int slotId, int gridX, int gridZ, int sizeChunks, Home home,
                  String name, long createdAt, String world) {
        this.id = id;
        this.seasonId = seasonId;
        this.owner = owner;
        this.team = team;
        this.slotId = slotId;
        this.gridX = gridX;
        this.gridZ = gridZ;
        this.sizeChunks = sizeChunks;
        this.home = home;
        this.name = name;
        this.createdAt = createdAt;
        this.world = world;
    }

    public UUID id() {
        return id;
    }

    public int seasonId() {
        return seasonId;
    }

    public UUID owner() {
        return owner;
    }

    public boolean team() {
        return team;
    }

    public int slotId() {
        return slotId;
    }

    public int gridX() {
        return gridX;
    }

    public int gridZ() {
        return gridZ;
    }

    public long createdAt() {
        return createdAt;
    }

    public String world() {
        return world;
    }

    public int sizeChunks() {
        return sizeChunks;
    }

    void sizeChunks(int sizeChunks) {
        this.sizeChunks = sizeChunks;
    }

    /** Empty until the island was built (a missing home means the paste never finished: it is rebuilt on /is). */
    public Optional<Home> home() {
        return Optional.ofNullable(home);
    }

    void home(Home home) {
        this.home = home;
    }

    /** Custom name (M2.5), or null for the default "<owner>'s Island". */
    public String customName() {
        return name;
    }

    String ownerName() {
        return ownerName;
    }

    void ownerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Map<UUID, Role> members() {
        return Collections.unmodifiableMap(members);
    }

    void putMember(UUID uuid, Role role) {
        members.put(uuid, role);
    }

    public Optional<Role> role(UUID player) {
        return Optional.ofNullable(members.get(player));
    }

    public Bounds bounds() {
        return IslandGrid.islandBounds(world, gridX, gridZ, sizeChunks);
    }

    public Bounds slotBounds() {
        return IslandGrid.slotBounds(world, gridX, gridZ);
    }

    public int centerX() {
        return IslandGrid.centerBlock(gridX);
    }

    public int centerZ() {
        return IslandGrid.centerBlock(gridZ);
    }

    IslandInfo info(String displayName, int maxSize) {
        return new IslandInfo(id, seasonId, owner, displayName, team, slotId, gridX, gridZ, sizeChunks, maxSize, bounds(),
                slotBounds(), createdAt);
    }
}
