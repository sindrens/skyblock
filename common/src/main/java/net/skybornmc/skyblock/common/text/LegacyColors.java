package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Essentials-style color codes, used for LuckPerms prefixes/suffixes, nicknames and player chat colors:
 * {@code &4 &c &b …}, formats {@code &l &o &n &m &k &r}, and hex as {@code &#FF3D2E} or {@code {#FF3D2E}}.
 * Works next to MiniMessage: {@link #toMiniMessage} converts codes so a prefix like {@code "&c&lADMIN &#FF8A1F»"}
 * can be embedded in any message.
 */
public final class LegacyColors {

    private static final Pattern HEX = Pattern.compile("(?i)(?:&#|\\{#)([0-9a-f]{6})}?");
    private static final Pattern CODE = Pattern.compile("(?i)[&§]([0-9a-fk-or])");
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private LegacyColors() {
    }

    /** {@code "&c&lADMIN"} → Component (hex supported). */
    public static Component toComponent(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return Component.empty();
        }
        Matcher m = HEX.matcher(legacy.replace('§', '&'));
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            StringBuilder x = new StringBuilder("&x");
            for (char c : m.group(1).toCharArray()) {
                x.append('&').append(c);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(x.toString()));
        }
        m.appendTail(out);
        return AMPERSAND.deserialize(out.toString());
    }

    /** {@code "&c&lADMIN &#FF8A1F»"} → {@code "<red><bold>ADMIN <#FF8A1F>»"} for use inside MiniMessage text. */
    public static String toMiniMessage(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return "";
        }
        String text = HEX.matcher(legacy).replaceAll(r -> "<reset><#" + r.group(1).toUpperCase(Locale.ROOT) + ">");
        Matcher m = CODE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(tag(Character.toLowerCase(m.group(1).charAt(0)))));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Removes all color codes (for length checks, logs, search). */
    public static String strip(String legacy) {
        if (legacy == null) {
            return "";
        }
        return CODE.matcher(HEX.matcher(legacy).replaceAll("")).replaceAll("");
    }

    /** True if the text contains any color code. */
    public static boolean hasCodes(String text) {
        return text != null && (HEX.matcher(text).find() || CODE.matcher(text).find());
    }

    private static String tag(char code) {
        return switch (code) {
            case '0' -> "<reset><black>";
            case '1' -> "<reset><dark_blue>";
            case '2' -> "<reset><dark_green>";
            case '3' -> "<reset><dark_aqua>";
            case '4' -> "<reset><dark_red>";
            case '5' -> "<reset><dark_purple>";
            case '6' -> "<reset><gold>";
            case '7' -> "<reset><gray>";
            case '8' -> "<reset><dark_gray>";
            case '9' -> "<reset><blue>";
            case 'a' -> "<reset><green>";
            case 'b' -> "<reset><aqua>";
            case 'c' -> "<reset><red>";
            case 'd' -> "<reset><light_purple>";
            case 'e' -> "<reset><yellow>";
            case 'f' -> "<reset><white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            default -> "<reset>";
        };
    }
}
