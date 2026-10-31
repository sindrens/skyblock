package net.skybornmc.skyblock.common.mob;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityClockTest {

    private static AbilitySpec spec(AbilityId id, double cooldownSeconds, double chance) {
        return new AbilitySpec(id, cooldownSeconds, chance, Map.of(), Map.of());
    }

    /** A generator that always returns the same double (0 = every roll succeeds, 0.99 = every roll fails). */
    private static RandomGenerator fixed(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    @Test
    void cooldownAndFirstDelay() {
        AbilityClock clock = new AbilityClock(List.of(spec(AbilityId.SLAM, 5, 1.0)), 1000, 60);
        assertEquals(1060, clock.nextReady(), "first delay");
        assertEquals(-1, clock.poll(1059, () -> true, true, 20, fixed(0)));
        assertEquals(0, clock.poll(1060, () -> true, true, 20, fixed(0)));
        assertEquals(1160, clock.nextReady(), "5 s cooldown");
        assertEquals(-1, clock.poll(1100, () -> true, true, 20, fixed(0)));
        assertEquals(0, clock.poll(1160, () -> true, true, 20, fixed(0)));
    }

    @Test
    void shortCooldownBeatsTheFirstDelay() {
        AbilityClock clock = new AbilityClock(List.of(spec(AbilityId.SLAM, 1, 1.0)), 0, 60);
        assertEquals(20, clock.nextReady());
    }

    @Test
    void failedRollWaitsRetryTicksAndKeepsTheCooldown() {
        AbilityClock clock = new AbilityClock(List.of(spec(AbilityId.SLAM, 10, 0.5)), 0, 0);
        assertEquals(-1, clock.poll(0, () -> true, true, 20, fixed(0.9)));
        assertEquals(20, clock.nextReady(), "retry, not the full cooldown");
        assertEquals(0, clock.poll(20, () -> true, true, 20, fixed(0.1)));
        assertEquals(220, clock.nextReady());
    }

    @Test
    void noPlayerOrNoCapacityUsesNothing() {
        AbilityClock clock = new AbilityClock(List.of(spec(AbilityId.SLAM, 10, 1.0)), 0, 0);
        AtomicInteger asked = new AtomicInteger();
        assertEquals(-1, clock.poll(0, () -> {
            asked.incrementAndGet();
            return false;
        }, true, 20, fixed(0)));
        assertEquals(0, clock.nextReady(), "still ready");
        assertEquals(-1, clock.poll(0, () -> {
            asked.incrementAndGet();
            return true;
        }, false, 20, fixed(0)));
        assertEquals(1, asked.get(), "proximity is not asked while the effect cap is full");
        assertEquals(0, clock.poll(0, () -> true, true, 20, fixed(0)));
    }

    @Test
    void proximityIsOnlyAskedWhenSomethingIsReady() {
        AbilityClock clock = new AbilityClock(List.of(spec(AbilityId.SLAM, 10, 1.0)), 0, 100);
        AtomicInteger asked = new AtomicInteger();
        for (long t = 0; t < 100; t++) {
            clock.poll(t, () -> asked.incrementAndGet() > 0, true, 20, fixed(0));
        }
        assertEquals(0, asked.get());
    }

    @Test
    void oneAbilityAtATimeAndRoundRobin() {
        AbilityClock clock = new AbilityClock(List.of(spec(AbilityId.SLAM, 1, 1.0), spec(AbilityId.CURSE, 1, 1.0),
                spec(AbilityId.ENRAGE, 1, 1.0)), 0, 0);
        assertEquals(2, clock.size(), "enrage is health-triggered, not on the clock");
        List<AbilityId> used = new ArrayList<>();
        long t = 0;
        for (int i = 0; i < 4; i++) {
            int index = clock.poll(t, () -> true, true, 20, fixed(0));
            assertTrue(index >= 0);
            used.add(clock.ability(index).id());
            clock.busyUntil(t + 30);
            assertTrue(clock.busy(t + 29));
            assertEquals(-1, clock.poll(t + 29, () -> true, true, 20, fixed(0)), "busy while its ability runs");
            t += 30;
        }
        assertEquals(List.of(AbilityId.SLAM, AbilityId.CURSE, AbilityId.SLAM, AbilityId.CURSE), used);
        assertFalse(clock.busy(t));
    }

    @Test
    void chanceMatchesOverManyTries() {
        AbilityClock clock = new AbilityClock(List.of(spec(AbilityId.SLAM, 1, 0.25)), 0, 0);
        RandomGenerator rng = new SplittableRandom(7);
        int starts = 0;
        int tries = 0;
        for (long t = 0; t < 200_000; t++) {
            if (t >= clock.nextReady()) {
                tries++;
                if (clock.poll(t, () -> true, true, 20, rng) >= 0) {
                    starts++;
                }
            }
        }
        assertEquals(0.25, starts / (double) tries, 0.02);
    }

    @Test
    void emptyClockNeverStarts() {
        AbilityClock clock = new AbilityClock(List.of(spec(AbilityId.ENRAGE, 0, 1)), 0, 0);
        assertEquals(Long.MAX_VALUE, clock.nextReady());
        assertEquals(-1, clock.poll(5, () -> true, true, 20, fixed(0)));
    }
}
