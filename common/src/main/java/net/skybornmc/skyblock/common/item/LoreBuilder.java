package net.skybornmc.skyblock.common.item;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds lore lines (MiniMessage strings) in the exact UI_STYLE §4.1 order:
 * <pre>
 * subtitle
 * (blank) description (max 2 lines, ~32 chars)
 * (blank) custom lines (level bar, signed-by, skin, history …)
 * (blank) Stats header + stat lines
 * (blank) Enchants / perk slots header + enchants + empty slots
 * (blank) action lines (always last)
 * (blank) footer: RARITY TYPE
 * </pre>
 * Empty sections are left out and exactly one blank line separates sections. All words and formats come from
 * {@code messages/items.yml} through the {@code labels} function (key → MiniMessage); dynamic values are escaped so
 * player-made text (names, notes) can never inject tags.
 */
public final class LoreBuilder {

    /** Visible characters per lore line before wrapping (UI_STYLE: ~32 for descriptions, max ~36 per line). */
    public static final int DESCRIPTION_WIDTH = 32;
    public static final int DESCRIPTION_MAX_LINES = 2;
    public static final int LEVEL_BAR_LENGTH = 10;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Function<String, String> labels;
    private String subtitle = "";
    private final List<String> description = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final List<String> stats = new ArrayList<>();
    private final List<String> enchants = new ArrayList<>();
    private int perkSlotsUsed = -1;
    private int perkSlotsTotal = -1;
    private final List<String> actions = new ArrayList<>();
    private String footer = "";

    /** @param labels message key (e.g. {@code lore.stats}) → MiniMessage text; must never return null */
    public LoreBuilder(Function<String, String> labels) {
        this.labels = labels;
    }

    /** Escapes a dynamic value (player names, amounts) so it is shown literally. */
    public static String escape(Object value) {
        return MM.escapeTags(String.valueOf(value));
    }

