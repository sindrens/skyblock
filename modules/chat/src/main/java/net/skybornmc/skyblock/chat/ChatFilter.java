package net.skybornmc.skyblock.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Content checks for one chat message (no state, no Bukkit; easy to test). Rate limit, duplicates, slow mode and the
 * new-player delay need history and live in {@link ChatModule}.
 */
final class ChatFilter {

    /** The rules, built from {@code chat.yml}. */
    record Rules(int capsMinLetters, double capsRatio, boolean blockLinks, List<String> allowedDomains, boolean blockIps,
                 boolean blockPhoneNumbers, List<String> replaceWords, List<String> blockWords) {
    }

    /**
     * Outcome. {@code blocked} is a message key suffix ({@code link}, {@code ip}, {@code phone}, {@code word}) or null;
     * {@code changes} lists what was altered ({@code caps}, {@code word}) for the audit log.
     */
    record Result(String text, String blocked, List<String> changes) {
        boolean isBlocked() {
            return blocked != null;
        }
    }

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)?((?:[a-z0-9-]+\\.)+(?:com|net|org|gg|io|xyz|me|co|tv|de|no|uk|eu|ru|us|club|fun|pro|"
                    + "online|site|store|live|world|space|top|info|biz|nl|se|dk|fi|fr|pl|es|it|ca|au|br))\\b(?:/\\S*)?");
    private static final Pattern IP = Pattern.compile("\\b(?:\\d{1,3}[.,\\s]){3}\\d{1,3}\\b(?::\\d{2,5})?");
    private static final Pattern PHONE = Pattern.compile("(?<![\\d.,])\\+?\\d[\\d\\s-]{6,16}\\d(?![\\d.,])");
    private static final Pattern THOUSANDS = Pattern.compile("\\d{1,3}(?:[ ,.]\\d{3})+");

    private ChatFilter() {
    }

    static Result check(String input, Rules rules, boolean linkBypass) {
        String text = input;
        List<String> changes = new ArrayList<>();

        if (rules.blockLinks() && !linkBypass && hasForeignLink(text, rules.allowedDomains())) {
            return new Result(input, "link", changes);
        }
        if (rules.blockIps() && !linkBypass && IP.matcher(text).find()) {
            return new Result(input, "ip", changes);
        }
        if (rules.blockPhoneNumbers() && hasPhoneNumber(text)) {
            return new Result(input, "phone", changes);
        }
        String normalized = normalize(text);
        for (String word : rules.blockWords()) {
            if (!word.isBlank() && containsWord(normalized, normalize(word))) {
                return new Result(input, "word", changes);
            }
        }
        for (String word : rules.replaceWords()) {
            if (word.isBlank()) {
                continue;
            }
            Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b");
            Matcher m = p.matcher(text);
            if (m.find()) {
                text = m.replaceAll(r -> "*".repeat(r.group().length()));
                changes.add("word");
            }
        }
        if (isShouting(text, rules.capsMinLetters(), rules.capsRatio())) {
            text = text.toLowerCase(Locale.ROOT);
            changes.add("caps");
        }
        return new Result(text, null, changes);
    }

    static boolean hasForeignLink(String text, List<String> allowed) {
        Matcher m = URL.matcher(text);
        while (m.find()) {
            String host = m.group(1).toLowerCase(Locale.ROOT);
            boolean ok = allowed.stream().map(d -> d.toLowerCase(Locale.ROOT))
                    .anyMatch(d -> host.equals(d) || host.endsWith("." + d));
            if (!ok) {
                return true;
            }
        }
        return false;
    }

    /**
     * 8–13 digits written like a phone number: with a leading {@code +} or grouped by spaces/dashes. Plain big numbers
     * ({@code 1000000000}) and thousands separators ({@code 10 000 000}) are not phone numbers.
     */
    static boolean hasPhoneNumber(String text) {
        Matcher m = PHONE.matcher(text);
        while (m.find()) {
            String candidate = m.group().strip();
            long digits = candidate.chars().filter(Character::isDigit).count();
            boolean grouped = candidate.startsWith("+") || candidate.matches(".*\\d[\\s-]\\d.*");
            if (digits >= 8 && digits <= 13 && grouped && !THOUSANDS.matcher(candidate).matches()) {
                return true;
            }
        }
        return false;
    }

    /** More than {@code ratio} of the letters are capitals and there are at least {@code minLetters} letters. */
    static boolean isShouting(String text, int minLetters, double ratio) {
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        return letters >= minLetters && upper > ratio * letters;
    }

    /** Lower case, common letter swaps undone (1→i, 3→e, 4→a, 0→o, 5→s, @→a, $→s), separators removed inside words. */
    static String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(switch (c) {
                case '1', '!' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '0' -> 'o';
                case '5', '$' -> 's';
                case '7' -> 't';
                default -> c;
            });
        }
        return sb.toString().replaceAll("(?<=\\p{L})[._*\\-]+(?=\\p{L})", "");
    }

    private static boolean containsWord(String normalizedText, String normalizedWord) {
        return Pattern.compile("(?<!\\p{L})" + Pattern.quote(normalizedWord) + "(?!\\p{L})").matcher(normalizedText).find();
    }

    /** Normalized form used for the duplicate-message check ("Hello!!" == "hello"). */
    static String duplicateKey(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
