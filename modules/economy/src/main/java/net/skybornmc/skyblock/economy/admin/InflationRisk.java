package net.skybornmc.skyblock.economy.admin;

import java.time.LocalDate;
import java.util.Map;

/**
 * The inflation risk badge of the dashboard (M3.8, docs/ECONOMY_BALANCE.md §7.2), pure. The sink ratio is money removed
 * / money created over the last {@code window} days (14: the 7-day average dips whenever many players save for the same
 * big purchase). <b>Act</b>: below the act line (60%) on {@code actDays} (3) days in a row. <b>Watch</b>: today's ratio
 * below the watch line (75%), or below the act line on fewer days in a row. <b>OK</b> otherwise. <b>No data</b> until
 * {@code minDays} days have any money moving.
 */
public final class InflationRisk {

    public enum Level {
        NO_DATA, OK, WATCH, ACT;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * @param ratio        today's rolling sink ratio in percent ({@code NaN}: nothing created in the window)
     * @param daysBelowAct days in a row (ending today) below the act line
     * @param daysWithData days in the window with any money created or removed
     */
    public record Risk(Level level, double ratio, int daysBelowAct, int daysWithData) {
    }

    private InflationRisk() {
    }

    /** The rolling sink ratio (percent) of the {@code window} days ending on {@code day}, {@code NaN} if nothing was created. */
    public static double ratio(Map<LocalDate, FlowSummary.Totals> daily, LocalDate day, int window) {
        long created = 0;
        long removed = 0;
        for (int i = 0; i < window; i++) {
            FlowSummary.Totals t = daily.get(day.minusDays(i));
            if (t != null) {
                created = Aggregation.add(created, t.created());
                removed = Aggregation.add(removed, t.removed());
            }
        }
        return created <= 0 ? Double.NaN : removed * 100.0 / created;
    }

    public static Risk evaluate(Map<LocalDate, FlowSummary.Totals> daily, LocalDate today, int window, double watchBelow,
                                double actBelow, int actDays, int minDays) {
        int withData = 0;
        for (int i = 0; i < window; i++) {
            FlowSummary.Totals t = daily.get(today.minusDays(i));
            if (t != null && (t.created() > 0 || t.removed() > 0)) {
                withData++;
            }
        }
        double now = ratio(daily, today, window);
        int below = 0;
        for (int i = 0; i < Math.max(actDays, 1) + window; i++) {
            double r = ratio(daily, today.minusDays(i), window);
            if (Double.isNaN(r) || r >= actBelow) {
                break;
            }
            below++;
        }
        Level level;
        if (withData < minDays) {
            level = Level.NO_DATA;
        } else if (below >= actDays) {
            level = Level.ACT;
        } else if (!Double.isNaN(now) && now < watchBelow) {
            level = Level.WATCH;
        } else {
            level = Level.OK;
        }
        return new Risk(level, now, below, withData);
    }
}
