package net.skybornmc.skyblock.chat;

import java.util.List;
import java.util.Map;

/**
 * Old bundled texts of {@code messages/chat.yml}: a live text that still equals one word for word (never edited by the
 * owner) is upgraded to the new bundled text on load ({@code Messages#retire}).
 */
final class RetiredChatTexts {

    /** {@code hover} before 2 Nov 2026 (no title line). */
    static final List<String> HOVER_V1 = List.of(
            "<name>",
            "",
            "<c:muted>Rank</c:muted> <c:value><rank_name>",
            "<nick_line>",
            "<c:muted>Island</c:muted> <c:value><island>",
            "<c:muted>Played</c:muted> <c:value><playtime>",
            "",
            "<bold><c:action>» CLICK</c:action></bold> <c:text>to message");

    /** {@code tab.header} before 2 Nov 2026. */
    static final List<String> TAB_HEADER_V1 = List.of(
            "",
            "<c:sky_frost><st>            </st></c:sky_frost>  <bold><grad:skyborn>SKYBORN</grad><c:sky_blue>MC</c:sky_blue></bold>  "
                    + "<c:sky_blue><st>            </st></c:sky_blue>",
            "<bold><grad:phoenix>Phoenix Skyblock</grad></bold>",
            "",
            "<c:value><online></c:value> <c:muted>online <sym:dot> <season>",
            "");

    /** {@code tab.footer} before 2 Nov 2026. */
    static final List<String> TAB_FOOTER_V1 = List.of(
            "",
            "<c:sky_blue>»</c:sky_blue> <grad:skyborn>play.skybornmc.net</grad> <c:sky_blue>«</c:sky_blue>",
            "");

    private RetiredChatTexts() {
    }

    static Map<String, String> all() {
        return Map.of("hover", String.join("\n", HOVER_V1),
                "tab.header", String.join("\n", TAB_HEADER_V1),
                "tab.footer", String.join("\n", TAB_FOOTER_V1));
    }
}
