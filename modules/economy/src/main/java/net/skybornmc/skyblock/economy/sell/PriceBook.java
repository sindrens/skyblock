package net.skybornmc.skyblock.economy.sell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Every item's dynamic price state, in memory, thread-safe (M3.4). Reads are lock-free and cheap (menus, quotes on the
 * main thread); the interval steps, the hourly impact recompute and persistence run off the main thread.
 *
 * <p>A sale <b>reserves</b> its price drop before it is paid ({@link #reserve}), so two sales at the same moment can't
 * both get the old price; a failed sale gives the drop back ({@link #undo}).</p>
 */
public final class PriceBook {

    /** What a reservation took, so it can be given back exactly. */
    public record Reservation(Map<String, Long> amounts, Map<String, Double> drops, long time) {
    }

    private final Map<String, PriceState> states = new ConcurrentHashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final Supplier<DynamicSettings> settings;

    public PriceBook(Supplier<DynamicSettings> settings) {
        this.settings = settings;
    }

    DynamicSettings settings() {
        return settings.get();
    }

    /** The state of an item (a fresh base-price state if it has none yet; not stored). */
    public PriceState get(String key) {
        PriceState s = states.get(key);
        return s != null ? s : PriceState.fresh(key, settings().defaultImpact());
    }

    /** Creates base-price states for keys that have none (so unsold items can drift). */
    public void ensure(Collection<String> keys) {
        double impact = settings().defaultImpact();
        for (String key : keys) {
            if (states.putIfAbsent(key, PriceState.fresh(key, impact)) == null) {
                dirty.add(key);
            }
        }
    }

    /** Loaded rows; each gets the interval steps it missed while the server was off (at most catch-up-max-hours). */
    public void load(Collection<PriceState> rows, Map<String, Long> updatedAt, long now) {
        DynamicSettings s = settings();
        long maxSteps = s.catchUpMaxHours() * 60L / s.intervalMinutes();
        for (PriceState row : rows) {
            long since = updatedAt.getOrDefault(row.key(), now);
            long steps = Math.max(0, Math.min(maxSteps, (now - since) / s.intervalMillis()));
            PriceState state = row.withMultiplier(PriceMath.clamp(row.multiplier(), s.floor(), s.ceiling()));
            for (long i = 0; i < steps && s.enabled(); i++) {
                long at = since + (i + 1) * s.intervalMillis();
                state = state.withMultiplier(PriceMath.step(state.multiplier(), s.recovery(), state.quiet(at, s.quietMinutes()),
                        s.driftStep(), s.floor(), s.ceiling()));
            }
            states.put(row.key(), state);
            if (steps > 0) {
                dirty.add(row.key());
            }
        }
    }

    /** Lowers the price of every item in {@code amounts} as if sold now; returns what to give back on failure. */
    public Reservation reserve(Map<String, Long> amounts, long now) {
        DynamicSettings s = settings();
        Map<String, Double> drops = new HashMap<>();
        for (Map.Entry<String, Long> e : amounts.entrySet()) {
            long amount = e.getValue();
            if (amount <= 0) {
                continue;
            }
            states.compute(e.getKey(), (key, old) -> {
                PriceState st = old != null ? old : PriceState.fresh(key, s.defaultImpact());
                double before = st.multiplier();
                double after = s.enabled() ? PriceMath.afterSale(before, st.impact(), amount, s.floor(), s.ceiling()) : before;
                drops.put(key, before - after);
                return new PriceState(key, after, st.impact(), now, st.hourAgo(), st.volume().add(now, amount),
                        st.soldTotal() + amount);
            });
            dirty.add(e.getKey());
        }
        return new Reservation(Map.copyOf(amounts), Map.copyOf(drops), now);
    }

    /** Gives a reservation back (the sale was not paid). The last-sale time stays (harmless: it only delays drift). */
    public void undo(Reservation r) {
        DynamicSettings s = settings();
        for (Map.Entry<String, Long> e : r.amounts().entrySet()) {
            double drop = r.drops().getOrDefault(e.getKey(), 0.0);
            long amount = e.getValue();
            states.computeIfPresent(e.getKey(), (key, st) -> new PriceState(key,
                    PriceMath.clamp(st.multiplier() + drop, s.floor(), s.ceiling()), st.impact(), st.lastSaleAt(), st.hourAgo(),
                    st.volume().add(r.time(), -amount), Math.max(0, st.soldTotal() - amount)));
            dirty.add(e.getKey());
        }
    }

    /** One interval step for every item (recovery toward base, drift up for quiet items). */
    public void step(long now) {
        DynamicSettings s = settings();
        if (!s.enabled()) {
            return;
        }
        double drift = s.driftStep();
        for (String key : List.copyOf(states.keySet())) {
            states.computeIfPresent(key, (k, st) -> {
                double next = PriceMath.step(st.multiplier(), s.recovery(), st.quiet(now, s.quietMinutes()), drift, s.floor(),
                        s.ceiling());
                if (next != st.multiplier()) {
                    dirty.add(k);
                }
                return st.withMultiplier(next);
            });
        }
    }

    /** Hourly: new adaptive impacts from the 24 h volume, and the trend reference (hour-ago multiplier). */
    public void recompute(long now) {
        DynamicSettings s = settings();
        for (String key : List.copyOf(states.keySet())) {
            states.computeIfPresent(key, (k, st) -> {
                double impact = PriceMath.adaptiveImpact(st.volume().total(now), s.minVolume(), s.recovery(), s.intervalMinutes(),
                        s.target(), s.defaultImpact(), s.minImpact(), s.maxImpact());
                dirty.add(k);
                return new PriceState(k, st.multiplier(), impact, st.lastSaleAt(), st.multiplier(), st.volume(), st.soldTotal());
            });
        }
    }

    /** The changed states since the last call (for saving); they are marked clean. */
    public List<PriceState> takeDirty() {
        List<PriceState> out = new ArrayList<>();
        for (String key : List.copyOf(dirty)) {
            dirty.remove(key);
            PriceState st = states.get(key);
            if (st != null) {
                out.add(st);
            }
        }
        return out;
    }

    /** Marks states dirty again (a save failed). */
    public void markDirty(Collection<PriceState> failed) {
        failed.forEach(st -> dirty.add(st.key()));
    }

    public int size() {
        return states.size();
    }
}
