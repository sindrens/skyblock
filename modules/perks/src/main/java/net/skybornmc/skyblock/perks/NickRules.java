package net.skybornmc.skyblock.perks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure nickname validation (no server access, unit-testable).
 *
 * <ul>
 *   <li>{@link Tier#PLAIN}: letters, digits and {@code _} only.</li>
 *   <li>{@link Tier#COLOR}: plus classic {@code &} color codes and {@code &l &o &n &r} (no {@code &k} magic text).</li>
 *   <li>{@link Tier#HEX}: plus MiniMessage colors, hex ({@code <#FF3D2E>}), gradients and rainbow.</li>
 * </ul>
 * The visible text must always be {@code [A-Za-z0-9_]} within the length limits, so a nickname never contains symbols,
 * spaces or hidden characters that make impersonation easy.
 */
public final class NickRules {

    public enum Tier { PLAIN, COLOR, HEX }

    /** {@code formatted} is safe MiniMessage (only whitelisted tags), {@code plain} the visible letters. */
    public record Nick(String formatted, String plain) {
    }

    /** Validation failure carrying a key of {@code messages/perks.yml}. */
    public static final class Invalid extends Exception {
        private final String key;

        public Invalid(String key) {
            super(key, null, false, false);
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** Only colors, decorations, gradients, rainbow and reset: no click, hover, insertion, fonts or translations. */
    public static final MiniMessage SAFE = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(),
                    StandardTags.rainbow(), StandardTags.reset()))
            .build();

    private static final Pattern PLAIN = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern LEGACY = Pattern.compile("&([0-9a-fA-FlonrLONR])");
    private static final Pattern ANY_LEGACY = Pattern.compile("[&§][0-9a-zA-Z]");
    private static final Pattern OBFUSCATED = Pattern.compile("<(obf|obfuscated)(:[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"), Map.entry('b', "<aqua>"), Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"), Map.entry('f', "<white>"), Map.entry('l', "<bold>"), Map.entry('o', "<italic>"),
            Map.entry('n', "<underlined>"), Map.entry('r', "<reset>"));

    private NickRules() {
    }

    /**
     * Validates {@code input} for {@code tier}.
     *
     * @throws Invalid with key {@code nick.too-long-raw}, {@code nick.no-format}, {@code nick.no-hex},
     *                 {@code nick.invalid-chars}, {@code nick.too-short} or {@code nick.too-long}
     */
    public static Nick validate(String input, Tier tier, int minLength, int maxLength) throws Invalid {
        String raw = input == null ? "" : input.strip();
        if (raw.length() > 256) {
            throw new Invalid("nick.too-long-raw");
        }
        String formatted;
        switch (tier) {
            case PLAIN -> {
                if (raw.indexOf('<') >= 0 || raw.indexOf('>') >= 0 || ANY_LEGACY.matcher(raw).find()) {
                    throw new Invalid("nick.no-format");
                }
                formatted = raw;
            }
            case COLOR -> {
                if (raw.indexOf('<') >= 0 || raw.indexOf('>') >= 0) {
                    throw new Invalid("nick.no-hex");
                }
                formatted = legacyToMini(raw);
            }
            default -> formatted = legacyToMini(raw);
        }
        if (ANY_LEGACY.matcher(formatted).find() || OBFUSCATED.matcher(formatted).find()) {
            throw new Invalid("nick.invalid-chars"); // &k magic text, § codes, <obf>
        }
        Component parsed = SAFE.deserialize(formatted);
        String plain = PlainTextComponentSerializer.plainText().serialize(parsed);
        if (!PLAIN.matcher(plain).matches()) {
            throw new Invalid("nick.invalid-chars");
        }
        if (plain.length() < minLength) {
            throw new Invalid("nick.too-short");
        }
        if (plain.length() > maxLength) {
            throw new Invalid("nick.too-long");
        }
        return new Nick(formatted, plain);
    }

    /** First reserved word contained in {@code plain} (case-insensitive), or {@code null}. */
    public static String reservedWord(String plain, Collection<String> reserved) {
        String lower = plain.toLowerCase(Locale.ROOT);
        for (String word : reserved) {
            if (word != null && !word.isBlank() && lower.contains(word.toLowerCase(Locale.ROOT))) {
                return word;
            }
        }
        return null;
    }

    /** True if {@code plain} equals the name of another known player (case-insensitive) other than {@code ownName}. */
    public static boolean impersonates(String plain, String ownName, Collection<String> knownNames) {
        if (plain.equalsIgnoreCase(ownName)) {
            return false;
        }
        for (String name : knownNames) {
            if (name.equalsIgnoreCase(plain)) {
                return true;
            }
        }
        return false;
    }

    static String legacyToMini(String raw) {
        StringBuilder out = new StringBuilder();
        Matcher m = LEGACY.matcher(raw);
        while (m.find()) {
            char code = Character.toLowerCase(m.group(1).charAt(0));
            m.appendReplacement(out, Matcher.quoteReplacement(LEGACY_TAGS.get(code)));
        }
        m.appendTail(out);
        return out.toString();
    }
}
