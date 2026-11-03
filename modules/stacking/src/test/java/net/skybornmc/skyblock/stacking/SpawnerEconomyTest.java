package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.stacking.StackingService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECON-SPAWNERS / ECON-TABLES A-B, pure: the island cap per tier, the kill unlock, the upgrade steps, the custom drop
 * amount (expected value exact), every drop sellable, and the payback window of each sold tier at the defaults.
 */
class SpawnerEconomyTest {

    // ------------------------------------------------------------------ cap (S3)

    @Test
    void capPerTierWithAClampedPerk() {
        assertEquals(32, SpawnerRules.cap(32, 0, 16));
        assertEquals(48, SpawnerRules.cap(32, 100, 16), "perk clamped to its maximum");
        assertEquals(32, SpawnerRules.cap(32, -5, 16), "a negative perk adds nothing");
        assertEquals(0, SpawnerRules.cap(0, 10, 16), "0 = no cap");
        assertEquals(1, SpawnerRules.room(31, 32));
        assertEquals(0, SpawnerRules.room(40, 32), "grandfathered islands above the cap: no room, never negative");
        assertEquals(Long.MAX_VALUE, SpawnerRules.room(1_000, 0));
    }

    // ------------------------------------------------------------------ unlock (step 9)

    @Test
    void aTierUnlocksByKillsOfThePreviousTier() {
        List<String> tier1 = List.of("pig", "cow", "chicken", "sheep", "rabbit");
        StackingService.Unlock locked = UnlockRules.status(2, 1_000, List.of(), tier1, Map.of("pig", 400L, "cow", 300L, "zombie", 9_999L));
        assertFalse(locked.unlocked());
        assertEquals(700, locked.kills(), "only tier 1 mobs count");
        assertEquals("tier", locked.mobsLabelKey());
        StackingService.Unlock open = UnlockRules.status(2, 1_000, List.of(), tier1, Map.of("pig", 600L, "sheep", 900L));
        assertTrue(open.unlocked());
        assertEquals(1_000, open.kills(), "shown capped at the target");
        StackingService.Unlock named = UnlockRules.status(3, 10, List.of("zombie"), List.of("zombie", "skeleton"), Map.of("skeleton", 50L));
        assertFalse(named.unlocked(), "only the named mobs count");
        assertEquals("mobs", named.mobsLabelKey());
        assertTrue(UnlockRules.status(1, 1_000, List.of(), List.of(), Map.of()).unlocked(), "tier 1 is open");
        assertTrue(UnlockRules.status(5, 0, List.of(), List.of(), Map.of()).unlocked(), "premium tier: 0 = no gate");
        assertEquals(0, UnlockRules.status(2, 10, List.of(), tier1, Map.of("pig", -50L)).kills(), "broken negative counts add nothing");
    }

    // ------------------------------------------------------------------ steps (step 5)

    @Test
    void stepsClampAndRespectTheFloor() {
        StackingConfig c = new StackingConfig();
        List<String> problems = new ArrayList<>();
        SpawnerSteps s = SpawnerSteps.parse(c.speedSteps, c.mobSteps, c.lootSteps, c.spawnerIntervalFloorSeconds, problems::add);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        assertEquals(10.0, s.seconds(0));
        assertEquals(5.0, s.seconds(99), "above the list: the last step");
        assertEquals(2.0, s.mobs(4));
        assertEquals(1.5, s.loot(5));
        assertEquals(2.0 * 2.0 * 1.5, (s.seconds(0) / s.seconds(5)) * s.mobs(4) * s.loot(5), 1e-9, "x6 combined maximum");
        SpawnerSteps fast = SpawnerSteps.parse(List.of("1"), List.of("1"), List.of("1"), 4, p -> { });
        assertEquals(4.0, fast.seconds(0), "never below the floor");
        assertEquals(80, fast.intervalTicks(0, 50, 0.0), "jitter never goes under the floor");
        assertEquals(200, s.intervalTicks(0, 0, 0.7), "no jitter: exactly 10 s");
        List<String> broken = new ArrayList<>();
        SpawnerSteps fallback = SpawnerSteps.parse(List.of("x"), List.of(), List.of("-1"), 4, broken::add);
        assertEquals(10.0, fallback.seconds(0), "empty after skipping: defaults");
        assertEquals(5, broken.size(), () -> String.join("\n", broken));
    }

    // ------------------------------------------------------------------ drops (step 8)

    @Test
    void dropAmountHasTheExactExpectedValue() {
        Random random = new Random(42);
        long total = 0;
        int runs = 100_000;
        for (int i = 0; i < runs; i++) {
            total += SpawnerDrops.amount(1, 0, 1.0, 1.3, random::nextDouble);
        }
        assertEquals(1.3, total / (double) runs, 0.01, "1.3 items per mob on average at loot x1.3");
        assertEquals(15, SpawnerDrops.amount(10, 0, 1.0, 1.5, () -> 0.99), "whole part only when the roll misses");
        assertEquals(0, SpawnerDrops.amount(-5, -5, 1.0, 1.0, () -> 0.0), "never negative");
        assertEquals(4, SpawnerDrops.amount(2, 2, 1.0, 1.0, () -> 0.5), "extra loot rolls count like kills");
    }

    @Test
    void everyDefaultDropParsesAndIsSellable() throws Exception {
        StackingConfig c = new StackingConfig();
        List<String> problems = new ArrayList<>();
        java.util.Set<String> items = new java.util.HashSet<>();
        for (String line : c.spawnerDropItems) {
            items.add(line.substring(0, line.indexOf('=')));
        }
        SpawnerDrops drops = SpawnerDrops.parse(c.spawnerDrops, mob -> true, id -> items.contains(id) || SpawnerDrops.vanilla(id) != null,
                problems::add);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        assertEquals(23, drops.all().size(), "20 earned mobs + 3 premium (ECON-TABLES A)");
        YamlConfiguration sell = YamlConfiguration.loadConfiguration(new File("../economy/src/main/resources/sell.yml"));
        List<String> unpriced = new ArrayList<>();
        for (SpawnerDrops.Drop d : drops.all().values()) {
            double price = SpawnerDrops.vanilla(d.item()) != null
                    ? sell.getDouble("categories.mob-loot." + d.item(), 0)
                    : sell.getDouble("custom-items.spawner-drops." + d.item(), 0);
            if (price <= 0) {
                unpriced.add(d.item());
            }
        }
        assertTrue(unpriced.isEmpty(), "every spawner drop has a sell.yml price: " + unpriced);
    }

    // ------------------------------------------------------------------ payback (ECON-TABLES B)

    @Test
    void everySoldTierPaysBackIn60To122Hours() {
        double factor = 0.85; // shop-settings.yml spawners.dynamic-factor
        double[][] tiers = {{100_000, 5}, {250_000, 10}, {500_000, 16}, {1_000_000, 30}};
        for (double[] t : tiers) {
            double perHour = SpawnerIncome.perHour(SpawnerSteps.DEFAULTS.seconds(0), SpawnerSteps.DEFAULTS.mobs(0), 1.0, t[1] * factor);
            double hours = SpawnerIncome.paybackHours(t[0], perHour);
            assertTrue(hours >= 60 && hours <= 122, "$" + (long) t[0] + " at $" + t[1] + " a drop pays back in " + hours + " h");
        }
        assertEquals(1_800, SpawnerIncome.perHour(10, 1, 1, 5), 1e-9, "360 spawns x $5");
        assertEquals(Double.POSITIVE_INFINITY, SpawnerIncome.paybackHours(100, 0));
        assertEquals(0, SpawnerIncome.perHour(0, 1, 1, 5));
    }
}
