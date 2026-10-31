package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusMath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mastery gating, effects (within the family caps), Treasury fee steps, homes, satchel and Ascension pricing (M5.5). */
class MasteryRulesTest {

    private static MasteryRules.Gate gate(boolean requires, int pillar, int island, boolean tasks, boolean permission, long bank) {
        return new MasteryRules.Gate(requires, pillar, island, tasks, permission, bank);
    }

    @Test
    void gatesRunInOrder() throws Exception {
        MasteryCatalog catalog = MasteryCatalogTest.bundled();
        MasteryCatalog.Def warrior = catalog.get("warrior").orElseThrow(); // combat pillar 10, then 20 ...
        long cost = warrior.rank(1).cost();
        assertEquals(MasteryRules.Status.PILLAR_LEVEL, MasteryRules.status(warrior, 0, gate(true, 9, 99, true, true, -1)));
        assertEquals(MasteryRules.Status.TASKS_OPEN, MasteryRules.status(warrior, 0, gate(true, 10, 0, false, true, -1)));
        assertEquals(MasteryRules.Status.NO_PERMISSION, MasteryRules.status(warrior, 0, gate(true, 10, 0, true, false, -1)));
        assertEquals(MasteryRules.Status.NO_MONEY, MasteryRules.status(warrior, 0, gate(true, 10, 0, true, true, cost - 1)));
        assertEquals(MasteryRules.Status.READY, MasteryRules.status(warrior, 0, gate(true, 10, 0, true, true, cost)));
        assertEquals(MasteryRules.Status.READY, MasteryRules.status(warrior, 0, gate(true, 10, 0, true, true, -1)), "unknown bank: the payment decides");
        assertEquals(MasteryRules.Status.PILLAR_LEVEL, MasteryRules.status(warrior, 1, gate(true, 19, 0, true, true, -1)), "rank II needs pillar 20");
        assertEquals(MasteryRules.Status.MAXED, MasteryRules.status(warrior, 5, gate(true, 99, 99, true, true, -1)));

        MasteryCatalog.Def wings = catalog.get("wings").orElseThrow();
        assertEquals(MasteryRules.Status.ISLAND_LEVEL, MasteryRules.status(wings, 0, gate(true, 0, 19, true, true, -1)));
        assertEquals(MasteryRules.Status.READY, MasteryRules.status(wings, 0, gate(true, 0, 20, true, true, -1)));

        MasteryCatalog.Def market = catalog.get("market_contacts").orElseThrow();
        assertEquals(MasteryRules.Status.NEEDS_MAXED, MasteryRules.status(market, 0, gate(false, 0, 999, true, true, -1)));
        assertEquals(MasteryRules.Status.READY, MasteryRules.status(market, 0, gate(true, 0, 0, true, true, -1)));

        MasteryCatalog.Def timber = catalog.get("timber").orElseThrow();
        assertEquals(MasteryRules.Status.COMING_SOON, MasteryRules.status(timber, 0, gate(true, 99, 99, true, true, -1)), "hidden, never bought");
        assertTrue(MasteryRules.Status.NO_MONEY.unlocked());
        assertFalse(MasteryRules.Status.TASKS_OPEN.unlocked());
    }

    @Test
    void tasksMustBeDoneAndTheEngineLoaded() throws Exception {
        MasteryCatalog.Def warrior = MasteryCatalogTest.bundled().get("warrior").orElseThrow();
        assertTrue(MasteryRules.tasksDone(warrior, 0, true, key -> key.equals("mastery.warrior.1")));
        assertFalse(MasteryRules.tasksDone(warrior, 1, true, key -> key.equals("mastery.warrior.1")));
        assertFalse(MasteryRules.tasksDone(warrior, 0, false, key -> true), "locked while the task engine loads");
    }