    /** Replaces {@code {token}} placeholders in a trusted format string. Values must already be escaped. */
    public static String fill(String format, Map<String, String> tokens) {
        String out = format;
        for (Map.Entry<String, String> e : tokens.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    private String label(String key) {
        return labels.apply(key);
    }

    /** Subtitle line, e.g. {@code Tool · Tier 3} (MiniMessage, trusted). */
    public LoreBuilder subtitle(String miniMessage) {
        this.subtitle = miniMessage == null ? "" : miniMessage;
        return this;
    }

    /** Description as a plain sentence; wrapped to {@value #DESCRIPTION_WIDTH} chars, max 2 lines (rest cut with ...). */
    public LoreBuilder description(String plainText) {
        description.clear();
        if (plainText != null && !plainText.isBlank()) {
            String format = label("lore.description");
            for (String line : wrap(plainText, DESCRIPTION_WIDTH, DESCRIPTION_MAX_LINES)) {
                description.add(fill(format, Map.of("text", escape(line))));
            }
        }
        return this;
    }

    /** A custom line in the data section (trusted MiniMessage; escape any dynamic parts with {@link #escape}). */
    public LoreBuilder line(String miniMessage) {
        lines.add(miniMessage);
        return this;
    }

    /** Level line: {@code Lv 23 ●●●●●●●●●● 62%} with filled/empty dots colored differently (formats from messages). */
    public LoreBuilder level(int level, double progress) {
        double p = Math.max(0, Math.min(1, progress));
        int filled = (int) Math.round(p * LEVEL_BAR_LENGTH);
        String bar = fill(label("lore.level-bar"), Map.of(
                "filled", label("lore.level-bar-filled").repeat(filled),
                "empty", label("lore.level-bar-empty").repeat(LEVEL_BAR_LENGTH - filled)));
        lines.add(fill(label("lore.level"), Map.of(
                "level", escape(level),
                "bar", bar,
                "percent", escape((int) Math.floor(p * 100)))));
        return this;
    }

    /**
     * A stat line under the Stats header.
     *
     * @param colorTag style tag for the bullet, e.g. {@code pillar:mining} or {@code c:ember}
     */
    public LoreBuilder stat(String colorTag, String label, String value) {
        stats.add(fill(label("lore.stat"), Map.of(
                "color", colorTag,
                "label", escape(label),
                "value", escape(value))));
        return this;
    }

    /** A custom enchant line (shown in the enchant color). */
    public LoreBuilder enchant(String name) {
        enchants.add(fill(label("lore.enchant"), Map.of("name", escape(name))));
        return this;
    }

    /** Perk slots: the header shows {@code used/total} and empty slots are listed after the enchants. */
    public LoreBuilder perkSlots(int used, int total) {
        this.perkSlotsUsed = Math.max(0, used);
        this.perkSlotsTotal = Math.max(0, total);
        return this;
    }

    /** {@code ▶ Right-click to open upgrades}. Actions always render last, before the footer. */
    public LoreBuilder action(String input, String text) {
        actions.add(fill(label("lore.action"), Map.of("input", escape(input), "text", escape(text))));
        return this;
    }

    /** Footer {@code RARE TOOL}; none for {@link ItemType#MENU}. */
    public LoreBuilder footer(Rarity rarity, ItemType type) {
        if (!type.hasFooter()) {
            footer = "";
            return this;
        }
        footer = fill(label("lore.footer"), Map.of(
                "rarity", rarity.id(),
                "rarity-name", label("rarity." + rarity.id()),
                "type-name", label("type." + type.id())));
        return this;
    }

    /** The finished lore, section by section. */
    public List<String> build() {
        List<List<String>> sections = new ArrayList<>();
        if (!subtitle.isBlank()) {
            sections.add(List.of(subtitle));
        }
        sections.add(description);
        sections.add(lines);
        if (!stats.isEmpty()) {
            List<String> s = new ArrayList<>();
            s.add(label("lore.stats"));
            s.addAll(stats);
            sections.add(s);
        }
        if (!enchants.isEmpty() || perkSlotsTotal > 0) {
            List<String> s = new ArrayList<>();
            if (perkSlotsTotal > 0) {
                s.add(fill(label("lore.perk-slots"), Map.of(
                        "used", escape(perkSlotsUsed), "total", escape(perkSlotsTotal))));
            } else {
                s.add(label("lore.enchants"));
            }
            s.addAll(enchants);
            int empty = perkSlotsTotal > 0 ? Math.max(0, perkSlotsTotal - Math.max(perkSlotsUsed, enchants.size())) : 0;
            for (int i = 0; i < empty; i++) {
                s.add(label("lore.empty-slot"));
            }
            sections.add(s);
        }
        sections.add(actions);
        if (!footer.isBlank()) {
            sections.add(List.of(footer));
        }

        List<String> out = new ArrayList<>();
        for (List<String> section : sections) {
            if (section.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.add("");
            }
            out.addAll(section);
        }
        return out;
    }

    /** Word-wraps {@code text} to {@code width} and {@code maxLines}; cut text ends with "..." (Bedrock-safe, not "…"). */
    static List<String> wrap(String text, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = text.trim().split("\\s+");
        int i = 0;
        for (; i < words.length; i++) {
            String word = words[i];
            if (line.isEmpty()) {
                line.append(word.length() > width ? word.substring(0, width) : word);
            } else if (line.length() + 1 + word.length() <= width) {
                line.append(' ').append(word);
            } else {
                out.add(line.toString());
                line.setLength(0);
                if (out.size() == maxLines) {
                    break;
                }
                line.append(word.length() > width ? word.substring(0, width) : word);
            }
        }
        if (!line.isEmpty() && out.size() < maxLines) {
            out.add(line.toString());
        } else if (i < words.length && !out.isEmpty()) {
            String last = out.getLast();
            out.set(out.size() - 1, (last.length() > width - 3 ? last.substring(0, width - 3) : last) + "...");
        }
        return out;
    }
}
