package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.essentials.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * The island cache and every island action of M2.1 (main thread): creation, build, home, sethome, size, void rescue.
 * Published as {@link IslandService}; member actions (M2.3) live in {@link MemberManager}, which updates this cache only
 * after the database committed. All active islands are kept in memory (a few hundred bytes each) so "island at
 * location" is a map lookup; offline islands' chunks are never kept loaded.
 */
final class IslandManager implements IslandService {

    private final IslandsModule module;
    private final Core core;
    private final IslandsConfig config;
    private final ModuleMessages msg;
    private final IslandStore store;
    private final IslandBuilder builder;
    private final Executor main;
    // Written on the main thread only; concurrent maps because placeholders may be read from async chat/scoreboards.
    private final Map<UUID, Island> byId = new ConcurrentHashMap<>();
    private final Map<Long, Island> bySlot = new ConcurrentHashMap<>();
    private final Map<UUID, Island> byMember = new ConcurrentHashMap<>();
    /** State of every slot ever handed out (M2.7: admin overview, tab completion of slot ids). Slot id = spiral index. */
    private final Map<Integer, SlotState> slotStates = new ConcurrentHashMap<>();
    /** O(1) allocation-free "island at block" for protection (main thread only). */
    private final Territory territory = new Territory();
    private RolePermissions rolePermissions = RolePermissions.none();
    /** ISL2: server defaults of {@code /is settings}. */
    private FlagDefaults flagDefaults = FlagDefaults.owner();
    /** ISL2: called (main thread) after an island setting changed, e.g. to re-send weather/time or re-check fly. */
    private final List<java.util.function.BiConsumer<Island, IslandFlag>> flagHooks = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** ISL2 mastery hooks (masteries module later). */
    private java.util.function.Predicate<UUID> wingsProvider;
    private java.util.function.ToIntBiFunction<UUID, UUID> extraHomesProvider;
    /** The islands world once seen (identity check in hot paths), cleared when another world object shows up. */
    private World islandWorld;
    /** Players whose island is being created or rebuilt right now (one at a time per player). */
    private final Set<UUID> building = new HashSet<>();
    private final Set<UUID> rescuing = new HashSet<>();
    private IslandBorders borders;
    private MemberManager members;
    /** Called after an island left every cache (M2.4: visiting and warp caches drop it too). */
    private final List<java.util.function.Consumer<Island>> removalHooks = new java.util.concurrent.CopyOnWriteArrayList<>();

    IslandManager(IslandsModule module, IslandStore store) {
        this.module = module;
        this.core = module.core();
        this.config = module.config();
        this.msg = module.msg();
        this.store = store;
        this.builder = new IslandBuilder(module);
        this.main = new MainThread(core.plugin());
    }

    void borders(IslandBorders borders) {
        this.borders = borders;
    }

    void members(MemberManager members) {
        this.members = members;
    }

    MemberManager memberManager() {
        return members;
    }

    IslandsModule module() {
        return module;
    }

    IslandStore store() {
        return store;
    }

    Core core() {
        return core;
    }

    IslandBuilder builder() {
        return builder;
    }

    /** Loads every active island (blocking, enable only). */
    void load() {
        byId.clear();
        bySlot.clear();
        byMember.clear();
        territory.clear();
        slotStates.clear();
        for (IslandStore.SlotRow row : store.slotsBlocking()) {
            slotStates.put(row.id(), SlotState.byKey(row.state()).orElse(SlotState.CLEARING));
        }
        for (Island island : store.loadAllBlocking(config.world)) {
            index(island);
        }
        core.plugin().getLogger().info("Islands: " + byId.size() + " island(s) loaded.");
    }

    private void index(Island island) {
        byId.put(island.id(), island);
        bySlot.put(IslandGrid.key(island.gridX(), island.gridZ()), island);
        territory.put(island);
        island.members().keySet().forEach(member -> byMember.put(member, island));
        slotStates.put(island.slotId(), SlotState.USED);
    }

    /**
     * Removes an island from every cache (M2.7, after the database removed it): lookups, territory (protection now
     * treats its slot as void), member index and coop players. The slot becomes {@code clearing}.
     */
    void deindex(Island island) {
        byId.remove(island.id(), island);
        bySlot.remove(IslandGrid.key(island.gridX(), island.gridZ()), island);
        territory.remove(island);
        byMember.values().removeIf(value -> value == island);
        island.clearCoops();
        slotStates.put(island.slotId(), SlotState.CLEARING);
        removalHooks.forEach(hook -> hook.accept(island));
    }

    /** Runs {@code hook} for every island removed from the caches (disband, staff delete or reset). */
    void onRemoved(java.util.function.Consumer<Island> hook) {
        removalHooks.add(hook);
    }

    /** Updates the cached state of a slot (the clearer freed it). */
    void slotState(int slotId, SlotState state) {
        slotStates.put(slotId, state);
    }

    /** Cached state of a slot, or empty if it was never handed out. */
    Optional<SlotState> slotState(int slotId) {
        return Optional.ofNullable(slotStates.get(slotId));
    }

