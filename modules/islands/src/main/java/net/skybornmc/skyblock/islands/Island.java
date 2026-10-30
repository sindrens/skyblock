package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Bounds;
import net.skybornmc.skyblock.common.island.IslandService.IslandInfo;
import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private UUID owner;
    private boolean team;
    private final int slotId;
    private final int gridX;
    private final int gridZ;
    private final long createdAt;
    private final String world;
    private final Map<UUID, Role> members = new LinkedHashMap<>();
    /** When each stored member joined (epoch millis) and who invited them (M2.3; null = leader/unknown). */
    private final Map<UUID, Long> joinedAt = new HashMap<>();
    private final Map<UUID, UUID> invitedBy = new HashMap<>();
    /** Coop players (temporary trust, never stored members, REVIEW A4). */
    private final Set<UUID> coops = new LinkedHashSet<>();
    /** Member Slots of this island ({@code null} = members.start-slots). */
    private Integer memberSlots;
    private final IslandPerms perms = new IslandPerms();
    /** ISL2: the island's own {@code /is settings} values. */
    private final IslandSettings settings = new IslandSettings();
    private int sizeChunks;
    // The playable area as plain ints (protection checks run per block event: no Bounds allocation there).
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;
    private Home home;
    private String name;
    private String ownerName;
    /** M2.4: locked = only members and coop players can enter. */
    private boolean locked;
    /** Owner answer 293: hidden from the visit browser by staff (still visitable by name), with the staff note. */
    private volatile boolean browserHidden;
    private volatile String browserHiddenNote;
    /** M2.4: warp slots of this island ({@code null} = warps.start-slots). */
    private Integer warpSlots;

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
        updateArea();
    }

    private void updateArea() {
        int half = sizeChunks / 2;
        minX = IslandGrid.slotMin(gridX) + (IslandGrid.CENTER_CHUNK - half) * IslandGrid.CHUNK;
        minZ = IslandGrid.slotMin(gridZ) + (IslandGrid.CENTER_CHUNK - half) * IslandGrid.CHUNK;
        maxX = minX + sizeChunks * IslandGrid.CHUNK - 1;
        maxZ = minZ + sizeChunks * IslandGrid.CHUNK - 1;
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

    /** Solo → team is one-way (DESIGN §3.2): there is no way back. */
    void markTeam() {
        this.team = true;
    }

    /** New leader after {@code /is transfer} (the cached owner name is reset). */
    void owner(UUID owner) {
        this.owner = owner;
        this.ownerName = null;
    }

    /** Member Slots stored on the island ({@code null} = the start value from islands.yml). */
    public Integer memberSlots() {
        return memberSlots;
    }

    void memberSlots(Integer memberSlots) {
        this.memberSlots = memberSlots;
    }

    /** Locked: visitors can't enter (M2.4 {@code /is lock}). */
    public boolean locked() {
        return locked;
    }

    void locked(boolean locked) {
        this.locked = locked;
    }

    public boolean browserHidden() {
        return browserHidden;
    }

    /** The staff note of a hidden island ({@code null} = none). */
    public String browserHiddenNote() {
        return browserHiddenNote;
    }

    void browserHidden(boolean hidden, String note) {
        this.browserHidden = hidden;
        this.browserHiddenNote = hidden ? note : null;
    }

    /** Warp slots stored on the island ({@code null} = warps.start-slots from islands.yml). */
    public Integer warpSlots() {
        return warpSlots;
    }

    void warpSlots(Integer warpSlots) {
        this.warpSlots = warpSlots;
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
        updateArea();
    }

    /** True if block x/z is inside the border (allocation-free; same result as {@code bounds().contains(x, z)}). */
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxZ() {
        return maxZ;
    }

    /** Role permission overrides of this island (M2.2; edited by the M2.3 matrix GUI through IslandManager). */
    public IslandPerms perms() {
        return perms;
    }

    /** The island's own {@code /is settings} values (ISL2; changed through the settings manager only). */
    public IslandSettings settings() {
        return settings;
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

    /** Adds or updates a stored member with join time and inviter (coop status of the same player ends). */
    void putMember(UUID uuid, Role role, long joined, UUID inviter) {
        members.put(uuid, role);
        joinedAt.put(uuid, joined);
        if (inviter != null) {
            invitedBy.put(uuid, inviter);
        }
        coops.remove(uuid);
    }

    void removeMember(UUID uuid) {
        members.remove(uuid);
        joinedAt.remove(uuid);
        invitedBy.remove(uuid);
    }

    /** When {@code uuid} joined (epoch millis), or {@code createdAt} for rows without a time. */
    public long joinedAt(UUID uuid) {
        Long joined = joinedAt.get(uuid);
        return joined == null ? createdAt : joined;
    }

    /** Who invited {@code uuid}, or null. */
    public UUID invitedBy(UUID uuid) {
        return invitedBy.get(uuid);
    }

    public Set<UUID> coops() {
        return Collections.unmodifiableSet(coops);
    }

    void addCoop(UUID uuid) {
        coops.add(uuid);
    }

    boolean removeCoop(UUID uuid) {
        return coops.remove(uuid);
    }

    void clearCoops() {
        coops.clear();
    }

    /** Number of stored members (leader included; coop players never count). */
    public int memberCount() {
        return members.size();
    }

    public Optional<Role> role(UUID player) {
        return Optional.ofNullable(members.get(player));
    }

    /** The stored role of {@code player}, {@link Role#COOP} for coop players, or null (allocation-free, for protection). */
    Role roleOrNull(UUID player) {
        Role role = members.get(player);
        if (role == null && !coops.isEmpty() && coops.contains(player)) {
            return Role.COOP;
        }
        return role;
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
