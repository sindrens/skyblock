package net.skybornmc.skyblock.economy.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Owner R3-25: minutes in the AFK pool don't count toward the interest activity rule. */
class ActivityPoolTest {

    @Test
    void aMinuteInThePoolMovesTheCountStartForward() {
        long since = 0;
        long now = 5 * 60_000L;
        // 3 of the 5 minutes in the pool: only 2 whole minutes are counted later
        for (int i = 0; i < 3; i++) {
            since = BankFeature.skipPool(since, now, BankFeature.POOL_SAMPLE_MS);
        }
        assertEquals(2, (now - since) / 60_000L);
        assertEquals(now, BankFeature.skipPool(now - 10_000, now, BankFeature.POOL_SAMPLE_MS), "never later than now");
    }
}
