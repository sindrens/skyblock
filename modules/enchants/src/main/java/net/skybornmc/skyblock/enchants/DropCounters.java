package net.skybornmc.skyblock.enchants;

import java.util.Map;

/**
 * In-memory book drop counters (ENCHANTS §3.4): the profile's pity counter and the account's first-find progress per
 * activity. They count while the database row is still loading (the count is merged when it arrives) and are saved in
 * batches, never per block. Pure and unit-tested; main thread only.
 */
final class DropCounters {

    private DropCounters() {
    }

    private static final int POOLS = Pool.values().length;

    /** Pity per profile key: actions since the last book, per pool. */
    static final class Pity {
        private final double[] since = new double[POOLS];
        private final boolean[] reset = new boolean[POOLS];
        private boolean loaded;
        private boolean dirty;

        boolean loaded() {
            return loaded;
        }

        double since(Pool pool) {
            return since[pool.ordinal()];
        }

        void add(Pool pool, double actions) {
            if (actions > 0 && Double.isFinite(actions)) {
                since[pool.ordinal()] += actions;
                dirty = true;
            }
        }

        /** A book of this pool dropped: starts over, also over a value that is still loading. Returns the old value. */
        double reset(Pool pool) {
            double old = since[pool.ordinal()];
            since[pool.ordinal()] = 0;
            reset[pool.ordinal()] = true;
            return old;
        }

        /** Undo of {@link #reset} when the book could not be saved. */
        void restore(Pool pool, double old) {
            since[pool.ordinal()] += Math.max(0, old);
            dirty = true;
        }

        /** The saved values arrived: they add to what was counted meanwhile (pools reset meanwhile keep their new count). */
        void load(Map<String, Double> saved) {
            for (Pool pool : Pool.values()) {
                Double v = saved.get(pool.id());
                if (v != null && Double.isFinite(v) && !reset[pool.ordinal()]) {
                    since[pool.ordinal()] += Math.max(0, v);
                }
            }
            loaded = true;
        }

        boolean dirty() {
            return dirty;
        }

        void clean() {
            dirty = false;
        }

        /** A save failed: write again with the next save. */
        void markDirty() {
            dirty = true;
        }
    }

    /** First finds per account (and season): actions per pool and milestones given out. */
    static final class FirstFinds {
        private final double[] actions = new double[POOLS];
        private final int[] granted = new int[POOLS];
        private final boolean[] pending = new boolean[POOLS];
        private boolean loaded;
        private boolean dirty;

        boolean loaded() {
            return loaded;
        }

        double actions(Pool pool) {
            return actions[pool.ordinal()];
        }

        int granted(Pool pool) {
            return granted[pool.ordinal()];
        }

        /** True while a first-find book of this pool is being saved (no second one starts meanwhile). */
        boolean pending(Pool pool) {
            return pending[pool.ordinal()];
        }

        /** Counts only while milestones are left (no needless writes afterwards). */
        void add(Pool pool, double n, int milestones) {
            if (n > 0 && Double.isFinite(n) && granted[pool.ordinal()] < milestones) {
                actions[pool.ordinal()] += n;
                dirty = true;
            }
        }

        void begin(Pool pool) {
            pending[pool.ordinal()] = true;
        }

        /** The grant finished: {@code ok} moves to the next milestone. */
        void finish(Pool pool, boolean ok, int from) {
            pending[pool.ordinal()] = false;
            if (ok) {
                granted[pool.ordinal()] = Math.max(granted[pool.ordinal()], from + 1);
            }
        }

        /** Saved values arrived: actions add up, granted takes the higher value. */
        void load(Map<String, DropStore.FirstFind> saved) {
            for (Pool pool : Pool.values()) {
                DropStore.FirstFind f = saved.get(pool.id());
                if (f != null) {
                    actions[pool.ordinal()] += Double.isFinite(f.actions()) ? Math.max(0, f.actions()) : 0;
                    granted[pool.ordinal()] = Math.max(granted[pool.ordinal()], f.granted());
                }
            }
            loaded = true;
        }

        /** A grant was refused (already given out, e.g. on another server): skip to at least the next milestone. */
        void skip(Pool pool, int from) {
            granted[pool.ordinal()] = Math.max(granted[pool.ordinal()], from + 1);
        }

        boolean dirty() {
            return dirty;
        }

        void clean() {
            dirty = false;
        }

        /** A save failed: write again with the next save. */
        void markDirty() {
            dirty = true;
        }
    }
}
