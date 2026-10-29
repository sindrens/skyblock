package net.skybornmc.skyblock.chat;

import java.util.regex.Pattern;

/**
 * Removes the {@code &} codes a player is not allowed to use in chat (Essentials-style tiers):
 * <ul>
 *   <li>classic colors {@code &0-&9 &a-&f} and {@code &r}</li>
 *   <li>formats {@code &l &m &n &o} ({@code &k} obfuscation is never allowed in chat)</li>
 *   <li>hex {@code &#RRGGBB} / {@code {#RRGGBB}}</li>
 * </ul>
 */
final class ChatColors {

    private static final Pattern HEX = Pattern.compile("(?i)(?:&#|\\{#)[0-9a-f]{6}}?");
    private static final Pattern CLASSIC = Pattern.compile("(?i)[&§][0-9a-fr]");
    private static final Pattern FORMAT = Pattern.compile("(?i)[&§][l-o]");
    private static final Pattern OBFUSCATED = Pattern.compile("(?i)[&§]k");
    private static final Pattern SECTION = Pattern.compile("§");

    private ChatColors() {
    }

    /** Text with only the allowed codes left. {@code §} is always turned into {@code &} first. */
    static String filter(String text, boolean classic, boolean format, boolean hex) {
        String out = SECTION.matcher(text).replaceAll("&");
        out = OBFUSCATED.matcher(out).replaceAll("");
        if (!hex) {
            out = HEX.matcher(out).replaceAll("");
        }
        if (!format) {
            out = FORMAT.matcher(out).replaceAll("");
        }
        if (!classic) {
            out = CLASSIC.matcher(out).replaceAll("");
        }
        return out;
    }
}
