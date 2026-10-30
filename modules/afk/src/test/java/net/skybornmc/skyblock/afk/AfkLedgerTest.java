package net.skybornmc.skyblock.afk;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Crediting: daily cap across midnight in Oslo, rewards every 15 min, key milestones, session steps. */
class AfkLedgerTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final long MIN = 60_000L;
    private final AfkRules rules = AfkRules.from(new AfkConfig());

    private static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, OSLO).toInstant().toEpochMilli();
    }

    private static AfkAccount fresh(int day) {
        AfkAccount a = new AfkAccount(UUID.randomUUID());
        a.restore(new AfkAccount.Snapshot(a.player, day, 0, 0, 0));
        return a;
    }

    /** Credits in check-sized steps from {@code start} for {@code minutes}; returns all results. */
    private static List<AfkLedger.Result> run(AfkAccount a, AfkRules rules, long start, long minutes) {
        List<AfkLedger.Result> out = new java.util.ArrayList<>();
        long step = 2_000;
        for (long t = start; t < start + minutes * MIN; t += step) {
            out.add(AfkLedger.credit(a, AfkLedger.dayKey(t, OSLO), step, Long.MAX_VALUE, rules));
        }
        return out;
    }

    @Test
    void dayKeysFollowOsloMidnightNotUtc() {
        // 23:30 UTC on 30 Oct 2026 is already 31 Oct in Oslo (UTC+1 after the DST change on 25 Oct).
        long utcLate = ZonedDateTime.of(2026, 10, 30, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli();
        assertEquals(20261031, AfkLedger.dayKey(utcLate, OSLO));
        assertEquals(20261030, AfkLedger.dayKey(at(2026, 10, 30, 23, 59), OSLO));
        assertEquals(30 * MIN, AfkLedger.millisToMidnight(at(2026, 10, 30, 23, 30), OSLO));
    }

    @Test
    void rewardEveryFifteenMinutesAndCapAtEightHours() {
        AfkAccount a = fresh(20261030);
        long start = at(2026, 10, 30, 8, 0);
        List<AfkLedger.Result> results = run(a, rules, start, 10 * 60);
        long rewards = results.stream().mapToLong(r -> r.rewards().size()).sum();
        assertEquals(32, rewards, "8 h / 15 min");
        assertEquals(480 * MIN, a.creditedMillis);
        assertTrue(AfkLedger.capped(a, 20261030, rules));
        assertEquals(32, a.rewardSeq);
        assertEquals(0, AfkLedger.credit(a, 20261030, 60_000, Long.MAX_VALUE, rules).credited(), "nothing past the cap");
    }

    @Test
    void capResetsAtOsloMidnightAndProgressCarriesOver() {
        AfkAccount a = fresh(20261030);
        // Capped on the 30th, with 10 minutes towards the next reward left from earlier.
        a.creditedMillis = 480 * MIN;
        a.progressMillis = 10 * MIN;
        assertEquals(0, AfkLedger.credit(a, 20261030, 2_000, Long.MAX_VALUE, rules).credited());
        // 00:00 Oslo: a new day, earning again; the next reward comes 5 minutes later (10 min carried over).
        int next = AfkLedger.dayKey(at(2026, 10, 31, 0, 0), OSLO);
        assertEquals(20261031, next);
        AfkLedger.Result r = AfkLedger.credit(a, next, 5 * MIN, Long.MAX_VALUE, rules);
        assertEquals(5 * MIN, r.credited());
        assertEquals(1, r.rewards().size());
        assertEquals(5 * MIN, a.creditedMillis, "today's total restarted");
        assertEquals(0, a.progressMillis);
    }

    @Test
    void sessionSpanningMidnightSplitsBetweenDays() {
        AfkAccount a = fresh(20261030);
        run(a, rules, at(2026, 10, 30, 20, 0), 8 * 60); // 20:00 -> 04:00
        assertEquals(20261031, a.day);
        assertEquals(4 * 60 * MIN, a.creditedMillis, "only the 4 h after midnight count for the 31st");
        assertEquals(32, a.rewardSeq, "4 h before + 4 h after midnight, no cap hit");
    }

    @Test
    void keyMilestonesAtOneThreeSixAndEightHours() {
        AfkAccount a = fresh(20261030);
        List<AfkLedger.Result> results = run(a, rules, at(2026, 10, 30, 8, 0), 9 * 60);
        List<Integer> keys = results.stream().flatMap(r -> r.keys().stream()).map(AfkRules.KeyMilestone::minutes).toList();
        assertEquals(List.of(60, 180, 360, 480), keys);
        // The next day they can be earned again.
        AfkAccount b = a;
        List<Integer> again = run(b, rules, at(2026, 10, 31, 8, 0), 61).stream().flatMap(r -> r.keys().stream())
                .map(AfkRules.KeyMilestone::minutes).toList();
        assertEquals(List.of(60), again);
    }

    @Test
    void rewardCarriesTheSessionLengthForStepGrowth() {
        AfkAccount a = fresh(20261030);
        List<AfkLedger.Reward> rewards = run(a, rules, at(2026, 10, 30, 8, 0), 120).stream().flatMap(r -> r.rewards().stream()).toList();
        assertEquals(8, rewards.size());
        assertEquals(15 * MIN, rewards.get(0).sessionMillis());
        assertEquals(100, rules.stepPercent(rewards.get(0).sessionMillis()));
        assertEquals(110, rules.stepPercent(rewards.get(1).sessionMillis()), "30 min");
        assertEquals(125, rules.stepPercent(rewards.get(3).sessionMillis()), "60 min");
        assertEquals(140, rules.stepPercent(rewards.get(7).sessionMillis()), "120 min");
    }

    @Test
    void ipRemainingLimitsCredit() {
        AfkAccount a = fresh(20261030);
        AfkLedger.Result r = AfkLedger.credit(a, 20261030, 10 * MIN, 3 * MIN, rules);
        assertEquals(3 * MIN, r.credited());
        assertFalse(r.reachedCap());
    }

    @Test
    void oneCreditCanPayTwoRewardsWithTheirOwnSessionLength() {
        AfkAccount a = fresh(20261030);
        AfkLedger.Result r = AfkLedger.credit(a, 20261030, 31 * MIN, Long.MAX_VALUE, rules);
        assertEquals(2, r.rewards().size());
        assertEquals(15 * MIN, r.rewards().get(0).sessionMillis());
        assertEquals(30 * MIN, r.rewards().get(1).sessionMillis());
        assertEquals(List.of(1L, 2L), r.rewards().stream().map(AfkLedger.Reward::seq).toList());
        assertEquals(MIN, a.progressMillis);
    }
}
