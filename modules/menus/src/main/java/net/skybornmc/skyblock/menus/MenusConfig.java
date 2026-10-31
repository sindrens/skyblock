package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Settings of /menu, /help, /settings and the menu item ({@code navigation.yml}). Layout: menu.yml, help: help.yml. */
public final class MenusConfig extends ModuleConfig {

    @Setting(path = "menu-item.enabled", desc = "Give everyone the menu item (players can still turn it off in /settings).")
    public boolean menuItemEnabled = true;

    @Setting(path = "menu-item.slot", desc = "Hotbar slot of the menu item (1-9).", min = 1, max = 9)
    public int menuItemSlot = 9;

    @Setting(path = "menu-item.material", desc = "Item used for the menu item.")
    public String menuItemMaterial = "nether_star";

    @Setting(path = "menu-item.check-seconds", desc = "Seconds between checks that put a missing menu item back "
            + "(it is also restored on join, respawn, world/game mode change and after closing an inventory). 0 = off.",
            min = 0, max = 300)
    public int menuItemCheckSeconds = 5;

    @Setting(path = "menu-item.inventory-open-delay-ticks", desc = "Clicking the menu item inside your own inventory first "
            + "closes the inventory, then opens /menu this many ticks later (UX3: Bedrock showed 'Large Chest' instead of the "
            + "title when the menu opened over the open inventory).", min = 1, max = 20)
    public int menuItemInventoryDelayTicks = 3;

    @Setting(path = "hub.show-unbuilt", desc = "Test server: show /menu and You buttons of features that are not built yet "
            + "in the 'Coming soon' look. Off (live server, owner G2): those buttons are hidden.")
    public boolean hubShowUnbuilt = false;

    @Setting(path = "top.cache-seconds", desc = "/menu → Top: seconds the Richest players and Top skills boards are reused "
            + "before they are read from the database again (island boards refresh on their own).", min = 5, max = 600)
    public int topCacheSeconds = 30;

    @Setting(path = "settings.ui-sound-keys", desc = "Sounds (sounds.yml keys) that count as menu sounds wherever they play; "
            + "players who turn 'Menu sounds' off in /settings don't hear them. Every sound a menu plays is muted too.")
    public List<String> uiSoundKeys = List.of("click", "menu-open", "menu-close", "page");

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
