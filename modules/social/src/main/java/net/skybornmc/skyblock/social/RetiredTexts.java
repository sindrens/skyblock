package net.skybornmc.skyblock.social;

import java.util.List;
import java.util.Map;

/**
 * Old bundled texts of {@code messages/social.yml}: a live text that still equals one word for word (never edited by the
 * owner) is upgraded to the new bundled text on load ({@code Messages#retire}).
 */
final class RetiredTexts {

    /** {@code rules} before STAFF-R3 (R3-9a added the line that report DMs are kept 180 days). */
    static final List<String> RULES_V1 = List.of(
            "",
            " <grad:phoenix><bold>RULES</bold></grad>",
            " <c:muted>1.</c:muted> <c:text>Be kind. No harassment, hate or threats.",
            " <c:muted>2.</c:muted> <c:text>No cheats, hacked clients, x-ray or unfair mods.",
            " <c:muted>3.</c:muted> <c:text>No duping or bug abuse. Report bugs to staff.",
            " <c:muted>4.</c:muted> <c:text>No spam, ads or scams.",
            " <c:muted>5.</c:muted> <c:text>No real-money trading outside the store.",
            " <c:muted>6.</c:muted> <c:text>Staff decisions are final unless you appeal with /appeal in game or on Discord.",
            " <c:muted>7.</c:muted> <c:text>English only in public chat. Private messages can be in any language.",
            " <c:muted>8.</c:muted> <c:text>No alt accounts to dodge punishments or farm rewards.",
            " <c:muted>9.</c:muted> <c:text>No macros or AFK farming outside the AFK pool.",
            " <c:muted>Chat, private messages and commands are logged for 30 days and staff can read them.</c:muted> "
                    + "<click:open_url:'https://skybornmc.net/privacy'><hover:show_text:'<c:action>Click to open'><c:sky_ice><u>Privacy"
                    + "</u></c:sky_ice></hover></click>",
            " <c:text>Full rules: <click:open_url:'<url>'><hover:show_text:'<c:action>Click to open'><c:sky_ice><u><url></u>"
                    + "</c:sky_ice></hover></click>",
            "");

    private RetiredTexts() {
    }

    static Map<String, String> all() {
        return Map.of("rules", String.join("\n", RULES_V1));
    }
}
