package net.skybornmc.skyblock.islands.stats;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContributionSharesTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private static int sum(List<ContributionShares.Share> shares) {
        return shares.stream().mapToInt(ContributionShares.Share::percent).sum();
    }

    @Test
    void thirdsAddUpTo100() {
        List<ContributionShares.Share> shares = ContributionShares.of(Map.of(A, 1L, B, 1L, C, 1L));
        assertEquals(100, sum(shares));
        assertEquals(List.of(34, 33, 33), shares.stream().map(ContributionShares.Share::percent).toList());
        assertEquals(A, shares.getFirst().member()); // ties: stable by member key
    }

    @Test
    void biggestFirstWithLargestRemainder() {
        // 2/3 = 66.67 → 67, 1/3 = 33.33 → 33
        List<ContributionShares.Share> shares = ContributionShares.of(Map.of(A, 100L, B, 200L));
        assertEquals(B, shares.get(0).member());
        assertEquals(67, shares.get(0).percent());
        assertEquals(33, shares.get(1).percent());
    }

    @Test
    void zeroAndNegativeAreLeftOut() {
        assertTrue(ContributionShares.of(Map.of()).isEmpty());
        assertTrue(ContributionShares.of(Map.of(A, 0L)).isEmpty());
        List<ContributionShares.Share> shares = ContributionShares.of(Map.of(A, 5L, B, 0L, C, -3L));
        assertEquals(1, shares.size());
        assertEquals(100, shares.getFirst().percent());
    }

    @Test
    void tinySharesStillSumTo100() {
        Map<UUID, Long> amounts = new HashMap<>(Map.of(A, 1_000_000L, B, 1L, C, 1L));
        List<ContributionShares.Share> shares = ContributionShares.of(amounts);
        assertEquals(100, sum(shares));
        assertEquals(100, shares.getFirst().percent());
    }

    @Test
    void randomSetsAlwaysSumTo100() {
        Random random = new Random(26);
        for (int round = 0; round < 500; round++) {
            Map<UUID, Long> amounts = new HashMap<>();
            int n = 1 + random.nextInt(12);
            for (int i = 0; i < n; i++) {
                amounts.put(UUID.randomUUID(), 1L + random.nextInt(100_000));
            }
            List<ContributionShares.Share> shares = ContributionShares.of(amounts);
            assertEquals(100, sum(shares));
            for (int i = 1; i < shares.size(); i++) {
                assertTrue(shares.get(i - 1).amount() >= shares.get(i).amount());
            }
        }
    }

    @Test
    void hugeAmountsDoNotOverflow() {
        assertEquals(100, sum(ContributionShares.of(Map.of(A, Long.MAX_VALUE, B, Long.MAX_VALUE))));
    }
}