    /** Ids of every slot ever handed out, ascending (tab completion). */
    List<Integer> slotIds() {
        return slotStates.keySet().stream().sorted().toList();
    }

    /** Number of slots per state (admin overview). */
    Map<SlotState, Integer> slotCounts() {
        Map<SlotState, Integer> counts = new java.util.EnumMap<>(SlotState.class);
        for (SlotState state : SlotState.values()) {
            counts.put(state, 0);
        }
        slotStates.values().forEach(state -> counts.merge(state, 1, Integer::sum));
        return counts;
    }

    /** The active island in grid cell gx/gz, or null. */
    Island islandInCell(int gridX, int gridZ) {
        return territory.inCell(gridX, gridZ);
    }

    /** True while {@code player}'s island is being built (creation or rebuild). */
    boolean isBuilding(UUID player) {
        return building.contains(player);
    }

    // ------------------------------------------------------------------ lookups (internal, fast)

    @Override
    public boolean isIslandWorld(World world) {
        if (world == null) {
            return false;
        }
        if (world == islandWorld) {
            return true;
        }
        if (world.getName().equals(config.world)) {
            islandWorld = world;
            return true;
        }
        return false;
    }

    /** Island whose playable area contains block x/z of the islands world, or null (O(1), no allocation). */
    Island islandAt(int x, int z) {
        return territory.at(x, z);
    }

    /** Island whose slot contains block x/z, or null (O(1), no allocation). */
    Island islandInSlotAt(int x, int z) {
        return territory.inSlot(x, z);
    }

    Territory territory() {
        return territory;
    }

    // ------------------------------------------------------------------ role permissions (M2.2; GUI in M2.3)

    RolePermissions rolePermissions() {
        return rolePermissions;
    }

    void rolePermissions(RolePermissions rolePermissions) {
        this.rolePermissions = rolePermissions;
    }

    /** The permission role of {@code player} on {@code island}: their stored role, or visitor. */
    PermRole permRole(Island island, UUID player) {
        return PermRole.of(island.roleOrNull(player));
    }

    /** True if {@code player} (by role, no bypass) may do {@code perm} on {@code island}. */
    boolean roleAllows(Island island, UUID player, IslandPerm perm) {
        return rolePermissions.allows(island.perms(), permRole(island, player), perm);
    }

    /**
     * Sets ({@code TRUE}/{@code FALSE}) or resets ({@code null} = server default) one permission of a role on an
     * island: saved first (atomic), then cached and logged ({@code island_perm}). The leader can't be changed.
     * Used by the M2.3 permission matrix and the M2.5 visitor settings.
     *
     * @return false if the island is unknown, the role is the leader, or saving failed
     */
    CompletableFuture<Boolean> setRolePermission(UUID islandId, PermRole role, IslandPerm perm, Boolean allowed, Actor actor) {
        Island island = byId.get(islandId);
        if (island == null || !role.editable()) {
            return CompletableFuture.completedFuture(false);
        }
        Boolean before = island.perms().override(role, perm);
        return store.savePerm(islandId, role, perm, allowed).thenApplyAsync(ignored -> {
            if (byId.get(islandId) != island) {
                return false;
            }
            island.perms().set(role, perm, allowed);
            core.audit().log(AuditCategory.ISLAND, "island_perm", actor, island.id().toString(), displayName(island), null, null,
                    Map.of("role", role.key(), "perm", perm.key(), "old", overrideText(before), "new", overrideText(allowed)));
            return true;
        }, main).exceptionally(error -> {
            core.plugin().getLogger().log(Level.SEVERE, "Islands: saving permission " + perm.key() + " of " + islandId + " failed",
                    Database.cause(error));
            return false;
        });
    }

    // ------------------------------------------------------------------ island settings (ISL2)

    FlagDefaults flagDefaults() {
        return flagDefaults;
    }

    void flagDefaults(FlagDefaults flagDefaults) {
        this.flagDefaults = flagDefaults;
    }

    /** The effective value of a setting ({@link IslandFlag#VISITOR_PICKUP} = the visitor row's pickup permission). */
    String flag(Island island, IslandFlag flag) {
        if (flag.virtual()) {
            return rolePermissions.allows(island.perms(), PermRole.VISITOR, IslandPerm.PICKUP) ? IslandFlag.ON : IslandFlag.OFF;
        }
        return island.settings().value(flag, flagDefaults);
    }

    /** True if a toggle setting is on for {@code island} (allocation-free; used in protection listeners). */
    boolean flagOn(Island island, IslandFlag flag) {
        if (flag.virtual()) {
            return rolePermissions.allows(island.perms(), PermRole.VISITOR, IslandPerm.PICKUP);
        }
        return island.settings().on(flag, flagDefaults);
    }

    /** Runs {@code hook} after every saved setting change. */
    void onFlagChange(java.util.function.BiConsumer<Island, IslandFlag> hook) {
        flagHooks.add(hook);
    }

