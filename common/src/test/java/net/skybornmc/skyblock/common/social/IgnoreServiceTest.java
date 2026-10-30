package net.skybornmc.skyblock.common.social;

import net.skybornmc.skyblock.common.item.StaffItems;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ANS3: the shared ignore rule (owner answer 316) and the staff-item helpers (owner answer 332). */
class IgnoreServiceTest {

    @Test
    void ignoringBlocksRequestsButNeverStaffOrYourself() {
        assertTrue(IgnoreService.blocks(false, false, true), "an ignored player's request is blocked");
        assertFalse(IgnoreService.blocks(false, false, false), "not ignored: allowed");
        assertFalse(IgnoreService.blocks(false, true, true), "staff can never be ignored (owner: never staff)");
        assertFalse(IgnoreService.blocks(true, false, true), "yourself is never blocked");
    }

    @Test
    void staffItemSourceIsShortAndClean() {
        assertEquals("staff", StaffItems.cleanSource(null));
        assertEquals("staff", StaffItems.cleanSource("  "));
        assertEquals("give:SlickyMatt", StaffItems.cleanSource("GIVE:SlickyMatt"));
        assertEquals(64, StaffItems.cleanSource("creative:" + "x".repeat(200)).length());
    }

    @Test
    void placedStaffBlockKeysAreValidNamespacedKeys() {
        assertEquals("staff_block/-12/64/7", StaffItems.blockKeyName(-12, 64, 7));
        assertDoesNotThrow(() -> new NamespacedKey("phoenixskyblock", StaffItems.blockKeyName(-30_000_000, -64, 29_999_999)));
    }
}
