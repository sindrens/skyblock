package net.skybornmc.skyblock.economy.admin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CursorRulesTest {

    private static final long NOW = 1_000_000L;
    private static final long GRACE = 120_000L;

    @Test
    void consecutiveIdsAreAllSafe() {
        assertEquals(3, CursorRules.safeCount(10, List.of(11L, 12L, 13L), List.of(NOW, NOW, NOW), NOW, GRACE));
    }

    @Test
    void stopsBeforeAHoleFollowedByAYoungRow() {
        // 12 is still committing: 13 and 14 must wait
        assertEquals(1, CursorRules.safeCount(10, List.of(11L, 13L, 14L), List.of(NOW, NOW - 1_000, NOW), NOW, GRACE));
    }

    @Test
    void holeAtTheStartWaitsToo() {
        assertEquals(0, CursorRules.safeCount(10, List.of(12L), List.of(NOW - 5_000), NOW, GRACE));
    }

    @Test
    void oldHoleIsARolledBackTransaction() {
        assertEquals(3, CursorRules.safeCount(10, List.of(11L, 13L, 14L), List.of(NOW - GRACE - 1, NOW - GRACE - 1, NOW), NOW, GRACE));
    }

    @Test
    void secondHoleStillWaits() {
        assertEquals(2, CursorRules.safeCount(10, List.of(11L, 13L, 15L),
                List.of(NOW - GRACE * 2, NOW - GRACE * 2, NOW - 1), NOW, GRACE));
    }

    @Test
    void empty() {
        assertEquals(0, CursorRules.safeCount(10, List.of(), List.of(), NOW, GRACE));
    }
}