    @Test
    void marketContactsWaitsForSellMultiplierAndAscensionForEverything() throws Exception {
        UpgradeCatalog upgrades = UpgradeStoreTest.bundled();
        MasteryCatalog masteries = MasteryCatalogTest.bundled();
        MasteryCatalog.Def market = masteries.get("market_contacts").orElseThrow();
        int sellMax = upgrades.get("sell_multiplier").orElseThrow().maxLevel();
        assertFalse(MasteryRules.requiresMet(market, upgrades, Map.of("sell_multiplier", sellMax - 1)));
        assertTrue(MasteryRules.requiresMet(market, upgrades, Map.of("sell_multiplier", sellMax)));

        Map<String, Integer> allUpgrades = new HashMap<>();
        upgrades.all().forEach(u -> allUpgrades.put(u.id(), u.maxLevel()));
        Map<String, Integer> shownMasteries = new HashMap<>();
        masteries.shown().forEach(m -> shownMasteries.put(m.id(), m.maxRank()));
        assertTrue(MasteryRules.allMaxed(upgrades, allUpgrades, masteries, shownMasteries), "hidden masteries never block Ascension");
        Map<String, Integer> missing = new HashMap<>(shownMasteries);
        missing.put("wings", 0);
        assertFalse(MasteryRules.allMaxed(upgrades, allUpgrades, masteries, missing));
        Map<String, Integer> buyableOnly = new HashMap<>();
        upgrades.all().stream().filter(UpgradeCatalog.Def::buyable).forEach(u -> buyableOnly.put(u.id(), u.maxLevel()));
        assertTrue(MasteryRules.allMaxed(upgrades, buyableOnly, masteries, shownMasteries), "coming-soon upgrades never block it either");
        assertFalse(MasteryRules.allMaxed(UpgradeCatalog.empty(), Map.of(), MasteryCatalog.empty(), Map.of()));
        assertEquals(MasteryRules.Status.NEEDS_MAXED, MasteryRules.ascensionStatus(true, 0, 0, 100, gate(false, 0, 0, true, true, -1)));
        assertEquals(MasteryRules.Status.READY, MasteryRules.ascensionStatus(true, 0, 0, 100, gate(true, 0, 0, true, true, 100)));
        assertEquals(MasteryRules.Status.NO_MONEY, MasteryRules.ascensionStatus(true, 0, 0, 100, gate(true, 0, 0, true, true, 99)));
        assertEquals(MasteryRules.Status.MAXED, MasteryRules.ascensionStatus(true, 3, 3, 100, gate(true, 0, 0, true, true, -1)));
        assertEquals(MasteryRules.Status.MAXED, MasteryRules.ascensionStatus(true, 0, 0, -1, gate(true, 0, 0, true, true, -1)), "price overflow");
        assertEquals(MasteryRules.Status.DISABLED, MasteryRules.ascensionStatus(false, 0, 0, 100, gate(true, 0, 0, true, true, -1)));
    }

    @Test
    void ascensionPricesFollowTheEndlessCurve() {
        // Ascension 1 = curve level 21 (after the 20 Market Contacts levels): 686M x 1.12^20 = ~6.62B (ECONOMY_BALANCE 5.4).
        assertEquals(6_620_000_000L, MasteryRules.ascensionCost(686_000_000L, 1.12, 21, 1));
        assertEquals(MasteryRules.nice(686_000_000L * Math.pow(1.12, 21)), MasteryRules.ascensionCost(686_000_000L, 1.12, 21, 2));
        assertEquals(686_000_000L, MasteryRules.ascensionCost(686_000_000L, 1.12, 1, 1), "start level 1 = the base");
        assertTrue(MasteryRules.ascensionCost(686_000_000L, 1.12, 21, 3) > MasteryRules.ascensionCost(686_000_000L, 1.12, 21, 2));
        assertEquals(-1, MasteryRules.ascensionCost(686_000_000L, 1.12, 21, 400), "no overflow: no more levels");
        assertEquals(-1, MasteryRules.ascensionCost(686_000_000L, 1.12, 21, 0));
        assertEquals(123_000, MasteryRules.nice(123_456));
        assertEquals(999, MasteryRules.nice(999));
    }

    @Test
    void levelPointsByTierAndAscension() throws Exception {
        MasteryCatalog catalog = MasteryCatalogTest.bundled();
        UpgradeRules.Points points = new UpgradeRules.Points(0.5, 1, 1.5, 4, 7);
        // Wings (tier 4): 1; Treasury I-II (tiers 3, 7): 0.5 + 1.5; Ascension 2 levels x 1.5.
        assertEquals(1 + 0.5 + 1.5 + 3.0, MasteryRules.points(catalog, Map.of("wings", 1, "treasury", 2, MasteryRules.ASCENSION_ID, 2), points, 1.5), 1e-9);
        assertEquals(0, MasteryRules.points(catalog, Map.of(), points, 1.5));
        assertEquals(0, MasteryRules.points(catalog, Map.of("removed_one", 3), points, 1.5), "unknown ids count nothing");
    }

