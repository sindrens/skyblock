package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.text.Messages;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Text pieces shared by items and menus: set and piece names, bonus lines, tier numerals. Every word comes from
 * {@code messages/wardrobe.yml}; formats use {@code {token}} placeholders (items) because lore is rendered once for
 * every viewer.
 */
final class WardrobeText {

    private final Messages messages;

    WardrobeText(Messages messages) {
        this.messages = messages;
    }

    /** Raw MiniMessage of a key, or a visible marker when it is missing. */
    String raw(String key) {
        String raw = messages.raw(WardrobeModule.ID, key);
        return raw == null ? "<red>[missing: wardrobe:" + key + "]" : raw;
    }

    /** Raw text with the tags removed (for plain item descriptions). */
    String plain(String key) {
        return raw(key).replaceAll("<[^>]+>", "");
    }

    /** {@code Deepforge Cuirass} (plain). */
    String pieceName(SetDef def, ArmorPiece piece) {
        return def.shortName() + " " + plain("piece-name." + piece.key());
    }

    /** Plain menu name of an entry: the set name, or {@code Deepforge · Coinmaster} for a merged entry. */
    String entryName(SetRegistry registry, WardrobeStore.Entry entry) {
        String a = registry.get(entry.setId()).map(SetDef::shortName).orElse(entry.setId());
        if (!entry.isMerged()) {
            return registry.get(entry.setId()).map(SetDef::name).orElse(entry.setId());
        }
        String b = registry.get(entry.mergedSetId()).map(SetDef::shortName).orElse(entry.mergedSetId());
        return LoreBuilder.fill(plain("merged-name"), Map.of("a", a, "b", b));
    }

    /** Colored (MiniMessage) menu name of an entry, in the look set's rarity color. */
    String entryColored(SetRegistry registry, WardrobeStore.Entry entry) {
        String rarity = registry.get(entry.look()).or(() -> registry.get(entry.setId())).map(d -> d.rarity().id()).orElse("common");
        return "<rarity:" + rarity + ">" + LoreBuilder.escape(entryName(registry, entry));
    }

    /** {@code ●●●●○○○○○○} for a progress 0..1 with {@code cells} cells. */
    static String bar(double progress, int cells) {
        int n = Math.max(1, cells);
        int full = (int) Math.floor(Math.max(0, Math.min(1, progress)) * n + 1e-9);
        return "●".repeat(full) + "○".repeat(n - full);
    }

    /** {@code <rarity:epic>Deepforge Plate} (MiniMessage, escaped name). */
    static String colored(SetDef def) {
        return "<rarity:" + def.rarity().id() + ">" + LoreBuilder.escape(def.name());
    }

    /** {@code I}…{@code IV}. */
    static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(tier);
        };
    }

    /** A bonus line (MiniMessage) like {@code Mining speed +4%} for a value already multiplied. */
    String bonus(String textKey, double value) {
        return LoreBuilder.fill(raw("bonus-text." + textKey), Map.of("value", LoreBuilder.escape(number(value))));
    }

    /** {@code 4}, {@code 4.5}, {@code 0.12}: at most two decimals, no trailing zeros. */
    static String number(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        BigDecimal d = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return d.scale() < 0 ? d.setScale(0).toPlainString() : d.toPlainString();
    }
}
