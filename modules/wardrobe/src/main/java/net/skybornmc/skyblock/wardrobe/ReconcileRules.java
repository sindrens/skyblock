package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which worn-piece items are real (WARDROBE §4.2, §4.3, WD20). Pure and unit-tested. A piece item is <b>valid</b> only in
 * its owner's matching armor slot, for the active profile, with the nonce the database row holds. Anything else is a
 * copy or a leftover and is deleted; a piece found outside the armor slots is a possible exploit (staff alert).
 */
final class ReconcileRules {

    enum Verdict {
        KEEP,
        /** Stale or leftover (old nonce, other profile, entry gone): delete quietly. */
        DELETE,
        /** Outside the armor slots or in the wrong slot: delete and alert staff. */
        DELETE_ALERT
    }

    /** What sits in an armor slot, for the fill step. */
    enum Slot { EMPTY, VALID, OTHER }

    /** What to do for a slot whose row says a piece is worn. */
    enum Fill {
        /** The valid piece is there. */
        NONE,
        /** The slot is empty: put the piece back (it vanished: crash, death drop, other plugin). */
        RENDER,
        /** Vanilla armor sits there now: the row is dropped (the set counts as taken off). */
        DROP_ROW
    }

    private ReconcileRules() {
    }

    /**
     * @param tag       the item's wardrobe data
     * @param armorSlot the armor slot it sits in, or null anywhere else (inventory, cursor, ender chest, containers)
     * @param profile   the holder's active profile key
     * @param rows      the profile's worn rows
     */
    static Verdict decide(WardrobeItems.PieceTag tag, ArmorPiece armorSlot, UUID profile, Map<ArmorPiece, WardrobeStore.Equipped> rows) {
        if (armorSlot == null) {
            return Verdict.DELETE_ALERT;
        }
        if (tag == null || tag.entry() == null || tag.nonce() == null || tag.piece() == null) {
            return Verdict.DELETE;
        }
        if (tag.piece() != armorSlot) {
            return Verdict.DELETE_ALERT;
        }
        if (tag.profile() == null || !tag.profile().equals(profile)) {
            return Verdict.DELETE;
        }
        WardrobeStore.Equipped row = rows.get(armorSlot);
        if (row == null || !row.entryId().equals(tag.entry()) || !row.nonce().equals(tag.nonce())) {
            return Verdict.DELETE;
        }
        return Verdict.KEEP;
    }

    /** For every worn row: render the missing piece, or drop the row when vanilla armor took the slot. */
    static Map<ArmorPiece, Fill> fill(Map<ArmorPiece, WardrobeStore.Equipped> rows, Map<ArmorPiece, Slot> slots) {
        Map<ArmorPiece, Fill> out = new EnumMap<>(ArmorPiece.class);
        for (ArmorPiece p : rows.keySet()) {
            Slot s = slots.getOrDefault(p, Slot.EMPTY);
            out.put(p, switch (s) {
                case VALID -> Fill.NONE;
                case EMPTY -> Fill.RENDER;
                case OTHER -> Fill.DROP_ROW;
            });
        }
        return out;
    }
}
