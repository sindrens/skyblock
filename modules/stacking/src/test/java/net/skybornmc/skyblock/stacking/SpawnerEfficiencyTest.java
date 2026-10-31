package net.skybornmc.skyblock.stacking;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner F2 (card D2): diminishing spawner output per island and tier, the in-memory counts and the shop hook value. */
class SpawnerEfficiencyTest {

    private static final SpawnerEfficiency CURVE = SpawnerEfficiency.defaults();

    @Test
    void configDefaultIsTheOwnersCurve() {
        assertEquals(SpawnerEfficiency.DEFAULT, new StackingConfig().spawnerEfficiency);
        List<String> problems = new ArrayList<>();
        SpawnerEfficiency parsed = SpawnerEfficiency.parse(new StackingConfig().spawnerEfficiency, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(5, parsed.bands().size());
    }

    @Test
    void bandMathMatchesTheCard() {
        assertEquals(0, CURVE.effective(0), 1e-9);
        assertEquals(1, CURVE.effective(1), 1e-9);
        assertEquals(16, CURVE.effective(16), 1e-9);
        assertEquals(16.6, CURVE.effective(17), 1e-9);
        assertEquals(16 + 16 * 0.6, CURVE.effective(32), 1e-9);
        // Card example: 38 tier-2 spawners = 16 + 16 x 0.6 + 6 x 0.35 = 27.7
        assertEquals(27.7, CURVE.effective(38), 1e-9);
        assertEquals(16 + 9.6 + 32 * 0.35, CURVE.effective(64), 1e-9);
        assertEquals(16 + 9.6 + 11.2 + 64 * 0.2, CURVE.effective(128), 1e-9);
        assertEquals(16 + 9.6 + 11.2 + 12.8 + 72 * 0.1, CURVE.effective(200), 1e-9);
        // always worth having: every extra spawner still adds output
        for (int n = 1; n < 600; n++) {
            assertTrue(CURVE.effective(n + 1) > CURVE.effective(n), "n=" + n);
        }
    }

    @Test
    void marginalPercentPerBand() {
        assertEquals(100, CURVE.percentOf(1));
        assertEquals(100, CURVE.percentOf(16));
        assertEquals(60, CURVE.percentOf(17));
        assertEquals(60, CURVE.percentOf(32));
        assertEquals(35, CURVE.percentOf(33));
        assertEquals(20, CURVE.percentOf(65));
        assertEquals(20, CURVE.percentOf(128));
        assertEquals(10, CURVE.percentOf(129));
        assertEquals(10, CURVE.percentOf(1_000_000));
        assertEquals(35, CURVE.nextPercent(38), "card: 38 owned, next one works at 35%");
        assertEquals(100, CURVE.nextPercent(0));
        assertEquals(60, CURVE.nextPercent(16));
    }

    @Test
    void factorSpreadsTheIslandOutputOverItsBlocks() {
        assertEquals(1.0, CURVE.factor(0), 1e-9);
        assertEquals(1.0, CURVE.factor(16), 1e-9);
        assertEquals(27.7 / 38, CURVE.factor(38), 1e-9);
        // two blocks (30 + 8) of one tier make exactly E(38) together
        double f = CURVE.factor(38);
        assertEquals(CURVE.effective(38), 30 * f + 8 * f, 1e-9);
        // the existing output math (StackMath.spawnerOutput) gets the factor via rateMultiplier
        assertEquals(Math.floor(38 * f), StackMath.spawnerOutput(1.0, 38, f, 0).mobs());
    }

    @Test
    void brokenOrEmptyConfig() {
        List<String> problems = new ArrayList<>();
        assertEquals(1.0, SpawnerEfficiency.parse(List.of(), problems::add).factor(500), 1e-9, "empty list = curve off");
        assertEquals(List.of(), problems);
        for (List<String> bad : List.of(List.of("1-16:100", "18-32:60", "33+:10"), List.of("1-16:100", "17-32:60"),
                List.of("1-16:100", "17+:60", "33+:10"), List.of("2-16:100", "17+:10"), List.of("1-16:150", "17+:10"),
                List.of("abc"), List.of("1-16", "17+:10"))) {
            problems.clear();
            SpawnerEfficiency curve = SpawnerEfficiency.parse(bad, problems::add);
            assertEquals(1, problems.size(), bad.toString());
            assertEquals(27.7, curve.effective(38), 1e-9, "falls back to the owner defaults: " + bad);
        }
        problems.clear();
        SpawnerEfficiency custom = SpawnerEfficiency.parse(List.of("1-8:100", "9+:50"), problems::add);
        assertEquals(List.of(), problems);
        assertEquals(8 + 2 * 0.5, custom.effective(10), 1e-9);
    }

    // ------------------------------------------------------------------ counts (place, stack, break, island removal)

    private static SpawnerManager.Spawner spawner(UUID island, String type, int amount) {
        return new SpawnerManager.Spawner(new SpawnerManager.Pos("islands", amount, 64, 0), type, amount, island, null, 0);
    }

    @Test
    void countsFollowPlaceStackBreakAndRemoval() {
        SpawnerCounts counts = new SpawnerCounts();
        UUID island = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        SpawnerManager.Spawner a = spawner(island, "zombie", 1);
        SpawnerManager.count(counts, a, 2, 1); // placed
        assertEquals(1, counts.get(island, 2));
        SpawnerManager.count(counts, a, 2, 29); // stacked (merge into the block)
        SpawnerManager.Spawner b = spawner(island, "skeleton", 1);
        SpawnerManager.count(counts, b, 2, 1); // second block, same tier
        SpawnerManager.count(counts, b, 2, 7);
        assertEquals(38, counts.get(island, 2), "counted across all blocks of the tier");
        assertEquals(0, counts.get(island, 1), "per tier");
        assertEquals(0, counts.get(other, 2), "per island");
        SpawnerManager.count(counts, a, 2, -1); // break one
        assertEquals(37, counts.get(island, 2));
        SpawnerManager.Spawner pig = spawner(island, "pig", 4);
        SpawnerManager.count(counts, pig, 1, 4);
        SpawnerManager.count(counts, spawner(other, "pig", 3), 1, 3);
        assertEquals(41, counts.total(island));
        // a spawner already removed (cleared with its island while a change was in flight) never counts again
        pig.removed = true;
        SpawnerManager.count(counts, pig, 1, 5);
        assertEquals(4, counts.get(island, 1));
        // island removal clears the island only
        counts.clearIsland(island);
        assertEquals(0, counts.get(island, 2));
        assertEquals(0, counts.total(island));
        assertEquals(3, counts.get(other, 1));
        // never below zero; empty islands are dropped
        counts.add(other, 1, -10);
        assertEquals(0, counts.get(other, 1));
        assertEquals(0, counts.islands());
        // spawners without an island (no island service) or an unknown tier are not counted
        SpawnerManager.count(counts, spawner(null, "pig", 1), 1, 1);
        SpawnerManager.count(counts, spawner(island, "gone", 1), 0, 1);
        assertEquals(0, counts.islands());
    }

    @Test
    void rebuildAfterReloadUsesTheNewTiers() {
        SpawnerCounts counts = new SpawnerCounts();
        UUID island = UUID.randomUUID();
        SpawnerManager.Spawner zombie = spawner(island, "zombie", 20);
        SpawnerManager.Spawner pig = spawner(island, "pig", 5);
        SpawnerManager.Spawner removed = spawner(island, "pig", 50);
        removed.removed = true;
        counts.add(island, 3, 999); // stale
        SpawnerManager.rebuild(counts, List.of(zombie, pig, removed), type -> type.equals("zombie") ? 2 : 1);
        assertEquals(20, counts.get(island, 2));
        assertEquals(5, counts.get(island, 1));
        assertEquals(0, counts.get(island, 3));
        assertFalse(counts.islands() != 1);
    }

    @Test
    void shopHookValue() {
        assertEquals(100, StackingModule.nextPercent(CURVE, 0, 1));
        assertEquals(100, StackingModule.nextPercent(CURVE, 15, 1));
        assertEquals(60, StackingModule.nextPercent(CURVE, 16, 2));
        assertEquals(35, StackingModule.nextPercent(CURVE, 38, 2));
        assertEquals(10, StackingModule.nextPercent(CURVE, 500, 2));
        assertEquals(100, StackingModule.nextPercent(CURVE, 500, 0), "unknown tier: no curve");
        assertEquals(100, StackingModule.nextPercent(SpawnerEfficiency.OFF, 500, 2));
    }

    @Test
    void menuPercentText() {
        assertEquals("35", SpawnerMenu.percent(35));
        assertEquals("12.5", SpawnerMenu.percent(12.5));
        assertEquals("27.7", SpawnerManager.number(CURVE.effective(38)));
    }
}
