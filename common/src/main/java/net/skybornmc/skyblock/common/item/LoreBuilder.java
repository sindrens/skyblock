package net.skybornmc.skyblock.common.item;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.skybornmc.skyblock.common.text.Messages;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The one shared lore builder (UI_STYLE §4.1, V2 look approved 2026-10-30). Used for custom items (through
 * {@link ItemRegistry}) and for menu icons ({@link #forMessages}, {@link #components}). Sections in this exact order:
 * <pre>
 * subtitle                                  muted
 * description                               wrapped to {@value #DESCRIPTION_WIDTH} chars
 * custom lines                              level bar, signed-by, skin, history ...
 * prices                                    | Buy Price: $8 each / | Sell Price: $2 each / You can afford: 12
 * | Stats                                   bar-prefixed rows
 * | Information (any custom section)        bar-prefixed "Label: value" rows
 * | Enchants / perk slots
 * progress                                  Progress: 23 / 50 + a dot bar
 * | Rewards                                 crate rewards (★ name (xN) | 12%) and quest rewards ([+] reward)
 * action lines                              » RIGHT-CLICK buy 64 (always last before the footer)
 * footer                                    RARE TOOL
 * </pre>
 * Empty sections are dropped and exactly one blank line separates sections. The builder only arranges: every word and
 * format comes from {@code messages/items.yml} through the {@code labels} function (key → MiniMessage); dynamic values
 * are escaped so player-made text (names, notes) can never inject tags.
 */
public final class LoreBuilder {

    /** Messages module that holds the lore formats. */
    public static final String MESSAGES = "items";
    /** Visible characters per description line before wrapping (UI_STYLE: max ~36 per line, descriptions ~32). */
    public static final int DESCRIPTION_WIDTH = 32;
    public static final int DESCRIPTION_MAX_LINES = 3;
    /** Hard visible width of any lore line (UI_STYLE §4.1). */
    public static final int LINE_WIDTH = 36;
    public static final int LEVEL_BAR_LENGTH = 10;
    public static final int PROGRESS_BAR_LENGTH = 10;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Function<String, String> labels;
    private String subtitle = "";
    private final List<String> description = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final List<String> prices = new ArrayList<>();
    private final List<String> stats = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();
    private final List<String> enchants = new ArrayList<>();
    private int perkSlotsUsed = -1;
    private int perkSlotsTotal = -1;
    private final List<String> progress = new ArrayList<>();
    private final List<String> rewards = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();
    private String footer = "";

    /** @param labels message key (e.g. {@code lore.stats}) → MiniMessage text; must never return null */
    public LoreBuilder(Function<String, String> labels) {
        this.labels = labels;
    }

    /** A builder reading its formats from {@code messages/items.yml}; missing keys are logged once and shown in red. */
    public static LoreBuilder forMessages(Messages messages) {
        return new LoreBuilder(labels(messages));
    }

    /** The label function over {@code messages/items.yml}. */
    public static Function<String, String> labels(Messages messages) {
        return key -> {
            String raw = messages.raw(MESSAGES, key);
            if (raw == null) {
                messages.component(Audience.empty(), MESSAGES, key); // logs the missing key once
                return "<red>[missing: items:" + key + "]</red>";
            }
            return raw;
        };
    }

    /** Parses finished lore lines for one viewer (Bedrock gets legacy colors), never italic. */
    public static List<Component> components(Messages messages, Audience viewer, List<String> lore) {
        List<Component> out = new ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(line.isEmpty() ? Component.empty()
                    : messages.parse(viewer, line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return out;
    }

    /** {@link #build()} parsed for {@code viewer}. */
    public List<Component> components(Messages messages, Audience viewer) {
        return components(messages, viewer, build());
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

    /** An item name in its rarity color and bold ({@code lore.name}); {@code nameMini} is trusted MiniMessage. */
    public static String name(Function<String, String> labels, Rarity rarity, String nameMini) {
        return fill(labels.apply("lore.name"), Map.of("rarity", rarity.id(), "name", nameMini));
    }

    private String label(String key) {
        return labels.apply(key);
    }

    /** Subtitle line, e.g. {@code Tool · Tier 3} (MiniMessage, trusted). */
    public LoreBuilder subtitle(String miniMessage) {
        this.subtitle = miniMessage == null ? "" : miniMessage;
        return this;
    }

    /** Description as a plain sentence; wrapped to {@value #DESCRIPTION_WIDTH} chars, max 3 lines (rest cut with ...). */
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

    // ------------------------------------------------------------------ prices

    /** {@code | Buy Price: $8 each} in the buy color. {@code price} is plain text (escaped), e.g. {@code $8}. */
    public LoreBuilder buyPrice(String price, boolean each) {
        prices.add(price("lore.buy-price", price, each));
        return this;
    }

    /** {@code | Sell Price: $2 each} in the sell color. */
    public LoreBuilder sellPrice(String price, boolean each) {
        prices.add(price("lore.sell-price", price, each));
        return this;
    }

    private String price(String key, String price, boolean each) {
        return fill(label(key), Map.of("price", escape(price), "unit", each ? label("lore.price-each") : ""));
    }

    /** {@code You can afford: 12} under the price lines. */
    public LoreBuilder afford(String amount) {
        prices.add(fill(label("lore.afford"), Map.of("amount", escape(amount))));
        return this;
    }

    /** A trusted extra line inside the price block (e.g. a "not for sale" warning). */
    public LoreBuilder priceLine(String miniMessage) {
        prices.add(miniMessage);
        return this;
    }

    // ------------------------------------------------------------------ sections

    /**
     * A stat line under the {@code | Stats} header.
     *
     * @param colorTag style tag for the bullet, e.g. {@code pillar:mining} or {@code c:ember} (formats may ignore it)
     */
    public LoreBuilder stat(String colorTag, String label, String value) {
        stats.add(fill(label("lore.stat"), Map.of(
                "color", colorTag,
                "label", escape(label),
                "value", escape(value))));
        return this;
    }

    /**
     * Starts a {@code | Title} section (bold header color) whose rows are bar-prefixed. Sections render in the order they
     * were started; one without rows is dropped.
     *
     * @param titleMini trusted MiniMessage title from a messages file, e.g. {@code Information}
     */
    public Section section(String titleMini) {
        Section s = new Section(titleMini);
        sections.add(s);
        return s;
    }

    /** One {@code | Header} section. */
    public final class Section {
        private final String title;
        private final List<String> rows = new ArrayList<>();

        private Section(String title) {
            this.title = title == null ? "" : title;
        }

        /** {@code | Label: value}; both plain text, escaped. */
        public Section row(String label, String value) {
            return rowMini(escape(label), escape(value));
        }

        /** {@code | Label: value}; both trusted MiniMessage. */
        public Section rowMini(String labelMini, String valueMini) {
            rows.add(fill(label("lore.section-row"), Map.of("label", labelMini, "value", valueMini)));
            return this;
        }

        /** {@code | any trusted text}. */
        public Section text(String miniMessage) {
            rows.add(fill(label("lore.section-line"), Map.of("text", miniMessage)));
            return this;
        }

        /** Back to the builder. */
        public LoreBuilder done() {
            return LoreBuilder.this;
        }

        List<String> render() {
            if (rows.isEmpty()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            out.add(fill(label("lore.section"), Map.of("title", title)));
            out.addAll(rows);
            return out;
        }
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

    // ------------------------------------------------------------------ progress and rewards

    /** {@code Progress: 23 / 50} plus a dot bar (filled in the gold color, empty in the locked color). */
    public LoreBuilder progress(long current, long max) {
        long safeMax = Math.max(1, max);
        long safeCurrent = Math.max(0, Math.min(current, safeMax));
        int filled = (int) Math.floor((double) safeCurrent / safeMax * PROGRESS_BAR_LENGTH);
        progress.clear();
        progress.add(fill(label("lore.progress"), Map.of(
                "current", escape(net.skybornmc.skyblock.common.util.Format.number(Math.max(0, current))),
                "max", escape(net.skybornmc.skyblock.common.util.Format.number(Math.max(0, max))))));
        progress.add(fill(label("lore.progress-bar"), Map.of(
                "filled", label("lore.progress-bar-filled").repeat(filled),
                "empty", label("lore.progress-bar-empty").repeat(PROGRESS_BAR_LENGTH - filled),
                "percent", escape((int) Math.floor((double) safeCurrent / safeMax * 100)))));
        return this;
    }

    /** A crate reward: {@code ★ Name (xN) | 12.5%}, the name in its rarity color. {@code name} is plain (escaped). */
    public LoreBuilder reward(Rarity rarity, String name, long amount, double chancePercent) {
        rewards.add(fill(label("lore.reward"), Map.of(
                "rarity", rarity.id(),
                "name", escape(name),
                "amount", escape(amount),
                "chance", escape(chance(chancePercent)))));
        return this;
    }

    /** A quest/mission reward: {@code [+] reward}; {@code rewardMini} is trusted MiniMessage (e.g. {@code $5,000}). */
    public LoreBuilder questReward(String rewardMini) {
        rewards.add(fill(label("lore.quest-reward"), Map.of("reward", rewardMini)));
        return this;
    }

    /** {@code 12.5}, {@code 0.05}, {@code 50}: at most 2 decimals, trailing zeros dropped. */
    static String chance(double percent) {
        if (Double.isNaN(percent) || percent < 0) {
            return "0";
        }
        return BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ actions and footer

    /** {@code » RIGHT-CLICK buy 64}: the input is shown upper case. Actions always render last, before the footer. */
    public LoreBuilder action(String input, String text) {
        actions.add(fill(label("lore.action"), Map.of(
                "input", escape(input == null ? "" : input.toUpperCase(Locale.ROOT)),
                "text", escape(text))));
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
        List<List<String>> parts = new ArrayList<>();
        if (!subtitle.isBlank()) {
            parts.add(List.of(subtitle));
        }
        parts.add(description);
        parts.add(lines);
        parts.add(prices);
        if (!stats.isEmpty()) {
            List<String> s = new ArrayList<>();
            s.add(label("lore.stats"));
            s.addAll(stats);
            parts.add(s);
        }
        for (Section section : sections) {
            parts.add(section.render());
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
            parts.add(s);
        }
        parts.add(progress);
        if (!rewards.isEmpty()) {
            List<String> s = new ArrayList<>();
            s.add(label("lore.rewards"));
            s.addAll(rewards);
            parts.add(s);
        }
        parts.add(actions);
        if (!footer.isBlank()) {
            parts.add(List.of(footer));
        }

        List<String> out = new ArrayList<>();
        for (List<String> part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.add("");
            }
            out.addAll(part);
        }
        return out;
    }

    /** Word-wraps {@code text} to {@code width} with no line limit. */
    public static List<String> wrap(String text, int width) {
        return wrap(text, width, Integer.MAX_VALUE);
    }

    /** Word-wraps {@code text} to {@code width} and {@code maxLines}; cut text ends with "..." (Bedrock-safe, not "…"). */
    public static List<String> wrap(String text, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
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
