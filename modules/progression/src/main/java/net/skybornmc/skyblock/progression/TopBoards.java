package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.IslandTop;
import net.skybornmc.skyblock.common.progression.IslandWeeklyTopEvent;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The island leaderboards (PHASE-5 M5.7): island top, value, weekly island gain, weekly pillar gain and one board per
 * pillar, each solo, team and all. Every {@code top.refresh-seconds} the main thread only copies the numbers it owns
 * (levels, pillars, the week's base values); sorting, names and the boards are made off the main thread and published
 * as one immutable snapshot that menus, placeholders and the Hall of Heroes read from any thread.
 *
 * <p><b>Weeks:</b> Monday 00:00 in {@code top.time-zone} (Europe/Oslo). At the start of a week every island's earned
 * level points and summed pillar levels are stored ({@code leaderboard_week_base}); the weekly boards rank what was
 * gained since (owner P5-26: earned sources only, so moving blocks or money does nothing). When a week ends its final
 * places and the next week's base are written in one transaction, then {@link IslandWeeklyTopEvent} fires (the weekly
 * rewards hook). A server that was down over Monday closes the old week at its first ranking.</p>
 *
 * <p><b>Hidden islands:</b> islands whose leader has {@code skyblock.progression.top.hidden} (staff) are never ranked;
 * the flag is kept in {@code leaderboard_hidden} so offline staff stay hidden.</p>
 */
final class TopBoards implements Listener {

    static final String PERM_HIDDEN = "skyblock.progression.top.hidden";

