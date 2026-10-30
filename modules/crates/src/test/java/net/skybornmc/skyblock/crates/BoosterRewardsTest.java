package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.2: crate rewards that paid crystals for boosters now give booster items (bundled tables and old server files). */
class BoosterRewardsTest {

    @Test
    void bundledBoosterRewardsAreLiveWithThePlanNumbers() {
        CrateCatalog c = Fixtures.bundled();
        CrateCatalog.Reward ember = c.crate("ember").orElseThrow().reward("sell_booster").orElseThrow();
        assertEquals(RewardType.BOOSTER, ember.type());
        assertEquals("sell:25:10", ember.boosterRef());
        assertEquals("skill_xp:100:30", c.crate("inferno").orElseThrow().reward("xp_booster").orElseThrow().boosterRef());
        CrateCatalog.Reward global = c.crate("eclipse").orElseThrow().reward("global_booster").orElseThrow();
        assertEquals(RewardType.GLOBAL_BOOSTER, global.type());
        assertEquals("sell:50:60", global.boosterRef());
        for (CrateCatalog.Crate crate : c.crates()) {
            for (CrateCatalog.Reward r : crate.rewards()) {
                if (r.id().contains("booster")) {
                    assertTrue(r.type() == RewardType.BOOSTER || r.type() == RewardType.GLOBAL_BOOSTER, crate.id() + "." + r.id());
                }
            }
        }
    }

    @Test
    void brokenBoosterRewardsAreSkipped() {
        List<String> problems = new ArrayList<>();
        CrateCatalog c = Fixtures.parse("""
                crates:
                  test:
                    rewards:
                      - {id: ok, type: booster, chance: 50, booster: sell, percent: 25, minutes: 10}
                      - {id: bad_type, type: booster, chance: 10, booster: flying, percent: 25, minutes: 10}
                      - {id: no_percent, type: global-booster, chance: 10, booster: sell, minutes: 10}
                      - {id: no_minutes, type: booster, chance: 10, booster: tool-xp, percent: 25}
                """, problems);
        assertEquals(List.of("ok"), c.crate("test").orElseThrow().rewards().stream().map(CrateCatalog.Reward::id).toList());
        assertEquals(3, problems.stream().filter(p -> p.contains("skipped")).count(), problems.toString());
    }

    @Test
    void boosterGrantCarriesTheBoosterAndFallsBackWhenTheModuleIsOff() {
        CrateCatalog.Reward r = Fixtures.parse("""
                crates:
                  test:
                    rewards:
                      - {id: b, type: booster, chance: 1, booster: crystals, percent: 50, minutes: 15, amount: 2}
                """, new ArrayList<>()).crate("test").orElseThrow().rewards().getFirst();
        RollEngine.Roll roll = new RollEngine.Roll(r, null, false, false);
        OpenPlan.Grant live = OpenPlan.resolve(roll, true, Fixtures.context(1, s -> false), new Random(1), UUID.randomUUID());
        assertEquals("booster", live.kind());
        assertEquals("crystals:50:15", live.ref());
        assertEquals(2, live.amount());
        assertFalse(live.inTransaction(), "items are handed out after the commit");
        OpenPlan.Context off = new OpenPlan.Context(1, new long[]{1_000}, 1.3, s -> false, x -> false, p -> List.of(), t -> List.of());
        assertTrue(OpenPlan.resolve(roll, true, off, new Random(1), UUID.randomUUID()).fallback(), "no boosters module: crystals");
    }

    @Test
    void oldComingSoonRewardsAreSwitchedOnKeepingTheOwnersChance() throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.loadFromString("""
                crates:
                  ember:
                    rewards:
                      - {id: sell_booster, type: coming-soon, chance: 12, icon: gold_ingot}
                      - {id: tool_skin, type: coming-soon, chance: 3}
                      - {id: global_booster, type: crystals, chance: 5, base: 10}
                """);
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.loadFromString("""
                crates:
                  ember:
                    rewards:
                      - {id: sell_booster, type: booster, chance: 8, booster: sell, percent: 25, minutes: 10, icon: gold_ingot}
                      - {id: tool_skin, type: coming-soon, chance: 6}
                      - {id: global_booster, type: global-booster, chance: 4, booster: sell, percent: 50, minutes: 30}
                """);
        assertTrue(CatalogMerge.upgradeLive(server, bundled));
        List<Map<?, ?>> rewards = server.getMapList("crates.ember.rewards");
        assertEquals("booster", rewards.get(0).get("type"));
        assertEquals(12, ((Number) rewards.get(0).get("chance")).intValue(), "the owner's chance stays");
        assertEquals("sell", rewards.get(0).get("booster"));
        assertEquals(25, ((Number) rewards.get(0).get("percent")).intValue());
        assertEquals("coming-soon", rewards.get(1).get("type"), "still not live in the jar");
        assertEquals("crystals", rewards.get(2).get("type"), "a reward the owner changed is never touched");
        assertFalse(CatalogMerge.upgradeLive(server, bundled), "nothing left to switch on");
        List<String> problems = new ArrayList<>();
        CrateCatalog.load(server, problems::add);
        assertTrue(problems.stream().noneMatch(p -> p.contains("skipped")), problems.toString());
    }
}
