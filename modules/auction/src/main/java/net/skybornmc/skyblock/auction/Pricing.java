package net.skybornmc.skyblock.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.skybornmc.skyblock.common.command.Durations;

/** Fee, tax and duration math (pure). Percentages are rounded down to whole dollars, then raised to the minimum. */
final class Pricing {

    private Pricing() {
    }

    /** Listing fee for {@code price}: {@code max(min, floor(price × percent / 100))}, never above the price. */
    static long fee(AuctionConfig config, long price) {
        return percentOf(price, config.feePercent, config.feeMin);
    }

    /** Sales tax for {@code price}: {@code max(min, floor(price × percent / 100))}, never above the price. */
    static long tax(AuctionConfig config, long price) {
        return percentOf(price, config.taxPercent, config.taxMin);
    }

    /** What the seller receives. */
    static long net(AuctionConfig config, long price) {
        return price - tax(config, price);
    }

    static long percentOf(long price, double percent, long min) {
        if (price <= 0) {
            return 0;
        }
        long value = BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(Math.max(0, percent)))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).longValueExact();
        return Math.min(price, Math.max(Math.max(0, min), value));
    }

    /** The configured durations that parse and are between 1 minute and 7 days, in config order. */
    static List<String> durations(AuctionConfig config) {
        List<String> out = new ArrayList<>();
        for (String raw : config.durations) {
            Duration d = parse(raw);
            if (d != null && !out.contains(raw.trim().toLowerCase(Locale.ROOT))) {
                out.add(raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (out.isEmpty()) {
            out.add("24h");
        }
        return out;
    }

    /** The default duration, or the first valid one if the default is not in the list. */
    static String defaultDuration(AuctionConfig config) {
        List<String> all = durations(config);
        String wanted = config.defaultDuration == null ? "" : config.defaultDuration.trim().toLowerCase(Locale.ROOT);
        return all.contains(wanted) ? wanted : all.getFirst();
    }

    /** A duration from the list, or {@code null} if it is not offered. */
    static Duration offered(AuctionConfig config, String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return durations(config).contains(key) ? parse(key) : null;
    }

    private static Duration parse(String raw) {
        try {
            Duration d = Durations.parse(raw.trim());
            return d.compareTo(Duration.ofMinutes(1)) >= 0 && d.compareTo(Duration.ofDays(7)) <= 0 ? d : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
