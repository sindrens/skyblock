package net.skybornmc.skyblock.wardrobe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who earns set XP (WARDROBE §3.1, WD10-WD12): piece shares, sources per set activity, merged entries, hourly caps. */
class SetXpTest {

    private static final WardrobeConfig C = new WardrobeConfig();
    private static final SetRegistry R = Fixtures.registry();
    private static final SetXp.Shares SHARES = C.shares();

    private static Map<String, Double> sources(String setId) {
        return R.get(setId).map(d -> C.sources(d.activity())).orElse(Map.of());
    }

    private static SetXp.WornEntry worn(UUID id, String set, String merged, int pieces) {
        return new SetXp.WornEntry(id, set, merged, pieces);
    }

    @Test
    void twoToThreePiecesEarnHalfFourPiecesEarnAll() {
        UUID a = UUID.randomUUID();
        assertTrue(SetXp.split(List.of(worn(a, "delver", null, 1)), "mining", 10, SetXpTest::sources, SHARES).isEmpty());
        assertEquals(5, SetXp.split(List.of(worn(a, "delver", null, 2)), "mining", 10, SetXpTest::sources, SHARES).get(a), 1e-9);
        assertEquals(5, SetXp.split(List.of(worn(a, "delver", null, 3)), "mining", 10, SetXpTest::sources, SHARES).get(a), 1e-9);
        assertEquals(10, SetXp.split(List.of(worn(a, "delver", null, 4)), "mining", 10, SetXpTest::sources, SHARES).get(a), 1e-9);
    }

    @Test
    void onlyTheBoostedActivityCounts() {
        UUID a = UUID.randomUUID();
        assertTrue(SetXp.split(List.of(worn(a, "delver", null, 4)), "foraging", 10, SetXpTest::sources, SHARES).isEmpty());
        assertTrue(SetXp.split(List.of(worn(a, "coinmaster", null, 4)), "mining", 10, SetXpTest::sources, SHARES).isEmpty());
        assertEquals(10, SetXp.split(List.of(worn(a, "coinmaster", null, 4)), "sell", 10, SetXpTest::sources, SHARES).get(a), 1e-9);
        assertEquals(10, SetXp.split(List.of(worn(a, "dreadguard", null, 4)), "boss", 10, SetXpTest::sources, SHARES).get(a), 1e-9);
        assertTrue(SetXp.split(List.of(worn(a, "lorekeeper", null, 4)), "boss", 10, SetXpTest::sources, SHARES).isEmpty(),
                "bosses: combat sets and Phoenix only");
        assertTrue(SetXp.split(List.of(worn(a, "phoenix", null, 4)), "sell", 10, SetXpTest::sources, SHARES).isEmpty(),
                "selling: Coinmaster only");
        assertTrue(SetXp.split(List.of(worn(a, "phoenix", null, 4)), "pvp", 10, SetXpTest::sources, SHARES).isEmpty(),
                "warzone kills: conquest sets only");
        assertEquals(10, SetXp.split(List.of(worn(a, "banneret", null, 4)), "pvp", 10, SetXpTest::sources, SHARES).get(a), 1e-9);
        assertEquals(10, SetXp.split(List.of(worn(a, "voidforged", null, 4)), "farming", 10, SetXpTest::sources, SHARES).get(a), 1e-9);
    }

    @Test
    void lorekeeperEarnsThreeQuartersOfEverySkill() {
        UUID a = UUID.randomUUID();
        for (String activity : List.of("mining", "foraging", "farming", "combat", "fishing")) {
            assertEquals(7.5, SetXp.split(List.of(worn(a, "lorekeeper", null, 4)), activity, 10, SetXpTest::sources, SHARES).get(a), 1e-9, activity);
        }
    }

    @Test
    void mergedEntriesEarnFromBothSetsAtTheHigherRate() {
        UUID m = UUID.randomUUID();
        assertEquals(10, SetXp.split(List.of(worn(m, "deepforge", "coinmaster", 4)), "mining", 10, SetXpTest::sources, SHARES).get(m), 1e-9);
        assertEquals(10, SetXp.split(List.of(worn(m, "deepforge", "coinmaster", 4)), "sell", 10, SetXpTest::sources, SHARES).get(m), 1e-9);
        assertEquals(10, SetXp.split(List.of(worn(m, "lorekeeper", "dreadguard", 4)), "combat", 10, SetXpTest::sources, SHARES).get(m), 1e-9);
        assertEquals(7.5, SetXp.split(List.of(worn(m, "dreadguard", "lorekeeper", 4)), "mining", 10, SetXpTest::sources, SHARES).get(m), 1e-9);
    }

