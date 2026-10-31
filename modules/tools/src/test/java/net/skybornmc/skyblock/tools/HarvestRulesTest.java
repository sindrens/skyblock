package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.tools.ToolKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Harvester hoe rules (PHASE-7 M7.4, owner V6 / PD10 / PD11 / PD13). */
class HarvestRulesTest {

    private static final List<Integer> LEVELS = List.of(1, 15, 30);
    private static final HarvestRules.Crop WHEAT = new HarvestRules.Crop("wheat", HarvestRules.Shape.AGE, "wheat_seeds");
    private static final HarvestRules.Crop CANE = new HarvestRules.Crop("sugar_cane", HarvestRules.Shape.COLUMN, "");
    private static final HarvestRules.Crop MELON = new HarvestRules.Crop("melon", HarvestRules.Shape.FRUIT, "attached_melon_stem");

    @Test
    void areaGrowsWithTheHoeLevel() {
        assertEquals(3, HarvestRules.size(1, LEVELS, 0, 9), "3x3 from Lv 1");
        assertEquals(3, HarvestRules.size(14, LEVELS, 0, 9));
        assertEquals(5, HarvestRules.size(15, LEVELS, 0, 9), "5x5 at Lv 15");
        assertEquals(5, HarvestRules.size(29, LEVELS, 0, 9));
        assertEquals(7, HarvestRules.size(30, LEVELS, 0, 9), "7x7 at Lv 30");
        assertEquals(7, HarvestRules.size(50, LEVELS, 0, 9));
    }

    @Test
    void wideReapRingsAreCappedAtTheMaxSize() {
        assertEquals(9, HarvestRules.size(30, LEVELS, 1, 9), "Wide Reap I on 7x7 = 9x9");
        assertEquals(9, HarvestRules.size(30, LEVELS, 2, 9), "never above 9x9");
        assertEquals(7, HarvestRules.size(15, LEVELS, 1, 9));
        assertEquals(7, HarvestRules.size(30, LEVELS, 3, 8), "an even max rounds down to odd");
        assertEquals(3, HarvestRules.size(1, LEVELS, -4, 9), "negative rings count as 0");
    }

    @Test
    void offsetsCoverTheSquareOnceWithTheCentreFirst() {
        for (int size : new int[]{1, 3, 5, 7, 9}) {
            List<int[]> offsets = HarvestRules.offsets(size);
            assertEquals(size * size, offsets.size());
            assertEquals(0, offsets.getFirst()[0]);
            assertEquals(0, offsets.getFirst()[1]);
            Set<String> seen = new HashSet<>();
            int r = (size - 1) / 2;
            for (int[] o : offsets) {
                assertTrue(seen.add(o[0] + "," + o[1]), "each block once (no double harvest, no double XP)");
                assertTrue(Math.abs(o[0]) <= r && Math.abs(o[1]) <= r);
            }
        }
    }

    @Test
    void onlyRipeCropsAreHarvested() {
        assertEquals(HarvestRules.Action.SKIP, HarvestRules.decide(WHEAT, HarvestRules.Cell.age(6, 7), 0, 50), "unripe wheat stays");
        assertEquals(HarvestRules.Action.SKIP, HarvestRules.decide(WHEAT, HarvestRules.Cell.age(0, 7), 0, 50));
        assertEquals(HarvestRules.Action.HARVEST_REPLANT, HarvestRules.decide(WHEAT, HarvestRules.Cell.age(7, 7), 0, 0));
        assertEquals(HarvestRules.Action.SKIP, HarvestRules.decide(CANE, HarvestRules.Cell.column(1), 0, 50), "a lone cane block stays");
        assertEquals(HarvestRules.Action.HARVEST, HarvestRules.decide(CANE, HarvestRules.Cell.column(3), 0, 0));
        assertEquals(HarvestRules.Action.SKIP, HarvestRules.decide(MELON, HarvestRules.Cell.fruit(false), 0, 50), "placed melons stay");
        assertEquals(HarvestRules.Action.HARVEST, HarvestRules.decide(MELON, HarvestRules.Cell.fruit(true), 0, 0));
        assertEquals(HarvestRules.Action.SKIP, HarvestRules.decide(null, HarvestRules.Cell.age(7, 7), 0, 0));
    }

