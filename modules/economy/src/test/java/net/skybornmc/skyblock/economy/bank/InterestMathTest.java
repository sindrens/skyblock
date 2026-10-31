package net.skybornmc.skyblock.economy.bank;

import net.skybornmc.skyblock.common.economy.AccountRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interest math (M3.3, BANK2): rates, caps, room, fee rounding, 8 h periods, the lowest-balance rule, the catch-up limit
 * and the 3 h → 8 h conversion.
 */
class InterestMathTest {

    private static final long HOUR = 3_600_000L;
    private static final long P = 8 * HOUR;
    private static final long OLD = 3 * HOUR;

    @Test
    void bank2DefaultsAreEightHoursWithHigherInterestPerDay() {
        BankConfig c = new BankConfig();
        assertEquals(8, c.periodHours);
        assertEquals(P, c.periodMillis());
        assertEquals(0.6, c.personalRatePercent);
        assertEquals(1.0, c.islandRatePercent);
        assertEquals(3, c.catchUpPeriods, "one day of payouts after downtime, as before (8 x 3 h)");
        BankTiers t = BankTiers.parse(c);
        // Per day at a full tier 1 bank: M3.3 8 x 150 = 1,200; BANK2 3 x 600 = 1,800 (+50%).
        long perDay = 24 / c.periodHours * InterestMath.payout(100_000, c.personalRatePercent, t.tier(1).payoutCap(), Long.MAX_VALUE);
        assertEquals(1_800, perDay);
        assertEquals(30_000, 24 / c.periodHours * InterestMath.payout(1_000_000, c.islandRatePercent, t.islandCap(0), Long.MAX_VALUE),
                "island level 0 full: 3 x 10k (M3.3: 8 x 2.5k = 20k)");
    }

    @Test
    void payoutIsRateTimesBasisRoundedDownAndCapped() {
        assertEquals(150, InterestMath.payout(100_000, 0.15, 150, Long.MAX_VALUE), "tier 1 full: exactly the cap");
        assertEquals(15, InterestMath.payout(10_000, 0.15, 150, Long.MAX_VALUE));
        assertEquals(1, InterestMath.payout(999, 0.15, 150, Long.MAX_VALUE), "1.4985 rounds down");
        assertEquals(0, InterestMath.payout(600, 0.15, 150, Long.MAX_VALUE), "0.9 rounds down to nothing");
        assertEquals(480, InterestMath.payout(500_000, 0.15, 480, Long.MAX_VALUE), "tier 2 full: 750 uncapped -> cap 480");
        assertEquals(2_500, InterestMath.payout(1_000_000, 0.25, 2_500, Long.MAX_VALUE), "island level 0 full");
        assertEquals(147_622_500, InterestMath.payout(1_048_576_000_000L, 0.25, 147_622_500, Long.MAX_VALUE), "island level 10 cap");
    }

    @Test
    void payoutNeverExceedsTheFreeRoomAndIsNeverNegative() {
        assertEquals(40, InterestMath.payout(99_960, 0.15, 150, 40));
        assertEquals(0, InterestMath.payout(100_000, 0.15, 150, 0), "a full bank earns nothing");
        assertEquals(0, InterestMath.payout(-5, 0.15, 150, 100));
        assertEquals(0, InterestMath.payout(100_000, 0, 150, 100));
        assertEquals(5_277_655, InterestMath.payout(Long.MAX_VALUE, 0.15, 5_277_655, Long.MAX_VALUE), "no overflow on huge balances");
    }

    @Test
    void feeRoundsUpSoSmallWithdrawalsAreNeverFree() {
        assertEquals(20_000, InterestMath.fee(1_000_000, 2.0));
        assertEquals(1, InterestMath.fee(49, 2.0), "0.98 rounds up");
        assertEquals(1, InterestMath.fee(1, 2.0));
        assertEquals(0, InterestMath.fee(1_000, 0));
        assertEquals(10, InterestMath.fee(10, 100), "never more than the amount");
    }

