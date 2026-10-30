package net.skybornmc.skyblock.rewards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Parsing of the list settings in {@code daily.yml} (pure, unit tested) and the pouch math shared with crates: money =
 * minutes of {@code REF(t)}, crystals = base x growth^(t-1) (ECONOMY_BALANCE §1.2, §9.1).
 */
final class RewardTables {

    /** Most days a calendar may have (what the /daily menu shows). */
    static final int MAX_DAYS = 30;

    private RewardTables() {
    }

    /**
     * The calendar from lines {@code "<day>: <reward>"}. Days must run 1..n without gaps; a missing or broken day is
     * reported and filled with {@code filler} so the calendar always works.
     */
    static List<RewardSpec> calendar(List<String> lines, RewardSpec filler, Consumer<String> problems) {
        TreeMap<Integer, RewardSpec> days = new TreeMap<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            String line = raw == null ? "" : raw.trim();
            int colon = line.indexOf(':');
            Long day = colon > 0 ? RewardSpec.number(line.substring(0, colon)) : null;
            if (day == null || day < 1 || day > MAX_DAYS) {
                problems.accept("daily.yml -> calendar.days: '" + line + "' must look like '<day 1-" + MAX_DAYS + ">: <reward>' (skipped)");
                continue;
            }
            if (days.containsKey(day.intValue())) {
                problems.accept("daily.yml -> calendar.days: day " + day + " is listed twice (the first one is used)");
                continue;
            }
            String spec = line.substring(colon + 1);
            RewardSpec.parse(spec, p -> problems.accept("daily.yml -> calendar.days day " + day + ": " + p))
                    .ifPresentOrElse(s -> days.put(day.intValue(), s), () -> days.put(day.intValue(), filler));
        }
        if (days.isEmpty()) {
            problems.accept("daily.yml -> calendar.days is empty; using one day of " + filler.kind().key());
            return List.of(filler);
        }
        int last = days.lastKey();
        List<RewardSpec> out = new ArrayList<>(last);
        for (int d = 1; d <= last; d++) {
            RewardSpec s = days.get(d);
            if (s == null) {
                problems.accept("daily.yml -> calendar.days: day " + d + " is missing; it gives " + filler.kind().key() + " for now");
                s = filler;
            }
            out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    /** Thresholds to ids from lines {@code "<number>: <id>"} (streak titles, playtime titles). */
    static NavigableMap<Integer, String> thresholds(List<String> lines, String where, Consumer<String> problems) {
        TreeMap<Integer, String> out = new TreeMap<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            String line = raw == null ? "" : raw.trim();
            int colon = line.indexOf(':');
            Long n = colon > 0 ? RewardSpec.number(line.substring(0, colon)) : null;
            String id = colon > 0 ? line.substring(colon + 1).trim() : "";
            if (n == null || n < 1 || n > Integer.MAX_VALUE || !id.matches("[a-z0-9_-]{1,48}")) {
                problems.accept(where + ": '" + line + "' must look like '<number>: <id>' (skipped)");
                continue;
            }
            if (out.putIfAbsent(n.intValue(), id) != null) {
                problems.accept(where + ": " + n + " is listed twice (the first one is used)");
            }
        }
        return Collections.unmodifiableNavigableMap(out);
    }

    /** Whole numbers ≥ 0; bad values are reported and skipped. */
    static long[] longs(List<String> raw, String where, Consumer<String> problems) {
        List<Long> out = new ArrayList<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            Long v = RewardSpec.number(String.valueOf(s));
            if (v == null) {
                problems.accept(where + ": '" + s + "' is not a whole number >= 0 (skipped)");
            } else {
                out.add(v);
            }
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    /** Ascending whole numbers ≥ 0 (tier unlock levels); bad or falling values are skipped. */
    static int[] levels(List<String> raw, String where, Consumer<String> problems) {
        List<Integer> out = new ArrayList<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            Long v = RewardSpec.number(String.valueOf(s));
            if (v == null || v > Integer.MAX_VALUE || (!out.isEmpty() && v < out.getLast())) {
                problems.accept(where + ": '" + s + "' must be a whole number, not lower than the one before (skipped)");
                continue;
            }
            out.add(v.intValue());
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Stage tier (1..n) for the highest stage skill level; {@code tierLevels[i]} unlocks tier i+1. */
    static int tierFor(int level, int[] tierLevels) {
        int tier = 1;
        for (int i = 0; i < tierLevels.length; i++) {
            if (level >= tierLevels[i]) {
                tier = i + 1;
            }
        }
        return tier;
    }

    /** Money pouch: {@code minutes} of reference income at the tier (the last value above the list); never overflows. */
    static long money(long minutes, int tier, long[] refPerMinute) {
        if (refPerMinute.length == 0 || minutes <= 0) {
            return 0;
        }
        long ref = refPerMinute[Math.max(0, Math.min(refPerMinute.length - 1, tier - 1))];
        if (ref <= 0) {
            return 0;
        }
        return ref > Long.MAX_VALUE / minutes ? Long.MAX_VALUE : ref * minutes;
    }

    /** Crystal pouch: base x growth^(tier-1), rounded. */
    static long crystals(long base, int tier, double growth) {
        if (base <= 0) {
            return 0;
        }
        double g = growth > 0 && Double.isFinite(growth) ? growth : 1.0;
        double v = base * Math.pow(g, Math.max(0, tier - 1));
        return v >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, Math.round(v));
    }

    /** Thresholds above {@code above} and at or below {@code value}, ascending (milestones newly reached). */
    static List<Integer> crossed(NavigableMap<Integer, String> thresholds, int above, int value) {
        if (value <= above) {
            return List.of();
        }
        return List.copyOf(thresholds.subMap(above, false, value, true).keySet());
    }

    /** The reward used for a missing or broken calendar day: a Crystal Pouch of this base. */
    static RewardSpec filler(long crystals) {
        return new RewardSpec(RewardSpec.Kind.CRYSTALS, "", Math.max(1, crystals), 0, -1);
    }
}