    @Test
    void effectsGoToTheirFamilyOnly() throws Exception {
        MasteryCatalog catalog = MasteryCatalogTest.bundled();
        Map<String, Integer> bought = Map.of("warrior", 2, "crystal_rush", 3, "lucky_strike", 1, "market_contacts", 5, "timber", 1);
        assertEquals(8, MasteryRules.bonusPercent(catalog, bought, BonusFamily.MOB_DAMAGE, "combat"));
        assertEquals(9, MasteryRules.bonusPercent(catalog, bought, BonusFamily.CRYSTALS, "mining"));
        assertEquals(4, MasteryRules.bonusPercent(catalog, bought, BonusFamily.RARE_FINDS, null));
        assertEquals(10, MasteryRules.bonusPercent(catalog, bought, BonusFamily.SELL, "sell"));
        assertEquals(4, MasteryRules.bonusPercent(catalog, bought, BonusFamily.EXTRA_DROPS, "foraging"), "Timber: foraging only");
        assertEquals(0, MasteryRules.bonusPercent(catalog, bought, BonusFamily.EXTRA_DROPS, "mining"));
        assertEquals(0, MasteryRules.bonusPercent(catalog, bought, BonusFamily.SKILL_XP, null));
        assertEquals(0, MasteryRules.bonusPercent(catalog, bought, null, null));
    }

    @Test
    void familyCapsStillHold() throws Exception {
        MasteryCatalog catalog = MasteryCatalogTest.bundled();
        double market = MasteryRules.bonusPercent(catalog, Map.of("market_contacts", 20), BonusFamily.SELL, "sell");
        assertEquals(40, market);
        // Sell Multiplier maxed (+60%) + Market Contacts (+40%) + 250% from other sources: the +300% sell cap holds.
        assertEquals(300, BonusMath.capped(60 + market + 250, 300), 1e-9);
        assertEquals(1.0 + 3.0, BonusMath.multiplier(60 + market + 250, 300), 1e-9);
        double warrior = MasteryRules.bonusPercent(catalog, Map.of("warrior", 5), BonusFamily.MOB_DAMAGE, "combat");
        assertEquals(200, BonusMath.capped(warrior + 190, BonusFamily.MOB_DAMAGE.defaultCap()), 1e-9);
    }

    @Test
    void treasuryHomesWingsAndSatchel() throws Exception {
        MasteryCatalog catalog = MasteryCatalogTest.bundled();
        assertEquals(0, MasteryRules.whole(catalog, Map.of(), MasteryEffect.TREASURY));
        assertEquals(1, MasteryRules.whole(catalog, Map.of("treasury", 1), MasteryEffect.TREASURY), "Treasury I = fee step 1 (1.5%)");
        assertEquals(3, MasteryRules.whole(catalog, Map.of("treasury", 3), MasteryEffect.TREASURY), "Treasury III = step 3 (0.5%)");
        assertEquals(0, MasteryRules.extraHomes(catalog, Map.of()), "base: only the island home");
        assertEquals(1, MasteryRules.extraHomes(catalog, Map.of("homestead", 1)));
        assertEquals(2, MasteryRules.extraHomes(catalog, Map.of("homestead", 2)));
        assertEquals(1, MasteryRules.value(catalog, Map.of("wings", 1), MasteryEffect.WINGS));
        assertEquals(0, MasteryRules.value(catalog, Map.of(), MasteryEffect.WINGS));
        assertEquals(30, MasteryRules.value(catalog, Map.of("deep_pockets", 3), MasteryEffect.DEEP_POCKETS));
        assertEquals(76, MasteryRules.satchelBonus(256, 30), "30% of 256, rounded down");
        assertEquals(0, MasteryRules.satchelBonus(256, 0));
        assertEquals(0, MasteryRules.satchelBonus(0, 50));
    }

    @Test
    void keysFitTheirColumns() {
        UUID island = UUID.randomUUID();
        String key = MasteryRules.idempotencyKey(999, island, "x".repeat(32), 999_999);
        assertTrue(key.length() <= 96, key);
        assertTrue(MasteryRules.reference("x".repeat(32), 999_999).length() <= 64);
        assertTrue(MasteryRules.idempotencyKey(1, island, "wings", 1).startsWith("mastery:1:"));
    }
}
