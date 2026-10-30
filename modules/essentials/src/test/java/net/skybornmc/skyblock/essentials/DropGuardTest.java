package net.skybornmc.skyblock.essentials;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QUICK1 drop guard: first drop refused, a second drop of the same item in time (or sneak + drop) goes through. */
class DropGuardTest {

    @Test
    void firstDropIsRefused() {
        assertFalse(DropGuard.allowed(false, null, "u:1", 1_000));
    }

    @Test
    void sneakDropAlwaysGoesThrough() {
        assertTrue(DropGuard.allowed(true, null, "u:1", 1_000));
    }

    @Test
    void secondDropOfTheSameItemInTimeGoesThrough() {
        DropGuard.Pending last = new DropGuard.Pending("u:1", 4_000);
        assertTrue(DropGuard.allowed(false, last, "u:1", 3_999));
        assertTrue(DropGuard.allowed(false, last, "u:1", 4_000));
    }

    @Test
    void lateOrDifferentItemAsksAgain() {
        DropGuard.Pending last = new DropGuard.Pending("u:1", 4_000);
        assertFalse(DropGuard.allowed(false, last, "u:1", 4_001), "window over");
        assertFalse(DropGuard.allowed(false, last, "u:2", 3_000), "another item");
    }
}