    /** Everything the boards show, made off the main thread. */
    record Snapshot(Map<IslandTop.Kind, Map<IslandTop.Board, List<IslandTop.Entry>>> kinds,
                    Map<Pillar, Map<IslandTop.Board, List<IslandTop.Entry>>> pillars, Map<UUID, Integer> places,
                    Map<UUID, TopBoard.Candidate> islands, long rankedAt) {

        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), 0);
    }

    /** The candidates of one ranking plus every island's current week values (the next base). */
    record Collected(List<TopBoard.Candidate> candidates, Map<UUID, WeeklyStore.Base> now) {
    }

    private record Loaded(Set<UUID> hidden, int baseWeek, Map<UUID, WeeklyStore.Base> base) {
    }

    private record Rolled(boolean done, List<IslandTop.Entry> solo, List<IslandTop.Entry> team, int places) {
    }

    private static final WeeklyStore.Base ZERO = new WeeklyStore.Base(0, 0);

    private final Core core;
    private final LevelConfig config;
    private final LevelManager levels;
    private final PillarManager pillars;
    private final WeeklyStore store;
    private final int season;
    private final ZoneId zone;
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile boolean ranking;
    /** Main thread: the week whose base is in {@link #base} (-1 = none yet). */
    private int baseWeek = -1;
    private Map<UUID, WeeklyStore.Base> base = Map.of();
    private boolean loaded;
    private boolean rolling;
    private boolean closed;
    private Scheduler.Task retry;

    TopBoards(Core core, LevelConfig config, LevelManager levels, PillarManager pillars, WeeklyStore store, int season) {
        this.core = core;
        this.config = config;
        this.levels = levels;
        this.pillars = pillars;
        this.store = store;
        this.season = season;
        ZoneId z;
        try {
            z = ZoneId.of(config.topTimeZone);
        } catch (RuntimeException e) {
            core.plugin().getLogger().warning("progression.yml → top.time-zone: unknown zone '" + config.topTimeZone + "', using Europe/Oslo");
            z = ZoneId.of("Europe/Oslo");
        }
        this.zone = z;
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        startTasks();
        core.ranks().onRankChange(uuid -> {
            if (!closed) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    syncHidden(p);
                }
            }
        });
        levels.onLoaded(this::rebuild);
        load();
    }

    void applyConfig() {
        if (!tasks.isEmpty()) {
            stopTasks();
            startTasks();
        }
    }

    private void startTasks() {
        long period = Math.max(10, config.topRefreshSeconds) * 20L;
        tasks.add(core.scheduler().repeat("progression-top", 100L, period, this::rebuild));
    }

    private void stopTasks() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
    }

    void shutdown() {
        closed = true;
        stopTasks();
        if (retry != null) {
            retry.cancel();
        }
        levels.onLoaded(null);
    }

    private void load() {
        int week = WeekWindow.weekOf(System.currentTimeMillis(), zone);
        core.database().async(c -> {
            Set<UUID> h = store.loadHidden(c);
            if (store.hasBase(c, season, week)) {
                return new Loaded(h, week, store.loadBase(c, season, week));
            }
            OptionalInt previous = store.previousWeek(c, season, week);
            return previous.isPresent() ? new Loaded(h, previous.getAsInt(), store.loadBase(c, season, previous.getAsInt()))
                    : new Loaded(h, -1, Map.of());
        }).whenComplete((l, error) -> core.scheduler().main("progression-top-load", () -> {
            if (closed) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: loading the weekly boards failed; retrying in 30 s", Database.cause(error));
                retry = core.scheduler().later("progression-top-load-retry", 30 * 20L, this::load);
                return;
            }
            hidden.clear();
            hidden.addAll(l.hidden());
            baseWeek = l.baseWeek();
            base = l.base();
            loaded = true;
            rolling = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                syncHidden(p);
            }
            rebuild();
        }));
    }

    // ------------------------------------------------------------------ ranking

    /** Main thread: copies the numbers, closes a finished week, then ranks off the main thread. */
    void rebuild() {
        IslandService islands = core.services().optional(IslandService.class).orElse(null);
        if (closed || !loaded || islands == null || !levels.ready() || !pillars.ready()) {
            return;
        }
        int week = WeekWindow.weekOf(System.currentTimeMillis(), zone);
        boolean roll = baseWeek != week && !rolling;
        if (ranking && !roll) {
            return; // the last ranking is still running: nothing to copy yet
        }
        Collected collected = collect(islands);
        if (roll) {
            rollOver(week, collected);
        }
        if (ranking) {
            return;
        }
        ranking = true;
        core.scheduler().async(() -> {
            try {
                snapshot = rank(named(collected.candidates()), System.currentTimeMillis());
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: ranking the island boards failed", e);
            } finally {
                ranking = false;
            }
        });
    }

    /** Main thread: one candidate per calculated island, weekly gains against the stored base. */
    private Collected collect(IslandService islands) {
        ProfileService profiles = ProfileService.of(core.services());
        Pillar[] all = Pillar.values();
        List<LevelManager.Snap> snaps = levels.snapshot();
        List<TopBoard.Candidate> out = new ArrayList<>(snaps.size());
        Map<UUID, WeeklyStore.Base> now = new HashMap<>(snaps.size() * 2);
        for (LevelManager.Snap s : snaps) {
            IslandService.IslandInfo info = islands.island(s.island()).orElse(null);
            if (info == null) {
                continue;
            }
            int[] lv = new int[all.length];
            double[] xp = new double[all.length];
            int pillarSum = 0;
            for (Pillar p : all) {
                lv[p.ordinal()] = pillars.pillarLevel(s.island(), p);
                xp[p.ordinal()] = pillars.pillarXp(s.island(), p);
                pillarSum += lv[p.ordinal()];
            }
            now.put(s.island(), new WeeklyStore.Base(s.earned(), pillarSum));
            WeeklyStore.Base start = baseWeek >= 0 ? base.getOrDefault(s.island(), ZERO) : null;
            double earnedGain = start == null ? 0 : TopBoard.gain(s.earned(), start.earned());
            int pillarGain = start == null ? 0 : Math.max(0, pillarSum - start.pillarLevels());
            UUID account = info.owner() == null ? null : profiles.ownerOf(info.owner());
            out.add(new TopBoard.Candidate(info.id(), info.owner(), account, info.name(), info.team(), s.level(), s.total(),
                    s.reachedAt(), s.blockWorth(), earnedGain, pillarGain, lv, xp, "", account != null && hidden.contains(account)));
        }
        return new Collected(out, now);
    }

    /** Off the main thread: leader names (player data reads), and the island name falls back to the leader's. */
    private List<TopBoard.Candidate> named(List<TopBoard.Candidate> list) {
        List<TopBoard.Candidate> out = new ArrayList<>(list.size());
        for (TopBoard.Candidate c : list) {
            String leader = c.account() == null ? "" : names.computeIfAbsent(c.account(), a -> {
                String n = Bukkit.getOfflinePlayer(a).getName();
                return n == null ? "" : n;
            });
            String name = c.name() == null || c.name().isBlank() ? leader : c.name();
            out.add(new TopBoard.Candidate(c.islandId(), c.ownerKey(), c.account(), name, c.team(), c.level(), c.points(), c.reachedAt(),
                    c.blockWorth(), c.earnedGain(), c.pillarGain(), c.pillarLevel(), c.pillarXp(), leader, c.hidden()));
        }
        return out;
    }

    /** Every board from the candidates (pure apart from the clock value passed in). */
    static Snapshot rank(List<TopBoard.Candidate> candidates, long now) {
        Map<IslandTop.Kind, Map<IslandTop.Board, List<IslandTop.Entry>>> kinds = new EnumMap<>(IslandTop.Kind.class);
        for (IslandTop.Kind kind : IslandTop.Kind.values()) {
            Map<IslandTop.Board, List<IslandTop.Entry>> byBoard = new EnumMap<>(IslandTop.Board.class);
            for (IslandTop.Board board : IslandTop.Board.values()) {
                byBoard.put(board, List.copyOf(TopBoard.rank(candidates, kind, board)));
            }
            kinds.put(kind, Collections.unmodifiableMap(byBoard));
        }
        Map<Pillar, Map<IslandTop.Board, List<IslandTop.Entry>>> pillarBoards = new EnumMap<>(Pillar.class);
        for (Pillar pillar : Pillar.values()) {
            Map<IslandTop.Board, List<IslandTop.Entry>> byBoard = new EnumMap<>(IslandTop.Board.class);
            for (IslandTop.Board board : IslandTop.Board.values()) {
                byBoard.put(board, List.copyOf(TopBoard.rankPillar(candidates, pillar, board)));
            }
            pillarBoards.put(pillar, Collections.unmodifiableMap(byBoard));
        }
        Map<UUID, TopBoard.Candidate> byIsland = new HashMap<>(candidates.size() * 2);
        candidates.forEach(c -> byIsland.put(c.islandId(), c));
        Map<UUID, Integer> places = TopBoard.ownPlaces(kinds.get(IslandTop.Kind.LEVEL).get(IslandTop.Board.SOLO),
                kinds.get(IslandTop.Kind.LEVEL).get(IslandTop.Board.TEAM));
        return new Snapshot(Collections.unmodifiableMap(kinds), Collections.unmodifiableMap(pillarBoards), Map.copyOf(places),
                Collections.unmodifiableMap(byIsland), now);
    }

    // ------------------------------------------------------------------ weeks

    /**
     * Closes the stored week (if any) with the current gains and starts {@code week} with the current values, in one
     * transaction. Ranking and SQL run on the database thread.
     */
    private void rollOver(int week, Collected collected) {
        rolling = true;
        Integer oldWeek = baseWeek >= 0 ? baseWeek : null;
        int placesPerBoard = Math.max(1, config.weekPlaces);
        List<TopBoard.Candidate> candidates = collected.candidates();
        Map<UUID, WeeklyStore.Base> next = Map.copyOf(collected.now());
        core.database().transaction(c -> {
            List<WeeklyStore.Place> places = new ArrayList<>();
            List<IslandTop.Entry> solo = List.of();
            List<IslandTop.Entry> team = List.of();
            if (oldWeek != null) {
                List<TopBoard.Candidate> named = named(candidates);
                solo = head(TopBoard.rank(named, IslandTop.Kind.WEEKLY_GAIN, IslandTop.Board.SOLO), placesPerBoard);
                team = head(TopBoard.rank(named, IslandTop.Kind.WEEKLY_GAIN, IslandTop.Board.TEAM), placesPerBoard);
                add(places, "weekly_gain_solo", solo);
                add(places, "weekly_gain_team", team);
                add(places, "weekly_pillars_solo", head(TopBoard.rank(named, IslandTop.Kind.WEEKLY_PILLARS, IslandTop.Board.SOLO), placesPerBoard));
                add(places, "weekly_pillars_team", head(TopBoard.rank(named, IslandTop.Kind.WEEKLY_PILLARS, IslandTop.Board.TEAM), placesPerBoard));
            }
            boolean done = store.rollOver(c, season, oldWeek, places, week, next);
            return new Rolled(done, solo, team, places.size());
        }).whenComplete((r, error) -> core.scheduler().main("progression-top-week", () -> {
            if (closed) {
                return;
            }
            rolling = false;
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: closing the week of the island boards failed; trying again at the next ranking",
                        Database.cause(error));
                return;
            }
            if (!r.done()) {
                loaded = false; // the week was started elsewhere (reload race): read it back
                load();
                return;
            }
            baseWeek = week;
            base = next;
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("season", season);
            details.put("closed_week", oldWeek == null ? "none" : String.valueOf(oldWeek));
            details.put("new_week", week);
            details.put("places", r.places());
            details.put("islands", next.size());
            core.audit().log(AuditCategory.ISLAND, "leaderboard_week", Actor.system("progression"), details);
            if (oldWeek != null) {
                Bukkit.getPluginManager().callEvent(new IslandWeeklyTopEvent(oldWeek, r.solo(), r.team()));
            }
            rebuild();
        }));
    }

    private static List<IslandTop.Entry> head(List<IslandTop.Entry> list, int n) {
        return list.size() <= n ? list : List.copyOf(list.subList(0, n));
    }

    private static void add(List<WeeklyStore.Place> out, String board, List<IslandTop.Entry> entries) {
        for (IslandTop.Entry e : entries) {
            out.add(new WeeklyStore.Place(board, e.place(), e.islandId(), e.value()));
        }
    }

    // ------------------------------------------------------------------ hidden (staff) islands

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        names.put(p.getUniqueId(), p.getName());
        if (loaded) {
            syncHidden(p);
        }
    }

    /** Main thread: keeps {@code leaderboard_hidden} in step with the player's permission (audited when it changes). */
    private void syncHidden(Player player) {
        UUID account = player.getUniqueId();
        boolean has = player.hasPermission(PERM_HIDDEN);
        if (has == hidden.contains(account)) {
            return;
        }
        if (has) {
            hidden.add(account);
        } else {
            hidden.remove(account);
        }
        String name = player.getName();
        long now = System.currentTimeMillis();
        core.database().async(c -> store.setHidden(c, account, has, now)).whenComplete((changed, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: saving the board visibility of " + name + " failed", Database.cause(error));
                return;
            }
            if (Boolean.TRUE.equals(changed)) {
                core.scheduler().ensureMain("progression-top-hidden", () -> core.audit().log(AuditCategory.ISLAND,
                        has ? "leaderboard_hidden" : "leaderboard_shown", Actor.system("progression"), account.toString(), name, null, null, null));
            }
        });
    }

    // ------------------------------------------------------------------ reads (any thread)

    List<IslandTop.Entry> top(IslandTop.Kind kind, IslandTop.Board board, int limit) {
        return limit(snapshot.kinds().getOrDefault(kind, Map.of()).getOrDefault(board, List.of()), limit);
    }

    List<IslandTop.Entry> pillarTop(Pillar pillar, IslandTop.Board board, int limit) {
        return limit(snapshot.pillars().getOrDefault(pillar, Map.of()).getOrDefault(board, List.of()), limit);
    }

    private static List<IslandTop.Entry> limit(List<IslandTop.Entry> list, int limit) {
        return limit <= 0 || list.size() <= limit ? list : list.subList(0, limit);
    }

    /** Place of the island on its own level board (solo or team), 0 when not ranked. */
    int place(UUID islandId) {
        return islandId == null ? 0 : snapshot.places().getOrDefault(islandId, 0);
    }

    /** The island's numbers at the last ranking (weekly gains, pillars), or null. */
    TopBoard.Candidate view(UUID islandId) {
        return islandId == null ? null : snapshot.islands().get(islandId);
    }

    /** True when the island's leader is hidden from the boards (staff). */
    boolean hiddenIsland(UUID islandId) {
        TopBoard.Candidate c = view(islandId);
        return c != null && c.hidden();
    }

    /** Milliseconds until the weekly boards start again (next Monday 00:00 in the board time zone). */
    long weekMillisLeft() {
        return WeekWindow.millisLeft(System.currentTimeMillis(), zone);
    }

    int cycleSeconds() {
        return Math.max(3, config.hallCycleSeconds);
    }
}
