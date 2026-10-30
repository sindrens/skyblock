package net.skybornmc.skyblock.common.wardrobe;

import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.tools.ToolPerk;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One owned wardrobe entry (a set, or a merged set) of the viewer's active profile, as the Enchanter's Armor tab sees it
 * (E5). A snapshot: read it again after every change.
 *
 * @param entryId     the wardrobe row
 * @param setId       the entry's (first) set id
 * @param mergedSetId the second set of a merged entry, or null
 * @param lookSetId   the set whose look the pieces use
 * @param name        plain display name, e.g. {@code Deepforge Plate} or {@code Deepforge · Coinmaster}
 * @param rarity      the look set's rarity (name color)
 * @param level       the set level the enchant rules use (EN4/EN12): the entry's level, or the max level while set levels
 *                    are turned off
 * @param enchants    armor enchants per piece (every piece present, empty list = none)
 * @param worn        pieces of this entry the player wears right now
 */
public record SetEntryView(UUID entryId, String setId, String mergedSetId, String lookSetId, String name, Rarity rarity, int level,
                           Map<ArmorPiece, List<ToolPerk>> enchants, Set<ArmorPiece> worn) {

    public SetEntryView {
        Objects.requireNonNull(entryId, "entryId");
        Map<ArmorPiece, List<ToolPerk>> copy = new EnumMap<>(ArmorPiece.class);
        for (ArmorPiece p : ArmorPiece.values()) {
            copy.put(p, PieceEnchants.sorted(enchants == null ? List.of() : enchants.getOrDefault(p, List.of())));
        }
        enchants = Map.copyOf(copy);
        worn = worn == null ? Set.of() : Set.copyOf(worn);
    }

    /** The enchants of one piece (sorted by slot). */
    public List<ToolPerk> enchants(ArmorPiece piece) {
        return enchants.getOrDefault(piece, List.of());
    }

    /** Enchants on all four pieces together. */
    public int enchantCount() {
        return enchants.values().stream().mapToInt(List::size).sum();
    }
}
