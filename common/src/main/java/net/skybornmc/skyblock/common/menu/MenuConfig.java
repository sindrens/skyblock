package net.skybornmc.skyblock.common.menu;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Menu framework settings ({@code menus.yml}). Texts live in {@code messages/menus.yml}. Layout: docs/UI_STYLE.md §5 (I9). */
public final class MenuConfig extends ModuleConfig {

    @Setting(path = "title.max-length", desc = "Longest chest title in characters (Bedrock cuts long titles). Leading breadcrumbs are dropped first.",
            min = 12, max = 48)
    public int titleMaxLength = 32;

    @Setting(path = "title.max-parts", desc = "Most title parts (Feature » Screen = 2). Extra leading parts are dropped; the item or player name stays.",
            min = 1, max = 4)
    public int titleMaxParts = 2;

    @Setting(path = "title.update-in-place", desc = "Tabs, pages and refreshes update the open window (no close/reopen flicker). "
            + "false = reopen the window when its title changes (fallback if a client shows the old title).")
    public boolean titleUpdateInPlace = true;

    @Setting(path = "navigation.max-depth", desc = "Most screens Back remembers per player (older ones are forgotten).",
            min = 2, max = 64)
    public int navigationMaxDepth = 16;

    @Setting(path = "navigation.origin-seconds", desc = "Seconds a menu that ran a command (e.g. /menu → /is) stays the Back target "
            + "for a screen that opens a moment later (after loading data).", min = 0, max = 30)
    public int navigationOriginSeconds = 3;

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

    @Setting(path = "bedrock-popups.enabled", desc = "Bedrock players can't click chat: they also get a small popup menu for "
            + "requests and links (TPA, island invites, trades, /discord, staff alerts). false = chat only.")
    public boolean bedrockPopups = true;

    @Setting(path = "bedrock-popups.queue-max", desc = "Most popups waiting per player while another menu is open (the oldest is dropped).",
            min = 1, max = 20)
    public int bedrockPopupQueueMax = 5;

    @Setting(path = "bedrock-popups.max-age-seconds", desc = "A waiting popup older than this is dropped (the chat line stays).",
            min = 10, max = 3600)
    public int bedrockPopupMaxAgeSeconds = 300;

    @Setting(path = "bedrock-popups.retry-ticks", desc = "Ticks between checks while a popup waits for a chat prompt to end (20 = 1 s).",
            min = 5, max = 200)
    public int bedrockPopupRetryTicks = 40;

    @Setting(path = "bedrock-forms.enabled", desc = "Bedrock players type text (island notes, warp names) in a form popup with "
            + "their on-screen keyboard instead of the chat (needs Floodgate). false = chat for everyone.")
    public boolean bedrockForms = true;

    @Setting(path = "gui.font.enabled", desc = "Frame layer master switch (GUI-FONT): Java players with the icon pack see menus as "
            + "our panel with a plaque and soft tiles, and the logo on the scoreboard and tab. false = nobody gets any glyph "
            + "(menus, scoreboard, tab); everyone sees the plain chest. Bedrock never sees glyphs.")
    public boolean fontEnabled = false;

    @Setting(path = "gui.font.panels", desc = "Draw the panel and hide empty slots. false also switches the plaque and the tiles off.")
    public boolean fontPanels = true;

    @Setting(path = "gui.font.titles", desc = "Plaque with the cartoon title letters (needs panels). false = the panel shows the plain title text.")
    public boolean fontTitles = true;

    @Setting(path = "gui.font.tiles", desc = "Soft tile behind every filled slot (needs panels). false = items float on the panel.")
    public boolean fontTiles = true;

    @Setting(path = "gui.font.logo", desc = "Logo glyph for the scoreboard title and the tab header (independent of panels).")
    public boolean fontLogo = true;

    @Setting(path = "gui.font.title-resend-min-ms", desc = "At most one frame title update per player per this many milliseconds "
            + "(a menu whose tiles change while open re-sends its title).", min = 100, max = 5000)
    public int fontTitleResendMinMs = 500;

    /** The frame layer switches of this file. */
    public net.skybornmc.skyblock.common.frame.FrameSwitches fontSwitches() {
        return new net.skybornmc.skyblock.common.frame.FrameSwitches(fontEnabled, fontPanels, fontTitles, fontTiles, fontLogo);
    }

    @Override
    public String fileName() {
        return "menus.yml";
    }

    /** I9 (GUI-P2 3.7): no frame, no filler, vanilla arrow/paper icons, no Main menu button. */
    @Override
    public java.util.Map<String, String> removedKeys() {
        String why = "I9: no frame, no filler, vanilla arrow/paper icons, no Main menu button";
        return java.util.Map.of("frame", why, "heads", why, "bar", why);
    }

    @Override
    public String header() {
        return """
                Menu (GUI) look and behaviour. Menu texts are in messages/menus.yml. Layout rules: docs/UI_STYLE.md section 5.
                Reload: /skyblock reload core""";
    }
}
