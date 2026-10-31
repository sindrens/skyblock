package net.skybornmc.skyblock.common.menu;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Menu framework settings ({@code menus.yml}). Texts live in {@code messages/menus.yml}. Layout: docs/UI_STYLE.md §5. */
public final class MenuConfig extends ModuleConfig {

    @Setting(path = "frame.border", desc = "Border pane of every menu (item id). Dark, so the content stands out.")
    public String frameBorder = "black_stained_glass_pane";

    @Setting(path = "frame.accent", desc = "Corner accent pane (Phoenix red).")
    public String frameAccent = "red_stained_glass_pane";

    @Setting(path = "frame.accent-glow", desc = "Pane next to each corner (ember orange), so the corners fade like a flame.")
    public String frameAccentGlow = "orange_stained_glass_pane";

    @Setting(path = "frame.inner", desc = "Filler for empty slots inside small menus (confirm dialog, number picker).")
    public String frameInner = "gray_stained_glass_pane";

    @Setting(path = "heads.back", desc = "Back button head: a player name (e.g. MHF_ArrowLeft) or a base64 texture value from minecraft-heads.com. Empty = arrow item.")
    public String headBack = "MHF_ArrowLeft";

    @Setting(path = "heads.previous", desc = "Previous page head (player name or base64 texture). Empty = arrow item.")
    public String headPrevious = "MHF_ArrowLeft";

    @Setting(path = "heads.next", desc = "Next page head (player name or base64 texture). Empty = arrow item.")
    public String headNext = "MHF_ArrowRight";

    @Setting(path = "title.max-length", desc = "Longest chest title in characters (Bedrock cuts long titles). Leading breadcrumbs are dropped first.",
            min = 12, max = 48)
    public int titleMaxLength = 32;

    @Setting(path = "title.max-parts", desc = "Most title parts (Feature » Screen = 2). Extra leading parts are dropped; the item or player name stays.",
            min = 1, max = 4)
    public int titleMaxParts = 2;

    @Setting(path = "title.update-in-place", desc = "Tabs, pages and refreshes update the open window (no close/reopen flicker). "
            + "false = reopen the window when its title changes (fallback if a client shows the old title).")
    public boolean titleUpdateInPlace = true;

    @Setting(path = "bar.main-menu-command", desc = "Command the Main menu button (slot 50) runs, without the slash.")
    public String mainMenuCommand = "menu";

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

    @Override
    public String fileName() {
        return "menus.yml";
    }

    @Override
    public String header() {
        return """
                Menu (GUI) look and behaviour. Menu texts are in messages/menus.yml. Layout rules: docs/UI_STYLE.md section 5.
                Reload: /skyblock reload core""";
    }
}
