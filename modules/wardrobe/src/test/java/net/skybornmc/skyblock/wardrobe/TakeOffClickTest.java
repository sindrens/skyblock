package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** FIXES1: a plain click on a worn set piece in an armor slot takes it off through the wardrobe. */
class TakeOffClickTest {

    @Test
    void armorSlotsMapToPieces() {
        assertEquals(ArmorPiece.HEAD, LockListener.armorAt(ClickType.LEFT, 39));
        assertEquals(ArmorPiece.CHEST, LockListener.armorAt(ClickType.SHIFT_LEFT, 38));
        assertEquals(ArmorPiece.LEGS, LockListener.armorAt(ClickType.RIGHT, 37));
        assertEquals(ArmorPiece.FEET, LockListener.armorAt(ClickType.SHIFT_RIGHT, 36));
    }

    @Test
    void otherClicksAndSlotsStayLocked() {
        assertNull(LockListener.armorAt(ClickType.NUMBER_KEY, 39), "number keys would move the piece");
        assertNull(LockListener.armorAt(ClickType.DROP, 39));
        assertNull(LockListener.armorAt(ClickType.SWAP_OFFHAND, 39));
        assertNull(LockListener.armorAt(ClickType.LEFT, 40), "off-hand slot");
        assertNull(LockListener.armorAt(ClickType.LEFT, 5));
    }
}