    /**
     * Sets one setting of an island to {@code value} (a value equal to the server default is stored as "default", so a
     * later default change reaches it): saved first (atomic), then cached, logged ({@code island_setting}) and the hooks
     * run. {@link IslandFlag#VISITOR_PICKUP} goes through {@link #setRolePermission} (logged as {@code island_perm}).
     *
     * @return false if the island is unknown, the value invalid, or saving failed
     */
    CompletableFuture<Boolean> setFlag(UUID islandId, IslandFlag flag, String value, Actor actor) {
        Island island = byId.get(islandId);
        String parsed = value == null ? null : flag.parse(value).orElse(null);
        if (island == null || parsed == null) {
            return CompletableFuture.completedFuture(false);
        }
        String before = flag(island, flag);
        if (flag.virtual()) {
            boolean allow = IslandFlag.ON.equals(parsed);
            Boolean override = allow == rolePermissions.defaultAllows(PermRole.VISITOR, IslandPerm.PICKUP) ? null : allow;
            return setRolePermission(islandId, PermRole.VISITOR, IslandPerm.PICKUP, override, actor).thenApply(ok -> {
                if (ok) {
                    flagHooks.forEach(hook -> hook.accept(island, flag));
                }
                return ok;
            });
        }
        String stored = parsed.equals(flagDefaults.value(flag)) ? null : parsed;
        return store.saveSetting(islandId, flag, stored).thenApplyAsync(ignored -> {
            if (byId.get(islandId) != island) {
                return false;
            }
            island.settings().set(flag, stored);
            core.audit().log(AuditCategory.ISLAND, "island_setting", actor, island.id().toString(), displayName(island), null, null,
                    Map.of("flag", flag.key(), "old", before, "new", parsed, "stored", stored == null ? "default" : stored));
            flagHooks.forEach(hook -> hook.accept(island, flag));
            return true;
        }, main).exceptionally(error -> {
            core.plugin().getLogger().log(Level.SEVERE, "Islands: saving setting " + flag.key() + " of " + islandId + " failed",
                    Database.cause(error));
            return false;
        });
    }

    private static String overrideText(Boolean value) {
        return value == null ? "default" : value ? "allow" : "deny";
    }

    Optional<Island> islandOfPlayer(UUID player) {
        return Optional.ofNullable(byMember.get(player));
    }

    /** The cached island by id, or null. */
    Island byId(UUID islandId) {
        return byId.get(islandId);
    }

    /** Every cached island (main thread; do not keep the view). */
    java.util.Collection<Island> islands() {
        return byId.values();
    }

    // ------------------------------------------------------------------ member cache (M2.3, after a commit only)

    void cacheJoin(Island island, UUID player, Role role, long joinedAt, UUID inviter) {
        island.putMember(player, role, joinedAt, inviter);
        byMember.put(player, island);
    }

    void cacheLeave(Island island, UUID player) {
        island.removeMember(player);
        byMember.remove(player, island);
    }

    void cacheRole(Island island, UUID player, Role role) {
        island.putMember(player, role);
    }

    void cacheTransfer(Island island, UUID oldLeader, UUID newLeader) {
        island.putMember(oldLeader, MemberRules.AFTER_TRANSFER);
        island.putMember(newLeader, Role.LEADER);
        island.owner(newLeader);
    }

    /**
     * A member's last known name (online name, server user cache), or "?" (main thread, no network). Accepts a profile
     * key (members, owners) or a player UUID.
     */
    String name(UUID player) {
        UUID person = person(player);
        Player online = Bukkit.getPlayer(person);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(person).getName();
        return name == null ? "?" : name;
    }

    // ------------------------------------------------------------------ profiles (PROF)

    /** Island profiles: membership, ownership, coop trust and personal homes are stored per profile key. */
    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    /** The member key (active profile key) of an online player. */
    UUID key(Player player) {
        return profiles().profileKey(player.getUniqueId());
    }

    /** The online player whose active profile is {@code memberKey}, or null (they are offline or on another profile). */
    Player online(UUID memberKey) {
        return memberKey == null ? null : profiles().onlinePlayer(memberKey).orElse(null);
    }

    /** The player (account) of a member key. */
    UUID person(UUID memberKey) {
        return profiles().ownerOf(memberKey);
    }

    /**
     * The key a service caller means: an online player's UUID becomes their active profile key; a stored member key or
     * an offline UUID is used as given.
     */
    UUID resolve(UUID playerOrKey) {
        return profiles().profileKey(playerOrKey);
    }

    /** The role on {@code island} of any profile of the player {@code person} (bans are per player), or null. */
    Role roleOfPlayer(Island island, UUID person) {
        for (Map.Entry<UUID, Role> e : island.members().entrySet()) {
            if (person(e.getKey()).equals(person)) {
                return e.getValue();
            }
        }
        for (UUID coop : island.coops()) {
            if (person(coop).equals(person)) {
                return Role.COOP;
            }
        }
        return null;
    }

