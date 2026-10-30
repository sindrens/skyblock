package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.essentials.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.MetadataValue;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * M2.4 visiting (main thread): {@code /is visit}, lock/unlock, expel, ban/unban, rate, like, the visit browser data
 * ({@link Showcase} stats cache), entry enforcement (teleports, ender pearls, flying in, joining inside) and the member
 * notifications (visit attempt while locked, arrival, departure; each a {@code /settings} toggle per member and, since
 * ISL2, the island's {@code notifications} setting). Every change is
 * saved first ({@link VisitStore}), then cached, logged (category island) and announced.
 */
final class VisitManager implements Listener {

    static final String PERM_VISIT_BYPASS = "skyblock.island.visit.bypass";
    static final String SETTING_ATTEMPTS = "island-visit-attempts";
    static final String SETTING_ARRIVALS = "island-visitor-arrivals";
    static final String SETTING_DEPARTURES = "island-visitor-departures";
    /** Stars of {@code /is rate} (plan: 1-5). */
    static final int MAX_STARS = 5;

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final IslandProtection protection;
    private final ModuleMessages msg;
    private final VisitStore store;
    private final Executor main;
    /** Bans per island (main thread writes; read by teleport guards and tab completion). */
    private final Map<UUID, Set<UUID>> bans = new ConcurrentHashMap<>();
    /** The island slot each online player stands in (for arrival/departure). */
    private final Map<UUID, UUID> presence = new HashMap<>();
    /** "visitor|island|kind" → last notification time (notify cooldowns). */
    private final Map<String, Long> notified = new HashMap<>();
    /** "island|visitor" pairs already recorded today, so arrivals don't hit the database again. */
    private final Set<String> recordedToday = new HashSet<>();
    private int recordedDay = Integer.MIN_VALUE;
    /** Visit/rating/like totals per island for the browser. */
    private volatile Map<UUID, Showcase.Stats> stats = Map.of();
    private final Set<UUID> busy = new HashSet<>();
    private Scheduler.Task statsTask;
    private PlayerSettings settings;
    private TeleportService teleports;
    private final TeleportService.Guard guard = this::guardMessage;
    private WarpManager warps;
    /** ISL2: told whenever a player's island slot changes ({@code null} = no island), e.g. for weather and time. */
    private final List<java.util.function.BiConsumer<Player, Island>> presenceHooks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<java.util.function.Consumer<Island>> visitHooks = new java.util.concurrent.CopyOnWriteArrayList<>();

    VisitManager(IslandsModule module, IslandManager islands, IslandProtection protection, VisitStore store) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.protection = protection;
        this.msg = module.msg();
        this.store = store;
        this.main = new MainThread(core.plugin());
    }

    void warps(WarpManager warps) {
        this.warps = warps;
    }

    VisitStore store() {
        return store;
    }

    /** Runs {@code hook} (main thread) every time a player enters another island's slot or leaves all of them. */
    void onPresence(java.util.function.BiConsumer<Player, Island> hook) {
        presenceHooks.add(hook);
    }

    /** Runs {@code hook} (main thread) for every first visit of a player to an island today (M2.6 visitor stats). */
    void onVisit(java.util.function.Consumer<Island> hook) {
        visitHooks.add(hook);
    }

    private IslandsConfig config() {
        return module.config();
    }

    /** Loads the bans (blocking, enable only) and starts the stats refresh. */
    void load() {
        bans.clear();
        store.loadBansBlocking().forEach((island, set) -> {
            Set<UUID> copy = ConcurrentHashMap.newKeySet();
            copy.addAll(set);
            bans.put(island, copy);
        });
        islands.onRemoved(island -> {
            bans.remove(island.id());
            presence.values().removeIf(island.id()::equals);
        });
        startStats();
        for (Player player : Bukkit.getOnlinePlayers()) {
            presence.remove(player.getUniqueId());
            Island here = slotIsland(player.getLocation());
            if (here != null) {
                presence.put(player.getUniqueId(), here.id());
            }
        }
    }

    /** Registers the /settings toggles and the teleport guard (one tick after enable: those modules may enable later). */
    void hookServices() {
        core.services().optional(PlayerSettings.class).ifPresent(service -> {
            service.register(new SettingToggle(IslandsModule.ID, SETTING_ATTEMPTS, SettingCategory.SOCIAL, Material.TRIPWIRE_HOOK, true,
                    null, IslandsModule.ID, "settings." + SETTING_ATTEMPTS, false));
            service.register(new SettingToggle(IslandsModule.ID, SETTING_ARRIVALS, SettingCategory.SOCIAL, Material.OAK_DOOR, true,
                    null, IslandsModule.ID, "settings." + SETTING_ARRIVALS, false));
            service.register(new SettingToggle(IslandsModule.ID, SETTING_DEPARTURES, SettingCategory.SOCIAL, Material.IRON_DOOR, true,
                    null, IslandsModule.ID, "settings." + SETTING_DEPARTURES, false));
            settings = service;
        });
        core.services().optional(TeleportService.class).ifPresent(service -> {
            service.addGuard(guard);
            teleports = service;
        });
    }

    /** (Re)starts the browser stats refresh (enable, reload). */
    void startStats() {
        if (statsTask != null) {
            statsTask.cancel();
        }
        long period = config().browserRefreshSeconds * 20L;
        statsTask = core.scheduler().repeat("island-showcase-stats", 20L, period, this::refreshStats);
    }

    void stop() {
        if (statsTask != null) {
            statsTask.cancel();
            statsTask = null;
        }
        if (settings != null) {
            settings.unregisterAll(IslandsModule.ID);
            settings = null;
        }
        if (teleports != null) {
            teleports.removeGuard(guard);
            teleports = null;
        }
        presence.clear();
    }

    private void refreshStats() {
        int today = today();
        store.stats(today - config().browserVisitsDays + 1, config().seasonId).whenCompleteAsync((loaded, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Islands: loading visit stats failed", Database.cause(error));
                return;
            }
            stats = Map.copyOf(loaded);
        }, main);
    }

    // ------------------------------------------------------------------ lookups

    ZoneId zone() {
        return VisitRules.zone(config().timezone);
    }

    int today() {
        return VisitRules.day(System.currentTimeMillis(), zone());
    }

    Showcase.Stats stats(UUID islandId) {
        return stats.getOrDefault(islandId, Showcase.Stats.EMPTY);
    }

    Map<UUID, Showcase.Stats> allStats() {
        return stats;
    }

    private void updateStats(UUID islandId, java.util.function.UnaryOperator<Showcase.Stats> change) {
        Map<UUID, Showcase.Stats> copy = new HashMap<>(stats);
        copy.put(islandId, change.apply(copy.getOrDefault(islandId, Showcase.Stats.EMPTY)));
        stats = Map.copyOf(copy);
    }

    boolean isBanned(UUID islandId, UUID player) {
        Set<UUID> set = bans.get(islandId);
        return set != null && set.contains(player);
    }

    Set<UUID> bans(UUID islandId) {
        Set<UUID> set = bans.get(islandId);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    /** Staff who ignore locks and bans: the protection bypass toggle, the visit bypass permission, spectators. */
    boolean bypass(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR || protection.bypassing(player) || player.hasPermission(PERM_VISIT_BYPASS);
    }

    VisitRules.Access access(Player player, Island island) {
        UUID key = islands.key(player);
        Role role = island.roleOrNull(key);
        // P3/P4: never onto an island where another of your own profiles is a member or coop.
        boolean ownProfile = role == null && islands.ownOtherProfileOn(island, key, null) != null;
        return VisitRules.access(role, isBanned(island.id(), player.getUniqueId()), island.locked(), bypass(player), ownProfile);
    }

    /** The island whose slot contains {@code at}, or null. */
    Island slotIsland(Location at) {
        if (at == null || !islands.isIslandWorld(at.getWorld())) {
            return null;
        }
        return islands.islandInSlotAt(at.getBlockX(), at.getBlockZ());
    }

    /** Vanished staff and spectators are never announced or counted ("vanished" metadata is the plugin convention). */
    @SuppressWarnings("deprecation")
    static boolean hidden(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The island of the player named {@code name} (online or known offline; any member, not only the leader), or null.
     * Main thread or tab completion (no network lookups).
     */
    Island islandByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return islands.islandOfPlayer(islands.key(online)).orElse(null);
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            Optional<Island> island = islands.islandOfPlayer(cached.getUniqueId());
            if (island.isPresent()) {
                return island.get();
            }
        }
        for (Island island : List.copyOf(islands.islands())) {
            if (name.equalsIgnoreCase(islands.ownerName(island))) {
                return island;
            }
        }
        return null;
    }

    /** UUID of a player who has played here (online name first, then the server's user cache), or null. */
    static UUID playerId(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? null : cached.getUniqueId();
    }

    /** Islands shown in the visit browser: built and not locked. */
    List<Island> listed() {
        List<Island> list = new ArrayList<>();
        for (Island island : islands.islands()) {
            if (!island.locked() && island.home().isPresent()) {
                list.add(island);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ /is visit

    /** {@code /is visit [player]}: the browser without a name, otherwise a teleport to that player's island home. */
    void visit(Player player, String name) {
        if (name == null) {
            openBrowser(player, null);
            return;
        }
        Island island = islandByName(name);
        if (island == null) {
            islands.fail(player, "visit.no-island", Msg.p("player", name));
            return;
        }
        visit(player, island);
    }

    /** Teleports to {@code island}'s home as a visitor (members simply go home). */
    void visit(Player player, Island island) {
        if (island.roleOrNull(islands.key(player)) != null) {
            islands.teleportHome(player);
            return;
        }
        if (!checkEntry(player, island)) {
            return;
        }
        if (island.home().isEmpty()) {
            islands.fail(player, "visit.not-ready", Msg.p("island", islands.displayName(island)));
            return;
        }
        Island.Home home = island.home().get();
        World world = Bukkit.getWorld(island.world());
        if (world == null) {
            islands.fail(player, "error.world", Msg.p("world", island.world()));
            return;
        }
        Location target = new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        teleportVisitor(player, island, target, islands.displayName(island), null);
    }

    /**
     * True if {@code player} may enter {@code island}; otherwise tells them why (and the members about an attempt on a
     * locked island).
     */
    boolean checkEntry(Player player, Island island) {
        VisitRules.Access access = access(player, island);
        if (access.allowed()) {
            return true;
        }
        deny(player, island, access);
        return false;
    }

    private void deny(Player player, Island island, VisitRules.Access access) {
        String name = islands.displayName(island);
        if (access == VisitRules.Access.BANNED) {
            islands.fail(player, "visit.banned", Msg.p("island", name));
        } else if (access == VisitRules.Access.OWN_PROFILE) {
            islands.fail(player, "visit.own-profile", Msg.p("island", name));
        } else {
            islands.fail(player, "visit.locked", Msg.p("island", name));
            notifyAttempt(island, player);
        }
    }

    /**
     * Safe-spot check at {@code target} (the nearest safe spot inside the island, else refused), then the teleport with
     * {@code visiting.warmup-seconds} through the essentials TeleportService (its guard re-checks bans and locks when the
     * warmup ends).
     */
    void teleportVisitor(Player player, Island island, Location target, String destination, Runnable onArrive) {
        target.getWorld().getChunkAtAsync(target).thenAcceptAsync(chunk -> {
            if (!player.isOnline()) {
                return;
            }
            Location safe = SafeSpots.isSafe(target) ? target : SafeSpots.nearest(target, island.bounds(), config().safeSearchRadius,
                    config().minBuildY, config().maxBuildY).orElse(null);
            if (safe == null) {
                islands.fail(player, "visit.unsafe", Msg.p("island", islands.displayName(island)));
                return;
            }
            player.setFallDistance(0);
            if (teleports != null) {
                teleports.teleport(player, safe, config().visitWarmupSeconds, destination, onArrive);
                return;
            }
            player.teleportAsync(safe, PlayerTeleportEvent.TeleportCause.COMMAND).thenAcceptAsync(ok -> {
                if (ok) {
                    core.sounds().play(player, "teleport");
                    if (onArrive != null) {
                        onArrive.run();
                    }
                }
            }, main);
        }, main);
    }

    /** TeleportService guard: refuses any teleport into an island the player is banned from or that is locked. */
    private Optional<String> guardMessage(Player player, Location destination) {
        Island target = slotIsland(destination);
        if (target == null || target == slotIsland(player.getLocation())) {
            return Optional.empty();
        }
        VisitRules.Access access = access(player, target);
        if (access.allowed()) {
            return Optional.empty();
        }
        if (access == VisitRules.Access.LOCKED) {
            notifyAttempt(target, player);
        }
        String key = access == VisitRules.Access.BANNED ? "visit.banned"
                : access == VisitRules.Access.OWN_PROFILE ? "visit.own-profile" : "visit.locked";
        return Optional.of(module.raw(key).replace("<island>", escape(islands.displayName(target))));
    }

    private static String escape(String text) {
        return text.replace("<", "\\<");
    }

    // ------------------------------------------------------------------ lock / unlock

    void lock(Player player, boolean locked) {
        Island island = managedIsland(player);
        if (island == null) {
            return;
        }
        if (island.locked() == locked) {
            msg.send(player, locked ? "lock.already" : "unlock.already");
            return;
        }
        if (!begin(player)) {
            return;
        }
        store.setLocked(island.id(), locked).whenCompleteAsync((saved, error) -> {
            end(player);
            if (error != null) {
                islands.databaseError(player, "lock", error);
                return;
            }
            if (!saved || islands.byId(island.id()) != island) {
                islands.fail(player, "error.island-gone");
                return;
            }
            island.locked(locked);
            core.audit().log(AuditCategory.ISLAND, locked ? "island_lock" : "island_unlock", Actor.player(player), island.id().toString(),
                    islands.displayName(island), null, null, Map.of("locked", locked));
            msg.send(player, locked ? "lock.done" : "unlock.done");
            core.sounds().play(player, "success");
            islands.memberManager().notifyMembers(island, islands.key(player), locked ? "notify.locked" : "notify.unlocked",
                    Msg.p("by", player.getName()));
            if (locked) {
                int sent = 0;
                for (Player visitor : visitorsOn(island)) {
                    if (access(visitor, island) == VisitRules.Access.LOCKED) {
                        msg.send(visitor, "lock.sent-off", Msg.p("island", islands.displayName(island)));
                        islands.sendOff(visitor, island);
                        sent++;
                    }
                }
                if (sent > 0) {
                    msg.send(player, "lock.visitors-sent", Msg.p("count", sent));
                }
            }
        }, main);
    }

    /** Online non-members standing in {@code island}'s slot. */
    List<Player> visitorsOn(Island island) {
        List<Player> list = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (island.roleOrNull(islands.key(online)) == null && slotIsland(online.getLocation()) == island) {
                list.add(online);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ expel

    void expel(Player player, String targetName) {
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null && !player.canSee(target)) {
            target = null;
        }
        boolean present = target != null && slotIsland(target.getLocation()) == island;
        VisitRules.ExpelCheck check = VisitRules.canExpel(island.roleOrNull(islands.key(player)),
                target == null ? null : island.roleOrNull(islands.key(target)), target != null && target.equals(player), present,
                target != null && bypass(target));
        String name = target == null ? targetName : target.getName();
        switch (check) {
            case NOT_ALLOWED -> islands.fail(player, "expel.not-allowed");
            case SELF -> islands.fail(player, "expel.self");
            case MEMBER -> islands.fail(player, "expel.member", Msg.p("player", name));
            // Staff with the bypass look like "not here" so nobody learns who is watching.
            case NOT_HERE, PROTECTED -> islands.fail(player, "expel.not-here", Msg.p("player", name));
            case OK -> {
                core.audit().log(AuditCategory.ISLAND, "island_expel", Actor.player(player), island.id().toString(),
                        islands.displayName(island), null, target.getLocation(),
                        Map.of("player", target.getUniqueId().toString(), "player_name", target.getName()));
                msg.send(target, "expel.you", Msg.p("island", islands.displayName(island)));
                core.sounds().play(target, "error");
                islands.sendOff(target, island);
                msg.send(player, "expel.done", Msg.p("player", target.getName()));
                core.sounds().play(player, "success");
                islands.memberManager().notifyMembers(island, islands.key(player), "notify.expelled", Msg.p("player", target.getName()),
                        Msg.p("by", player.getName()));
            }
        }
    }

    // ------------------------------------------------------------------ ban / unban

    void ban(Player player, String targetName) {
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        UUID target = playerId(targetName);
        if (target == null) {
            islands.fail(player, "ban.unknown", Msg.p("player", targetName));
            return;
        }
        String name = islands.name(target);
        VisitRules.BanCheck check = VisitRules.canBan(island.roleOrNull(islands.key(player)), islands.roleOfPlayer(island, target),
                target.equals(player.getUniqueId()), isBanned(island.id(), target), bans(island.id()).size(), config().maxBans);
        switch (check) {
            case NOT_ALLOWED -> islands.fail(player, "ban.not-allowed");
            case SELF -> islands.fail(player, "ban.self");
            case MEMBER -> islands.fail(player, "ban.member", Msg.p("player", name));
            case COOP -> islands.fail(player, "ban.coop", Msg.p("player", name));
            case ALREADY -> islands.fail(player, "ban.already", Msg.p("player", name));
            case LIMIT -> islands.fail(player, "ban.limit", Msg.p("max", config().maxBans));
            case OK -> {
                if (!begin(player)) {
                    return;
                }
                store.ban(island.id(), target, player.getUniqueId(), config().maxBans).whenCompleteAsync((result, error) -> {
                    end(player);
                    if (error != null) {
                        islands.databaseError(player, "ban", error);
                        return;
                    }
                    switch (result) {
                        case GONE -> islands.fail(player, "error.island-gone");
                        case MEMBER -> islands.fail(player, "ban.member", Msg.p("player", name));
                        case ALREADY -> islands.fail(player, "ban.already", Msg.p("player", name));
                        case LIMIT -> islands.fail(player, "ban.limit", Msg.p("max", config().maxBans));
                        case BANNED -> banned(player, island, target, name);
                    }
                }, main);
            }
        }
    }

    private void banned(Player player, Island island, UUID target, String name) {
        if (islands.byId(island.id()) != island) {
            return;
        }
        bans.computeIfAbsent(island.id(), id -> ConcurrentHashMap.newKeySet()).add(target);
        core.audit().log(AuditCategory.ISLAND, "island_ban", Actor.player(player), island.id().toString(), islands.displayName(island),
                null, null, Map.of("player", target.toString(), "player_name", name));
        msg.send(player, "ban.done", Msg.p("player", name));
        core.sounds().play(player, "success");
        islands.memberManager().notifyMembers(island, islands.key(player), "notify.banned", Msg.p("player", name),
                Msg.p("by", player.getName()));
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            msg.send(online, "ban.you", Msg.p("island", islands.displayName(island)));
            if (slotIsland(online.getLocation()) == island && !bypass(online)) {
                core.sounds().play(online, "error");
                islands.sendOff(online, island);
            }
        }
    }

    void unban(Player player, String targetName) {
        Island island = ownIsland(player);
        if (island == null) {
            return;
        }
        if (!VisitRules.canManageVisitors(island.roleOrNull(islands.key(player)))) {
            islands.fail(player, "ban.not-allowed");
            return;
        }
        UUID target = bannedByName(island, targetName);
        if (target == null) {
            islands.fail(player, "unban.not-banned", Msg.p("player", targetName));
            return;
        }
        String name = islands.name(target);
        if (!begin(player)) {
            return;
        }
        store.unban(island.id(), target).whenCompleteAsync((removed, error) -> {
            end(player);
            if (error != null) {
                islands.databaseError(player, "unban", error);
                return;
            }
            Set<UUID> set = bans.get(island.id());
            if (set != null) {
                set.remove(target);
            }
            if (!removed) {
                islands.fail(player, "unban.not-banned", Msg.p("player", name));
                return;
            }
            core.audit().log(AuditCategory.ISLAND, "island_unban", Actor.player(player), island.id().toString(),
                    islands.displayName(island), null, null, Map.of("player", target.toString(), "player_name", name));
            msg.send(player, "unban.done", Msg.p("player", name));
            core.sounds().play(player, "success");
            islands.memberManager().notifyMembers(island, islands.key(player), "notify.unbanned", Msg.p("player", name),
                    Msg.p("by", player.getName()));
            Player online = Bukkit.getPlayer(target);
            if (online != null) {
                msg.send(online, "unban.you", Msg.p("island", islands.displayName(island)));
            }
        }, main);
    }

    private UUID bannedByName(Island island, String name) {
        if (name == null) {
            return null;
        }
        for (UUID banned : bans(island.id())) {
            if (islands.name(banned).equalsIgnoreCase(name)) {
                return banned;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ rate / like

    void rate(Player player, String name, int stars) {
        Island island = islandByName(name);
        if (island == null) {
            islands.fail(player, "visit.no-island", Msg.p("player", name));
            return;
        }
        // Ratings and likes are per player (owner O7): an island any of your profiles belongs to is your own.
        Role role = islands.roleOfPlayer(island, player.getUniqueId());
        if (role != null && role != Role.COOP) {
            islands.fail(player, "rate.own");
            return;
        }
        if (!begin(player)) {
            return;
        }
        int season = config().seasonId;
        store.voteContext(island.id(), player.getUniqueId(), season, today()).thenComposeAsync(context -> {
            VisitRules.VoteCheck check = VisitRules.canRate(stars, MAX_STARS, island.roleOrNull(islands.key(player)), context.visited(),
                    config().requireVisit, context.rating());
            if (check != VisitRules.VoteCheck.OK) {
                end(player);
                voteDenied(player, island, check, "rate", stars);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            return store.rate(island.id(), player.getUniqueId(), season, stars).thenAcceptAsync(old -> {
                end(player);
                Integer before = old.orElse(null);
                updateStats(island.id(), s -> s.withRating(before, stars));
                Map<String, Object> details = new HashMap<>();
                details.put("stars", stars);
                details.put("old", before == null ? "-" : before);
                details.put("season", season);
                core.audit().log(AuditCategory.ISLAND, "island_rate", Actor.player(player), island.id().toString(),
                        islands.displayName(island), null, null, details);
                msg.send(player, before == null ? "rate.done" : "rate.changed", Msg.p("island", islands.displayName(island)),
                        Msg.p("stars", stars), Msg.p("old", before == null ? 0 : before), Msg.p("max", MAX_STARS));
                core.sounds().play(player, "success");
            }, main);
        }, main).exceptionally(error -> {
            end(player);
            return islands.databaseError(player, "rate", error);
        });
    }

    void like(Player player, String name) {
        Island island = islandByName(name);
        if (island == null) {
            islands.fail(player, "visit.no-island", Msg.p("player", name));
            return;
        }
        // Ratings and likes are per player (owner O7): an island any of your profiles belongs to is your own.
        Role role = islands.roleOfPlayer(island, player.getUniqueId());
        if (role != null && role != Role.COOP) {
            islands.fail(player, "like.own");
            return;
        }
        if (!begin(player)) {
            return;
        }
        int season = config().seasonId;
        int day = today();
        store.voteContext(island.id(), player.getUniqueId(), season, day).thenComposeAsync(context -> {
            VisitRules.VoteCheck check = VisitRules.canLike(island.roleOrNull(islands.key(player)), context.visited(),
                    config().requireVisit, context.likedToday());
            if (check != VisitRules.VoteCheck.OK) {
                end(player);
                voteDenied(player, island, check, "like", 0);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            return store.like(island.id(), player.getUniqueId(), day, season).thenAcceptAsync(added -> {
                end(player);
                if (!added) {
                    voteDenied(player, island, VisitRules.VoteCheck.ALREADY, "like", 0);
                    return;
                }
                updateStats(island.id(), Showcase.Stats::withLike);
                core.audit().log(AuditCategory.ISLAND, "island_like", Actor.player(player), island.id().toString(),
                        islands.displayName(island), null, null, Map.of("day", day, "season", season));
                msg.send(player, "like.done", Msg.p("island", islands.displayName(island)));
                core.sounds().play(player, "success");
                islands.memberManager().notifyMembers(island, Set.of(), "notify.liked", Msg.p("player", player.getName()));
            }, main);
        }, main).exceptionally(error -> {
            end(player);
            return islands.databaseError(player, "like", error);
        });
    }

    private void voteDenied(Player player, Island island, VisitRules.VoteCheck check, String what, int stars) {
        String name = islands.displayName(island);
        switch (check) {
            case INVALID_STARS -> islands.fail(player, "rate.stars", Msg.p("max", MAX_STARS));
            case OWN_ISLAND -> islands.fail(player, what + ".own");
            case NOT_VISITED -> islands.fail(player, what + ".not-visited", Msg.p("island", name));
            case ALREADY -> islands.fail(player, what + ".already", Msg.p("island", name), Msg.p("stars", stars));
            case OK -> {
            }
        }
    }

    // ------------------------------------------------------------------ browser

    void openBrowser(Player player, Showcase.Sort sort) {
        core.menus().open(player, new VisitMenu(module, islands, this, warps, sort == null ? Showcase.Sort.VISITS : sort));
    }

    // ------------------------------------------------------------------ enforcement and presence (events)

    /** Any teleport into an island the player is banned from or that is locked is cancelled (pearls, /tpa, plugins). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleportGuard(PlayerTeleportEvent event) {
        Island target = slotIsland(event.getTo());
        if (target == null || target == slotIsland(event.getFrom())) {
            return;
        }
        Player player = event.getPlayer();
        VisitRules.Access access = access(player, target);
        if (!access.allowed()) {
            event.setCancelled(true);
            deny(player, target, access);
        }
    }

    /** Flying (or being pushed) across the void into such an island is stopped at its slot edge. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMoveGuard(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (IslandGrid.cell(from.getBlockX()) == IslandGrid.cell(to.getBlockX())
                && IslandGrid.cell(from.getBlockZ()) == IslandGrid.cell(to.getBlockZ())) {
            return;
        }
        Island target = slotIsland(to);
        if (target == null || target == slotIsland(from)) {
            return;
        }
        Player player = event.getPlayer();
        VisitRules.Access access = access(player, target);
        if (!access.allowed()) {
            event.setCancelled(true);
            String key = "deny|" + target.id();
            if (cooldownPassed(player.getUniqueId(), key, Math.max(1, config().attemptCooldownSeconds))) {
                deny(player, target, access);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        updatePresence(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (IslandGrid.cell(from.getBlockX()) != IslandGrid.cell(to.getBlockX())
                || IslandGrid.cell(from.getBlockZ()) != IslandGrid.cell(to.getBlockZ())) {
            updatePresence(event.getPlayer(), to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        updatePresence(event.getPlayer(), event.getRespawnLocation());
    }

    /** Logging in inside an island you are banned from (or that was locked meanwhile) sends you to spawn. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later("island-visit-join", 1L, () -> {
            if (!player.isOnline()) {
                return;
            }
            Island here = slotIsland(player.getLocation());
            if (here != null) {
                VisitRules.Access access = access(player, here);
                if (!access.allowed()) {
                    msg.send(player, access == VisitRules.Access.BANNED ? "visit.join-banned"
                                    : access == VisitRules.Access.OWN_PROFILE ? "visit.join-own-profile" : "visit.join-locked",
                            Msg.p("island", islands.displayName(here)));
                    islands.sendOff(player, here);
                    return;
                }
            }
            updatePresence(player, player.getLocation());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID before = presence.remove(player.getUniqueId());
        if (before != null) {
            Island island = islands.byId(before);
            if (island != null) {
                departed(player, island);
            }
        }
        String prefix = player.getUniqueId() + "|";
        notified.keySet().removeIf(key -> key.startsWith(prefix) && key.contains("|deny|"));
        busy.remove(player.getUniqueId());
    }

    private void updatePresence(Player player, Location at) {
        Island now = slotIsland(at);
        UUID nowId = now == null ? null : now.id();
        UUID before = nowId == null ? presence.remove(player.getUniqueId()) : presence.put(player.getUniqueId(), nowId);
        if (java.util.Objects.equals(before, nowId)) {
            return;
        }
        presenceHooks.forEach(hook -> hook.accept(player, now));
        if (before != null) {
            Island old = islands.byId(before);
            if (old != null) {
                departed(player, old);
            }
        }
        if (now != null) {
            arrived(player, now);
        }
    }

    private void arrived(Player player, Island island) {
        if (island.roleOrNull(islands.key(player)) != null || hidden(player)) {
            return;
        }
        msg.actionBar(player, "visit.welcome", Msg.p("island", islands.displayName(island)));
        if (cooldownPassed(player.getUniqueId(), island.id() + "|arrive", config().notifyCooldownSeconds)) {
            notifyMembers(island, player, SETTING_ARRIVALS, "notify.arrived");
        }
        recordVisit(player, island);
    }

    private void departed(Player player, Island island) {
        if (island.roleOrNull(islands.key(player)) != null || hidden(player)) {
            return;
        }
        if (cooldownPassed(player.getUniqueId(), island.id() + "|leave", config().notifyCooldownSeconds)) {
            notifyMembers(island, player, SETTING_DEPARTURES, "notify.departed");
        }
    }

    /** First visit today of this player to this island: stored (unique visitors per day) and logged. */
    private void recordVisit(Player player, Island island) {
        int day = today();
        if (day != recordedDay) {
            recordedDay = day;
            recordedToday.clear();
        }
        if (!recordedToday.add(island.id() + "|" + player.getUniqueId())) {
            return;
        }
        UUID visitor = player.getUniqueId();
        String visitorName = player.getName();
        store.recordVisit(island.id(), visitor, day, config().seasonId).whenCompleteAsync((first, error) -> {
            if (error != null) {
                recordedToday.remove(island.id() + "|" + visitor);
                core.plugin().getLogger().log(Level.WARNING, "Islands: recording a visit failed", Database.cause(error));
                return;
            }
            if (first) {
                updateStats(island.id(), Showcase.Stats::withVisit);
                visitHooks.forEach(hook -> hook.accept(island));
                core.audit().log(AuditCategory.ISLAND, "island_visit", Actor.player(visitor, visitorName), island.id().toString(),
                        islands.displayName(island), null, null, Map.of("day", day));
            }
        }, main);
    }

    /** Members hear about a visit attempt on their locked island (at most once per visitor per cooldown). */
    void notifyAttempt(Island island, Player visitor) {
        if (hidden(visitor) || !cooldownPassed(visitor.getUniqueId(), island.id() + "|attempt", config().attemptCooldownSeconds)) {
            return;
        }
        notifyMembers(island, visitor, SETTING_ATTEMPTS, "notify.attempt");
    }

    private void notifyMembers(Island island, Player visitor, String setting, String key) {
        if (!islands.flagOn(island, IslandFlag.NOTIFICATIONS)) {
            return; // ISL2: the island turned visitor notifications off in /is settings
        }
        TagResolver who = Msg.p("player", visitor.getName());
        for (UUID member : List.copyOf(island.members().keySet())) {
            Player online = islands.online(member);
            if (online == null || !online.canSee(visitor) || (settings != null && !settings.get(online, setting))) {
                continue;
            }
            msg.send(online, key, who);
            core.sounds().play(online, "notify");
        }
    }

    /** True (and remembered) if {@code seconds} passed since the last time for this player and key. */
    private boolean cooldownPassed(UUID player, String key, int seconds) {
        long now = System.currentTimeMillis();
        String full = player + "|" + key;
        Long last = notified.get(full);
        if (last != null && now - last < seconds * 1000L) {
            return false;
        }
        if (notified.size() > 10_000) {
            long cutoff = now - 3_600_000L;
            notified.values().removeIf(time -> time < cutoff);
        }
        notified.put(full, now);
        return true;
    }

    // ------------------------------------------------------------------ helpers and tab completion

    /** The sender's island, or tells them they have none. */
    private Island ownIsland(Player player) {
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return null;
        }
        return own.get();
    }

    /** The sender's island if they are its leader or a mod; otherwise tells them. */
    private Island managedIsland(Player player) {
        Island island = ownIsland(player);
        if (island == null) {
            return null;
        }
        if (!VisitRules.canManageVisitors(island.roleOrNull(islands.key(player)))) {
            islands.fail(player, "lock.not-allowed");
            return null;
        }
        return island;
    }

    private boolean begin(Player player) {
        if (!busy.add(player.getUniqueId())) {
            msg.send(player, "member.busy");
            return false;
        }
        return true;
    }

    private void end(Player player) {
        busy.remove(player.getUniqueId());
    }

    /** Tab completion of /is visit, rate, like, warp: visible online players with an island, then island owners. */
    List<String> islandNames(CommandSender sender) {
        return safely(() -> {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if ((!(sender instanceof Player viewer) || viewer.canSee(online)) && islands.islandOfPlayer(islands.key(online)).isPresent()) {
                    names.add(online.getName());
                }
            }
            Set<String> seen = new HashSet<>();
            names.forEach(n -> seen.add(n.toLowerCase(java.util.Locale.ROOT)));
            for (Island island : List.copyOf(islands.islands())) {
                String owner = islands.ownerName(island);
                if (owner != null && !"?".equals(owner) && seen.add(owner.toLowerCase(java.util.Locale.ROOT))) {
                    names.add(owner);
                }
            }
            return names;
        });
    }

    /** Tab completion of /is expel: visitors the sender can see on their island. */
    List<String> visitorNames(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return safely(() -> islands.islandOfPlayer(islands.key(player))
                .map(island -> visitorsOn(island).stream().filter(player::canSee).map(Player::getName).toList()).orElse(List.of()));
    }

    /** Tab completion of /is ban: visible online players who are not on the sender's island and not banned yet. */
    List<String> banCandidates(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return safely(() -> {
            Optional<Island> own = islands.islandOfPlayer(islands.key(player));
            if (own.isEmpty()) {
                return List.of();
            }
            Island island = own.get();
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player) && player.canSee(online) && island.roleOrNull(islands.key(online)) == null
                        && !isBanned(island.id(), online.getUniqueId())) {
                    names.add(online.getName());
                }
            }
            return names;
        });
    }

    /** Tab completion of /is unban: the sender's island's banned players. */
    List<String> bannedNames(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return safely(() -> islands.islandOfPlayer(islands.key(player))
                .map(island -> bans(island.id()).stream().map(islands::name).toList()).orElse(List.of()));
    }

    static List<String> safely(java.util.function.Supplier<List<String>> names) {
        try {
            return names.get();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** For menus: every cached island (main thread). */
    Collection<Island> allIslands() {
        return islands.islands();
    }
}
