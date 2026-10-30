package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.achievements.IslandBoost;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.island.IslandRemovedEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.LevelSource;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Island achievements (AC3, RANKS_TITLES_ACHIEVEMENTS §C4): progress per island shared by its members, island boosts
 * for the whole island and the island level source {@code achievements}. Main thread; database work goes through the
 * module's one ordered chain ({@link Tracker#queue}).
 *
 * <h2>Where the values come from</h2>
 * <ul>
 *   <li>Island level: {@link ProgressionService#islandLevel} on the island poll.</li>
 *   <li>Crops, blocks mined, logs, mobs: the sum of every member key's all-time stats in the island's
 *       {@link IslandStatsService#snapshot} (members and former members; coop players and visitors never have a row).</li>
 *   <li>Visitors: the snapshot's all-time unique visitors. Likes: audit row {@code island/island_like}.</li>
 *   <li>Team Flame: {@link TeamActivity} from the playtime minute (members only, alts on one IP once).</li>
 * </ul>
 *
 * <h2>Anti-abuse (§C4)</h2>
 * Only stored members count (never coop); a member who leaves takes nothing (progress and boosts stay with the island);
 * titles go only to members with {@code island.title-min-days} on the island; Team Flame counts one per IP.
 */
final class IslandTracker implements Listener {

    /** Source id of the island XP boost and the bonus parts. */
    static final String SOURCE = "achievements";
    private static final int TOP_MEMBERS = 5;

    private final AchievementsModule module;
    private final Core core;
    private final Tracker tracker;
    private final AchievementStore store = new AchievementStore();
    private final Map<UUID, IslandProgress> islands = new HashMap<>();
    private final TeamActivity team = new TeamActivity();
    /** Last read member contributions per island (the Island tab's top members). */
    private final Map<UUID, Map<UUID, Long>> contributions = new HashMap<>();
    /** Island titles already granted (or found owned) this run: "account:title". */
    private final Set<String> titleDone = new HashSet<>();
    private boolean loaded;
    private boolean hooked;

    IslandTracker(AchievementsModule module, Tracker tracker) {
        this.module = module;
        this.core = module.core();
        this.tracker = tracker;
    }

    private AchievementsConfig config() {
        return module.config();
    }

    private AchievementRegistry registry() {
        return module.registry();
    }

    private Optional<IslandService> islandService() {
        return core.services().optional(IslandService.class);
    }

    private ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    // ------------------------------------------------------------------ lifecycle

    /** Loads every island's progress (async) and hooks the island XP boost and level source when done. */
    void start() {
        tracker.queue(store::loadIslands).whenComplete((data, error) -> core.scheduler().main("achievements-islands-load", () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Achievements: island progress could not be loaded; island achievements "
                        + "are paused until the next start (nothing is lost)", Database.cause(error));
                return;
            }
            Set<UUID> ids = new HashSet<>(data.values().keySet());
            ids.addAll(data.unlocks().keySet());
            for (UUID id : ids) {
                islands.computeIfAbsent(id, IslandProgress::new);
            }
            for (IslandProgress p : islands.values()) {
                p.load(data.values().getOrDefault(p.islandId(), Map.of()), data.unlocks().getOrDefault(p.islandId(), Map.of()));
                p.points(registry(), config().perTier());
            }
            loaded = true;
            hook();
            reevaluateAll();
            core.plugin().getLogger().info("Achievements: island progress of " + islands.size() + " island(s) loaded.");
        }));
    }

    /** Registers the island XP boost and the level source (after the load, so a level is never computed from zeros). */
    void hook() {
        if (!loaded) {
            return;
        }
        Optional<ProgressionService> progression = core.services().optional(ProgressionService.class);
        if (progression.isEmpty()) {
            return;
        }
        progression.get().islandXpBoost(SOURCE, id -> percent(id, IslandBoost.ISLAND_XP));
        progression.get().levelPointsProvider(LevelSource.ACHIEVEMENTS, id -> config().islandEnabled ? points(id) * config().levelPointsPerPoint : 0);
        hooked = true;
        for (IslandProgress p : islands.values()) {
            if (p.points() > 0) {
                progression.get().requestLevelUpdate(p.islandId());
            }
        }
    }

    void unhook() {
        if (!hooked) {
            return;
        }
        core.services().optional(ProgressionService.class).ifPresent(p -> {
            p.islandXpBoost(SOURCE, null);
            p.levelPointsProvider(LevelSource.ACHIEVEMENTS, null);
        });
        hooked = false;
    }

    /** Points per tier changed (reload): recompute every island's points and boosts, then re-check the goals. */
    void reload() {
        for (IslandProgress p : islands.values()) {
            p.points(registry(), config().perTier());
        }
        reevaluateAll();
        core.services().optional(ProgressionService.class).ifPresent(progression -> islands.values().forEach(p -> {
            if (p.points() > 0) {
                progression.requestLevelUpdate(p.islandId());
            }
        }));
    }

    void reevaluateAll() {
        if (!loaded) {
            return;
        }
        for (IslandProgress p : List.copyOf(islands.values())) {
            handle(p, IslandProgression.evaluateAll(p, registry(), isTeam(p.islandId())));
        }
    }

    // ------------------------------------------------------------------ queries (main thread)

    boolean loaded() {
        return loaded;
    }

    Optional<IslandProgress> find(UUID islandId) {
        return islandId == null ? Optional.empty() : Optional.ofNullable(islands.get(islandId));
    }

    int points(UUID islandId) {
        IslandProgress p = islandId == null ? null : islands.get(islandId);
        return p == null || !config().islandEnabled ? 0 : p.points();
    }

    double percent(UUID islandId, IslandBoost boost) {
        return BoostMath.islandPercent(points(islandId), boost, config());
    }

    boolean isTeam(UUID islandId) {
        return islandService().flatMap(s -> s.island(islandId)).map(IslandService.IslandInfo::team).orElse(false);
    }

    /** Up to five members with the most counted island work (last island poll), best first: profile key → amount. */
    List<Map.Entry<UUID, Long>> topMembers(UUID islandId) {
        Map<UUID, Long> of = contributions.getOrDefault(islandId, Map.of());
        return of.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder())).limit(TOP_MEMBERS).toList();
    }

    // ------------------------------------------------------------------ adding progress

    private IslandProgress progress(UUID islandId) {
        return islands.computeIfAbsent(islandId, id -> {
            IslandProgress p = new IslandProgress(id);
            if (loaded) {
                p.load(Map.of(), Map.of());
            }
            return p;
        });
    }

    /** Adds to an island (main thread). Ignored while island achievements are off, or for islands that don't exist. */
    void add(UUID islandId, IslandMetric metric, long amount) {
        if (islandId == null || !config().islandEnabled || tracker.resetting()) {
            return;
        }
        if (islandService().flatMap(s -> s.island(islandId)).isEmpty()) {
            return;
        }
        if (registry().island().stream().noneMatch(d -> d.metric() == metric)) {
            return;
        }
        IslandProgress p = progress(islandId);
        handle(p, IslandProgression.apply(p, registry(), metric, amount, isTeam(islandId)));
    }

    /** Values from one island stats snapshot (island poll, main thread). */
    void onSnapshot(UUID islandId, IslandStatsService.Snapshot snap) {
        if (!config().islandEnabled) {
            return;
        }
        Map<IslandStat, Long> sums = new EnumMap<>(IslandStat.class);
        Map<UUID, Long> work = new HashMap<>();
        for (Map.Entry<UUID, Map<IslandStat, IslandStatsService.Totals>> member : snap.members().entrySet()) {
            long mine = 0;
            for (IslandStat stat : COUNTED) {
                IslandStatsService.Totals t = member.getValue().get(stat);
                long v = t == null ? 0 : Math.max(0, t.allTime());
                sums.merge(stat, v, ProgressMath::add);
                mine = ProgressMath.add(mine, v);
            }
            work.put(member.getKey(), mine);
        }
        contributions.put(islandId, work);
        add(islandId, IslandMetric.CROPS_HARVESTED, sums.getOrDefault(IslandStat.CROPS_HARVESTED, 0L));
        add(islandId, IslandMetric.BLOCKS_MINED, sums.getOrDefault(IslandStat.BLOCKS_MINED, 0L));
        add(islandId, IslandMetric.LOGS_CHOPPED, sums.getOrDefault(IslandStat.LOGS_CHOPPED, 0L));
        add(islandId, IslandMetric.MOBS_KILLED, sums.getOrDefault(IslandStat.MOBS_KILLED, 0L));
        add(islandId, IslandMetric.VISITORS, snap.totals(IslandStat.VISITORS).allTime());
        core.services().optional(ProgressionService.class).ifPresent(p -> add(islandId, IslandMetric.ISLAND_LEVEL, p.islandLevel(islandId)));
        grantPendingTitles(islandId);
    }

    /** Island stats that count as a member's work (shared island metrics + the Island tab's top members). */
    private static final List<IslandStat> COUNTED = List.of(IslandStat.CROPS_HARVESTED, IslandStat.BLOCKS_MINED,
            IslandStat.LOGS_CHOPPED, IslandStat.MOBS_KILLED);

    /** One online minute of a player on their active profile (Team Flame). */
    void minute(Player player, UUID profileKey) {
        if (!config().islandEnabled) {
            return;
        }
        Optional<IslandService> service = islandService();
        if (service.isEmpty()) {
            return;
        }
        Optional<IslandService.IslandInfo> island = service.get().islandOfProfile(profileKey);
        if (island.isEmpty() || !island.get().team() || !service.get().isMember(island.get().id(), profileKey)) {
            return;
        }
        long day = LocalDate.now(zone()).toEpochDay();
        InetSocketAddress address = player.getAddress();
        String ip = address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
        int active = team.minute(day, island.get().id(), profileKey, ip, config().teamActiveMinutes);
        if (active > 0) {
            add(island.get().id(), IslandMetric.TEAM_ACTIVE, active);
        }
    }

    private ZoneId zone() {
        ZoneId zone = Format.parseZone(config().islandTimeZone);
        return zone == null ? Format.DEFAULT_ZONE : zone;
    }

    // ------------------------------------------------------------------ unlocks

    private void handle(IslandProgress p, List<IslandProgression.Unlock> unlocks) {
        if (unlocks.isEmpty()) {
            return;
        }
        p.points(registry(), config().perTier());
        long now = System.currentTimeMillis();
        UUID islandId = p.islandId();
        for (IslandProgression.Unlock u : unlocks) {
            long value = p.value(u.def().metric());
            int points = BoostMath.tierPoints(config().perTier(), u.tier());
            tracker.queue(c -> store.unlockIsland(c, islandId, u.def().id(), u.tier(), now)).whenComplete((added, error) -> {
                if (error != null) {
                    core.plugin().getLogger().warning("Achievements: could not store island " + u.def().id() + " tier " + u.tier()
                            + " for " + islandId + ": " + Database.cause(error).getMessage());
                    return;
                }
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("achievement", u.def().id());
                details.put("tier", u.tier());
                details.put("tiers", u.def().tiers());
                details.put("value", value);
                details.put("points", points);
                details.put("new", added);
                if (u.title() != null) {
                    details.put("title", u.title());
                }
                core.audit().log(AuditCategory.ISLAND, "island_achievement_unlock", Actor.system(AchievementsModule.ID),
                        islandId.toString(), (long) points, null, details);
                if (u.title() != null && Boolean.TRUE.equals(added)) {
                    core.scheduler().main("achievements-island-title", () -> grantTitles(islandId, u.def()));
                }
            });
        }
        toastMembers(islandId, unlocks);
        core.services().optional(ProgressionService.class).ifPresent(prog -> prog.requestLevelUpdate(islandId));
    }

    private void toastMembers(UUID islandId, List<IslandProgression.Unlock> unlocks) {
        for (Player player : onlineMembers(islandId)) {
            module.toasts().showIsland(player, unlocks);
        }
    }

    /** Members of the island who are online on that profile right now. */
    List<Player> onlineMembers(UUID islandId) {
        Optional<IslandService> service = islandService();
        if (service.isEmpty()) {
            return List.of();
        }
        List<Player> out = new ArrayList<>();
        ProfileService profiles = profiles();
        for (UUID member : service.get().members(islandId).keySet()) {
            profiles.onlinePlayer(member).filter(pl -> profiles.profileKey(pl.getUniqueId()).equals(member)).ifPresent(out::add);
        }
        return out;
    }

    /** The last tier is done: the title goes to every member who has been on the island long enough. */
    private void grantTitles(UUID islandId, IslandAchievementDef def) {
        Optional<IslandService> service = islandService();
        if (service.isEmpty() || !def.hasTitle()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID member : service.get().members(islandId).keySet()) {
            OptionalLong since = service.get().memberSince(islandId, member);
            if (IslandProgression.titleEligible(since, now, config().islandTitleMinDays)) {
                grantTitle(profiles().ownerOf(member), islandId, def);
            }
        }
    }

    /** Online members who reached the minimum days after the last tier was done get the title now (island poll). */
    private void grantPendingTitles(UUID islandId) {
        IslandProgress p = islands.get(islandId);
        Optional<IslandService> service = islandService();
        Optional<Cosmetics> cosmetics = core.services().optional(Cosmetics.class);
        if (p == null || service.isEmpty() || cosmetics.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (IslandAchievementDef def : registry().island()) {
            if (!def.hasTitle() || p.unlocked(def.id()) < def.tiers()) {
                continue;
            }
            for (Player player : onlineMembers(islandId)) {
                UUID member = profiles().profileKey(player.getUniqueId());
                if (titleDone.contains(player.getUniqueId() + ":" + def.title().id())
                        || cosmetics.get().owns(player.getUniqueId(), Cosmetics.Kind.TITLE, def.title().id())) {
                    continue;
                }
                if (IslandProgression.titleEligible(service.get().memberSince(islandId, member), now, config().islandTitleMinDays)) {
                    grantTitle(player.getUniqueId(), islandId, def);
                }
            }
        }
    }

    private void grantTitle(UUID account, UUID islandId, IslandAchievementDef def) {
        Optional<Cosmetics> cosmetics = core.services().optional(Cosmetics.class);
        if (cosmetics.isEmpty() || account == null) {
            return;
        }
        String key = account + ":" + def.title().id();
        if (!titleDone.add(key)) {
            return;
        }
        cosmetics.get().grant(account, Cosmetics.Kind.TITLE, def.title().id(), "island_achievement:" + def.id() + ":" + islandId, false)
                .whenComplete((added, error) -> core.scheduler().ensureMain("achievements-island-title", () -> {
                    if (error != null) {
                        titleDone.remove(key);
                        core.plugin().getLogger().warning("Achievements: island title '" + def.title().id() + "' could not be granted to "
                                + account + ": " + Database.cause(error).getMessage());
                        return;
                    }
                    Player player = Bukkit.getPlayer(account);
                    if (Boolean.TRUE.equals(added) && player != null) {
                        module.msg().send(player, "unlock.title", Msg.c("display", cosmetics.get().display(player, Cosmetics.Kind.TITLE, def.title().id())));
                    }
                }));
    }

    // ------------------------------------------------------------------ saving

    void saveAll() {
        if (tracker.resetting()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (IslandProgress p : islands.values()) {
            if (!p.loaded()) {
                continue;
            }
            List<IslandProgress.Row> rows = p.drain();
            if (rows.isEmpty()) {
                continue;
            }
            tracker.queue(c -> {
                store.saveIsland(c, p.islandId(), rows, now);
                return null;
            }).whenComplete((v, error) -> {
                if (error != null) {
                    core.scheduler().main("achievements-island-retry", () -> {
                        if (islands.get(p.islandId()) == p) {
                            p.restore(rows);
                        }
                    });
                    core.plugin().getLogger().warning("Achievements: saving island " + p.islandId() + " failed, retrying with the next save: "
                            + Database.cause(error).getMessage());
                }
            });
        }
    }

    /** Shutdown: writes what is left (blocking; the database closes right after the modules). */
    void saveBlocking() {
        long now = System.currentTimeMillis();
        for (IslandProgress p : islands.values()) {
            List<IslandProgress.Row> rows = p.drain();
            if (rows.isEmpty()) {
                continue;
            }
            try {
                core.database().blockingTransaction(c -> {
                    store.saveIsland(c, p.islandId(), rows, now);
                    return null;
                });
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.SEVERE, "Achievements: could not save island " + p.islandId(), e);
            }
        }
    }

    /** Takes every island's unsaved rows (season reset: written in the reset's own transaction). */
    Map<UUID, List<IslandProgress.Row>> drainAll() {
        Map<UUID, List<IslandProgress.Row>> out = new HashMap<>();
        for (IslandProgress p : islands.values()) {
            List<IslandProgress.Row> rows = p.drain();
            if (!rows.isEmpty()) {
                out.put(p.islandId(), rows);
            }
        }
        return out;
    }

    /** Season reset: every island starts from zero (stored rows were wiped by the reset). */
    void resetAll() {
        List<UUID> ids = new ArrayList<>(islands.keySet());
        islands.clear();
        contributions.clear();
        team.clear();
        core.services().optional(ProgressionService.class).ifPresent(p -> ids.forEach(p::requestLevelUpdate));
    }

    /** Member keys (profile keys) of every island of the islands we know, for the season summary. Main thread. */
    Map<UUID, UUID> memberIslands() {
        Map<UUID, UUID> out = new HashMap<>();
        islandService().ifPresent(s -> {
            for (UUID island : islands.keySet()) {
                for (UUID member : s.members(island).keySet()) {
                    out.put(member, island);
                }
            }
        });
        return out;
    }

    Set<UUID> teamIslands() {
        Set<UUID> out = new HashSet<>();
        for (UUID island : islands.keySet()) {
            if (isTeam(island)) {
                out.add(island);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ islands removed

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandRemoved(IslandRemovedEvent event) {
        UUID id = event.islandId();
        islands.remove(id);
        contributions.remove(id);
        tracker.queue(c -> store.deleteIsland(c, id)).whenComplete((rows, error) -> {
            if (error != null) {
                core.plugin().getLogger().warning("Achievements: could not remove island " + id + ": " + Database.cause(error).getMessage());
                return;
            }
            core.audit().log(AuditCategory.ISLAND, "island_achievements_removed", Actor.system(AchievementsModule.ID), id.toString(),
                    (long) rows, null, Map.of("reason", event.reason() == null ? "" : event.reason()));
        });
    }
}
