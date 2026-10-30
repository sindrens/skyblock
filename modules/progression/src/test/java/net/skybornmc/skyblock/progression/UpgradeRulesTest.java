package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gating order, island level points, effect values and the anti-double-pay keys of island upgrades (M5.4). */
class UpgradeRulesTest {

    private static UpgradeCatalog.Def def(UpgradeEffect effect, boolean enabled, UpgradeCatalog.Level... levels) {
        return new UpgradeCatalog.Def("test_upgrade", UpgradeTab.ISLAND, "anvil", 1, effect, enabled, List.of(levels));
    }

    private static final UpgradeCatalog.Def SIZE = def(UpgradeEffect.ISLAND_SIZE, true,
            new UpgradeCatalog.Level(1, 3_350, 5, 0), new UpgradeCatalog.Level(3, 22_900, 7, 10), new UpgradeCatalog.Level(5, 157_000, 9, 0));

    @Test
    void statusChecksRunInOrder() {
        // tasks first, then island level, then the permission, then money; maxed and coming soon before everything
        assertEquals(UpgradeRules.Status.TASKS_OPEN, UpgradeRules.status(SIZE, 0, false, 99, false, 0));
        assertEquals(UpgradeRules.Status.NO_PERMISSION, UpgradeRules.status(SIZE, 0, true, 0, false, 0));
        assertEquals(UpgradeRules.Status.NO_MONEY, UpgradeRules.status(SIZE, 0, true, 0, true, 3_349));
        assertEquals(UpgradeRules.Status.READY, UpgradeRules.status(SIZE, 0, true, 0, true, 3_350));
        assertEquals(UpgradeRules.Status.READY, UpgradeRules.status(SIZE, 0, true, 0, true, -1), "unknown balance: the payment decides");
        assertEquals(UpgradeRules.Status.ISLAND_LEVEL, UpgradeRules.status(SIZE, 1, true, 9, true, 1_000_000));
        assertEquals(UpgradeRules.Status.READY, UpgradeRules.status(SIZE, 1, true, 10, true, 1_000_000));
        assertEquals(UpgradeRules.Status.MAXED, UpgradeRules.status(SIZE, 3, true, 99, true, 1_000_000));
        UpgradeCatalog.Def vault = def(UpgradeEffect.ISLAND_VAULT, true, new UpgradeCatalog.Level(1, 10, 2, 0));
        assertEquals(UpgradeRules.Status.COMING_SOON, UpgradeRules.status(vault, 0, true, 99, true, 1_000));
        UpgradeCatalog.Def off = def(UpgradeEffect.ISLAND_SIZE, false, new UpgradeCatalog.Level(1, 10, 5, 0));
        assertEquals(UpgradeRules.Status.DISABLED, UpgradeRules.status(off, 0, true, 99, true, 1_000));
        assertTrue(UpgradeRules.Status.NO_PERMISSION.unlocked());
        assertTrue(UpgradeRules.Status.NO_MONEY.unlocked());
        assertFalse(UpgradeRules.Status.TASKS_OPEN.unlocked());
        assertFalse(UpgradeRules.Status.MAXED.unlocked());
    }

    @Test
    void tasksGateTheNextLevelOnly() {
        assertTrue(UpgradeRules.tasksDone(SIZE, 1, true, key -> key.equals("upgrade.test_upgrade.2")));
        assertFalse(UpgradeRules.tasksDone(SIZE, 0, true, key -> key.equals("upgrade.test_upgrade.2")));
        assertFalse(UpgradeRules.tasksDone(SIZE, 1, false, key -> true), "task engine not loaded: locked");
        assertEquals("upgrade.island_size.3", UpgradeCatalog.Def.unlockKey("island_size", 3));
    }

    @Test
    void levelPointsByTaskTier() {
        UpgradeRules.Points p = new UpgradeRules.Points(0.5, 1, 1.5, 4, 7);
        assertEquals(0.5, p.of(1));
        assertEquals(0.5, p.of(3));
        assertEquals(1.0, p.of(4));
        assertEquals(1.0, p.of(6));
        assertEquals(1.5, p.of(7));
        assertEquals(1.5, p.of(12));
        UpgradeCatalog catalog = new UpgradeCatalog(List.of(SIZE));
        assertEquals(0, UpgradeRules.points(catalog, Map.of(), p));
        assertEquals(0.5 + 0.5 + 1.0, UpgradeRules.points(catalog, Map.of("test_upgrade", 3), p), 1e-9);
        assertEquals(2.0, UpgradeRules.points(catalog, Map.of("test_upgrade", 99), p), 1e-9, "never above the catalog's levels");
        assertEquals(0, UpgradeRules.points(catalog, Map.of("removed_upgrade", 4), p), "unknown ids give nothing");
    }

