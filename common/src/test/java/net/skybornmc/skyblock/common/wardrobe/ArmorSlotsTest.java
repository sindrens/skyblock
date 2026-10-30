package net.skybornmc.skyblock.common.wardrobe;

import net.skybornmc.skyblock.common.tools.ToolPerk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** EN12 slots and the EN4 gate by set level on wardrobe pieces (E5), plus the enchant JSON/tag codecs. */
class ArmorSlotsTest {

    private final ArmorSlots slots = ArmorSlots.DEFAULTS;

    @Test
    void slotsPerPiece() {
        assertEquals(List.of(1, 2), slots.slots(ArmorPiece.HEAD));
        assertEquals(List.of(1, 2, 3), slots.slots(ArmorPiece.CHEST), "only the chestplate has the mythic slot");
        assertFalse(slots.exists(ArmorPiece.FEET, ArmorSlots.MYTHIC_SLOT));
        assertFalse(slots.exists(ArmorPiece.LEGS, 4));
    }

    @Test
    void slotsOpenWithTheSetLevel() {
        assertTrue(slots.unlocked(ArmorPiece.HEAD, 1, 0));
        assertFalse(slots.unlocked(ArmorPiece.HEAD, 2, 24));
        assertTrue(slots.unlocked(ArmorPiece.HEAD, 2, 25));
        assertFalse(slots.unlocked(ArmorPiece.CHEST, 3, 49));
        assertTrue(slots.unlocked(ArmorPiece.CHEST, 3, 50));
        assertEquals(1, slots.openSlots(ArmorPiece.LEGS, 24));
        assertEquals(2, slots.openSlots(ArmorPiece.LEGS, 25));
        assertEquals(25, slots.unlockLevel(2));
        assertEquals(50, slots.unlockLevel(3));
    }

    @Test
    void gateBySetLevel() {
        assertEquals(0, slots.gate(5, 0), "a level 0 set (0x bonus) holds no working enchant");
        assertEquals(1, slots.gate(5, 1));
        assertEquals(3, slots.gate(5, 25));
        assertEquals(5, slots.gate(5, 50));
        assertEquals(5, slots.gate(5, 99), "clamped at the max level");
        assertEquals(1, slots.requiredLevel(5, 1));
        assertEquals(41, slots.requiredLevel(5, 5));
        assertEquals(51, slots.requiredLevel(5, 6), "never");
    }

    @Test
    void dormantEnchants() {
        ToolPerk vitality3 = new ToolPerk(1, "vitality", 3);
        assertTrue(slots.working(ArmorPiece.HEAD, vitality3, 25, 5));
        assertFalse(slots.working(ArmorPiece.HEAD, vitality3, 0, 5), "after a merge to level 0 the enchant stays but is dormant");
        assertFalse(slots.working(ArmorPiece.HEAD, new ToolPerk(2, "fortitude", 1), 24, 4), "slot 2 not open yet");
        assertFalse(slots.working(ArmorPiece.HEAD, vitality3, 50, 0), "unknown enchant never works");
    }

    @Test
    void codecsRoundTrip() {
        List<ToolPerk> list = List.of(new ToolPerk(2, "fortitude", 2, 1, 900), new ToolPerk(1, "vitality", 3));
        List<ToolPerk> decoded = PieceEnchants.decode(PieceEnchants.encode(list));
        assertEquals(PieceEnchants.sorted(list), decoded);
        assertEquals(1, decoded.getFirst().slot(), "sorted by slot");
        assertEquals(900, decoded.get(1).invested());
        assertEquals(List.of(new ToolPerk(1, "vitality", 3), new ToolPerk(2, "fortitude", 2)),
                PieceEnchants.fromTag(PieceEnchants.tag(list)));
        assertEquals(List.of(), PieceEnchants.decode("not json"));
        assertEquals(List.of(new ToolPerk(1, "vitality", 3)), PieceEnchants.fromTag("1:vitality:3;broken;9:x"));
    }
}