    /**
     * Owner decision P3: the member key of another profile of the same player as {@code memberKey} that is a member or
     * coop of {@code island}, or was one ({@code history}: profile keys from the join history), or null.
     */
    UUID ownOtherProfileOn(Island island, UUID memberKey, java.util.Collection<UUID> history) {
        ProfileService ps = profiles();
        for (UUID member : island.members().keySet()) {
            if (ps.ownOtherProfile(member, memberKey)) {
                return member;
            }
        }
        for (UUID coop : island.coops()) {
            if (ps.ownOtherProfile(coop, memberKey)) {
                return coop;
            }
        }
        if (history != null) {
            for (UUID past : history) {
                if (ps.ownOtherProfile(past, memberKey)) {
                    return past;
                }
            }
        }
        return null;
    }

    /** Sends a player who no longer belongs on {@code island} (kick, leave) to the fallback spawn if they stand in its slot. */
    void sendOff(Player player, Island island) {
        Location at = player.getLocation();
        if (!isIslandWorld(at.getWorld()) || !island.slotBounds().contains(at.getBlockX(), at.getBlockZ())) {
            return;
        }
        toFallback(player, () -> player.setFallDistance(0));
    }

    int count() {
        return byId.size();
    }

    /** Re-sends (or removes) the border of players in the islands world after {@code island} changed or was removed. */
    void refreshBorders(Island island) {
        if (borders != null) {
            borders.refresh(island);
        }
    }

    /** The owner's name (cached on the island; main thread or tab completion). */
    String ownerName(Island island) {
        if (island.ownerName() == null) {
            island.ownerName(name(island.owner()));
        }
        return island.ownerName();
    }

    /** The island name shown to players: the custom name, or "&lt;owner&gt;'s Island". */
    String displayName(Island island) {
        if (island.customName() != null && !island.customName().isBlank()) {
            return island.customName();
        }
        if (island.ownerName() == null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(person(island.owner()));
            island.ownerName(owner.getName() == null ? "?" : owner.getName());
        }
        return module.raw("default-name").replace("<owner>", island.ownerName());
    }

    // ------------------------------------------------------------------ IslandService

    @Override
    public String worldName() {
        return config.world;
    }

    @Override
    public Optional<IslandInfo> islandAt(Location location) {
        if (location == null || !isIslandWorld(location.getWorld())) {
            return Optional.empty();
        }
        return Optional.ofNullable(islandAt(location.getBlockX(), location.getBlockZ())).map(this::info);
    }

    @Override
    public Optional<IslandInfo> islandInSlot(Location location) {
        if (location == null || !isIslandWorld(location.getWorld())) {
            return Optional.empty();
        }
        return Optional.ofNullable(islandInSlotAt(location.getBlockX(), location.getBlockZ())).map(this::info);
    }

    /** {@code player}: an online player's UUID (their active profile's island) or a profile key. */
    @Override
    public Optional<IslandInfo> islandOf(UUID player) {
        return islandOfPlayer(resolve(player)).map(this::info);
    }

    @Override
    public Optional<IslandInfo> islandOfProfile(UUID profileKey) {
        return islandOfPlayer(profileKey).map(this::info);
    }

    @Override
    public Optional<IslandInfo> island(UUID islandId) {
        return Optional.ofNullable(byId.get(islandId)).map(this::info);
    }

    @Override
    public Map<UUID, Role> members(UUID islandId) {
        Island island = byId.get(islandId);
        return island == null ? Map.of() : Map.copyOf(island.members());
    }

    @Override
    public Optional<Role> role(UUID islandId, UUID player) {
        Island island = byId.get(islandId);
        return island == null ? Optional.empty() : island.role(resolve(player));
    }

    @Override
    public Optional<Location> home(UUID islandId) {
        Island island = byId.get(islandId);
        return island == null ? Optional.empty() : island.home().map(h -> location(island, h));
    }

    @Override
    public boolean teleportHome(Player player) {
        Optional<Island> island = islandOfPlayer(key(player));
        if (island.isEmpty()) {
            msg.send(player, "no-island");
            core.sounds().play(player, "error");
            return false;
        }
        goHome(player, island.get(), false);
        return true;
    }

    @Override
    public CompletableFuture<Boolean> setSize(UUID islandId, int sizeChunks, Actor actor) {
        Island island = byId.get(islandId);
        if (island == null || !IslandGrid.validSize(sizeChunks) || sizeChunks > config.maxSize()) {
            return CompletableFuture.completedFuture(false);
        }
        int before = island.sizeChunks();
        return store.saveSize(islandId, sizeChunks).thenApplyAsync(saved -> {
            if (saved) {
                island.sizeChunks(sizeChunks);
                core.audit().log(AuditCategory.ISLAND, "island_size", actor, island.id().toString(), displayName(island), null, null,
                        Map.of("old", before, "new", sizeChunks));
                if (borders != null) {
                    borders.refresh(island);
                }
            }
            return saved;
        }, main);
    }

