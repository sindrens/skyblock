package net.skybornmc.skyblock.common.rank;

import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * How every rank looks as text (I7 phase 0, chat.yml {@code ranks.looks}): its colour, or a three-colour gradient
 * (Immortal), and the legacy colour code(s) Bedrock players see. Pure, no Bukkit, thread-safe (immutable).
 *
 * <p>One config line per LuckPerms group: {@code group=#RRGGBB|&code} or {@code group=#A,#B,#C|&e&l>&6&l>&c&l} (a
 * {@code >} splits the Bedrock text into equal parts, one code per part). The {@code default} entry is the look of any
 * group that has no line (the plain "Ashborn" look).</p>
 */
public final class RankLooks {

    /** The entry used for a group that has no line of its own. */
    public static final String DEFAULT_GROUP = "default";

    /** Used only when even the {@code default} line is missing or broken. */
    private static final Look FALLBACK = new Look(List.of(TextColor.color(0xB5ABA4)), "&7");

    /**
     * The shipped looks, copied exactly from the retired pack v1 {@code pack-ids.yml badges.ranks} (colour and Bedrock
     * code; the font, shine, family and cap fields only drew the font badge and are gone).
     */
    /** The pack v1 looks shipped before Ranks V2; chat.yml v3 moves an untouched list to {@link #DEFAULTS}. */
    public static final List<String> DEFAULTS_V1 = List.of(
            "default=#B5ABA4|&7", "hatchling=#FF9A4D|&6", "fledgling=#9BE84A|&a", "firewing=#4FB4FF|&9", "flamecrest=#B06BFF|&5",
            "sunfeather=#FFD21A|&e", "pyrelord=#FF3B4A|&c", "immortal=#FFE27A,#FF8C1A,#FF2D3F|&e&l>&6&l>&c&l",
            "torchbearer=#2EE6D0|&3", "flameheart=#FF5FD2|&d", "firecrown=#FFFFFF|&f&l", "owner=#FFC21A|&e&l",
            "admin=#FF6B6B|&c&l", "mod=#6CC4FF|&9&l", "helper=#7CEB8A|&a&l", "builder=#FF9A3D|&6&l");

    /** Ranks V2 (build spec RANKS-V2, owner EP-Q10 to EP-Q15): staff in bold caps, Phoenix-life donor ranks, Ashborn grey, New dark grey. */
    public static final List<String> DEFAULTS = List.of(
            "default=#555555|&8",
            "ember=#AAAAAA|&7",
            "hatchling=#E3BE7A|&e",
            "fledgling=#C8702A|&6",
            "firewing=#FF7F9E|&c",
            "flamecrest=#D63FB0|&d",
            "sunfeather=#C27BFF|&5",
            "pyrelord=#2F5BFF|&9",
            "immortal=#FFE27A,#FF8C1A,#FF2D3F|&e&l>&6&l>&c&l",
            "torchbearer=#2A9D8F|&3",
            "flameheart=#8FB8FF|&b",
            "firecrown=#FFFFFF|&f&l",
            "owner=#FFFF55|&e&l",
            "admin=#FF5555|&c&l",
            "mod=#55FFFF|&b&l",
            "helper=#55FF55|&a&l",
            "builder=#FFAA00|&6&l");

    /**
     * One rank look.
     *
     * @param colors  one colour, or several for a left-to-right gradient; never empty
     * @param bedrock legacy {@code &} code(s); {@code >} splits the text into equal parts
     */
    public record Look(List<TextColor> colors, String bedrock) {

        public Look {
            colors = List.copyOf(colors);
            bedrock = bedrock == null || bedrock.isBlank() ? "&7" : bedrock.strip();
        }

        /** The one colour that stands for the rank (name in chat, tab, nametag, scoreboard): the middle of the list. */
        public TextColor nameColor() {
            return colors.get(colors.size() / 2);
        }

