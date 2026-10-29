package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.util.NumberFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Player-facing XP numbers: {@code 1.5}, {@code 12}, {@code 950}, {@code 1.2k}, {@code 3.4M}; progress bars and percents. */
final class XpFormat {

    private XpFormat() {
    }

    /** One decimal below 1,000 (dropped when whole), compact above. Rounded down, never shows more than earned. */
    static String xp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "?";
        }
        if (Math.abs(value) >= 1000) {
            return NumberFormatter.compact(value);
        }
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
    }

    /** Whole percent, rounded down (100 only when the progress is complete). */
    static int percent(double progress) {
        if (!(progress > 0)) {
            return 0;
        }
        return (int) Math.min(100, Math.floor(progress * 100 + 1e-9));
    }

    /** Filled segments of a bar of {@code length} for {@code progress} (rounded down; full only at 100%). */
    static int filled(double progress, int length) {
        if (!(progress > 0) || length <= 0) {
            return 0;
        }
        return (int) Math.min(length, Math.floor(progress * length + 1e-9));
    }
}
