package net.skybornmc.skyblock.depths.drops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REVIEW3 R3-18: key fragments from mine and forest tiers 11+. */
class FragmentDropsTest {

    private static final List<String> WINGS = List.of("mine", "forest");

    @Test
    void onlyHighMineAndForestTiers() {
        assertEquals(1.0 / 18_000, FragmentDrops.chance("mine", 11, WINGS, 11, 18_000, 1.0), 1e-15);
        assertEquals(1.0 / 18_000, FragmentDrops.chance("forest", 11, WINGS, 11, 18_000, 1.0), 1e-15);
        assertEquals(1.0 / 18_000, FragmentDrops.chance("MINE", 12, WINGS, 11, 18_000, 1.0), 1e-15);
        assertEquals(0, FragmentDrops.chance("mine", 10, WINGS, 11, 18_000, 1.0));
        assertEquals(0, FragmentDrops.chance("combat", 12, WINGS, 11, 18_000, 1.0));
        assertEquals(0, FragmentDrops.chance(null, 12, WINGS, 11, 18_000, 1.0));
    }

    @Test
    void rareFindBonusRaisesIt() {
        assertEquals(1.45 / 18_000, FragmentDrops.chance("mine", 11, WINGS, 11, 18_000, 1.45), 1e-15, "Treasure Hunter III");
        assertEquals(1.0, FragmentDrops.chance("mine", 11, WINGS, 11, 1, 5.0), "never above 100%");
        assertEquals(1.0 / 18_000, FragmentDrops.chance("mine", 11, WINGS, 11, 18_000, Double.NaN), 1e-15);
    }

    @Test
    void defaultIsAboutOneFragmentPer3Point3Hours() {
        // ECONOMY_BALANCE 3.3: a regular breaks 5,541 (tier 11) to 5,819 (tier 12) blocks per played hour.
        double t11 = FragmentDrops.hoursPerFragment(18_000, 5_541, 1.0);
        double t12 = FragmentDrops.hoursPerFragment(18_000, 5_819, 1.0);
        assertTrue(t11 > 3.0 && t11 < 3.4, "tier 11: " + t11);
        assertTrue(t12 > 3.0 && t12 < 3.4, "tier 12: " + t12);
        assertTrue(t11 * 3 > 9 && t11 * 3 < 10.5, "one key (3 fragments) takes about 10 h");
    }

    @Test
    void rollsMatchTheChance() {
        SplittableRandom rng = new SplittableRandom(7);
        int found = 0;
        for (int i = 0; i < 400_000; i++) {
            if (FragmentDrops.drops(1.0 / 1_000, rng)) {
                found++;
            }
        }
        assertTrue(found > 330 && found < 470, "about 400: " + found);
        assertFalse(FragmentDrops.drops(0, rng));
    }
}
