package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.util.Format;

import java.time.Duration;

/** How progress values read in the menu (UI_STYLE §2: compact numbers in lore, durations like "2h 15m"). */
final class ValueFormat {

    private ValueFormat() {
    }

    /** Progress value: exact below 10,000, compact above; money with $; minutes as a duration. */
    static String format(Metric metric, long value) {
        long v = Math.max(0, value);
        return switch (metric.unit()) {
            case MONEY -> Format.money(v);
            case MINUTES -> Format.duration(Duration.ofMinutes(v));
            case LEVEL -> Format.number(v);
            case COUNT -> v < 10_000 ? Format.number(v) : Format.compact(v);
        };
    }

    /** Goal in the goals line: always short ("10k", "$1M", "50h"). */
    static String compact(Metric metric, long value) {
        long v = Math.max(0, value);
        return switch (metric.unit()) {
            case MONEY -> "$" + (v < 1_000 ? Format.number(v) : Format.compact(v));
            case MINUTES -> v % 60 == 0 && v >= 60 ? (v / 60) + "h" : v + "m";
            case LEVEL -> Format.number(v);
            case COUNT -> v < 1_000 ? Format.number(v) : Format.compact(v);
        };
    }
}