    @Test
    void severalWornSetsEachEarnTheirShare() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, Double> out = SetXp.split(List.of(worn(a, "delver", null, 2), worn(b, "deepforge", null, 2)), "mining", 8,
                SetXpTest::sources, SHARES);
        assertEquals(4, out.get(a), 1e-9);
        assertEquals(4, out.get(b), 1e-9);
    }

    @Test
    void unknownOrDisabledSetsEarnNothing() {
        UUID a = UUID.randomUUID();
        assertTrue(SetXp.split(List.of(worn(a, "nope", null, 4)), "mining", 10, SetXpTest::sources, SHARES).isEmpty());
        assertTrue(SetXp.split(List.of(worn(a, "delver", null, 4)), "mining", 10, id -> Map.of(), SHARES).isEmpty());
        assertTrue(SetXp.split(List.of(worn(a, "delver", null, 4)), "mining", 0, SetXpTest::sources, SHARES).isEmpty());
        assertTrue(SetXp.split(List.of(worn(a, "delver", null, 4)), "mining", Double.NaN, SetXpTest::sources, SHARES).isEmpty());
    }

    @Test
    void parsesSources() {
        List<String> problems = new ArrayList<>();
        Map<String, Double> out = SetXp.parseSources(List.of("mining", "foraging:0.75", "nope", "farming:x", "combat:-1", " "), problems::add, "test");
        assertEquals(Map.of("mining", 1.0, "foraging", 0.75), out);
        assertEquals(3, problems.size());
    }

    @Test
    void everySetActivityHasValidSources() {
        for (String activity : SetRegistry.ACTIVITIES) {
            List<String> problems = new ArrayList<>();
            Map<String, Double> parsed = SetXp.parseSources(C.rawSources(activity), problems::add, activity);
            assertEquals(List.of(), problems, activity);
            assertFalse(parsed.isEmpty(), activity + " earns from something");
        }
        assertEquals(List.of(), C.validate(), "default wardrobe.yml has no problems");
    }

    @Test
    void actionsMapToActivitiesAndThePlansXp() {
        assertEquals(1, C.actionXp(SetXp.Action.DEPTHS_ORE));
        assertEquals(0.25, C.actionXp(SetXp.Action.ISLAND_ORE));
        assertEquals(0.5, C.actionXp(SetXp.Action.CROP));
        assertEquals(8, C.actionXp(SetXp.Action.DEPTHS_KILL));
        assertEquals(1500, C.actionXp(SetXp.Action.WORLD_BOSS));
        assertEquals(25, C.actionXp(SetXp.Action.FISH));
        assertEquals(50, C.actionXp(SetXp.Action.WARZONE_KILL));
        for (SetXp.Action a : SetXp.Action.values()) {
            assertTrue(SetXp.ACTIVITIES.contains(a.activity()), a.id());
            assertEquals(a, SetXp.Action.byId(a.id()).orElseThrow());
        }
        assertEquals(SetXp.Action.SELL_ITEM, SetXp.Action.byId(net.skybornmc.skyblock.common.wardrobe.WardrobeService.XP_SELL_ITEM).orElseThrow());
        assertEquals(SetXp.Action.KOTH_TICK, SetXp.Action.byId(net.skybornmc.skyblock.common.wardrobe.WardrobeService.XP_KOTH_TICK).orElseThrow());
        assertTrue(SetXp.Action.byId("mining").isEmpty(), "activities are not action ids");
    }

    @Test
    void hourlyCapsLimitSellXp() {
        SetXp.HourlyCap cap = new SetXp.HourlyCap();
        long hour = 3_600_000L * 1000;
        assertEquals(8_000, C.hourlyCapMap().get("sell"));
        assertEquals(5_000, cap.allow("sell", 5_000, 8_000, hour), 1e-9);
        assertEquals(3_000, cap.allow("sell", 5_000, 8_000, hour + 1000), 1e-9);
        assertEquals(0, cap.allow("sell", 1, 8_000, hour + 2000), 1e-9);
        assertEquals(50, cap.allow("mining", 50, 0, hour + 2000), 1e-9, "no cap");
        assertEquals(10, cap.allow("sell", 10, 8_000, hour + 3_600_000L), 1e-9, "a new hour");
    }

    @Test
    void piecesPerEntry() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, Integer> expected = new HashMap<>();
        expected.put(a, 3);
        expected.put(b, 1);
        assertEquals(expected, SetXp.piecesPerEntry(List.of(a, b, a, a)));
    }
}
