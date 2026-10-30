package net.skybornmc.skyblock.common.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaggerTest {

    @Test
    void everyEntryIsDueExactlyOncePerPeriod() {
        for (int period : new int[]{2, 20, 40, 1200}) {
            for (int i = 0; i < 200; i++) {
                UUID id = UUID.randomUUID();
                int hits = 0;
                for (long tick = 1000; tick < 1000 + period; tick++) {
                    if (Stagger.due(id, tick, period)) {
                        hits++;
                    }
                }
                assertEquals(1, hits, "period " + period);
            }
        }
    }

    @Test
    void periodOfOneOrLessIsAlwaysDue() {
        assertTrue(Stagger.due(12345, 7, 1));
        assertTrue(Stagger.due(12345, 7, 0));
        assertTrue(Stagger.due((UUID) null, 7, 20));
    }

    @Test
    void entriesAreSpreadOverTheTicks() {
        int period = 20;
        int[] perTick = new int[period];
        for (int i = 0; i < 2000; i++) {
            UUID id = UUID.randomUUID();
            for (int tick = 0; tick < period; tick++) {
                if (Stagger.due(id, tick, period)) {
                    perTick[tick]++;
                }
            }
        }
        for (int count : perTick) {
            // 100 expected per tick; no tick may carry a big share of the work
            assertTrue(count > 40 && count < 200, "uneven buckets: " + java.util.Arrays.toString(perTick));
        }
    }

    @Test
    void sequentialHashesLandInDifferentBuckets() {
        int period = 20;
        java.util.Set<Long> buckets = new java.util.HashSet<>();
        for (int hash = 0; hash < 20; hash++) {
            for (long tick = 0; tick < period; tick++) {
                if (Stagger.due(hash, tick, period)) {
                    buckets.add(tick);
                }
            }
        }
        assertTrue(buckets.size() > 8, "sequential hashes bunch up: " + buckets);
    }
}