    @Test
    void replantRespectsTheFarmingGate() {
        HarvestRules.Crop carrots = new HarvestRules.Crop("carrots", HarvestRules.Shape.AGE, "carrot");
        assertEquals(HarvestRules.Action.SKIP, HarvestRules.decide(carrots, HarvestRules.Cell.age(7, 7), 5, 4),
                "Farming 4 can't plant carrots (5), so the area leaves them alone");
        assertEquals(HarvestRules.Action.HARVEST_REPLANT, HarvestRules.decide(carrots, HarvestRules.Cell.age(7, 7), 5, 5));
    }

    @Test
    void replantedCropsAreNeverFullyGrown() {
        assertEquals(0, HarvestRules.replantAge(0, 7));
        assertEquals(3, HarvestRules.replantAge(3, 7), "Green Thumb stages");
        assertEquals(6, HarvestRules.replantAge(99, 7), "never the last stage");
        assertEquals(0, HarvestRules.replantAge(-2, 7));
        assertEquals(0, HarvestRules.replantAge(5, 0));
    }

    @Test
    void hoeToolXpComesOnlyFromEligibleHarvestsWithAHoe() {
        assertEquals(2.5 * 9, HarvestRules.toolXp(true, ToolKind.HOE, 2.5, 9, 1.0), 1e-9, "tier XP x crops");
        assertEquals(0.5 * 4, HarvestRules.toolXp(true, ToolKind.HOE, 1, 4, 0.5), 1e-9, "crop factor");
        assertEquals(0, HarvestRules.toolXp(false, ToolKind.HOE, 2.5, 9, 1.0), "bone meal / no hoe / not a member: no XP");
        assertEquals(0, HarvestRules.toolXp(true, ToolKind.PICKAXE, 2.5, 9, 1.0), "only hoes level on crops");
        assertEquals(0, HarvestRules.toolXp(true, ToolKind.HOE, 2.5, 0, 1.0));
        assertEquals(0, HarvestRules.toolXp(true, ToolKind.HOE, Double.NaN, 3, 1.0));
        // An area swing of 9 ripe wheat fires 9 harvest events of 1 crop each: the same XP as 9 single breaks, never more.
        double area = 0;
        for (int i = 0; i < 9; i++) {
            area += HarvestRules.toolXp(true, ToolKind.HOE, 1, 1, 1.0);
        }
        assertEquals(HarvestRules.toolXp(true, ToolKind.HOE, 1, 9, 1.0), area, 1e-9);
    }

    @Test
    void extraCropDrops() {
        assertEquals(1, HarvestRules.boostedAmount(1, 1.0, 3, 64, 0.0), "tier 1: no bonus");
        assertEquals(2, HarvestRules.boostedAmount(1, 1.25, 3, 64, 0.1), "25% chance hit");
        assertEquals(1, HarvestRules.boostedAmount(1, 1.25, 3, 64, 0.5), "25% chance miss");
        assertEquals(5, HarvestRules.boostedAmount(4, 1.25, 3, 64, 0.99), "4 x 1.25 = 5 sure");
        assertEquals(3, HarvestRules.boostedAmount(1, 9.0, 3, 64, 0.0), "max 3 drops per action");
        assertEquals(64, HarvestRules.boostedAmount(40, 2.0, 3, 64, 0.0), "never above the stack size");
        assertEquals(2, HarvestRules.boostedAmount(2, 0.5, 3, 64, 0.0), "never fewer than dropped");
        assertEquals(0, HarvestRules.boostedAmount(0, 2.0, 3, 64, 0.0));
    }

    @Test
    void cropListsParse() {
        List<String> problems = new ArrayList<>();
        Map<String, HarvestRules.Crop> crops = HarvestRules.crops(List.of("wheat=wheat_seeds", "minecraft:carrots = carrot", "broken"),
                List.of("sugar_cane", "Bad!"), List.of("melon=attached_melon_stem", "wheat=x"), problems::add);
        assertEquals(HarvestRules.Shape.AGE, crops.get("wheat").shape(), "the first listing wins");
        assertEquals("carrot", crops.get("carrots").extra());
        assertEquals(HarvestRules.Shape.COLUMN, crops.get("sugar_cane").shape());
        assertEquals("attached_melon_stem", crops.get("melon").extra());
        assertEquals(4, crops.size());
        assertEquals(2, problems.size(), problems.toString());
        assertEquals(List.of(1, 15, 30), HarvestRules.levels(List.of("30", "1", "x", "15", "15", "0")));
    }
}
