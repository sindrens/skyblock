package net.skybornmc.skyblock.npcs;

import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where a leaderboard's rows come from (pure, unit-tested): {@code money} (or another currency), {@code skill <type>},
 * {@code island <stat> [period] [board]}, or {@code none} (shown as "Coming soon", e.g. island value until Phase 5).
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

    /** One source text; empty = invalid. */
    static Optional<BoardSource> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] p = text.trim().toLowerCase(Locale.ROOT).split("\\s+");
        switch (p[0]) {
            case "none":
                return p.length == 1 ? Optional.of(new None()) : Optional.empty();
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