        /** True when every Bedrock part is bold ({@code &l}): staff, Immortal and Firecrown are bold on Java too. */
        public boolean bold() {
            return bedrockSegments().stream().allMatch(seg -> seg.toLowerCase(java.util.Locale.ROOT).contains("&l"));
        }

        /** The Bedrock codes, one per equal part of the text. */
        public List<String> bedrockSegments() {
            return List.of(bedrock.split(">", -1));
        }
    }

    /** The classic colour of a {@code &x} code ({@code &e} = #FFFF55), or null for a non-colour code. */
    public static String legacyHex(String code) {
        if (code == null || code.length() < 2) {
            return null;
        }
        return switch (Character.toLowerCase(code.charAt(1))) {
            case '0' -> "#000000"; case '1' -> "#0000AA"; case '2' -> "#00AA00"; case '3' -> "#00AAAA";
            case '4' -> "#AA0000"; case '5' -> "#AA00AA"; case '6' -> "#FFAA00"; case '7' -> "#AAAAAA";
            case '8' -> "#555555"; case '9' -> "#5555FF"; case 'a' -> "#55FF55"; case 'b' -> "#55FFFF";
            case 'c' -> "#FF5555"; case 'd' -> "#FF55FF"; case 'e' -> "#FFFF55"; case 'f' -> "#FFFFFF";
            default -> null;
        };
    }

    private final Map<String, Look> looks;

    private RankLooks(Map<String, Look> looks) {
        this.looks = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(looks));
    }

    /** The shipped looks ({@link #DEFAULTS}). */
    public static RankLooks defaults() {
        return parse(DEFAULTS, problem -> {
        });
    }

    /**
     * Reads config lines. A line without {@code =}, with a bad group id, a duplicate group or no valid colour is skipped;
     * a bad hex inside a line is dropped. Every problem is reported to {@code problems} (one log line each).
     */
    public static RankLooks parse(List<String> lines, Consumer<String> problems) {
        Map<String, Look> out = new LinkedHashMap<>();
        if (lines != null) {
            for (String line : lines) {
                Look look = null;
                String group = null;
                if (line != null) {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        group = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
                        look = parseLook(line.substring(eq + 1), line, problems);
                    }
                }
                if (group == null || !group.matches("[a-z0-9_-]+")) {
                    problems.accept("ranks.looks: '" + line + "' is not group=#RRGGBB|&code, skipped");
                } else if (out.containsKey(group)) {
                    problems.accept("ranks.looks: '" + group + "' is listed twice, the first line is used");
                } else if (look != null) {
                    out.put(group, look);
                }
            }
        }
        return new RankLooks(out);
    }

    private static Look parseLook(String value, String line, Consumer<String> problems) {
        int bar = value.indexOf('|');
        String colorPart = bar < 0 ? value : value.substring(0, bar);
        String bedrock = bar < 0 ? "" : value.substring(bar + 1);
        List<TextColor> colors = new ArrayList<>();
        for (String hex : colorPart.split(",")) {
            TextColor c = TextColor.fromHexString(hex.strip());
            if (c == null) {
                problems.accept("ranks.looks: '" + hex.strip() + "' in '" + line + "' is not a #RRGGBB colour, ignored");
            } else {
                colors.add(c);
            }
        }
        if (colors.isEmpty()) {
            problems.accept("ranks.looks: '" + line + "' has no valid colour, skipped");
            return null;
        }
        return new Look(colors, bedrock);
    }

    /** The look of {@code group}; the {@code default} look for an unknown or blank group. Never null. */
    public Look look(String group) {
        Look own = group == null ? null : looks.get(group.toLowerCase(Locale.ROOT));
        if (own != null) {
            return own;
        }
        return looks.getOrDefault(DEFAULT_GROUP, FALLBACK);
    }

    /** True when {@code group} has its own line. */
    public boolean has(String group) {
        return group != null && looks.containsKey(group.toLowerCase(Locale.ROOT));
    }

    /** Every group with its own line (tab completion of {@code /rank preview}). */
    public java.util.Set<String> groups() {
        return looks.keySet();
    }
}