    @Test
    void periodsAndCountdown() {
        assertEquals(0, InterestMath.period(0, P));
        assertEquals(0, InterestMath.period(P - 1, P));
        assertEquals(1, InterestMath.period(P, P));
        assertEquals(P - 1_000, InterestMath.untilNextPayout(1_000, P));
        assertEquals(8 * HOUR - 1_000, InterestMath.untilNextPayout(1_000, P), "8 hours between payouts");
        assertEquals(3, InterestMath.period(24 * HOUR, P), "payouts at 00:00, 08:00 and 16:00 UTC: 3 a day");
        assertEquals(java.util.List.of(8 * HOUR, 16 * HOUR, 24 * HOUR), InterestMath.nextPayouts(HOUR, P, 24 * HOUR - 1));
        assertEquals(java.util.List.of(8 * HOUR), InterestMath.nextPayouts(0, P, 8 * HOUR), "exactly now is not next");
        assertTrue(InterestMath.inFreeWindow(P + 9 * 60_000L, P, 10));
        assertFalse(InterestMath.inFreeWindow(P + 10 * 60_000L, P, 10));
        assertFalse(InterestMath.inFreeWindow(P, P, 0), "0 = always a fee");
    }

    @Test
    void basisIsTheLowestBalanceOfThePeriod() {
        InterestMath.State fresh = InterestMath.State.fresh(1, 5, 1_000);
        assertEquals(4, fresh.settledCycle(), "nothing due before the first use");
        // Deposit of 9,000 during period 5 on top of 1,000: period 5 pays on 1,000 only (no deposit-before-payout trick).
        InterestMath.State afterDeposit = InterestMath.afterChange(fresh, 5, 1_000, 9_000, false);
        assertEquals(1_000, InterestMath.basis(afterDeposit, 5, 10_000));
        // Period 6: nothing moved since, so the whole balance counts.
        assertEquals(10_000, InterestMath.basis(afterDeposit, 6, 10_000));
        // A withdrawal lowers the lowest balance. Owner R3-41: the fee is the only rule, an old forfeit flag is ignored.
        InterestMath.State freeOut = InterestMath.afterChange(afterDeposit, 5, 10_000, -9_500, false);
        assertEquals(500, InterestMath.basis(freeOut, 5, 500));
        InterestMath.State feeOut = InterestMath.afterChange(afterDeposit, 5, 10_000, -100, true);
        assertEquals(1_000, InterestMath.basis(feeOut, 5, 9_900), "R3-41: a fee no longer costs the period's interest");
        assertEquals(9_900, InterestMath.basis(feeOut, 6, 9_900));
    }

    @Test
    void aStateOfAnOlderPeriodRollsOverOnTheFirstChange() {
        InterestMath.State old = new InterestMath.State(3, 5, 200, true, 9);
        InterestMath.State rolled = InterestMath.afterChange(old, 10, 7_000, 3_000, false);
        assertEquals(10, rolled.cycle());
        assertEquals(7_000, rolled.minBalance(), "lowest = the balance before the first change");
        assertFalse(rolled.forfeited(), "an old forfeit does not carry over");
        assertEquals(3, rolled.tier());
        assertTrue(InterestMath.settledUpTo(old, 10));
        assertFalse(InterestMath.settledUpTo(old, 11), "period 10 must be settled before period 11 may roll");
    }

    @Test
    void catchUpIsLimited() {
        InterestMath.State s = new InterestMath.State(1, 0, 0, false, 10);
        assertEquals(10, InterestMath.skipTo(s, 15, 8), "5 open periods: all paid");
        assertEquals(92, InterestMath.skipTo(s, 100, 8), "90 open periods after downtime: only the last 8 are paid");
        assertEquals(100, InterestMath.skipTo(s, 100, 0));
    }

