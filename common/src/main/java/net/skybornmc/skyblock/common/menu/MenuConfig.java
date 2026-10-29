package net.skybornmc.skyblock.common.menu;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Menu framework settings ({@code menus.yml}). Texts live in {@code messages/menus.yml}. */
public final class MenuConfig extends ModuleConfig {

    @Setting(path = "frame.top-bottom", desc = "Filler item of the top and bottom rows (item id, e.g. red_stained_glass_pane).")
    public String frameTopBottom = "red_stained_glass_pane";

    @Setting(path = "frame.sides", desc = "Filler item of the side columns.")
    public String frameSides = "black_stained_glass_pane";

    @Setting(path = "frame.inner", desc = "Filler for empty slots inside small menus (confirm dialog).")
    public String frameInner = "black_stained_glass_pane";

    @Setting(desc = "Minimum milliseconds between two clicks of one player in a menu (anti double-click dupes).",
            min = 0, max = 2000)
    public int clickDebounceMs = 120;

    @Setting(path = "prompt.timeout-seconds", desc = "Seconds a chat prompt (type a value in chat) waits before it is cancelled.",
            min = 5, max = 600)
    public int promptTimeoutSeconds = 60;

    @Setting(path = "prompt.cancel-word", desc = "Word players type to cancel a chat prompt.")
    public String promptCancelWord = "cancel";

    @Setting(desc = "Close open menus when the player teleports.")
    public boolean closeOnTeleport = true;

    @Override
    public String fileName() {
        return "menus.yml";
    }

    @Override
    public String header() {
        return """
                Menu (GUI) framework settings. Menu texts are in messages/menus.yml.
                Reload: /skyblock reload core""";
    }
}
