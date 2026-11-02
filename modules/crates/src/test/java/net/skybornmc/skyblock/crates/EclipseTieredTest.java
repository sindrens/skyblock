package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tiered Eclipse Crate (owner F7a, V1-V3): 3 of 9 per band plus 1 of 5 jackpots, no pity, no re-roll. */
class EclipseTieredTest {

    private static CrateCatalog.Crate eclipse() {
        return Fixtures.bundled().crate("eclipse").orElseThrow();
    }

    @Test
    void bundledPoolIs9x3Plus5Jackpots() {
        CrateCatalog.Crate c = eclipse();
        assertTrue(c.tiered());
        assertEquals(0, c.pity());
        assertEquals(9, c.inBand("common").size());
        assertEquals(9, c.inBand("mid").size());
        assertEquals(9, c.inBand("rare").size());
        assertEquals(5, c.inBand("jackpot").size());
        assertEquals(10, c.perOpen());
        assertEquals(100.0 / 3, c.percent(c.inBand("mid").getFirst()), 1e-9, "3 of 9: exact odds");
        double jackpots = c.inBand("jackpot").stream().mapToDouble(c::percent).sum();
        assertEquals(100.0, jackpots, 1e-9, "one jackpot every open");
        assertTrue(c.inBand("jackpot").size() <= PreviewMenu.JACKPOT_SLOTS.length);
    }

    @Test
    void oneOpenGivesThreeDifferentPerBandAndOneJackpot() {
        CrateCatalog.Crate c = eclipse();
        Random rng = new Random(7);
        for (int run = 0; run < 200; run++) {
            RollEngine.Batch b = RollEngine.rollTiered(c, 1, rng, k -> false);
            assertEquals(10, b.rolls().size());
            assertEquals(0, b.pityAfter());
            for (String band : List.of("common", "mid", "rare")) {
                Set<String> ids = new HashSet<>();
                b.rolls().stream().filter(r -> band.equals(c.band(r.reward()))).forEach(r -> ids.add(r.reward().id()));
                assertEquals(3, ids.size(), band + " has 3 different rewards");
            }
            assertEquals(1, b.rolls().stream().filter(r -> "jackpot".equals(c.band(r.reward()))).count());
            assertFalse(b.rolls().stream().anyMatch(RollEngine.Roll::forced), "no pity");
        }
    }

    @Test
    void ownedCosmeticJackpotPaysItsFallbackInsteadOfARoll() {
        CrateCatalog.Crate c = eclipse();
        Random rng = new Random(1);
        int dup = 0;
        for (int run = 0; run < 500; run++) {
            for (RollEngine.Roll r : RollEngine.rollTiered(c, 1, rng, k -> true).rolls()) {
                if (r.reward().type().cosmetic()) {
                    assertTrue(r.fallback(), "owned: fallback, never another reward");
                    dup++;
                }
            }
        }
        assertTrue(dup > 0);
    }

    @Test
    void severalKeysShareTheirBoundFlagPerOpen() {
        CrateCatalog.Crate c = eclipse();
        OpenPlan.Plan plan = OpenPlan.build(c, new CrateMath.KeyUse(1, 1), 0, Fixtures.context(1, k -> false), new Random(3),
                java.util.UUID::randomUUID);
        assertEquals(20, plan.grants().size());
        assertTrue(plan.grants().subList(0, 10).stream().allMatch(OpenPlan.Grant::bound));
        assertFalse(plan.grants().subList(10, 20).stream().anyMatch(OpenPlan.Grant::bound));
    }

    @Test
    void untouchedLiveTableSwitchesEditedOneStays() {
        YamlConfiguration bundled = Fixtures.resource(CrateCatalog.FILE);
        YamlConfiguration live = new YamlConfiguration();
        List<Map<String, Object>> old = new ArrayList<>();
        for (String id : EclipseMigration.OLD_IDS) {
            old.add(Map.of("id", id, "type", "coming-soon", "chance", 1));
        }
        live.set("crates.eclipse.pity", 10);
        live.set("crates.eclipse.rewards", old);
        EclipseMigration.Result r = EclipseMigration.migrate(live, bundled);
        assertTrue(r.changed());
        assertEquals(EclipseMigration.ids(bundled.getConfigurationSection("crates.eclipse")),
                EclipseMigration.ids(live.getConfigurationSection("crates.eclipse")));
        assertFalse(live.contains("crates.eclipse.pity"));
        assertEquals(3, live.getInt("crates.eclipse.draw.mid"));
        assertFalse(EclipseMigration.migrate(live, bundled).changed(), "runs once");

        YamlConfiguration edited = new YamlConfiguration();
        List<Map<String, Object>> mine = new ArrayList<>(old);
        mine.add(Map.of("id", "my_reward", "type", "coming-soon", "chance", 1));
        edited.set("crates.eclipse.rewards", mine);
        EclipseMigration.migrate(edited, bundled);
        assertEquals(17, edited.getMapList("crates.eclipse.rewards").size(), "edited table kept");
    }
}
