package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.SendGate;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 6 Nov 2026: the daily pay limit by playtime (anti-boost). */
class PayLimitTest {

    private static final List<SendGateImpl.Tier> TIERS = SendGateImpl.tiers(new EconomyConfig().payLimitTiers);

    @Test
    void defaultLadderParses() {
        assertEquals(5, TIERS.size());
        assertEquals(new SendGateImpl.Tier(0, 20_000), TIERS.getFirst());
        assertEquals(new SendGateImpl.Tier(24, -1), TIERS.getLast(), "24 h = no limit");
        assertEquals(List.of(new SendGateImpl.Tier(1, 5), new SendGateImpl.Tier(3, 1_500_000)),
                SendGateImpl.tiers(List.of("3:1.5M", "broken", "1:5", "x:y")), "sorted, broken lines skipped");
    }

    @Test
    void tierFollowsPlaytime() {
        assertEquals(0, SendGateImpl.tierFor(TIERS, 0).hours());
        assertEquals(0, SendGateImpl.tierFor(TIERS, 2).hours());
        assertEquals(3, SendGateImpl.tierFor(TIERS, 3).hours());
        assertEquals(12, SendGateImpl.tierFor(TIERS, 23).hours());
        assertEquals(24, SendGateImpl.tierFor(TIERS, 500).hours());
        assertNull(SendGateImpl.nextTier(TIERS, TIERS.getLast()));
        assertEquals(3, SendGateImpl.nextTier(TIERS, TIERS.getFirst()).hours());
    }

    @Test
    void amountMustFitWhatIsLeftToday() {
        assertSame(SendGate.Check.OPEN, SendGateImpl.decideLimit(TIERS, 0, 0, 20_000), "exactly the limit is fine");
        SendGate.Check refused = SendGateImpl.decideLimit(TIERS, 0, 15_000, 10_000);
        assertFalse(refused.allowed());
        assertEquals(SendGate.Kind.DAILY_LIMIT, refused.kind());
        assertEquals(5_000, refused.left());
        assertEquals(3, refused.nextHours());
        assertEquals(100_000, refused.nextLimit());
        assertEquals(0, refused.missing(), "the earned rule's missing amount stays 0");
        assertSame(SendGate.Check.OPEN, SendGateImpl.decideLimit(TIERS, 30, 999_999_999, 1_000_000_000), "no limit at the top");
        assertEquals(0, SendGateImpl.decideLimit(TIERS, 0, 50_000, 1).left(), "never below 0 left");
    }

    @Test
    void theDayStartsAtMidnightInTheZone() {
        ZoneId oslo = ZoneId.of("Europe/Oslo");
        long noon = java.time.ZonedDateTime.of(2026, 11, 7, 12, 0, 0, 0, oslo).toInstant().toEpochMilli();
        long midnight = java.time.ZonedDateTime.of(2026, 11, 7, 0, 0, 0, 0, oslo).toInstant().toEpochMilli();
        assertEquals(midnight, SendGateImpl.dayStart(noon, oslo));
        assertTrue(SendGateImpl.dayStart(midnight, oslo) == midnight);
    }

    @Test
    void theOldEarnedRuleIsOffByDefault() {
        assertEquals(0, new EconomyConfig().sendGateRequired, "owner 6 Nov 2026: replaced by the pay limit");
        assertTrue(new EconomyConfig().shardsIslandOnly);
    }
}
