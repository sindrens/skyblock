package net.skybornmc.skyblock.crates;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateAmbientTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void particlesFromTheMinimumRarityUp() {
        assertFalse(CrateAmbient.atLeast("common", "epic"));
        assertFalse(CrateAmbient.atLeast("rare", "epic"));
        assertTrue(CrateAmbient.atLeast("epic", "epic"));
        assertTrue(CrateAmbient.atLeast("legendary", "epic"));
        assertTrue(CrateAmbient.atLeast("MYTHIC", " Epic "));
        assertTrue(CrateAmbient.atLeast("special", "epic"));
        assertFalse(CrateAmbient.atLeast("bogus", "epic"));
        assertFalse(CrateAmbient.atLeast("epic", "bogus"));
        assertFalse(CrateAmbient.atLeast(null, "epic"));
    }

    @Test
    void chimesOncePerApproach() {
        CrateAmbient.Approach a = new CrateAmbient.Approach(6, 8, 30_000);
        assertFalse(a.update(A, "ember", 100 * 100, 0));
        assertTrue(a.update(A, "ember", 5 * 5, 1_000));
        assertFalse(a.update(A, "ember", 4 * 4, 2_000)); // still inside
        assertFalse(a.update(A, "ember", 7 * 7, 3_000)); // between near and far: still inside
        assertFalse(a.update(A, "ember", 5 * 5, 4_000)); // never left
    }

    @Test
    void needsToLeaveAndWaitTheCooldown() {
        CrateAmbient.Approach a = new CrateAmbient.Approach(6, 8, 30_000);
        assertTrue(a.update(A, "ember", 4, 0));
        assertFalse(a.update(A, "ember", 9 * 9, 5_000)); // left
        assertFalse(a.update(A, "ember", 4, 10_000)); // back too soon
        assertFalse(a.update(A, "ember", 9 * 9, 12_000));
        assertTrue(a.update(A, "ember", 4, 31_000)); // cooldown over
    }

    @Test
    void playersAndCratesAreIndependent() {
        CrateAmbient.Approach a = new CrateAmbient.Approach(6, 8, 30_000);
        assertTrue(a.update(A, "ember", 4, 0));
        assertTrue(a.update(B, "ember", 4, 0));
        assertTrue(a.update(A, "cinder", 4, 0));
        a.forget(A);
        assertTrue(a.update(A, "ember", 4, 1));
    }

    @Test
    void zeroCooldownChimesOnEveryNewApproach() {
        CrateAmbient.Approach a = new CrateAmbient.Approach(6, 8, 0);
        assertTrue(a.update(A, "ember", 4, 0));
        assertFalse(a.update(A, "ember", 9 * 9, 1));
        assertTrue(a.update(A, "ember", 4, 2));
    }
}
