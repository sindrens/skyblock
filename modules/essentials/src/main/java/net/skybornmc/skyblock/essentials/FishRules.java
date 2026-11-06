package net.skybornmc.skyblock.essentials;

import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owner 6 Nov 2026 (FULL_PLAN.md 2): the /fish waters, their catches and the anti-AFK guard. Pure, unit-tested.
 * A water unlocks by catches in total (Shallows 0, River 500, Lake 2,000, Reef 6,000, Deep 15,000, Abyss 35,000). Every
 * catch pays the water's money directly (like the /farm and /mine bonus, but every time), so vanilla fish keep a small
 * sell price and island fish farms never print money.
 */
final class FishRules {

    private FishRules() {
    }

    /** One weighted catch: item, amount and weight. */
    record Catch(Material item, int amount, double weight) {
    }

    /** One water: name (message key part), catches to unlock it, money per catch, what bites. */
    record Water(String id, long unlockAt, long pay, List<Catch> catches) {
    }

    /**
     * Parses {@code id:unlockAt:pay:item*amount@weight,item@weight,...} (amount 1 when left out). Broken lines are
     * skipped with a reason; the waters are sorted by unlockAt and the first always unlocks at 0.
     */
    static List<Water> parse(List<String> lines, Consumer<String> problems) {
        List<Water> out = new ArrayList<>();
        for (String raw : lines) {
            String[] p = raw.strip().split(":", 4);
            if (p.length != 4) {
                problems.accept("grind.fish-waters: '" + raw + "' needs id:unlock:pay:item@weight,... (skipped)");
                continue;
            }
            try {
                List<Catch> catches = new ArrayList<>();
                for (String c : p[3].split(",")) {
                    String[] w = c.strip().split("@", 2);
                    String[] ia = w[0].strip().split("\\*", 2);
                    Material m = Material.matchMaterial(ia[0].strip());
                    if (m == null || m == Material.AIR) {
                        throw new IllegalArgumentException("'" + ia[0] + "' is not an item");
                    }
                    int amount = ia.length == 2 ? Integer.parseInt(ia[1].strip()) : 1;
                    double weight = w.length == 2 ? Double.parseDouble(w[1].strip()) : 1;
                    if (amount < 1 || weight <= 0) {
                        throw new IllegalArgumentException("amount and weight must be above 0");
                    }
                    catches.add(new Catch(m, amount, weight));
                }
                out.add(new Water(p[0].strip().toLowerCase(java.util.Locale.ROOT), Math.max(0, Long.parseLong(p[1].strip())),
                        Math.max(0, Long.parseLong(p[2].strip())), List.copyOf(catches)));
            } catch (RuntimeException e) {
                problems.accept("grind.fish-waters: '" + raw + "': " + e.getMessage() + " (skipped)");
            }
        }
        out.sort(java.util.Comparator.comparingLong(Water::unlockAt));
        if (!out.isEmpty() && out.getFirst().unlockAt() != 0) {
            Water f = out.getFirst();
            out.set(0, new Water(f.id(), 0, f.pay(), f.catches()));
        }
        return List.copyOf(out);
    }

    /** The newest water unlocked at {@code fished} catches (null without waters). */
    static Water waterFor(List<Water> waters, long fished) {
        Water out = null;
        for (Water w : waters) {
            if (out == null || fished >= w.unlockAt()) {
                out = w;
            }
        }
        return out;
    }

    /** The next water after {@code now}, or null at the last. */
    static Water next(List<Water> waters, Water now) {
        int i = waters.indexOf(now);
        return i < 0 || i + 1 >= waters.size() ? null : waters.get(i + 1);
    }

    /** A weighted catch for {@code random} in [0, 1). */
    static Catch roll(Water water, double random) {
        double total = 0;
        for (Catch c : water.catches()) {
            total += c.weight();
        }
        double at = random * total;
        for (Catch c : water.catches()) {
            at -= c.weight();
            if (at < 0) {
                return c;
            }
        }
        return water.catches().getLast();
    }

    /**
     * Anti-AFK (owner: be aware of exploits): an auto-clicker fishing from one spot without moving or looking gets
     * nothing, and nobody gets more than {@code perMinute} catches a minute. One per player, main thread.
     */
    static final class Guard {
        private final Deque<double[]> casts = new ArrayDeque<>();
        private final Deque<Long> catches = new ArrayDeque<>();

        /** Remembers a cast: where and how the player looked. */
        void cast(double x, double y, double z, float yaw, float pitch, int keep) {
            casts.addLast(new double[]{Math.floor(x), Math.floor(y), Math.floor(z), yaw, pitch});
            while (casts.size() > Math.max(2, keep)) {
                casts.removeFirst();
            }
        }

        /** True when the last {@code same} casts were all from one block with the same look (within one degree). */
        boolean frozen(int same) {
            if (same < 2 || casts.size() < same) {
                return false;
            }
            double[] first = casts.peekLast();
            int n = 0;
            for (var it = casts.descendingIterator(); it.hasNext() && n < same; n++) {
                double[] c = it.next();
                if (c[0] != first[0] || c[1] != first[1] || c[2] != first[2] || Math.abs(c[3] - first[3]) > 1 || Math.abs(c[4] - first[4]) > 1) {
                    return false;
                }
            }
            return true;
        }

        /** Counts a catch at {@code now}; false when the minute is full (the catch gives nothing). */
        boolean allow(long now, int perMinute) {
            while (!catches.isEmpty() && now - catches.peekFirst() >= 60_000L) {
                catches.removeFirst();
            }
            if (catches.size() >= Math.max(1, perMinute)) {
                return false;
            }
            catches.addLast(now);
            return true;
        }
    }
}
