package net.skybornmc.skyblock.wardrobe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** GUI-BUGS: the staff entry screen lists each level once, in order. */
class StaffEntryLevelsTest {

    @Test
    void maxFortyHasNoDuplicate() {
        assertEquals(List.of(0, 1, 10, 25, 39, 40), StaffEntryMenu.levelChoices(40));
    }

    @Test
    void higherMaxKeepsSevenButtons() {
        assertEquals(List.of(0, 1, 10, 25, 40, 49, 50), StaffEntryMenu.levelChoices(50));
    }

    @Test
    void smallMaxCollapses() {
        assertEquals(List.of(0, 1, 4, 5), StaffEntryMenu.levelChoices(5));
        assertEquals(List.of(0), StaffEntryMenu.levelChoices(0));
    }
}