    @Test
    void interestKeyIsPerAccountAndPeriod() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals("bank_interest:bank:" + id + ":480m:42", BankOps.interestKey(AccountRef.bank(id), P, 42));
        assertEquals("bank_interest:island:" + id + ":480m:42", BankOps.interestKey(AccountRef.island(id), P, 42));
        assertEquals("bank_interest:bank:" + id + ":42", BankOps.interestKey(AccountRef.bank(id), OLD, 42),
                "3 h keeps the M3.3 key, so a period paid before the update is still recognised");
        assertTrue(BankOps.interestKey(AccountRef.island(id), 24 * HOUR, Long.MAX_VALUE).length() <= 96, "fits the ledger key column");
    }

    @Test
    void convertingThreeHourStatesNeverPaysTimeTwice() {
        // Settled up to 87 h (old period 28), open old period 29 = 87-90 h with a deposit in it. 87 h lies inside the
        // 8 h period 10 (80-88 h): that one was partly paid, so it counts as settled; period 11 (88-96 h) is the next.
        InterestMath.State open = new InterestMath.State(2, 29, 5_000, true, 28);
        InterestMath.State c = InterestMath.convertPeriod(open, OLD, P);
        assertEquals(10, c.settledCycle());
        assertEquals(10, c.cycle(), "the tracked period moves to the 8 h period it started in");
        assertEquals(5_000, c.minBalance());
        assertTrue(c.forfeited());
        assertEquals(2, c.tier());
        assertTrue(InterestMath.settledUpTo(c, 11), "deposits and withdrawals work right away in period 11");
        // Period 10 is settled (settledCycle 10), so the sweep never pays it again; the old forfeit flag is ignored (R3-41).
        assertEquals(5_000, InterestMath.basis(c, 10, 9_000));

        // Aligned: settled up to exactly 24 h (old period 7): the 8 h period 3 (24-32 h) is still open and paid in full,
        // on the lowest balance tracked since 24 h.
        InterestMath.State aligned = InterestMath.convertPeriod(new InterestMath.State(1, 8, 700, false, 7), OLD, P);
        assertEquals(2, aligned.settledCycle());
        assertEquals(3, aligned.cycle());
        assertEquals(700, InterestMath.basis(aligned, 3, 9_000));

        // An idle account (nothing moved for a while) keeps paying on its balance.
        InterestMath.State idle = InterestMath.convertPeriod(new InterestMath.State(1, 3, 100, false, 30), OLD, P);
        assertEquals(11, idle.settledCycle(), "93 h is inside period 11 (88-96 h)");
        assertTrue(idle.cycle() <= idle.settledCycle());
        assertEquals(9_000, InterestMath.basis(idle, 12, 9_000));

        // Same length or nonsense: unchanged; and back again (8 h -> 3 h) follows the same rule.
        assertEquals(open, InterestMath.convertPeriod(open, P, P));
        InterestMath.State back = InterestMath.convertPeriod(new InterestMath.State(1, 11, 0, false, 10), P, OLD);
        assertEquals(29, back.settledCycle(), "settled to 88 h: the 3 h period 29 (87-90 h) was partly paid, so it is settled");
        assertEquals(29, back.cycle());
    }

    @Test
    void convertedStatesNeverPayAStretchOfTimeTwice() {
        // Brute force over many settle points: the paid time before and after the change never overlaps.
        for (long settled = 0; settled < 200; settled++) {
            InterestMath.State s = new InterestMath.State(1, settled + 1, 0, false, settled);
            InterestMath.State c = InterestMath.convertPeriod(s, OLD, P);
            long paidUntil = (settled + 1) * OLD;
            long nextPaidFrom = (c.settledCycle() + 1) * P;
            assertTrue(nextPaidFrom >= paidUntil, "settled " + settled + ": new payouts start at or after " + paidUntil);
            assertTrue(nextPaidFrom - paidUntil < P, "settled " + settled + ": at most part of one period is skipped");
            assertTrue(c.cycle() <= c.settledCycle() + 1);
        }
    }

    @Test
    void percentOfCapacity() {
        assertEquals(Long.MAX_VALUE, InterestMath.percentOf(1_000_000, -1));
        assertEquals(100_000, InterestMath.percentOf(1_000_000, 10));
        assertEquals(0, InterestMath.percentOf(1_000_000, 0));
    }
}
