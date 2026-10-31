package net.skybornmc.skyblock.enchants;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static net.skybornmc.skyblock.enchants.EnchantRules.SlotState.COVERED;
import static net.skybornmc.skyblock.enchants.EnchantRules.SlotState.EMPTY;
import static net.skybornmc.skyblock.enchants.EnchantRules.SlotState.FILLED;
import static net.skybornmc.skyblock.enchants.EnchantRules.SlotState.LOCKED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** GUI-4b (UX_REVIEW §2): the slot screen is skipped when only one slot fits the mode. */
class SlotChoiceTest {

    @Test
    void applyNeedsExactlyOneEmptySlot() {
        assertEquals(OptionalInt.of(2), SlotMenu.onlyChoice(List.of(FILLED, EMPTY, LOCKED, LOCKED, LOCKED), SlotMenu.Mode.APPLY));
        assertEquals(OptionalInt.of(1), SlotMenu.onlyChoice(List.of(EMPTY, LOCKED, LOCKED, LOCKED, LOCKED), SlotMenu.Mode.APPLY),
                "a new tool: one open slot");
        assertEquals(OptionalInt.empty(), SlotMenu.onlyChoice(List.of(EMPTY, EMPTY, LOCKED, LOCKED, LOCKED), SlotMenu.Mode.APPLY),
                "two free slots: the player picks");
        assertEquals(OptionalInt.empty(), SlotMenu.onlyChoice(List.of(FILLED, COVERED, LOCKED, LOCKED, LOCKED), SlotMenu.Mode.APPLY),
                "no free slot: the slot screen explains why");
    }

    @Test
    void upgradeAndRemoveNeedExactlyOneFilledSlot() {
        for (SlotMenu.Mode mode : List.of(SlotMenu.Mode.UPGRADE, SlotMenu.Mode.REMOVE)) {
            assertEquals(OptionalInt.of(1), SlotMenu.onlyChoice(List.of(FILLED, COVERED, EMPTY, LOCKED, LOCKED), mode),
                    "a 2-slot enchant counts once");
            assertEquals(OptionalInt.empty(), SlotMenu.onlyChoice(List.of(FILLED, FILLED, EMPTY, LOCKED, LOCKED), mode));
            assertEquals(OptionalInt.empty(), SlotMenu.onlyChoice(List.of(EMPTY, EMPTY, LOCKED, LOCKED, LOCKED), mode));
        }
    }
}