    /** M3.3 bank rights and any other role permission by key; {@code player}: member key or online player's UUID. */
    @Override
    public boolean hasIslandPermission(UUID islandId, UUID player, String permissionKey) {
        Island island = islandId == null ? null : byId.get(islandId);
        if (island == null || player == null) {
            return false;
        }
        return IslandPerm.byKey(permissionKey).map(perm -> roleAllows(island, resolve(player), perm)).orElse(false);
    }

    @Override
    public java.util.Set<UUID> coops(UUID islandId) {
        Island island = byId.get(islandId);
        return island == null ? java.util.Set.of() : java.util.Set.copyOf(island.coops());
    }

    @Override
    public boolean isCoop(UUID islandId, UUID player) {
        Island island = byId.get(islandId);
        return island != null && island.coops().contains(resolve(player));
    }

    @Override
    public int memberSlots(UUID islandId) {
        Island island = byId.get(islandId);
        return island == null ? 0 : slots(island);
    }

    /** Effective member slots of a cached island. */
    int slots(Island island) {
        return MemberRules.slots(island.memberSlots(), Math.min(config.startSlots, config.maxSlots), config.maxSlots);
    }

    @Override
    public CompletableFuture<Boolean> setMemberSlots(UUID islandId, int slots, Actor actor) {
        return members.setMemberSlots(islandId, slots, actor);
    }

    /** {@code player}: a member key, or a player UUID (then their profile that is on this island). */
    @Override
    public CompletableFuture<Boolean> removeMember(UUID islandId, UUID player, String reason, Actor actor) {
        Island island = byId.get(islandId);
        UUID key = player;
        if (island != null && island.roleOrNull(player) == null && !island.coops().contains(player)) {
            for (UUID member : island.members().keySet()) {
                if (person(member).equals(player)) {
                    key = member;
                }
            }
            for (UUID coop : island.coops()) {
                if (person(coop).equals(player)) {
                    key = coop;
                }
            }
        }
        return members.removeByService(islandId, key, reason, actor);
    }

    @Override
    public CompletableFuture<Boolean> blockJoining(UUID player, long untilEpochMillis, String reason, Actor actor) {
        return members.blockJoining(player, untilEpochMillis, reason, actor);
    }

    @Override
    public CompletableFuture<Optional<Long>> joinBlockedUntil(UUID player) {
        return members.joinBlockedUntil(player);
    }

    // ------------------------------------------------------------------ moderation hooks (Phase P7, spec 13.2)

    @Override
    public boolean supportsModeration() {
        return module.visits() != null;
    }

    @Override
    public CompletableFuture<Boolean> banFromIsland(UUID islandId, UUID player, long untilEpochMillis, String reason,
                                                    Actor actor, String reference) {
        return core.scheduler().supplyMain("island-staff-ban", () -> byId.get(islandId)).thenCompose(island ->
                island == null || module.visits() == null ? CompletableFuture.completedFuture(false)
                        : module.visits().staffBan(island, player, untilEpochMillis, reason, actor, reference));
    }

    @Override
    public CompletableFuture<Boolean> liftIslandBan(String reference, Actor actor) {
        return module.visits() == null || reference == null ? CompletableFuture.completedFuture(false)
                : module.visits().liftStaffBan(reference, actor);
    }

    @Override
    public boolean expelFromIsland(UUID islandId, UUID player, String reason, Actor actor) {
        Island island = byId.get(islandId);
        return island != null && module.visits() != null && module.visits().staffKick(island, player, reason, actor);
    }

    // ------------------------------------------------------------------ mastery hooks (ISL2)

    @Override
    public boolean hasWings(UUID islandId) {
        java.util.function.Predicate<UUID> provider = wingsProvider;
        return provider != null && islandId != null && provider.test(islandId);
    }

    @Override
    public void wingsProvider(java.util.function.Predicate<UUID> provider) {
        this.wingsProvider = provider;
    }

    @Override
    public int extraMemberHomes(UUID islandId, UUID player) {
        java.util.function.ToIntBiFunction<UUID, UUID> provider = extraHomesProvider;
        return provider == null ? 0 : Math.max(0, provider.applyAsInt(islandId, player));
    }

    @Override
    public void extraHomesProvider(java.util.function.ToIntBiFunction<UUID, UUID> provider) {
        this.extraHomesProvider = provider;
    }

    private IslandInfo info(Island island) {
        return island.info(displayName(island), config.maxSize());
    }

    static Location location(Island island, Island.Home home) {
        return new Location(Bukkit.getWorld(island.world()), home.x(), home.y(), home.z(), home.yaw(), home.pitch());
    }

    // ------------------------------------------------------------------ creation

