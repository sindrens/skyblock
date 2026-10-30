package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.menu.MenuService;
import org.junit.jupiter.api.Test;

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
}
