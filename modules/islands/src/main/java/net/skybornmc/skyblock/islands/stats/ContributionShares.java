package net.skybornmc.skyblock.islands.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-member contribution (M2.6): whole percent shares that always add up to exactly 100 (largest remainder method), and
 * the members ordered by amount. Pure.
 */
public final class ContributionShares {

    /** One member's part. */
    public record Share(UUID member, long amount, int percent) {
    }

    private ContributionShares() {
    }

    /**
     * Shares of every member with a positive amount, highest amount first (ties by member key, so the order is stable).
     * Empty when nobody has anything.
     */
    public static List<Share> of(Map<UUID, Long> amounts) {
        List<Map.Entry<UUID, Long>> positive = new ArrayList<>();
        double total = 0;
        for (Map.Entry<UUID, Long> e : amounts.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                positive.add(e);
                total += e.getValue();
            }
        }
        if (positive.isEmpty()) {
            return List.of();
        }
        positive.sort(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        int n = positive.size();
        int[] percent = new int[n];
        double[] remainder = new double[n];
        int given = 0;
        for (int i = 0; i < n; i++) {
            double exact = Math.min(100.0, positive.get(i).getValue() * 100.0 / total);
            percent[i] = (int) Math.floor(exact);
            remainder[i] = exact - percent[i];
            given += percent[i];
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        // biggest remainder first; ties go to the bigger amount (earlier in the list)
        order.sort(Comparator.<Integer>comparingDouble(i -> remainder[i]).reversed().thenComparingInt(i -> i));
        for (int k = 0; given < 100 && k < n; k++) {
            percent[order.get(k)]++;
            given++;
        }
        List<Share> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Share(positive.get(i).getKey(), positive.get(i).getValue(), percent[i]));
        }
        return out;
    }

    /** {@link #of} as member → percent, in the same order. */
    public static Map<UUID, Integer> percents(Map<UUID, Long> amounts) {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        for (Share share : of(amounts)) {
            out.put(share.member(), share.percent());
        }
        return out;
    }
}
