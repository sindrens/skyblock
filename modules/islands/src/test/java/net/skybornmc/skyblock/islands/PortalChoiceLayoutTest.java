package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.menu.PickListLayout;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-4c: the island portal shows every invite (it used to hide all but 3). */
class PortalChoiceLayoutTest {

    @Test
    void everyInviteGetsItsOwnSlot() {
        for (int invites = 0; invites < PortalChoiceMenu.MAX_CHOICES; invites++) {
            int shown = 1 + invites;
            int rows = PickListLayout.rows(shown);
            Set<Integer> slots = new HashSet<>();
            for (int i = 0; i < shown; i++) {
                int slot = PickListLayout.slot(i, shown);
                assertTrue(slot < (rows - 1) * 9, "above the bottom bar");
                assertTrue(slots.add(slot), "one slot per choice");
            }
        }
        assertEquals(3, PickListLayout.rows(1 + 6), "up to 6 invites still fit the small 3-row screen");
        assertEquals(4, PickListLayout.rows(1 + 7));
    }
}
