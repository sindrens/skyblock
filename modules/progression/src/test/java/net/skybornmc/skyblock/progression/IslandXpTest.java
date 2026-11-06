package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island level v2 (owner 7 Nov 2026, plans/TOOLS_V5.md 3): the island level is the island XP. */
class IslandXpTest {

    private static final LevelConfig C = new LevelConfig();

    @Test
    void eachLevelNeedsALittleMore() {
        assertTrue(C.xpMode(), "island XP is the default");
        assertEquals(0, IslandXp.level(0, 1500, 1.5));
        assertEquals(0, IslandXp.level(1499, 1500, 1.5));
        assertEquals(1, IslandXp.level(1500, 1500, 1.5));
        assertEquals(10, IslandXp.level(IslandXp.totalFor(10, 1500, 1.5), 1500, 1.5));
        assertEquals(9, IslandXp.level(IslandXp.totalFor(10, 1500, 1.5) - 1, 1500, 1.5));
        assertEquals(47_434, Math.round(IslandXp.totalFor(10, 1500, 1.5)));
        double last = 0;
        for (int l = 1; l < 200; l++) {
            double step = IslandXp.totalFor(l + 1, 1500, 1.5) - IslandXp.totalFor(l, 1500, 1.5);
            assertTrue(step > last, "level " + l);
            last = step;
            assertEquals(l, IslandXp.level(IslandXp.totalFor(l, 1500, 1.5), 1500, 1.5));
        }
        assertEquals(0.5, IslandXp.progress((IslandXp.totalFor(3, 1500, 1.5) + IslandXp.totalFor(4, 1500, 1.5)) / 2, 1500, 1.5), 1e-9);
        // 13 crops a second at 1 XP: level 10 in about an hour alone
        double hours = IslandXp.totalFor(10, C.xpBase, C.xpExponent) / (13 * 3600.0);
        assertTrue(hours > 0.8 && hours < 1.3, "level 10 in ~1 h: " + hours);
    }

    @Test
    void rewardsGrowWithTheLevel() {
        assertEquals(200_000, IslandXp.money(10, C.rewardMoneyPerLevel));
        assertEquals(0, IslandXp.crystals(7, C.rewardCrystalsPerLevel));
        assertEquals(100, IslandXp.crystals(10, C.rewardCrystalsPerLevel));
        assertTrue(IslandXp.key(5));
        assertFalse(IslandXp.key(6));
        assertEquals(4, C.version());
    }
}
