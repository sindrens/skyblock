package net.skybornmc.skyblock.economy.sell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dynamic prices v2: how many of each item every scope sold today ({@code sell_island_daily}). A scope is an island
 * ({@code i:<island id>}) or a profile ({@code p:<profile key>}); a sale counts for every scope of the seller, and the
 * price follows the highest of them (so hopping islands doesn't reset it). The daily reset is lazy: a row is rolled over
 * when it is read on a later sales day. Thread-safe (one lock; every call is a few map lookups).
 *
 * <p>A sale <b>reserves</b> its items before it is paid ({@link #reserve}), so two sales at the same moment can't both
 * get the full price; a failed sale gives them back ({@link #undo}).</p>
 */
public final class DayCounts {

    /** One saved row: {@code sold} items of {@code item} by {@code scope} on sales day {@code day} (epoch day). */
    public record Row(String scope, String item, long day, long sold) {
    }

    /** What a reservation added, so it can be taken back exactly. */
    public record Reservation(List<Row> added) {
        public static final Reservation NONE = new Reservation(List.of());
    }

    private final Map<String, Row> rows = new HashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    private final Supplier<DayPrice> settings;

    public DayCounts(Supplier<DayPrice> settings) {
        this.settings = settings;
    }

    private static String id(String scope, String item) {
        return scope + "|" + item;
    }

    /** The current row (rolled over to {@code day}); null when there is none. Caller holds the lock. */
    private Row current(String scope, String item, long day) {
        String id = id(scope, item);
        Row r = rows.get(id);
        if (r == null || r.day() == day) {
            return r;
        }
        if (r.day() > day) {
            return r; // clock went back: keep it as it is
        }
        long rolled = settings.get().rollover(r.sold(), day - r.day());
        Row next = new Row(scope, item, day, rolled);
        rows.put(id, next);
        dirty.add(id);
        return next;
    }

    /** Items of {@code item} sold today by one scope. */
    public synchronized long count(String scope, String item, long day) {
        Row r = current(scope, item, day);
        return r == null ? 0 : r.sold();
    }

    /** The count that sets the price: the highest of the seller's scopes. */
    public synchronized long count(Collection<String> scopes, String item, long day) {
        long max = 0;
        for (String scope : scopes) {
            Row r = current(scope, item, day);
            if (r != null && r.sold() > max) {
                max = r.sold();
            }
        }
        return max;
    }

    /** Adds a sale's amounts to every scope of the seller. */
    public synchronized Reservation reserve(Collection<String> scopes, Map<String, Long> amounts, long day) {
        if (scopes.isEmpty() || amounts.isEmpty()) {
            return Reservation.NONE;
        }
        List<Row> added = new ArrayList<>();
        for (String scope : scopes) {
            for (Map.Entry<String, Long> e : amounts.entrySet()) {
                long amount = e.getValue() == null ? 0 : e.getValue();
                if (amount <= 0) {
                    continue;
                }
                Row r = current(scope, e.getKey(), day);
                long sold = r == null ? 0 : r.sold();
                long next = sold + amount < sold ? Long.MAX_VALUE : sold + amount;
                rows.put(id(scope, e.getKey()), new Row(scope, e.getKey(), day, next));
                dirty.add(id(scope, e.getKey()));
                added.add(new Row(scope, e.getKey(), day, amount));
            }
        }
        return new Reservation(List.copyOf(added));
    }

    /** Gives a failed sale's items back (only if it is still the same sales day). */
    public synchronized void undo(Reservation reservation) {
        for (Row a : reservation.added()) {
            String id = id(a.scope(), a.item());
            Row r = rows.get(id);
            if (r != null && r.day() == a.day()) {
                rows.put(id, new Row(r.scope(), r.item(), r.day(), Math.max(0, r.sold() - a.sold())));
                dirty.add(id);
            }
        }
    }

    /** Saved rows at enable; rows that roll over to nothing by {@code today} are skipped. Returns how many were kept. */
    public synchronized int load(Collection<Row> saved, long today) {
        int kept = 0;
        DayPrice s = settings.get();
        for (Row r : saved) {
            long sold = r.day() < today ? s.rollover(r.sold(), today - r.day()) : r.sold();
            if (sold <= 0 && r.day() < today) {
                continue;
            }
            rows.put(id(r.scope(), r.item()), r);
            kept++;
        }
        return kept;
    }

    /** Changed rows since the last call (to save). */
    public synchronized List<Row> takeDirty() {
        List<Row> out = new ArrayList<>(dirty.size());
        for (String id : dirty) {
            Row r = rows.get(id);
            if (r != null) {
                out.add(r);
            }
        }
        dirty.clear();
        return out;
    }

    /** Puts rows back after a failed save. */
    public synchronized void markDirty(Collection<Row> failed) {
        for (Row r : failed) {
            dirty.add(id(r.scope(), r.item()));
        }
    }

    /** Drops rows from memory that are 0 and older than today (the database keeps nothing it needs from them). */
    public synchronized int prune(long today) {
        int before = rows.size();
        rows.values().removeIf(r -> r.day() < today && settings.get().rollover(r.sold(), today - r.day()) <= 0
                && !dirty.contains(id(r.scope(), r.item())));
        return before - rows.size();
    }

    /** Every scope's rows for one item... (staff view): {scope: count today}. */
    public synchronized Map<String, Long> scopesOf(String item, long day) {
        Map<String, Long> out = new HashMap<>();
        for (Row r : new ArrayList<>(rows.values())) {
            if (r.item().equals(item)) {
                Row c = current(r.scope(), item, day);
                if (c != null && c.sold() > 0) {
                    out.put(c.scope(), c.sold());
                }
            }
        }
        return out;
    }

    public synchronized int size() {
        return rows.size();
    }
}
