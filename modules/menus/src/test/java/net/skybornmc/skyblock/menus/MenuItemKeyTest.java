package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.menu.MenuService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIXES1 regression: the hotbar menu item shared the PDC key of GUI buttons, so the menu framework's leak check deleted
 * it on join ("Removed 1 menu item(s) from X's inventory") and after every menu close.
 */
class MenuItemKeyTest {

    @Test
    void hotbarItemHasItsOwnKey() {
        assertNotEquals(MenuService.BUTTON_KEY, MenuItem.KEY);
        assertTrue(MenuItem.KEY.matches("[a-z0-9_]+"), "valid namespaced key");
    }

    /** SMALL2: the periodic check only does the full inventory pass when the item can actually go back. */
    @Test
    void cheapCheckOnlyActsWhenThereIsRoom() {
        assertEquals(MenuItem.Placement.NONE, MenuItem.placement(true, false, true, false, 3), "already in its slot");
        assertEquals(MenuItem.Placement.NONE, MenuItem.placement(false, false, false, true, 3), "turned off");
        assertEquals(MenuItem.Placement.NONE, MenuItem.placement(true, true, false, true, 3), "staff mode / profile switch");
        assertEquals(MenuItem.Placement.PUT, MenuItem.placement(true, false, false, true, -1), "empty slot needs no free slot");
        assertEquals(MenuItem.Placement.MOVE_THEN_PUT, MenuItem.placement(true, false, false, false, 12));
        assertEquals(MenuItem.Placement.NO_ROOM, MenuItem.placement(true, false, false, false, -1),
                "full inventory with an own item in the slot: nothing, and no full scan every few seconds");
    }
}
