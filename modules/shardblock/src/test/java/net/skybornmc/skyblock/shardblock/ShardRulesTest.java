package net.skybornmc.skyblock.shardblock;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECON-SHARDS / ECON-TABLES C: level tables, upgrade costs, the double roll and the daily cap math. */
class ShardRulesTest {

    private static ShardRules defaults(List<String> problems) {
        return ShardRules.of(new ShardBlockConfig(), problems::add);
    }

    @Test
    void defaultsLoadWithoutProblems() {
        List<String> problems = new ArrayList<>();
        ShardRules r = defaults(problems);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        assertEquals(5, r.maxLevel(ShardRules.Kind.RESPAWN));
        assertEquals(5, r.maxLevel(ShardRules.Kind.DOUBLE));
        assertEquals(300, r.respawnSeconds(0), "owner 3 Nov 2026: 5 min at level 0");
        assertEquals(60, r.respawnSeconds(5), "1 min at the top");
        assertEquals(60, r.respawnSeconds(99), "clamped");
        assertEquals(0.0, r.doublePercent(0));
        assertEquals(25.0, r.doublePercent(5));
    }

    @Test
    void costsPerLevelAndNothingPastTheTop() {
        ShardRules r = defaults(new ArrayList<>());
        assertEquals(new ShardRules.Cost(Currency.MONEY, 2000), r.cost(ShardRules.Kind.RESPAWN, 1), "owner 3 Nov 2026: money, not shards");
        assertEquals(new ShardRules.Cost(Currency.MONEY, 250000), r.cost(ShardRules.Kind.RESPAWN, 5));
        assertEquals(new ShardRules.Cost(Currency.MONEY, 1500), r.cost(ShardRules.Kind.DOUBLE, 1));
        assertNull(r.cost(ShardRules.Kind.RESPAWN, 6), "maxed");
        assertNull(r.cost(ShardRules.Kind.DOUBLE, 0));
    }

    @Test
    void costParsingRefusesCreditsAndJunk() {
        assertEquals(new ShardRules.Cost(Currency.MONEY, 5), ShardRules.parseCost(" money : 5 "));
        assertNull(ShardRules.parseCost("credits:5"), "credits are never a shard block price");
        assertNull(ShardRules.parseCost("crystals:0"));
        assertNull(ShardRules.parseCost("crystals"));
        assertNull(ShardRules.parseCost("crystals:abc"));
        assertNull(ShardRules.parseCost(null));
    }

    @Test
    void brokenListsAreReportedAndMadeSafe() {
        ShardBlockConfig c = new ShardBlockConfig();
        c.respawnSeconds = List.of("x", "-5");
        c.doublePercent = List.of("150");
        c.respawnCosts = List.of("crystals:100", "bogus", "crystals:300");
        List<String> problems = new ArrayList<>();
        ShardRules r = ShardRules.of(c, problems::add);
        assertEquals(1800, r.respawnSeconds(0), "empty after skipping: 30 min");
        assertEquals(0.0, r.doublePercent(0));
        assertEquals(0, r.maxLevel(ShardRules.Kind.RESPAWN));
        assertTrue(problems.size() >= 5, () -> String.join("\n", problems));
        // A broken cost stops the levels above it.
        c = new ShardBlockConfig();
        c.respawnCosts = List.of("crystals:100", "bogus", "crystals:300");
        r = ShardRules.of(c, p -> { });
        assertEquals(new ShardRules.Cost(Currency.CRYSTALS, 100), r.cost(ShardRules.Kind.RESPAWN, 1));
        assertNull(r.cost(ShardRules.Kind.RESPAWN, 2));
        assertNull(r.cost(ShardRules.Kind.RESPAWN, 3));
    }

    @Test
    void doubleRoll() {
        RandomGenerator low = new FixedRandom(0.01);
        RandomGenerator high = new FixedRandom(0.99);
        assertEquals(2, ShardRules.rollAmount(1, 25, low));
        assertEquals(1, ShardRules.rollAmount(1, 25, high));
        assertEquals(1, ShardRules.rollAmount(1, 0, low), "0% never doubles");
        assertEquals(1, ShardRules.rollAmount(0, 0, high), "at least 1");
        assertEquals(6, ShardRules.rollAmount(3, 100, high));
    }

    @Test
    void dailyCapMath() {
        assertEquals(100, ShardRules.capRemaining(0, 100));
        assertEquals(1, ShardRules.capRemaining(99, 100));
        assertEquals(0, ShardRules.capRemaining(150, 100));
        assertEquals(Integer.MAX_VALUE, ShardRules.capRemaining(1_000, 0), "0 = no cap");
    }

    @Test
    void theDayStartsAtOsloMidnight() {
        ZoneId oslo = ShardRules.zone("Europe/Oslo");
        // 22:59:59 UTC on 1 Nov 2026 = 23:59:59 Oslo (winter time); one second later is the next day.
        int before = ShardRules.dayKey(Instant.parse("2026-11-01T22:59:59Z"), oslo);
        int after = ShardRules.dayKey(Instant.parse("2026-11-01T23:00:00Z"), oslo);
        assertEquals(before + 1, after);
        assertEquals(ZoneId.of("Europe/Oslo"), ShardRules.zone("Not/AZone"), "broken zone falls back to Oslo");
    }

    /** A RandomGenerator that always returns the same double. */
    private record FixedRandom(double value) implements RandomGenerator {
        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public double nextDouble() {
            return value;
        }
    }
}
