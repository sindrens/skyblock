package net.skybornmc.skyblock.economy.sell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QUICK1 (QoL #2a): bulk selling skips the hotbar while "Sell all skips hotbar" is on. */
class SellProtectionTest {

    @Test
    void hotbarSlotsAreSkippedWhenTheSettingIsOn() {
        for (int slot = 0; slot < 9; slot++) {
            assertTrue(SellFeature.bulkSkips(slot, true), "hotbar slot " + slot);
            assertFalse(SellFeature.bulkSkips(slot, false), "setting off sells slot " + slot);
        }
    }

    @Test
    void mainInventoryIsAlwaysSold() {
        for (int slot = 9; slot < SellFeature.STORAGE_SLOTS; slot++) {
            assertFalse(SellFeature.bulkSkips(slot, true), "slot " + slot);
        }
        assertFalse(SellFeature.bulkSkips(-1, true), "no slot is not a hotbar slot");
    }
}
