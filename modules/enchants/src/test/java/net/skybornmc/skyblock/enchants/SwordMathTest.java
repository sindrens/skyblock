package net.skybornmc.skyblock.enchants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Depths sword enchant numbers (ENCHANTS §6.5, §8.3). */
class SwordMathTest {

    @Test
    void mobDamageFamilyWithGiantSlayerIsCappedOnce() {
        assertEquals(1.25, SwordMath.mobMultiplier(25, 32, false, 200), 1e-9, "Giant Slayer only on bosses");
        assertEquals(1.57, SwordMath.mobMultiplier(25, 32, true, 200), 1e-9);
        assertEquals(3.0, SwordMath.mobMultiplier(190, 32, true, 200), 1e-9, "cap +200%");
        assertEquals(1.0, SwordMath.mobMultiplier(0, 0, true, 200), 1e-9);
    }

    @Test
    void pvpUsesTheNormalizedValues() {
        assertEquals(1.10, SwordMath.pvpMultiplier(25, 40), 1e-9, "Phoenix Edge V: +2% per level in PvP");
        assertEquals(1.0, SwordMath.pvpMultiplier(25, 0), 1e-9);
        assertEquals(2.5, SwordMath.scaled(5, 50), 1e-9, "Bloodthirst at half strength");
    }

    @Test
    void healAndShares() {
        assertEquals(0.5, SwordMath.heal(10, 5), 1e-9);
        assertEquals(0, SwordMath.heal(0, 5), 1e-9);
        assertEquals(4.5, SwordMath.share(10, 45), 1e-9);
        assertEquals(30, SwordMath.share(10, 300), 1e-9);
    }

    @Test
    void executionerNeverKillsBosses() {
        assertTrue(SwordMath.executes(1.5, 20, 10, false));
        assertFalse(SwordMath.executes(2.5, 20, 10, false));
        assertFalse(SwordMath.executes(1.5, 20, 10, true));
        assertFalse(SwordMath.executes(0, 20, 10, false), "already dead");
    }
}
