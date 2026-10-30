package net.skybornmc.skyblock.npcs;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.leaderboard.TopLine;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Leaderboard holograms: top {@code leaderboards.size} rows per board, read every {@code leaderboards.refresh-minutes}
 * through the services (database work off the main thread; one read per distinct source), then the texts are set on
 * the main thread. #1/#2/#3 use the {@code top1}/{@code top2}/{@code top3} colors (V2 style, messages/npcs.yml).
 */
final class Leaderboards {

    /** One ranked row, ready to show. */
    record Row(String player, Component value) {
    }

    /** One line of a board: message key, place, and the row (null for an empty place). */
    record Line(String key, int place, Row row) {
    }

    private final NpcsModule module;
    private volatile Map<String, BoardSource> sources = Map.of();
    /** Rows per source key; missing = not read yet. */
    private final Map<String, List<Row>> rows = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private Scheduler.Task task;

    Leaderboards(NpcsModule module) {
        this.module = module;
    }

    /** Takes the parsed sources ({@link NpcPlan}, parsed off the main thread) and starts the refresh task. */
    void start(Map<String, BoardSource> parsed) {
        stop();
        sources = parsed;
        long period = module.config().refreshMinutes * 60L * 20L;
        // First read shortly after start (services of later modules are registered by then).
        task = module.core().scheduler().asyncRepeat(40, period, this::refresh);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** The source of a board id (unknown ids show "Coming soon"). */
    BoardSource sourceOf(String boardId) {
        return sources.getOrDefault(boardId, new BoardSource.None());
    }

    /** Starts one read per distinct source used by a placed board. Any thread. */
    void refresh() {
        Set<String> started = new HashSet<>();
        for (String boardId : module.boardIds()) {
            BoardSource source = sourceOf(boardId);
            if (source instanceof BoardSource.None || !started.add(source.key()) || !inFlight.add(source.key())) {
                continue;
            }
            CompletableFuture<List<Row>> read;
            try {
                read = read(source);
            } catch (RuntimeException e) {
                inFlight.remove(source.key());
                module.core().plugin().getLogger().warning("[npcs] leaderboard " + source.key() + " failed: " + e);
                continue;
            }
            read.whenComplete((list, error) -> {
                inFlight.remove(source.key());
                if (error != null) {
                    module.core().plugin().getLogger().warning("[npcs] leaderboard " + source.key() + " failed: " + error);
                    return;
                }
                module.core().scheduler().ensureMain("npcs:leaderboard", () -> {
                    rows.put(source.key(), list);
                    module.boardUpdated(source.key());
                });
            });
        }
    }

    /**
     * Reads one source off the main thread (the services' database futures). Player boards are formatted right there;
     * island boards on the main thread (island names come from the islands service).
     */
    private CompletableFuture<List<Row>> read(BoardSource source) {
        int size = module.config().size;
        var services = module.core().services();
        Scheduler scheduler = module.core().scheduler();
        return switch (source) {
            case BoardSource.None none -> CompletableFuture.completedFuture(List.of());
            // QUICK1 (PERFORMANCE "npcs:board-skill"): player boards only parse messages (thread-safe, no world access),
            // so they are formatted on the database thread; nothing of them runs on the main thread any more.
            case BoardSource.Money m -> services.optional(EconomyService.class)
                    .map(eco -> eco.top(m.currency(), size).thenApply(list -> players(list, v -> money(eco, m.currency(), v))))
                    .orElse(CompletableFuture.completedFuture(List.of()));
            case BoardSource.Skill s -> services.optional(SkillService.class)
                    .map(sk -> sk.top(s.skill(), size).thenApply(list -> players(list, xp -> module.text("leaderboard.value.skill",
                            Msg.number("level", sk.levelFor(xp)), Msg.number("xp", xp)))))
                    .orElse(CompletableFuture.completedFuture(List.of()));
            // PHASE-5 M5.2: the progression module keeps its boards ranked in memory (names resolved there), so this only
            // formats the cached rows.
            case BoardSource.IslandLevel l -> CompletableFuture.completedFuture(services
                    .optional(net.skybornmc.skyblock.common.progression.ProgressionService.class)
                    .map(pr -> levels(pr.islandTop(l.kind(), l.board(), size), l.kind())).orElse(List.of()));
            case BoardSource.Island i -> services.optional(IslandStatsService.class)
                    .map(st -> st.top(i.stat(), i.period(), i.board(), size)
                            .thenCompose(list -> scheduler.supplyMain("npcs:board-island", () -> islands(list, i.stat()))))
                    .orElse(CompletableFuture.completedFuture(List.of()));
        };
    }

    private Component money(EconomyService eco, Currency currency, long amount) {
        return module.text("leaderboard.value.currency", Msg.p("amount", eco.format(currency, amount)));
    }

    private List<Row> players(List<TopLine> list, java.util.function.LongFunction<Component> value) {
        List<Row> out = new ArrayList<>(list.size());
        for (TopLine line : list) {
            out.add(new Row(displayName(line.name(), module.plain("leaderboard.unknown-player")), value.apply(line.value())));
        }
        return out;
    }

    private List<Row> levels(List<net.skybornmc.skyblock.common.progression.IslandTop.Entry> list,
                             net.skybornmc.skyblock.common.progression.IslandTop.Kind kind) {
        List<Row> out = new ArrayList<>(list.size());
        for (var e : list) {
            Component value = kind == net.skybornmc.skyblock.common.progression.IslandTop.Kind.LEVEL
                    ? module.text("leaderboard.value.island-level", Msg.number("level", e.level()))
                    : module.text("leaderboard.value.island-value", Msg.p("amount", Format.money(Math.round(e.blockWorth()))));
            out.add(new Row(displayName(e.name(), module.plain("leaderboard.unknown-island")), value));
        }
        return out;
    }

    /** Main thread: island names from the islands service (else the owner's name). */
    private List<Row> islands(List<IslandStatsService.TopEntry> list, IslandStat stat) {
        IslandService islands = module.core().services().optional(IslandService.class).orElse(null);
        List<Row> out = new ArrayList<>(list.size());
        for (IslandStatsService.TopEntry e : list) {
            String name = null;
            if (islands != null) {
                var info = islands.island(e.islandId()).orElse(null);
                if (info != null) {
                    name = info.name();
                    if ((name == null || name.isBlank()) && info.owner() != null) {
                        name = Bukkit.getOfflinePlayer(info.owner()).getName();
                    }
                }
            }
            String amount = switch (stat.unit()) {
                case MONEY -> Format.money(e.amount());
                case SECONDS -> Format.duration(java.time.Duration.ofSeconds(e.amount()));
                case COUNT -> Format.number(e.amount());
            };
            out.add(new Row(displayName(name, module.plain("leaderboard.unknown-island")),
                    module.text("leaderboard.value.island", Msg.p("amount", amount))));
        }
        return out;
    }

    /** Rows of a board's source; {@code null} = not read yet. */
    List<Row> rowsOf(String boardId) {
        BoardSource source = sourceOf(boardId);
        return source instanceof BoardSource.None ? null : rows.get(source.key());
    }

    // ------------------------------------------------------------------ pure helpers (unit-tested)

    /** Message key of a place: #1-#3 have their own colors. */
    static String rankKey(int place) {
        return switch (place) {
            case 1 -> "leaderboard.top1";
            case 2 -> "leaderboard.top2";
            case 3 -> "leaderboard.top3";
            default -> "leaderboard.place";
        };
    }

    /** Always exactly {@code size} lines (empty places padded) so the board never changes height. */
    static List<Line> lines(List<Row> rows, int size) {
        List<Line> out = new ArrayList<>(size);
        for (int place = 1; place <= size; place++) {
            Row row = rows != null && place <= rows.size() ? rows.get(place - 1) : null;
            out.add(new Line(row == null ? "leaderboard.empty-place" : rankKey(place), place, row));
        }
        return out;
    }

    /** A shown name: trimmed, at most 16 characters, {@code fallback} when unknown. */
    static String displayName(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String n = name.strip();
        return n.length() > 16 ? n.substring(0, 16) : n;
    }
}
