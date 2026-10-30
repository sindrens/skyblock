package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Warp names, slots, management and categories (M2.4). */
class WarpRulesTest {

    @Test
    void names() {
        assertEquals(WarpRules.NameCheck.OK, WarpRules.checkName("shop", 16));
        assertEquals(WarpRules.NameCheck.OK, WarpRules.checkName("Mob_Farm-2", 16));
        assertEquals(WarpRules.NameCheck.EMPTY, WarpRules.checkName("", 16));
        assertEquals(WarpRules.NameCheck.EMPTY, WarpRules.checkName(null, 16));
        assertEquals(WarpRules.NameCheck.TOO_LONG, WarpRules.checkName("a".repeat(17), 16));
        assertEquals(WarpRules.NameCheck.CHARACTERS, WarpRules.checkName("my shop", 16));
        assertEquals(WarpRules.NameCheck.CHARACTERS, WarpRules.checkName("<red>x", 16));
        assertEquals("mob_farm", WarpRules.key("Mob_Farm"));
    }

    @Test
    void slotsDefaultToThreeAndAreCapped() {
        assertEquals(3, WarpRules.slots(null, 3, 10));
        assertEquals(5, WarpRules.slots(5, 3, 10));
        assertEquals(10, WarpRules.slots(50, 3, 10));
        assertEquals(0, WarpRules.slots(-1, 3, 10));
    }

    @Test
    void limit() {
        assertTrue(WarpRules.fits(2, 3, false));
        assertFalse(WarpRules.fits(3, 3, false), "the 4th warp doesn't fit");
        assertTrue(WarpRules.fits(3, 3, true), "moving an existing warp always fits");
        assertFalse(WarpRules.fits(0, 0, false));
    }

    @Test
    void managementAndUse() {
        assertTrue(WarpRules.canManage(Role.LEADER));
        assertTrue(WarpRules.canManage(Role.MOD));
        assertFalse(WarpRules.canManage(Role.MEMBER));
        assertFalse(WarpRules.canManage(Role.COOP));
        assertTrue(WarpRules.canUse(true, null));
        assertFalse(WarpRules.canUse(false, null), "private warps are for members");
        assertTrue(WarpRules.canUse(false, Role.COOP));
    }

    @Test
    void categories() {
        assertEquals(5, WarpCategory.values().length);
        assertEquals(WarpCategory.SHOPS, WarpCategory.byKey(" Shops ").orElseThrow());
        assertEquals(WarpCategory.OTHER, WarpCategory.byKeyOrOther("castles"));
        assertEquals(WarpCategory.SHOPS, WarpCategory.OTHER.next(), "cycling wraps around");
        assertEquals(java.util.List.of("shops", "farms", "parkour", "builds", "other"), WarpCategory.keys());
    }
}
