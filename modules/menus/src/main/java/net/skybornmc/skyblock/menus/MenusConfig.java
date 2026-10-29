package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Settings of /menu, /help, /settings and the menu item ({@code navigation.yml}). Layout: menu.yml, help: help.yml. */
public final class MenusConfig extends ModuleConfig {

    @Setting(path = "menu-item.enabled", desc = "Give everyone the menu item (players can still turn it off in /settings).")
    public boolean menuItemEnabled = true;

    @Setting(path = "menu-item.slot", desc = "Hotbar slot of the menu item (1-9).", min = 1, max = 9)
    public int menuItemSlot = 9;

    @Setting(path = "menu-item.material", desc = "Item used for the menu item.")
    public String menuItemMaterial = "nether_star";

    @Override
    public String fileName() {
        return "navigation.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock navigation: /menu, /help, /settings and the menu item.
                The /menu layout is in menu.yml, the help pages in help.yml, all texts in messages/navigation.yml.
                Reload: /skyblock reload menus""";
    }
}
