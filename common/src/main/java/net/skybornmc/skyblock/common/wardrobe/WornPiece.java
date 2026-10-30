package net.skybornmc.skyblock.common.wardrobe;

import net.skybornmc.skyblock.common.tools.ToolPerk;

import java.util.List;
import java.util.UUID;

/**
 * A set piece the player wears right now with its armor enchants (E5 effects), all of them: the enchants module decides
 * which work ({@link ArmorSlots#working}) at {@code level}.
 *
 * @param piece    the armor slot
 * @param entryId  the wardrobe row
 * @param level    the set level for the enchant rules (see {@link SetEntryView#level()})
 * @param enchants the piece's enchants, sorted by slot
 */
public record WornPiece(ArmorPiece piece, UUID entryId, int level, List<ToolPerk> enchants) {

    public WornPiece {
        enchants = PieceEnchants.sorted(enchants);
    }
}
