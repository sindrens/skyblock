package net.skybornmc.skyblock.social;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialLogicTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void rateLimiterAllowsBurstThenBlocksThenRecovers() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            assertEquals(0, limiter.tryAcquire(a, 1000 + i, 5, 5000));
        }
        long wait = limiter.tryAcquire(a, 1010, 5, 5000);
        assertTrue(wait > 0 && wait <= 5000);
        assertEquals(0, limiter.tryAcquire(b, 1010, 5, 5000), "other players are not affected");
        assertEquals(0, limiter.tryAcquire(a, 6001, 5, 5000), "window passed");
    }

    @Test
    void afkAutoAndBack() {
        AfkTracker afk = new AfkTracker();
        afk.active(a, 0);
        afk.active(b, 250_000);
        assertEquals(Set.of(a), afk.dueForAuto(300_000, 300_000));
        assertTrue(afk.set(a, true, 300_000));
        assertTrue(afk.isAfk(a));
        assertTrue(afk.dueForAuto(300_000, 300_000).isEmpty(), "already AFK players are not due again");
        assertTrue(afk.active(a, 310_000), "activity brings a player back");
        assertFalse(afk.isAfk(a));
        assertFalse(afk.active(a, 320_000), "no 'back' message when they weren't AFK");
    }

    @Test
    void manualToggleReportsChangesOnly() {
        AfkTracker afk = new AfkTracker();
        assertTrue(afk.set(a, true, 0));
        assertFalse(afk.set(a, true, 0));
        assertTrue(afk.set(a, false, 0));
        assertFalse(afk.set(a, false, 0));
    }

    @Test
    void preProcessPlaceholderStripsUnsafeCharacters() {
        // Doesn't throw and builds a resolver; unsafe characters are removed before parsing.
        assertTrue(SocialModule.pre("player", "Ste've<b>").has("player"));
    }

    @Test
    void stillDueRepeatsTheScanForOnePlayer() {
        AfkTracker afk = new AfkTracker();
        assertTrue(afk.dueForAuto(0, 300_000).isEmpty(), "nobody tracked");
        afk.active(a, 0);
        assertTrue(afk.dueForAuto(300_000, 300_000).contains(a));
        assertTrue(afk.stillDue(a, 300_000, 300_000));
        afk.active(a, 300_100);
        assertFalse(afk.stillDue(a, 300_200, 300_000), "PERF8: activity between the async scan and the main tick keeps them active");
        afk.set(a, true, 0);
        assertFalse(afk.stillDue(a, 900_000, 300_000), "already AFK");
        afk.remove(b);
        assertFalse(afk.stillDue(b, 900_000, 300_000), "left the server");
    }
}
