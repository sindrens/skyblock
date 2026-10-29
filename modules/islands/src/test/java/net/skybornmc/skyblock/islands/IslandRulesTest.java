package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandRulesTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void creationCooldownAfterDisband() {
        long now = 100 * HOUR;
        assertEquals(0, IslandManager.cooldownRemaining(null, 24 * HOUR, now), "never disbanded");
        assertEquals(4 * HOUR, IslandManager.cooldownRemaining(now - 20 * HOUR, 24 * HOUR, now));
        assertEquals(0, IslandManager.cooldownRemaining(now - 25 * HOUR, 24 * HOUR, now));
        assertEquals(0, IslandManager.cooldownRemaining(now - HOUR, 0, now), "cooldown off");
    }

    @Test
    void sizesAreForcedOddAndStartNeverExceedsMax() {
        IslandsConfig config = new IslandsConfig();
        assertEquals(3, config.startSize());
        assertEquals(15, config.maxSize());
        assertTrue(config.sizeProblems().isEmpty());
        config.startChunks = 4;
        config.maxChunks = 16;
        assertEquals(3, config.startSize());
        assertEquals(15, config.maxSize());
        assertEquals(1, config.sizeProblems().size());
        config.startChunks = 9;
        config.maxChunks = 5;
        assertEquals(5, config.startSize());
        assertEquals(1, config.sizeProblems().size());
        config.maxChunks = 99;
        assertEquals(31, config.maxSize());
    }
}
