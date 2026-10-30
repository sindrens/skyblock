package net.skybornmc.skyblock.afk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** afk.yml parsing, stage tiers, step growth and money. */
class AfkRulesTest {

    @Test
    void defaultsParseWithoutProblems() {
        AfkRules r = AfkRules.from(new AfkConfig());
        assertEquals(List.of(), r.problems());
        assertEquals(12, r.moneyByTier().length);
        assertEquals(12, r.tierLevels().length);
        assertEquals(4, r.keys().size());
        assertEquals(15 * 60_000L, r.rewardMillis());
        assertEquals(480 * 60_000L, r.dailyCapMillis());
    }

    @Test
    void stepsGrowWithTheSessionAndNeverShrink() {
        AfkRules r = AfkRules.from(new AfkConfig());
        int last = 0;
        for (int minutes = 0; minutes <= 600; minutes += 5) {
            int p = r.stepPercent(minutes * 60_000L);
            assertTrue(p >= last, "step at " + minutes);
            last = p;
        }
        assertEquals(100, r.stepPercent(0));
        assertEquals(100, r.stepPercent(29 * 60_000L));
        assertEquals(110, r.stepPercent(30 * 60_000L));
        assertEquals(180, r.stepPercent(479 * 60_000L));
        assertEquals(180, r.maxPercent());
    }

    @Test
    void tiersFromSkillLevels() {
        AfkRules r = AfkRules.from(new AfkConfig());
        assertEquals(1, r.tierFor(0));
        assertEquals(1, r.tierFor(4));
        assertEquals(2, r.tierFor(5));
        assertEquals(9, r.tierFor(42));
        assertEquals(12, r.tierFor(150));
    }

    @Test
    void moneyFollowsTierAndStep() {
        AfkRules r = AfkRules.from(new AfkConfig());
        assertEquals(310, r.money(1, 0));
        assertEquals(558, r.money(1, 480 * 60_000L), "310 x 180%");
        assertEquals(1_070_000, r.money(12, 0));
        assertEquals(1_070_000, r.money(40, 0), "above the table: last value");
        assertEquals(310, r.money(0, 0), "below: first value");
    }

    @Test
    void moneyPerHourStaysAtMostFivePercentOfReferenceIncome() {
        // REF(t) = 2,500 x 1.3^(t-1) x P(t) (ECONOMY_BALANCE §1.2); max AFK money per hour = 4 rewards at the top step.
        AfkRules r = AfkRules.from(new AfkConfig());
        for (int t = 1; t <= 12; t++) {
            double price = 20 * Math.pow(1.35, Math.min(t, 8) - 1) * Math.pow(2.2, Math.max(0, t - 8));
            double ref = 2500 * Math.pow(1.3, t - 1) * price;
            double perHour = 60.0 / 15 * r.money(t, Long.MAX_VALUE / 2);
            assertTrue(perHour <= 0.05 * ref, "tier " + t + ": " + perHour + " > 5% of " + ref);
        }
    }

    @Test
    void badEntriesAreSkippedAndReported() {
        AfkConfig c = new AfkConfig();
        c.sessionSteps = List.of("0:100", "abc", "30:x", "60:150");
        c.keyMilestones = List.of("60:ember:1", "60:cinder:1", "999:blaze:1", "10:Bad Crate:1", "5:ember");
        c.moneyByTier = List.of("100", "-5", "x");
        c.tierLevels = List.of("0", "10", "5");
        AfkRules r = AfkRules.from(c);
        assertEquals(2, r.steps().size());
        assertEquals(2, r.keys().size(), "60 ember + 999 blaze (reported as unreachable)");
        assertEquals(1, r.moneyByTier().length);
        assertEquals(2, r.tierLevels().length);
        assertEquals(9, r.problems().size(), String.join("\n", r.problems()));
    }

    @Test
    void timeTexts() {
        assertEquals("14:59", AfkText.countdown(14 * 60_000L + 58_100));
        assertEquals("0:00", AfkText.countdown(0));
        assertEquals("1:00:00", AfkText.countdown(3_600_000));
        assertEquals("3h 15m", AfkText.hoursMinutes(195 * 60_000L));
        assertEquals("8h", AfkText.hoursMinutes(480 * 60_000L));
        assertEquals("0m", AfkText.hoursMinutes(59_000));
    }
}
