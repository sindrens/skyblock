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
        assertEquals(2.0 * 2.0 * 1.5, (s.seconds(0) / s.seconds(10)) * s.mobs(4) * s.loot(5), 1e-9, "x6 combined maximum (Spawners v2: speed level 10 = 5 s)");
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

    /** Prices sell.yml gets from recipes (not listed), as the live server reports them (7 Nov 2026), and unsellable arrows. */
    static final java.util.Map<String, Double> DERIVED = java.util.Map.of("sugar", 216.0, "glass_bottle", 27.0, "stick", 6.0,
            "copper_ingot", 55.0, "gold_nugget", 277.0, "arrow", 0.0);

    static double price(YamlConfiguration sell, String item) {
        if (DERIVED.containsKey(item)) {
            return DERIVED.get(item);
        }
        if (SpawnerDrops.vanilla(item) == null) {
            return sell.getDouble("custom-items.spawner-drops." + item, 0);
        }
        for (String cat : List.of("mob-loot", "fish", "ores", "crops")) {
            if (sell.contains("categories." + cat + "." + item)) {
                return sell.getDouble("categories." + cat + "." + item);
            }
        }
        return 0;
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
        assertEquals(List.of("beef", "leather"), drops.of("cow").stream().map(SpawnerDrops.Drop::item).toList(),
                "owner 7 Nov 2026: a cow drops 1 beef and 1 leather");
        assertTrue(drops.of("zombie").stream().allMatch(d -> d.perMob() == 1.0));
        YamlConfiguration sell = YamlConfiguration.loadConfiguration(new File("../economy/src/main/resources/sell.yml"));
        List<String> unpriced = new ArrayList<>();
        for (List<SpawnerDrops.Drop> list : drops.all().values()) {
            for (SpawnerDrops.Drop d : list) {
                if (price(sell, d.item()) <= 0 && !d.item().equals("arrow")) {
                    unpriced.add(d.item());
                }
            }
        }
        assertTrue(unpriced.isEmpty(), "every spawner drop has a sell price: " + unpriced);
    }

    @Test
    void aMobOnTwoLinesKeepsBothAndADuplicateItemIsSkipped() {
        List<String> problems = new ArrayList<>();
        SpawnerDrops d = SpawnerDrops.parse(List.of("cow:beef:1", "cow:leather:1", "cow:beef:2"), m -> true, i -> true, problems::add);
        assertEquals(2, d.of("cow").size());
        assertEquals(1, problems.size());
        assertEquals("beef", d.first("cow").orElseThrow().item());
        assertTrue(d.of("pig").isEmpty());
    }

    /** Owner 7 Nov 2026 (plans/HOE_SHARDS_SPAWNERS.md): every spawner sold in /shop pays back in about 50 hours. */
    @Test
    void everyShopSpawnerPaysBackInAbout50Hours() {
        StackingConfig c = new StackingConfig();
        SpawnerDrops drops = SpawnerDrops.parse(c.spawnerDrops, m -> true, i -> true, p -> { });
        YamlConfiguration sell = YamlConfiguration.loadConfiguration(new File("../economy/src/main/resources/sell.yml"));
        YamlConfiguration shop = YamlConfiguration.loadConfiguration(new File("../shop/src/main/resources/shop.yml"));
        List<String> off = new ArrayList<>();
        java.util.Map<Integer, Double> best = new java.util.HashMap<>();
        List<List<String>> tiers = List.of(c.tier1, c.tier2, c.tier3, c.tier4);
        for (int t = 0; t < tiers.size(); t++) {
            for (String mob : tiers.get(t)) {
                double perKill = 0;
                for (SpawnerDrops.Drop d : drops.of(mob)) {
                    perKill += d.perMob() * price(sell, d.item());
                }
                double perHour = SpawnerIncome.perHour(SpawnerSteps.DEFAULTS.seconds(0), SpawnerSteps.DEFAULTS.mobs(0), 1.0, perKill);
                double cost = shop.getDouble("categories.spawners.items." + mob, 0);
                double hours = SpawnerIncome.paybackHours(cost, perHour);
                if (cost <= 0 || hours < 45 || hours > 56) {
                    off.add(mob + " $" + (long) cost + " earns $" + (long) perHour + "/h = " + Math.round(hours) + " h");
                }
                best.merge(t + 1, perHour, Math::max);
            }
        }
        assertTrue(off.isEmpty(), "paybacks: " + off);
        for (int t = 2; t <= 4; t++) {
            assertTrue(best.get(t) >= 1.5 * best.get(t - 1), "tier " + t + "'s best spawner earns clearly more than tier " + (t - 1) + "'s");
        }
        assertEquals(List.of("blaze", "enderman", "guardian"), c.tier4);
    }

    // ------------------------------------------------------------------ payback (ECON-TABLES B)

    @Test
    void incomeMath() {
        assertEquals(1_800, SpawnerIncome.perHour(10, 1, 1, 5), 1e-9, "360 spawns x $5");
        assertEquals(Double.POSITIVE_INFINITY, SpawnerIncome.paybackHours(100, 0));
        assertEquals(0, SpawnerIncome.perHour(0, 1, 1, 5));
    }
}
