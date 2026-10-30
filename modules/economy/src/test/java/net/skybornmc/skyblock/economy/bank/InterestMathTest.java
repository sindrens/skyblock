package net.skybornmc.skyblock.economy.bank;

import net.skybornmc.skyblock.common.economy.AccountRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Interest math (M3.3): rates, caps, room, fee rounding, periods, the lowest-balance rule and the catch-up limit. */
class InterestMathTest {

    private static final long P = 3 * 3_600_000L;

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
        // A free withdrawal lowers the lowest balance; one with a fee forfeits the period.
        InterestMath.State freeOut = InterestMath.afterChange(afterDeposit, 5, 10_000, -9_500, false);
        assertEquals(500, InterestMath.basis(freeOut, 5, 500));
        InterestMath.State feeOut = InterestMath.afterChange(afterDeposit, 5, 10_000, -100, true);
        assertEquals(0, InterestMath.basis(feeOut, 5, 9_900));
        assertEquals(9_900, InterestMath.basis(feeOut, 6, 9_900), "the next period earns again");
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
        assertEquals("bank_interest:bank:" + id + ":42", BankOps.interestKey(AccountRef.bank(id), 42));
        assertEquals("bank_interest:island:" + id + ":42", BankOps.interestKey(AccountRef.island(id), 42));
        assertTrue(BankOps.interestKey(AccountRef.island(id), Long.MAX_VALUE).length() <= 96, "fits the ledger key column");
    }

    @Test
    void percentOfCapacity() {
        assertEquals(Long.MAX_VALUE, InterestMath.percentOf(1_000_000, -1));
        assertEquals(100_000, InterestMath.percentOf(1_000_000, 10));
        assertEquals(0, InterestMath.percentOf(1_000_000, 0));
    }
}