    /** {@code /is}: home if the player has an island, otherwise the "Create your island" confirm menu. */
    void openOrCreate(Player player) {
        Optional<Island> own = islandOfPlayer(key(player));
        if (own.isPresent()) {
            module.hub().open(player, own.get()); // M2.5: the island menu (home is one click there, or /is home)
            return;
        }
        if (building.contains(key(player))) {
            msg.send(player, "create.busy");
            return;
        }
        if (!worldLoaded(player)) {
            return;
        }
        UUID id = key(player);
        store.lastDisband(id).thenAcceptAsync(last -> {
            if (!player.isOnline()) {
                return;
            }
            long remaining = cooldownRemaining(last.orElse(null), cooldownMillis(), System.currentTimeMillis());
            if (remaining > 0) {
                sendCooldown(player, remaining);
                return;
            }
            core.menus().open(player, confirmMenu(player));
        }, main).exceptionally(error -> databaseError(player, "lastDisband", error));
    }

    private ConfirmMenu confirmMenu(Player player) {
        int size = config.startSize();
        return new ConfirmMenu(
                viewer -> module.text(viewer, "create.menu.crumb"),
                core.menus().item(Material.GRASS_BLOCK, player, IslandsModule.ID, "create.menu.shown",
                        Msg.p("size", size), Msg.p("blocks", size * IslandGrid.CHUNK)),
                viewer -> module.lines(viewer, "create.menu.details", Msg.p("size", size), Msg.p("blocks", size * IslandGrid.CHUNK),
                        Msg.p("max", config.maxSize())),
                this::create,
                viewer -> msg.send(viewer, "create.cancelled"));
    }

    /** Creating needs the islands world; tells the player (and keeps the database untouched) when it is missing. */
    private boolean worldLoaded(Player player) {
        if (Bukkit.getWorld(config.world) != null) {
            return true;
        }
        fail(player, "error.world", Msg.p("world", config.world));
        return false;
    }

    long cooldownMillis() {
        return Duration.ofHours(config.creationCooldownHours).toMillis();
    }

    /** Milliseconds left of the creation cooldown after a disband at {@code lastDisband} (0 = ready). Pure. */
    static long cooldownRemaining(Long lastDisband, long cooldownMillis, long now) {
        if (lastDisband == null || cooldownMillis <= 0) {
            return 0;
        }
        return Math.max(0, lastDisband + cooldownMillis - now);
    }

    private void sendCooldown(Player player, long remainingMillis) {
        msg.send(player, "create.cooldown", Msg.p("time", Format.duration(Duration.ofMillis(remainingMillis))));
        core.sounds().play(player, "error");
    }

    /** Confirmed: saves the island (one transaction), builds the islet, teleports. */
    void create(Player player) {
        UUID id = key(player);
        if (islandOfPlayer(id).isPresent()) {
            goHome(player, islandOfPlayer(id).get(), false);
            return;
        }
        if (!worldLoaded(player)) {
            return;
        }
        if (!building.add(id)) {
            msg.send(player, "create.busy");
            return;
        }
        // Phase P7 (spec 13.2 coop-block): a staff join block also stops creating islands (account-wide)
        members.joinBlockedUntil(player.getUniqueId()).whenCompleteAsync((blocked, blockError) -> {
            if (blockError == null && blocked.isPresent() && blocked.get() > System.currentTimeMillis()) {
                building.remove(id);
                if (blocked.get() == Long.MAX_VALUE) {
                    msg.send(player, "create.blocked-perm");
                } else {
                    msg.send(player, "create.blocked", Msg.p("time", Format.duration(java.time.Duration.ofMillis(
                            blocked.get() - System.currentTimeMillis()))));
                }
                core.sounds().play(player, "error");
                return;
            }
            createNow(player, id);
        }, main);
    }

    private void createNow(Player player, UUID id) {
        msg.send(player, "create.started");
        store.create(id, config.seasonId, config.startSize(), cooldownMillis(), config.world).whenCompleteAsync((result, error) -> {
            if (error != null) {
                building.remove(id);
                databaseError(player, "create", error);
                return;
            }
            switch (result) {
                case IslandStore.CreateResult.AlreadyHasIsland ignored -> {
                    building.remove(id);
                    msg.send(player, "create.already");
                }
                case IslandStore.CreateResult.Cooldown cooldown -> {
                    building.remove(id);
                    sendCooldown(player, cooldown.remainingMillis());
                }
                case IslandStore.CreateResult.Created created -> {
                    Island island = created.island();
                    island.ownerName(player.getName());
                    index(island);
                    core.audit().log(AuditCategory.ISLAND, "island_create", Actor.player(player), island.id().toString(),
                            displayName(island), null, new Location(Bukkit.getWorld(island.world()), island.centerX(),
                                    config.surfaceY, island.centerZ()),
                            Map.of("slot", island.slotId(), "grid_x", island.gridX(), "grid_z", island.gridZ(),
                                    "size", island.sizeChunks(), "season", island.seasonId()));
                    buildAndArrive(id, island, true);
                }
            }
        }, main);
    }