    @Test
    void storedEffectsAreRaisedNeverLowered() {
        UpgradeCatalog catalog = new UpgradeCatalog(List.of(SIZE));
        assertEquals(-1, UpgradeRules.raiseTo(catalog, Map.of(), UpgradeEffect.ISLAND_SIZE, 3));
        assertEquals(5, UpgradeRules.raiseTo(catalog, Map.of("test_upgrade", 1), UpgradeEffect.ISLAND_SIZE, 3));
        assertEquals(-1, UpgradeRules.raiseTo(catalog, Map.of("test_upgrade", 1), UpgradeEffect.ISLAND_SIZE, 5));
        assertEquals(-1, UpgradeRules.raiseTo(catalog, Map.of("test_upgrade", 1), UpgradeEffect.ISLAND_SIZE, 11),
                "a paid or staff size above the upgrade stays");
        assertEquals(9, UpgradeRules.raiseTo(catalog, Map.of("test_upgrade", 3), UpgradeEffect.ISLAND_SIZE, 7));
    }

    @Test
    void liveEffectValues() throws Exception {
        UpgradeCatalog catalog = UpgradeStoreTest.bundled();
        assertEquals(OptionalInt.empty(), UpgradeRules.spawnerStack(catalog, Map.of()));
        assertEquals(OptionalInt.of(32), UpgradeRules.spawnerStack(catalog, Map.of("spawner_stack_limit", 2)));
        assertEquals(OptionalInt.of(512), UpgradeRules.spawnerStack(catalog, Map.of("spawner_stack_limit", 12)));
        assertEquals(1.0, UpgradeRules.spawnerRate(catalog, Map.of()), 1e-9);
        assertEquals(1.36, UpgradeRules.spawnerRate(catalog, Map.of("spawner_rate", 3)), 1e-9);
        assertEquals(50, UpgradeRules.whole(catalog, Map.of("entity_limits", 1), UpgradeEffect.ENTITY_LIMITS));
        assertEquals(10, UpgradeRules.whole(catalog, Map.of("bank_interest", 10), UpgradeEffect.BANK_INTEREST));
        assertEquals(60, UpgradeRules.bonusPercent(catalog, Map.of("sell_multiplier", 12), UpgradeEffect.SELL_BONUS), 1e-9);
        assertEquals(6, UpgradeRules.bonusPercent(catalog, Map.of("mine_fortune", 3), UpgradeEffect.MINE_FORTUNE), 1e-9);
        assertEquals(0, UpgradeRules.bonusPercent(catalog, Map.of("mine_fortune", 3), UpgradeEffect.LUMBERJACK), 1e-9);
        assertEquals(24, UpgradeRules.bonusPercent(catalog, Map.of("xp_boost", 12), UpgradeEffect.XP_BOOST), 1e-9);
        // member slots count the leader: 4 + leader at the start (5), 8 + leader after 4 levels (owner 242)
        assertEquals(9, UpgradeRules.raiseTo(catalog, Map.of("member_slots", 4), UpgradeEffect.MEMBER_SLOTS, 5));
        assertEquals(9, UpgradeRules.raiseTo(catalog, Map.of("warp_slots", 6), UpgradeEffect.WARP_SLOTS, 3));
    }

    @Test
    void keysFitTheLedgerColumns() {
        String longest = "abcdefghijklmnopqrstuvwxyz_01234";
        assertEquals(32, longest.length());
        String key = UpgradeRules.idempotencyKey(Integer.MAX_VALUE, UUID.randomUUID(), longest, UpgradeCatalog.MAX_LEVELS);
        assertTrue(key.length() <= 96, key);
        assertTrue(UpgradeRules.reference(longest, UpgradeCatalog.MAX_LEVELS).length() <= 64);
        UUID island = UUID.randomUUID();
        assertEquals(UpgradeRules.idempotencyKey(3, island, "island_size", 2), UpgradeRules.idempotencyKey(3, island, "island_size", 2));
        assertFalse(UpgradeRules.idempotencyKey(3, island, "island_size", 2).equals(UpgradeRules.idempotencyKey(4, island, "island_size", 2)),
                "a new season may buy the level again");
        assertTrue(net.skybornmc.skyblock.common.economy.TxReason.isValid(UpgradeRules.LEDGER_REASON));
    }

    @Test
    void romanNumerals() {
        assertEquals("I", UpgradeRules.roman(1));
        assertEquals("IV", UpgradeRules.roman(4));
        assertEquals("IX", UpgradeRules.roman(9));
        assertEquals("XII", UpgradeRules.roman(12));
        assertEquals("XXX", UpgradeRules.roman(30));
        assertEquals("40", UpgradeRules.roman(40));
    }
}
