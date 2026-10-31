package net.skybornmc.skyblock.npcs;

import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.progression.IslandTop;
import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where a leaderboard's rows come from (pure, unit-tested): {@code money} (or another currency), {@code skill <type>},
 * {@code island <stat> [period] [board]}, {@code island_level [all|solo|team]}, {@code island_value [all|solo|team]}
 * (PHASE-5 M5.2, progression module), {@code island_gain [this_week] [all|solo|team|cycle]},
 * {@code pillar_gain [this_week] [all|solo|team|cycle]} (M5.7 weekly boards; {@code cycle} switches between Solo and Team)
 * or {@code none} (shown as "Coming soon").
 * Parsed from {@code npcs.yml → leaderboards.sources}.
 */
sealed interface BoardSource {

    /** Same key = same query, so boards that show the same data share one read. */
    String key();

    record None() implements BoardSource {
        @Override
        public String key() {
            return "none";
        }
    }

    record Money(Currency currency) implements BoardSource {
        @Override
        public String key() {
            return "currency:" + currency.key();
        }
    }

    record Skill(SkillType skill) implements BoardSource {
        @Override
        public String key() {
            return "skill:" + skill.id();
        }
    }

    record Island(IslandStat stat, IslandStatsService.Period period, IslandStatsService.Board board) implements BoardSource {
        @Override
        public String key() {
            return "island:" + stat.key() + ":" + period.name().toLowerCase(Locale.ROOT) + ":" + board.name().toLowerCase(Locale.ROOT);
        }
    }

    /** Island level or block worth board of the progression module (PHASE-5 M5.2). */
    record IslandLevel(IslandTop.Kind kind, IslandTop.Board board) implements BoardSource {
        @Override
        public String key() {
            return "island_" + kind.id() + ":" + board.id();
        }
    }

    /**
     * Weekly island gain or weekly pillar gain board of the progression module (PHASE-5 M5.7). {@code cycle} = the panel
     * switches between the Solo and the Team board ({@code board} is then {@link IslandTop.Board#SOLO}, unused).
     */
    record IslandWeekly(IslandTop.Kind kind, IslandTop.Board board, boolean cycle) implements BoardSource {
        @Override
        public String key() {
            return "island_" + kind.id() + ":" + (cycle ? "cycle" : board.id());
        }
    }

    /** One source text; empty = invalid. */
    static Optional<BoardSource> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] p = text.trim().toLowerCase(Locale.ROOT).split("\\s+");
        switch (p[0]) {
            case "none":
                return p.length == 1 ? Optional.of(new None()) : Optional.empty();
            case "island_level", "island_value": {
                if (p.length > 2) {
                    return Optional.empty();
                }
                IslandTop.Kind kind = p[0].equals("island_level") ? IslandTop.Kind.LEVEL : IslandTop.Kind.VALUE;
                Optional<IslandTop.Board> board = p.length == 2 ? IslandTop.Board.byId(p[1]) : Optional.of(IslandTop.Board.ALL);
                return board.map(b -> new IslandLevel(kind, b));
            }
            case "island_gain", "pillar_gain": {
                IslandTop.Kind kind = p[0].equals("island_gain") ? IslandTop.Kind.WEEKLY_GAIN : IslandTop.Kind.WEEKLY_PILLARS;
                int i = 1;
                if (i < p.length && p[i].equals("this_week")) {
                    i++;
                }
                if (p.length - i > 1) {
                    return Optional.empty();
                }
                if (i == p.length) {
                    return Optional.of(new IslandWeekly(kind, IslandTop.Board.ALL, false));
                }
                if (p[i].equals("cycle")) {
                    return Optional.of(new IslandWeekly(kind, IslandTop.Board.SOLO, true));
                }
                return IslandTop.Board.byId(p[i]).map(b -> new IslandWeekly(kind, b, false));
            }
            case "skill":
                return p.length == 2 ? SkillType.byId(p[1]).map(Skill::new) : Optional.empty();
            case "island": {
                if (p.length < 2 || p.length > 4) {
                    return Optional.empty();
                }
                Optional<IslandStat> stat = IslandStat.byKey(p[1]);
                IslandStatsService.Period period = p.length > 2 ? enumOf(IslandStatsService.Period.class, p[2]) : IslandStatsService.Period.ALL_TIME;
                IslandStatsService.Board board = p.length > 3 ? enumOf(IslandStatsService.Board.class, p[3]) : IslandStatsService.Board.ALL;
                if (stat.isEmpty() || period == null || board == null) {
                    return Optional.empty();
                }
                return Optional.of(new Island(stat.get(), period, board));
            }
            default:
                return p.length == 1 ? Currency.byKey(p[0]).map(Money::new) : Optional.empty();
        }
    }

    /** {@code leaderboards.sources} → id → source; broken lines become problems, unknown ids default to {@link None}. */
    static Map<String, BoardSource> parseAll(List<String> lines, List<String> problems) {
        Map<String, BoardSource> out = new LinkedHashMap<>();
        List<String> pairProblems = new ArrayList<>();
        RoleRules.pairs("leaderboards.sources", lines, pairProblems).forEach((id, text) -> {
            Optional<BoardSource> source = parse(text);
            if (source.isEmpty()) {
                problems.add("npcs.yml -> leaderboards.sources: '" + id + ": " + text + "' is not a valid source (shows Coming soon)");
                out.put(id, new None());
            } else {
                out.put(id, source.get());
            }
        });
        problems.addAll(pairProblems);
        return out;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String text) {
        try {
            return Enum.valueOf(type, text.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
