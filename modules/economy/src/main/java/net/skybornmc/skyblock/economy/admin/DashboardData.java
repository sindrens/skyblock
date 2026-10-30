package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.Currency;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Everything the dashboard overview shows for one currency (M3.8), read in one go on a database thread from the
 * summary tables (never the whole ledger) and cached for a few seconds.
 *
 * @param supplyNow     all balances + notes not cashed yet, right now
 * @param notesNow      the part of it held as money notes
 * @param supplyDayAgo  the snapshot 24 h ago (-1 if none)
 * @param supplyWeekAgo the snapshot 7 days ago (-1 if none)
 * @param today         since midnight (display time zone)
 * @param week          the last 7 x 24 hours
 * @param fortnight     the last 14 x 24 hours (baseline-days)
 * @param daily         day totals for the risk badge
 */
public record DashboardData(Currency currency, long takenAt, long supplyNow, long notesNow, long supplyDayAgo, long supplyWeekAgo,
                            FlowSummary.Period today, FlowSummary.Period week, FlowSummary.Period fortnight,
                            Map<LocalDate, FlowSummary.Totals> daily, InflationRisk.Risk risk, long openAlerts) {

    /** Hours of the "last 7 days" period. */
    static final int WEEK_HOURS = 7 * 24;

    static DashboardData load(Connection c, Currency currency, WatchConfig cfg, ZoneId zone, long now) throws SQLException {
        long nowHour = Buckets.hour(now);
        LocalDate todayDate = Buckets.day(nowHour, zone);
        int days = Math.max(cfg.baselineDays, 7) + cfg.actDays + 1;
        long from = Math.min(Buckets.firstHour(todayDate.minusDays(days), zone), nowHour - days * 24L);
        List<FlowSummary.FlowRow> rows = WatchStore.flows(c, currency, from);
        Map<String, String> transit = FlowSummary.transitGroups(cfg.transitGroups);
        long end = nowHour + 1;
        FlowSummary.Period today = FlowSummary.period(rows, Buckets.firstHour(todayDate, zone), end, transit);
        FlowSummary.Period week = FlowSummary.period(rows, end - WEEK_HOURS, end, transit);
        FlowSummary.Period fortnight = FlowSummary.period(rows, end - cfg.baselineDays * 24L, end, transit);
        Map<LocalDate, FlowSummary.Totals> daily = FlowSummary.daily(rows, transit, zone);
        InflationRisk.Risk risk = InflationRisk.evaluate(daily, todayDate, cfg.baselineDays, cfg.watchBelowPercent,
                cfg.actBelowPercent, cfg.actDays, cfg.riskMinDays);
        long[] supply = WatchStore.liveSupply(c).get(currency);
        return new DashboardData(currency, now, Aggregation.add(supply[0], supply[1]), supply[1],
                WatchStore.supplyAt(c, currency, nowHour - 24), WatchStore.supplyAt(c, currency, nowHour - WEEK_HOURS),
                today, week, fortnight, daily, risk, WatchStore.openCount(c));
    }

    /** The period for a {@link Range}. */
    public FlowSummary.Period period(Range range) {
        return switch (range) {
            case TODAY -> today;
            case WEEK -> week;
            case FORTNIGHT -> fortnight;
        };
    }

    /** The periods the dashboard can show. */
    public enum Range {
        TODAY, WEEK, FORTNIGHT;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public Range next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}
