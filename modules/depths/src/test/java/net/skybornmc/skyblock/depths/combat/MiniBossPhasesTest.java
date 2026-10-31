package net.skybornmc.skyblock.depths.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniBossPhasesTest {

    @Test
    void startsNormalAndEnragesOnceAtThreshold() {
        MiniBossPhases p = new MiniBossPhases(0.3);
        assertEquals(MiniBossPhases.Phase.NORMAL, p.phase());
        assertNull(p.update(200, 300), "67% left: still normal");
        assertNull(p.update(91, 300), "just above 30%");
        assertEquals(MiniBossPhases.Phase.ENRAGED, p.update(90, 300), "exactly 30% enrages (same rule as the ability)");
        assertTrue(p.enraged());
        assertNull(p.update(50, 300), "the change is reported once");
    }

    @Test
    void neverGoesBackWhenHealedOrRescaled() {
        MiniBossPhases p = new MiniBossPhases(0.3);
        p.update(60, 300);
        assertNull(p.update(290, 300), "leash healing does not end the phase");
        assertNull(p.update(240, 750), "health scaling does not end the phase");
        assertTrue(p.enraged());
    }

    @Test
    void scalingKeepsTheShareSoNoFalseTrigger() {
        MiniBossPhases p = new MiniBossPhases(0.3);
        // 40% left, then two more fighters raise max 300 -> 450 with the share kept (180/450 = 40%)
        assertNull(p.update(120, 300));
        assertNull(p.update(BossFight.rescaled(120, 300, 450), 450));
        assertFalse(p.enraged());
    }

    @Test
    void killingBlowAndBrokenValuesChangeNothing() {
        MiniBossPhases p = new MiniBossPhases(0.3);
        assertNull(p.update(0, 300), "dead: the fight is over");
        assertNull(p.update(-5, 300));
        assertNull(p.update(10, 0));
        assertNull(p.update(Double.NaN, 300));
        assertFalse(p.enraged());
    }

    @Test
    void withoutEnrageThereIsNoPhase() {
        for (MiniBossPhases p : new MiniBossPhases[] {MiniBossPhases.none(), new MiniBossPhases(Double.NaN),
                new MiniBossPhases(0), new MiniBossPhases(1.5)}) {
            assertFalse(p.hasEnrage());
            assertNull(p.update(1, 300));
            assertFalse(p.enraged());
        }
    }
}