    /**
     * Staff reset (M2.7): a new island for {@code owner} right away (no creation cooldown). Built and entered at once when
     * the owner is online; otherwise built the next time they use {@code /is} (offline islands are never loaded).
     */
    CompletableFuture<IslandStore.CreateResult> createFor(UUID owner, Actor actor) {
        return store.create(owner, config.seasonId, config.startSize(), 0, config.world).thenApplyAsync(result -> {
            if (result instanceof IslandStore.CreateResult.Created created) {
                Island island = created.island();
                island.ownerName(name(owner));
                index(island);
                core.audit().log(AuditCategory.ISLAND, "island_create", actor, island.id().toString(), displayName(island), null,
                        new Location(Bukkit.getWorld(island.world()), island.centerX(), config.surfaceY, island.centerZ()),
                        Map.of("slot", island.slotId(), "grid_x", island.gridX(), "grid_z", island.gridZ(),
                                "size", island.sizeChunks(), "season", island.seasonId(), "reset", true));
                if (online(owner) != null && !building.contains(owner)) {
                    buildAndArrive(owner, island, true);
                }
            }
            return result;
        }, main);
    }

    /** Builds (or rebuilds after a crash) the starter islet, saves the home and brings {@code playerId} there. */
    void buildAndArrive(UUID playerId, Island island, boolean created) {
        building.add(playerId);
        builder.build(island).whenCompleteAsync((build, error) -> {
            building.remove(playerId);
            Player player = online(playerId);
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Islands: building island " + island.id() + " failed", error);
                core.audit().log(AuditCategory.ISLAND, "island_build_failed", Actor.system("islands"), island.id().toString(),
                        displayName(island), null, null, Map.of("error", String.valueOf(Database.cause(error).getMessage())));
                if (player != null) {
                    msg.send(player, "create.build-failed");
                    core.sounds().play(player, "error");
                }
                return;
            }
            island.home(build.home());
            store.saveHome(island.id(), build.home()).exceptionally(saveError -> {
                core.plugin().getLogger().log(Level.SEVERE, "Islands: saving the home of " + island.id() + " failed", saveError);
                return null;
            });
            core.audit().log(AuditCategory.ISLAND, "island_build", Actor.system("islands"), island.id().toString(),
                    displayName(island), null, location(island, build.home()),
                    Map.of("source", build.source(), "starter_chest", build.chestItems(), "rebuild", !created));
            if (borders != null) {
                borders.refresh(island);
            }
            if (player != null) {
                teleport(player, location(island, build.home()), true, () -> arrived(player, created));
            }
        }, main);
    }

    private void arrived(Player player, boolean created) {
        if (created) {
            msg.title(player, "create.title");
            core.sounds().play(player, "unlock");
            msg.send(player, "create.welcome");
            msg.send(player, "create.objective");
        } else {
            msg.actionBar(player, "home.arrived");
        }
    }

    // ------------------------------------------------------------------ home

    /** Teleports to the island home after a safe-spot check (rebuilds a never-finished island first). */
    void goHome(Player player, Island island, boolean instant) {
        if (island.home().isEmpty()) {
            if (building.contains(key(player))) {
                msg.send(player, "create.busy");
                return;
            }
            msg.send(player, "home.rebuilding");
            buildAndArrive(key(player), island, true);
            return;
        }
        Location home = location(island, island.home().get());
        if (home.getWorld() == null) {
            msg.send(player, "error.world", Msg.p("world", island.world()));
            return;
        }
        home.getWorld().getChunkAtAsync(home).thenAcceptAsync(chunk -> {
            if (!player.isOnline()) {
                return;
            }
            Location target = safeHome(player, island, home);
            teleport(player, target, instant, () -> arrived(player, false));
        }, main);
    }

    /** {@code home} if it is safe; otherwise the nearest safe spot, the top of the center column, or a rescue block. */
    Location safeHome(Player player, Island island, Location home) {
        if (SafeSpots.isSafe(home)) {
            return home;
        }
        Optional<Location> near = SafeSpots.nearest(home, island.bounds(), config.safeSearchRadius, config.minBuildY, config.maxBuildY);
        if (near.isPresent()) {
            msg.send(player, "home.moved-safe");
            return near.get();
        }
        World world = home.getWorld();
        Optional<SafeSpot.Spot> top = SafeSpot.highestIn(SafeSpots.column(world), island.centerX(), island.centerZ(),
                config.minBuildY, config.maxBuildY);
        if (top.isPresent()) {
            msg.send(player, "home.moved-safe");
            return new Location(world, top.get().x() + 0.5, top.get().y(), top.get().z() + 0.5, home.getYaw(), home.getPitch());
        }
        Block below = home.getBlock().getRelative(0, -1, 0);
        Material rescue = Material.matchMaterial(config.rescueBlock);
        if (rescue != null && rescue.isBlock() && rescue.isSolid() && below.getType().isAir() && island.bounds().contains(below.getLocation())
                && SafeSpots.classify(home.getBlock().getType()) == SafeSpot.Kind.PASSABLE) {
            below.setType(rescue);
            core.audit().log(AuditCategory.ISLAND, "island_rescue_block", Actor.player(player), island.id().toString(),
                    displayName(island), null, below.getLocation(), Map.of("block", rescue.getKey().toString()));
            msg.send(player, "home.rescued");
        }
        return home;
    }

    /** {@code /is sethome}: only inside the own island's border, on a safe spot, within the build height. */
    void setHome(Player player) {
        Optional<Island> own = islandOfPlayer(key(player));
        if (own.isEmpty()) {
            msg.send(player, "no-island");
            core.sounds().play(player, "error");
            return;
        }
        Island island = own.get();
        // M2.3: Leader + Mod move the home.
        if (!MemberRules.canSetHome(island.roleOrNull(key(player)))) {
            fail(player, "sethome.not-allowed");
            return;
        }
        Location at = player.getLocation();
        if (!island.bounds().contains(at)) {
            fail(player, "sethome.outside");
            return;
        }
        if (at.getY() < config.minBuildY || at.getY() > config.maxBuildY) {
            fail(player, "sethome.height", Msg.p("min", config.minBuildY), Msg.p("max", config.maxBuildY));
            return;
        }
        if (!SafeSpots.isSafe(at)) {
            fail(player, "sethome.unsafe");
            return;
        }
        Island.Home before = island.home().orElse(null);
        Island.Home home = new Island.Home(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        store.saveHome(island.id(), home).whenCompleteAsync((ignored, error) -> {
            if (error != null) {
                databaseError(player, "sethome", error);
                return;
            }
            island.home(home);
            core.audit().log(AuditCategory.ISLAND, "island_sethome", Actor.player(player), island.id().toString(), displayName(island),
                    null, at, Map.of("old", before == null ? "-" : format(before), "new", format(home)));
            msg.send(player, "sethome.done");
            core.sounds().play(player, "success");
        }, main);
    }

    private static String format(Island.Home home) {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f %.0f", home.x(), home.y(), home.z(), home.yaw());
    }

    // ------------------------------------------------------------------ void rescue

    /** Brings a player who fell into the void back: the island they are over, their own island, or the fallback world. */
    void rescueFromVoid(Player player) {
        if (!rescuing.add(player.getUniqueId())) {
            return;
        }
        player.setFallDistance(0);
        Location at = player.getLocation();
        Island here = islandInSlotAt(at.getBlockX(), at.getBlockZ());
        Island target = here != null && here.home().isPresent() ? here : islandOfPlayer(key(player))
                .filter(i -> i.home().isPresent()).orElse(null);
        Runnable done = () -> {
            rescuing.remove(player.getUniqueId());
            player.setFallDistance(0);
            msg.actionBar(player, "void.rescued");
        };
        if (target != null) {
            Location home = location(target, target.home().get());
            home.getWorld().getChunkAtAsync(home).thenAcceptAsync(chunk -> {
                Location safe = SafeSpots.isSafe(home) ? home : SafeSpots.nearest(home, target.bounds(), config.safeSearchRadius,
                        config.minBuildY, config.maxBuildY).orElse(null);
                if (safe == null) {
                    toFallback(player, done);
                    return;
                }
                player.teleportAsync(safe, PlayerTeleportEvent.TeleportCause.PLUGIN).whenCompleteAsync((ok, e) -> done.run(), main);
            }, main);
            return;
        }
        toFallback(player, done);
    }

    void toFallback(Player player, Runnable done) {
        World fallback = Bukkit.getWorld(config.fallbackWorld);
        if (fallback == null) {
            fallback = Bukkit.getWorlds().getFirst();
        }
        player.teleportAsync(fallback.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN)
                .whenCompleteAsync((ok, e) -> done.run(), main);
    }

    void forget(UUID player) {
        rescuing.remove(player);
    }

    // ------------------------------------------------------------------ helpers

    /** Teleport through the essentials TeleportService (guards, warmup, /back) when present. */
    void teleport(Player player, Location target, boolean instant, Runnable onArrive) {
        String destination = module.raw("home.destination");
        player.setFallDistance(0);
        Optional<TeleportService> teleports = core.services().optional(TeleportService.class);
        if (teleports.isPresent()) {
            if (instant) {
                teleports.get().now(player, target, destination, onArrive);
            } else {
                teleports.get().teleport(player, target, config.homeWarmupSeconds, destination, onArrive);
            }
            return;
        }
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.COMMAND).thenAcceptAsync(ok -> {
            if (ok) {
                core.sounds().play(player, "teleport");
                onArrive.run();
            } else {
                msg.send(player, "error.teleport");
            }
        }, main);
    }

    void fail(Player player, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        msg.send(player, key, placeholders);
        core.sounds().play(player, "error");
    }

    Void databaseError(Player player, String what, Throwable error) {
        core.plugin().getLogger().log(Level.SEVERE, "Islands: database error during " + what, Database.cause(error));
        core.plugin().getServer().getScheduler().runTask(core.plugin(), () -> {
            if (player.isOnline()) {
                msg.send(player, "error.database");
                core.sounds().play(player, "error");
            }
        });
        return null;
    }

    /** Admin hook (M2.7 {@code /isadmin slots}): every slot row. */
    CompletableFuture<List<IslandStore.SlotRow>> slots() {
        return store.slots();
    }
}
