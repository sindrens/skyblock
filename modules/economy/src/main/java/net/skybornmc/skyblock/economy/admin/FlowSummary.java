package net.skybornmc.skyblock.economy.admin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns the hourly flow sums of one currency into what the dashboard shows (M3.8), pure: money created, removed and
 * moved in a period, the sink ratio, the biggest sources and sinks, and day totals for the inflation risk.
 *
 * <p><b>Transit groups</b> ({@code flows.transit-groups}): money that only passes through the server, like an auction
 * (the buyer's payment is removed, the seller's payout created) or a money note (removed when made, created when cashed).
 * Counting both sides would make the sink ratio look healthier than it is, so a group only counts its difference: the
 * auction's sales tax is a sink, notes cashed beyond those made (staff notes) are a source.</p>
 */
public final class FlowSummary {

    /** One row of {@code eco_watch_flows} for one currency. */
    public record FlowRow(long hour, String reason, Flow flow, long amount, long count) {
    }

    /** Created, removed and moved money. */
    public record Totals(long created, long removed, long moved) {
        public static final Totals ZERO = new Totals(0, 0, 0);

        public Totals plus(Totals o) {
            return new Totals(Aggregation.add(created, o.created), Aggregation.add(removed, o.removed),
                    Aggregation.add(moved, o.moved));
        }

        /** Created minus removed (positive = the supply grew). */
        public long net() {
            return created - removed;
        }

        /** Removed / created in percent, or {@code NaN} when nothing was created. */
        public double sinkPercent() {
            return created <= 0 ? Double.NaN : removed * 100.0 / created;
        }
    }

    /**
     * One reason (or transit group) in a period.
     *
     * @param group      true for a transit group (created/removed hold its difference)
     * @param transitIn  for a group: what its reasons created (e.g. auction payouts), else 0
     * @param transitOut for a group: what its reasons removed (e.g. auction payments), else 0
     */
    public record ReasonTotals(String key, boolean group, long created, long removed, long moved, long count, long transitIn,
                               long transitOut) {

        /** Everything that went through this reason (for sorting). */
        public long volume() {
            return Aggregation.add(Aggregation.add(created, removed), Aggregation.add(moved, Aggregation.add(transitIn, transitOut)));
        }
    }

    /** A period: totals plus every reason, biggest volume first. */
    public record Period(Totals totals, List<ReasonTotals> reasons) {

        /** Reasons that created money, biggest first. */
        public List<ReasonTotals> sources() {
            return reasons.stream().filter(r -> r.created() > 0)
                    .sorted(Comparator.comparingLong(ReasonTotals::created).reversed()).toList();
        }

        /** Reasons that removed money, biggest first. */
        public List<ReasonTotals> sinks() {
            return reasons.stream().filter(r -> r.removed() > 0)
                    .sorted(Comparator.comparingLong(ReasonTotals::removed).reversed()).toList();
        }

        public ReasonTotals reason(String key) {
            return reasons.stream().filter(r -> r.key().equals(key)).findFirst().orElse(null);
        }
    }

    private FlowSummary() {
    }

    /**
     * Parses {@code flows.transit-groups} lines ({@code "auction ah_buy ah_sale"}) into reason → group name. A reason in
     * two groups stays in the first.
     */
    public static Map<String, String> transitGroups(Collection<String> lines) {
        Map<String, String> out = new HashMap<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String[] parts = line.trim().toLowerCase(Locale.ROOT).split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            for (int i = 1; i < parts.length; i++) {
                out.putIfAbsent(parts[i], parts[0]);
            }
        }
        return out;
    }

    /** Sums the rows with {@code fromHour <= hour < toHour}. */
    public static Period period(List<FlowRow> rows, long fromHour, long toHour, Map<String, String> transit) {
        List<FlowRow> in = new ArrayList<>();
        for (FlowRow r : rows) {
            if (r.hour() >= fromHour && r.hour() < toHour) {
                in.add(r);
            }
        }
        return sum(in, transit);
    }

    /** Day totals (display days in {@code zone}), oldest first. Transit groups are netted per day. */
    public static Map<LocalDate, Totals> daily(List<FlowRow> rows, Map<String, String> transit, ZoneId zone) {
        Map<LocalDate, List<FlowRow>> byDay = new TreeMap<>();
        for (FlowRow r : rows) {
            byDay.computeIfAbsent(Buckets.day(r.hour(), zone), d -> new ArrayList<>()).add(r);
        }
        Map<LocalDate, Totals> out = new LinkedHashMap<>();
        byDay.forEach((day, list) -> out.put(day, sum(list, transit).totals()));
        return out;
    }

    private static final class Acc {
        long created;
        long removed;
        long moved;
        long count;
    }

    private static Period sum(List<FlowRow> rows, Map<String, String> transit) {
        Map<String, Acc> plain = new LinkedHashMap<>();
        Map<String, Acc> groups = new LinkedHashMap<>();
        for (FlowRow r : rows) {
            String group = transit.get(r.reason());
            Acc acc = group != null ? groups.computeIfAbsent(group, k -> new Acc()) : plain.computeIfAbsent(r.reason(), k -> new Acc());
            switch (r.flow()) {
                case CREATED -> acc.created = Aggregation.add(acc.created, r.amount());
                case REMOVED -> acc.removed = Aggregation.add(acc.removed, r.amount());
                case MOVED -> acc.moved = Aggregation.add(acc.moved, r.amount());
            }
            acc.count = Aggregation.add(acc.count, r.count());
        }
        List<ReasonTotals> reasons = new ArrayList<>();
        Totals totals = Totals.ZERO;
        for (Map.Entry<String, Acc> e : plain.entrySet()) {
            Acc a = e.getValue();
            reasons.add(new ReasonTotals(e.getKey(), false, a.created, a.removed, a.moved, a.count, 0, 0));
            totals = totals.plus(new Totals(a.created, a.removed, a.moved));
        }
        for (Map.Entry<String, Acc> e : groups.entrySet()) {
            Acc a = e.getValue();
            long net = a.created - a.removed;
            long created = Math.max(0, net);
            long removed = Math.max(0, -net);
            reasons.add(new ReasonTotals(e.getKey(), true, created, removed, a.moved, a.count, a.created, a.removed));
            totals = totals.plus(new Totals(created, removed, a.moved));
        }
        reasons.sort(Comparator.comparingLong(ReasonTotals::volume).reversed().thenComparing(ReasonTotals::key));
        return new Period(totals, List.copyOf(reasons));
    }

    /** Percent change from {@code before} to {@code now}, or {@code NaN} when there is nothing to compare with. */
    public static double percentChange(long before, long now) {
        if (before <= 0) {
            return Double.NaN;
        }
        return (now - (double) before) * 100.0 / before;
    }

    /**
     * Velocity: how many times the supply changed hands in the period (money moved between accounts / supply), or
     * {@code NaN} without supply.
     */
    public static double velocity(long moved, long supply) {
        return supply <= 0 ? Double.NaN : moved / (double) supply;
    }
}
