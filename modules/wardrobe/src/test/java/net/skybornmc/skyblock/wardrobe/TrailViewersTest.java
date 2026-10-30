package net.skybornmc.skyblock.wardrobe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F1: who sees a set trail ("Particles" toggle). */
class TrailViewersTest {

    @Test
    void ownerAlwaysSeesOwnTrail() {
        assertTrue(Leveling.showsTrail(true, false, false, 1e9, 32));
    }

    @Test
    void othersNeedTheToggleSightAndRange() {
        assertTrue(Leveling.showsTrail(false, true, true, 32 * 32, 32));
        assertFalse(Leveling.showsTrail(false, false, true, 1, 32), "particles off");
        assertFalse(Leveling.showsTrail(false, true, false, 1, 32), "owner vanished for this viewer");
        assertFalse(Leveling.showsTrail(false, true, true, 32 * 32 + 1, 32), "too far");
    }
}
