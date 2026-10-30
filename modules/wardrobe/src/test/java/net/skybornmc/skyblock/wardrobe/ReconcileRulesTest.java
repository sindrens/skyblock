package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which piece items are real (WARDROBE §4.2/§4.3, WD20) and how missing pieces are filled. */
class ReconcileRulesTest {

    private final UUID profile = UUID.randomUUID();
    private final UUID entry = UUID.randomUUID();
    private final UUID nonce = UUID.randomUUID();
    private final Map<ArmorPiece, WardrobeStore.Equipped> rows = new EnumMap<>(Map.of(ArmorPiece.CHEST,
            new WardrobeStore.Equipped(ArmorPiece.CHEST, entry, nonce)));

    private WardrobeItems.PieceTag tag(ArmorPiece piece, UUID profile, UUID nonce) {
        return new WardrobeItems.PieceTag(entry, "delver", piece, profile, nonce);
    }

    @Test
    void validPieceIsKept() {
        assertEquals(ReconcileRules.Verdict.KEEP, ReconcileRules.decide(tag(ArmorPiece.CHEST, profile, nonce), ArmorPiece.CHEST, profile, rows));
    }

    @Test
    void outsideArmorOrWrongSlotIsAnExploit() {
        assertEquals(ReconcileRules.Verdict.DELETE_ALERT, ReconcileRules.decide(tag(ArmorPiece.CHEST, profile, nonce), null, profile, rows));
        assertEquals(ReconcileRules.Verdict.DELETE_ALERT, ReconcileRules.decide(tag(ArmorPiece.CHEST, profile, nonce), ArmorPiece.HEAD, profile, rows));
    }

    @Test
    void staleNonceOtherProfileOrNoRowIsDeletedQuietly() {
        assertEquals(ReconcileRules.Verdict.DELETE, ReconcileRules.decide(tag(ArmorPiece.CHEST, profile, UUID.randomUUID()), ArmorPiece.CHEST, profile, rows));
        assertEquals(ReconcileRules.Verdict.DELETE, ReconcileRules.decide(tag(ArmorPiece.CHEST, UUID.randomUUID(), nonce), ArmorPiece.CHEST, profile, rows),
                "a piece from another (own) profile never counts (P4)");
        assertEquals(ReconcileRules.Verdict.DELETE, ReconcileRules.decide(tag(ArmorPiece.CHEST, profile, nonce), ArmorPiece.CHEST, profile, Map.of()));
        assertEquals(ReconcileRules.Verdict.DELETE, ReconcileRules.decide(new WardrobeItems.PieceTag(null, null, null, null, null), ArmorPiece.CHEST, profile, rows));
        WardrobeItems.PieceTag otherEntry = new WardrobeItems.PieceTag(UUID.randomUUID(), "delver", ArmorPiece.CHEST, profile, nonce);
        assertEquals(ReconcileRules.Verdict.DELETE, ReconcileRules.decide(otherEntry, ArmorPiece.CHEST, profile, rows));
    }

    @Test
    void fill() {
        Map<ArmorPiece, WardrobeStore.Equipped> all = new EnumMap<>(ArmorPiece.class);
        for (ArmorPiece p : ArmorPiece.values()) {
            all.put(p, new WardrobeStore.Equipped(p, entry, UUID.randomUUID()));
        }
        Map<ArmorPiece, ReconcileRules.Fill> f = ReconcileRules.fill(all, Map.of(ArmorPiece.HEAD, ReconcileRules.Slot.VALID,
                ArmorPiece.CHEST, ReconcileRules.Slot.EMPTY, ArmorPiece.LEGS, ReconcileRules.Slot.OTHER));
        assertEquals(ReconcileRules.Fill.NONE, f.get(ArmorPiece.HEAD));
        assertEquals(ReconcileRules.Fill.RENDER, f.get(ArmorPiece.CHEST));
        assertEquals(ReconcileRules.Fill.DROP_ROW, f.get(ArmorPiece.LEGS), "vanilla armor took the slot");
        assertEquals(ReconcileRules.Fill.RENDER, f.get(ArmorPiece.FEET), "unknown = empty: re-create");
    }
}
