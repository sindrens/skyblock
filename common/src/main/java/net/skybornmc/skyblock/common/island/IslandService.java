package net.skybornmc.skyblock.common.island;

import net.skybornmc.skyblock.common.audit.Actor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Island roles, highest first (owner decisions: Leader, Admin (ISL2, between Leader and Mod), Mod, Member, Coop).
     * Coop is temporary and never a stored member (see {@link #coops}). After a transfer the old leader becomes Admin.
     */
    enum Role { LEADER, ADMIN, MOD, MEMBER, COOP }

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

    /**
     * The island of one profile, by its profile key exactly as stored (never resolved to the player's active profile).
     * Use this for a player's inactive profiles (e.g. the {@code /profiles} menu).
     */
    default Optional<IslandInfo> islandOfProfile(UUID profileKey) {
        return islandOf(profileKey);
    }

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

    /** What {@link #enterFromPortal} did. */
    enum PortalEntry {
        /** The player has an island: the (instant) home teleport started. */
        HOME,
        /** The player had no island: it is being created (no menu) and they are brought there when it is built. */
        CREATING,
        /** Their island is still being built from an earlier request; nothing new started (the player was told). */
        BUSY,
        /** Nothing started (islands world missing, staff join block...); the player was told why. */
        REFUSED
    }

    /**
     * The island portal at spawn (owner answer 159): with an island on the active profile it teleports the player home
     * (instant, safe-spot check); without one it creates the island right away (the normal {@code /is create} flow with
     * the applied starter schematic, no confirm menu, a short "Creating your island" title) and brings them there.
     * {@code onArrive} runs on the main thread once the player is on the island, with {@code true} for a new island; it
     * never runs if the teleport or creation fails. Main thread.
     */
    default PortalEntry enterFromPortal(Player player, java.util.function.Consumer<Boolean> onArrive) {
        return PortalEntry.REFUSED;
    }

    /**
     * Admin/upgrade hook (Phase 5 Island Size, M2.7 /isadmin setsize): sets the border width in chunks (odd, between the
     * start size and the max). Saved, logged (category island) and shown to players on the island right away.
     *
     * @return false if the island is unknown or the size is invalid
     */
    CompletableFuture<Boolean> setSize(UUID islandId, int sizeChunks, Actor actor);

    // ------------------------------------------------------------------ upgrade hooks read by the stacking module

    /**
     * Upgrade hook ("Spawner Stack Limit" mastery): the most spawners one stacked spawner block may hold on this island.
     * Empty = the start value from {@code stacking.yml} ({@code spawners.stack-start}, 8); the stacking module clamps any
     * value to {@code spawners.stack-max} (512). Called on the main thread when a spawner is placed or added to.
     */
    default java.util.OptionalInt spawnerStackLimit(UUID islandId) {
        return java.util.OptionalInt.empty();
    }

    /** Upgrade hook ("Spawner Rate" mastery): multiplier on every spawner's output on this island (1.0 = no upgrade). */
    default double spawnerRateMultiplier(UUID islandId) {
        return 1.0;
    }

    /**
     * Island Fuel hook: true while the island has fuel. Spawners then also run while no member is online (otherwise
     * only with a player within the activation range). False until Island Fuel exists.
     */
    default boolean hasIslandFuel(UUID islandId) {
        return false;
    }

    /**
     * Island Fuel hook (AFK pool rewards, later the island shop): adds {@code minutes} of fuel to the island's tank
     * (saved, logged, clamped to the tank size by the implementation). Completes with false while Island Fuel does not
     * exist yet or the island is unknown; callers that must not lose the fuel keep it and try again later.
     */
    default CompletableFuture<Boolean> addIslandFuel(UUID islandId, long minutes, String reason, Actor actor) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Upgrade hook: extra percent on the island entity limits of {@code stacking.yml} (armor stands, item frames,
     * minecarts, boats, villagers, displays). 0 = no upgrade; the owner's upgrade gives +50.
     */
    default int entityLimitBonusPercent(UUID islandId) {
        return 0;
    }

    // ------------------------------------------------------------------ island bank (M3.3)

    /** Island permission key: put money into the island bank. */
    String PERM_BANK_DEPOSIT = "bank-deposit";
    /** Island permission key: take money out of the island bank. */
    String PERM_BANK_WITHDRAW = "bank-withdraw";
    /** Island permission key: see the island bank history (who put in and took out what). */
    String PERM_BANK_HISTORY = "bank-history";

    /**
     * True if {@code player} (a member key or an online player's UUID) has the island permission {@code permissionKey}
     * (e.g. {@link #PERM_BANK_WITHDRAW}) on the island through their role. The leader has every permission; coop players and
     * visitors never have management rights such as the bank ones. False for unknown islands/keys. Main thread.
     */
    default boolean hasIslandPermission(UUID islandId, UUID player, String permissionKey) {
        return false;
    }

    /** Upgrade hook (Phase 5 "Bank Capacity"): the island's level, 0 until upgrades exist. Main thread. */
    default int bankCapacityLevel(UUID islandId) {
        return 0;
    }

    /** Upgrade hook (Phase 5 "Bank Interest"): the island's level, 0 until upgrades exist. Main thread. */
    default int bankInterestLevel(UUID islandId) {
        return 0;
    }

    // ------------------------------------------------------------------ members (M2.3)

    /**
     * Coop players of the island (temporary trust, REVIEW A4): they build with the coop role permissions only, earn the
     * island no XP or task progress, have no bank access and never take a member slot. Removed automatically when no
     * leader or mod of the island is online. Empty for an unknown island.
     */
    Set<UUID> coops(UUID islandId);

    /** True if {@code player} is a coop player (not a stored member) of the island. */
    default boolean isCoop(UUID islandId, UUID player) {
        return coops(islandId).contains(player);
    }

    /**
     * True if XP, task progress and bank access earned/used by {@code player} should count for the island: stored
     * members only, never coop players (REVIEW A4). XP a member earned stays with the island when they leave (A5, M2.6).
     */
    default boolean countsForIsland(UUID islandId, UUID player) {
        return isMember(islandId, player);
    }

    /** Member slots of the island (leader included; start value until the Member Slots upgrade). 0 for an unknown island. */
    int memberSlots(UUID islandId);

    /**
     * Upgrade/admin hook (Phase 5 Member Slots): sets the island's member slots (1 to {@code members.max-slots}).
     * Saved and logged ({@code island_member_slots}). Existing members are never removed when lowered.
     */
    CompletableFuture<Boolean> setMemberSlots(UUID islandId, int slots, Actor actor);

    /**
     * Moderation hook (Phase P7): removes a stored member (never the leader) or a coop player from the island. The player
     * is told (with {@code reason} when not blank), online members are notified, the join history records the leave and
     * the change is logged ({@code island_kick} with {@code reason}, or {@code island_coop} for coop players).
     *
     * @return false if the player was neither a member nor a coop player, is the leader, or saving failed
     */
    CompletableFuture<Boolean> removeMember(UUID islandId, UUID player, String reason, Actor actor);

    /**
     * Moderation hook (Phase P7): {@code player} can't accept island invites or be cooped until {@code untilEpochMillis}
     * (0 or a past time lifts the block). Current coop trust is removed right away. Saved and logged
     * ({@code island_join_block}).
     */
    CompletableFuture<Boolean> blockJoining(UUID player, long untilEpochMillis, String reason, Actor actor);

    /** {@link #blockJoining(UUID, long, String, Actor)} by the system without a reason. */
    default CompletableFuture<Boolean> blockJoining(UUID player, long untilEpochMillis) {
        return blockJoining(player, untilEpochMillis, "", Actor.SYSTEM);
    }

    /** End of the player's join block (epoch millis), if one is active. */
    CompletableFuture<Optional<Long>> joinBlockedUntil(UUID player);

    /**
     * Moderation hook (Phase P7, spec 13.2 {@code island-ban}): {@code player} (player UUID, every profile) can't enter
     * this one island until {@code untilEpochMillis} ({@link Long#MAX_VALUE} = until lifted), even as a member. Staff with
     * the visit bypass still can. A player on the island right now is sent off it. Saved with {@code reference} (e.g.
     * {@code punishment:12}) so {@link #liftIslandBan} can end exactly this ban; logged ({@code island_staff_ban}).
     *
     * @return false if the island is unknown or saving failed
     */
    default CompletableFuture<Boolean> banFromIsland(UUID islandId, UUID player, long untilEpochMillis, String reason,
                                                     Actor actor, String reference) {
        return CompletableFuture.completedFuture(false);
    }

    /** Ends the island ban saved with {@code reference} (undo/pardon). Logged ({@code island_staff_unban}). */
    default CompletableFuture<Boolean> liftIslandBan(String reference, Actor actor) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Moderation hook (Phase P7, spec 13.2 {@code island-kick}): sends {@code player} (player UUID) off this island now if
     * they are on it (they may come back). Logged ({@code island_staff_kick}). Main thread.
     *
     * @return true if the player was on the island and was sent off
     */
    default boolean expelFromIsland(UUID islandId, UUID player, String reason, Actor actor) {
        return false;
    }

    /** True if the islands module supports the P7 moderation hooks above (island ban and island kick). */
    default boolean supportsModeration() {
        return false;
    }

    // ------------------------------------------------------------------ mastery hooks (ISL2; filled by the masteries later)

    /**
     * True if the island has the "Wings" mastery: everyone on it may fly inside its border (visitors only when the
     * island's {@code visitor-fly} setting is on), like players with {@code skyblock.island.fly}. False until a provider
     * is set ({@link #wingsProvider}).
     */
    boolean hasWings(UUID islandId);

    /** Masteries module hook: decides {@link #hasWings} per island id ({@code null} = nobody has Wings). Main thread. */
    void wingsProvider(java.util.function.Predicate<UUID> provider);

    /**
     * Extra personal homes ({@code /is setmyhome}) a member has on the island on top of {@code homes.per-member} (1),
     * e.g. from a mastery. 0 until a provider is set ({@link #extraHomesProvider}).
     */
    int extraMemberHomes(UUID islandId, UUID player);

    /** Masteries module hook for {@link #extraMemberHomes} ({@code null} = none). Main thread. */
    void extraHomesProvider(java.util.function.ToIntBiFunction<UUID, UUID> provider);

    /**
     * The island's bank-fee mastery level (BANK2, owner #169): every level lowers the bank withdrawal fee one step
     * ({@code bank.yml → withdraw.mastery-fee-percent}: 2% → 1.5% → 1.0% → 0.5%) for the island bank and for the personal
     * banks of its members. 0 until {@code /is masteries} sets a provider ({@link #bankFeeMasteryProvider}). Main thread.
     */
    default int bankFeeMasteryLevel(UUID islandId) {
        return 0;
    }

    /** Masteries module hook for {@link #bankFeeMasteryLevel} ({@code null} = level 0 everywhere). Main thread. */
    default void bankFeeMasteryProvider(java.util.function.ToIntFunction<UUID> provider) {
    }
}
