package net.skybornmc.skyblock.economy.bank;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Interest rules (M3.3, balance sheet §6), pure and unit-tested.
 *
 * <ul>
 *   <li>Time is cut into fixed periods of {@code periodMillis} (8 h since BANK2, 3 h before) counted from the epoch; period
 *       {@code p} is paid once it is over, with the idempotency key {@code bank_interest:<account>:<minutes>m:<p>}, so a
 *       restart or a double run never pays twice. When the period length changes, {@link #convertPeriod} moves every state
 *       to the new numbering without paying any stretch of time twice.</li>
 *   <li>Interest of a period is paid on the <b>lowest balance during that period</b> (deposits during the period don't
 *       count until the next one), capped per payout and never above the bank's free room.</li>
 *   <li>A withdrawal with a fee forfeits the period it happens in; withdrawals in the free minutes right after a payout
 *       only lower the lowest balance.</li>
 * </ul>
 */
final class InterestMath {

    private InterestMath() {
    }

    /**
     * Interest state of one bank account.
     *
     * @param tier         personal bank tier (1..10; unused for island banks)
     * @param cycle        the period {@code minBalance} and {@code forfeited} belong to
     * @param minBalance   lowest balance during {@code cycle}
     * @param forfeited    a withdrawal with a fee happened during {@code cycle}
     * @param settledCycle last period whose interest was decided (paid, or skipped with nothing to pay)
     */
    record State(int tier, long cycle, long minBalance, boolean forfeited, long settledCycle) {

        /** A fresh account first used during {@code period} with {@code balance} in it (nothing due for earlier periods). */
        static State fresh(int tier, long period, long balance) {
            return new State(tier, period, balance, false, period - 1);
        }

        State withSettled(long settled) {
            return new State(tier, cycle, minBalance, forfeited, settled);
        }

        State withTier(int newTier) {
            return new State(newTier, cycle, minBalance, forfeited, settledCycle);
        }
    }

    static long period(long now, long periodMillis) {
        return Math.floorDiv(now, periodMillis);
    }

    static long periodStart(long period, long periodMillis) {
        return period * periodMillis;
    }

    /** Milliseconds until the next payout time. */
    static long untilNextPayout(long now, long periodMillis) {
        return periodStart(period(now, periodMillis) + 1, periodMillis) - now;
    }

    /** True in the first {@code freeMinutes} of a period (right after a payout): withdrawals are free. */
    static boolean inFreeWindow(long now, long periodMillis, int freeMinutes) {
        return freeMinutes > 0 && now - periodStart(period(now, periodMillis), periodMillis) < freeMinutes * 60_000L;
    }

    /**
     * The payout for one period: {@code basis × rate%}, rounded down, at most {@code cap} and at most {@code room} (the
     * free space in the bank). Never negative.
     */
    static long payout(long basis, double ratePercent, long cap, long room) {
        if (basis <= 0 || ratePercent <= 0 || cap <= 0 || room <= 0) {
            return 0;
        }
        long raw = BigDecimal.valueOf(basis).multiply(BigDecimal.valueOf(ratePercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).min(BigDecimal.valueOf(Long.MAX_VALUE)).longValue();
        return Math.max(0, Math.min(raw, Math.min(cap, room)));
    }

    /** The early-withdrawal fee: {@code amount × percent%}, rounded <b>up</b> (no fee-free small withdrawals), at most the amount. */
    static long fee(long amount, double percent) {
        if (amount <= 0 || percent <= 0) {
            return 0;
        }
        long raw = BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).min(BigDecimal.valueOf(amount)).longValue();
        return Math.max(0, raw);
    }

    /**
     * What period {@code period}'s interest is paid on. {@code balance} = the account's balance now (only used when the
     * state is older than the period: nothing moved since, so the balance is the lowest balance of the period).
     */
    static long basis(State state, long period, long balance) {
        if (state.cycle() == period) {
            return state.forfeited() ? 0 : Math.max(0, state.minBalance());
        }
        if (state.cycle() < period) {
            return Math.max(0, balance);
        }
        return 0;
    }

    /** True if every period before {@code now}'s period was settled, so a deposit or withdrawal may roll the state. */
    static boolean settledUpTo(State state, long currentPeriod) {
        return state.settledCycle() >= currentPeriod - 1;
    }

    /**
     * The state after a deposit ({@code delta > 0}) or withdrawal ({@code delta < 0}) during {@code currentPeriod}, with
     * {@code before} = the balance before it. A state of an older period first rolls to the current one (its lowest
     * balance = the balance before the change). Withdrawals lower the lowest balance; {@code forfeit} marks the period.
     * Call only when {@link #settledUpTo} holds.
     */
    static State afterChange(State state, long currentPeriod, long before, long delta, boolean forfeit) {
        long min = state.minBalance();
        boolean lost = state.forfeited();
        if (state.cycle() != currentPeriod) {
            min = before;
            lost = false;
        }
        long after = before + delta;
        min = Math.min(min, Math.max(0, after));
        return new State(state.tier(), currentPeriod, min, lost || forfeit, state.settledCycle());
    }

    /**
     * The first period still to settle and whether older ones must be skipped: with more than {@code maxCatchUp} open
     * periods (server downtime) only the last {@code maxCatchUp} are paid. Returns the new settled period to jump to
     * without payout, or {@code state.settledCycle()} if no skip is needed.
     */
    static long skipTo(State state, long lastDuePeriod, int maxCatchUp) {
        long open = lastDuePeriod - state.settledCycle();
        if (open > maxCatchUp) {
            return lastDuePeriod - maxCatchUp;
        }
        return state.settledCycle();
    }

    /**
     * The next payout times within {@code withinMillis} from {@code now} (epoch millis, the first one is the next payout),
     * for the menu ("Paid at 01:00, 09:00, 17:00").
     */
    static java.util.List<Long> nextPayouts(long now, long periodMillis, long withinMillis) {
        java.util.List<Long> out = new java.util.ArrayList<>();
        long t = periodStart(period(now, periodMillis) + 1, periodMillis);
        while (t <= now + withinMillis && out.size() < 24) {
            out.add(t);
            t += periodMillis;
        }
        return out;
    }

    /**
     * Moves a state from periods of {@code oldMillis} to periods of {@code newMillis} (BANK2: 3 h → 8 h) so that no
     * stretch of time is ever paid twice:
     * <ul>
     *   <li>Everything up to the end of the last settled old period was decided. The new period holding that moment is
     *       settled too unless it starts exactly there (then it is still open and paid in full later). A partly covered
     *       new period is therefore skipped once: the change can cost a part of one payout, never pay one twice.</li>
     *   <li>The tracked period (lowest balance, forfeit) moves to the new period it started in. A state is only ever
     *       open for the period right after the settled one, so the result never has an open period without a lowest
     *       balance.</li>
     * </ul>
     */
    static State convertPeriod(State state, long oldMillis, long newMillis) {
        if (oldMillis == newMillis || oldMillis <= 0 || newMillis <= 0) {
            return state;
        }
        long settledEnd = Math.multiplyExact(state.settledCycle() + 1, oldMillis);
        long q = Math.floorDiv(settledEnd, newMillis);
        long settled = q * newMillis == settledEnd ? q - 1 : q;
        long cycle = Math.floorDiv(Math.multiplyExact(state.cycle(), oldMillis), newMillis);
        return new State(state.tier(), cycle, state.minBalance(), state.forfeited(), settled);
    }

    /** {@code percent} % of {@code capacity} (daily withdraw limits); -1 = no limit; never negative otherwise. */
    static long percentOf(long capacity, int percent) {
        if (percent < 0) {
            return Long.MAX_VALUE;
        }
        return BigDecimal.valueOf(capacity).multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                .longValue();
    }
}
