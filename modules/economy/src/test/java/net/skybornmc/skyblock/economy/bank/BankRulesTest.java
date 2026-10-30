package net.skybornmc.skyblock.economy.bank;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.economy.FakeProfiles;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bank access (P4, island roles), role limits, tier tables and the menu helpers (M3.3). */
class BankRulesTest {

    private final FakeProfiles profiles = new FakeProfiles();
    private final UUID steve = UUID.randomUUID();
    private final UUID steve2 = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();

    BankRulesTest() {
        profiles.add(steve, steve);
        profiles.add(steve, steve2);
        profiles.add(alex, alex);
    }

    @Test
    void personalBankOnlyFromTheSameProfile() {
        assertEquals(BankRules.Access.OK, BankRules.personal(steve, steve, profiles));
        assertEquals(BankRules.Access.OK, BankRules.personal(steve2, steve2, profiles));
        assertEquals(BankRules.Access.OWN_PROFILE, BankRules.personal(steve, steve2, profiles), "P4: never into another own profile");
        assertEquals(BankRules.Access.OWN_PROFILE, BankRules.personal(steve2, steve, profiles));
        assertEquals(BankRules.Access.NOT_YOURS, BankRules.personal(alex, steve, profiles));
        assertEquals(BankRules.Access.NOT_YOURS, BankRules.personal(null, steve, profiles));
    }

    @Test
    void islandBankNeedsMembershipOfThatIslandAndThePermission() {
        UUID island = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertEquals(BankRules.Access.OK, BankRules.island(island, island, Role.MEMBER, true));
        assertEquals(BankRules.Access.NO_PERMISSION, BankRules.island(island, island, Role.MEMBER, false));
        assertEquals(BankRules.Access.NOT_MEMBER, BankRules.island(island, island, Role.COOP, true), "coop never (REVIEW A4)");
        assertEquals(BankRules.Access.NOT_MEMBER, BankRules.island(other, island, Role.LEADER, true),
                "P4: the purse's profile is on another island (e.g. another own profile)");
        assertEquals(BankRules.Access.NOT_MEMBER, BankRules.island(null, island, null, true));
    }

    @Test
    void dailyLimitPerRole() {
        BankConfig config = new BankConfig();
        long capacity = 1_000_000;
        assertEquals(Long.MAX_VALUE, BankRules.remainingToday(capacity, config.limitPercent(Role.LEADER), 900_000));
        assertEquals(Long.MAX_VALUE, BankRules.remainingToday(capacity, config.limitPercent(Role.ADMIN), 900_000));
        assertEquals(250_000, BankRules.remainingToday(capacity, config.limitPercent(Role.MOD), 0));
        assertEquals(50_000, BankRules.remainingToday(capacity, config.limitPercent(Role.MEMBER), 50_000));
        assertEquals(0, BankRules.remainingToday(capacity, config.limitPercent(Role.MEMBER), 400_000), "never negative");
        assertEquals(0, config.limitPercent(Role.COOP));
        assertEquals(0, config.limitPercent(null));
    }

    @Test
    void maxAmounts() {
        assertEquals(40, BankRules.maxDeposit(1_000, 100, 60), "bank room");
        assertEquals(30, BankRules.maxDeposit(30, 100, 60), "purse");
        assertEquals(0, BankRules.maxDeposit(30, 100, 150));
        assertEquals(500, BankRules.maxWithdraw(500, Long.MAX_VALUE));
        assertEquals(100, BankRules.maxWithdraw(500, 100));
    }

    @Test
    void shippedTablesMatchTheBalanceSheet() {
        BankTiers tiers = BankTiers.parse(new BankConfig());
        assertTrue(tiers.problems().isEmpty(), tiers.problems().toString());
        assertEquals(10, tiers.maxTier());
        assertEquals(new BankTiers.PersonalTier(1, 100_000, 0, 150), tiers.tier(1));
        assertEquals(new BankTiers.PersonalTier(4, 12_500_000, 250_000, 4_915), tiers.tier(4));
        assertEquals(195_312_500_000L, tiers.tier(10).capacity());
        for (BankTiers.PersonalTier t : tiers.personal().subList(1, tiers.maxTier())) {
            assertEquals(t.capacity() / 50, t.cost(), "unlock cost = 2% of capacity (owner #42): tier " + t.tier());
        }
        assertEquals(11, tiers.island().size());
        assertEquals(1_000_000, tiers.islandCapacity(0));
        assertEquals(2_500, tiers.islandCap(0));
        assertEquals(1_048_576_000_000L, tiers.islandCapacity(10));
        assertEquals(147_622_500, tiers.islandCap(10));
        assertEquals(tiers.islandCapacity(10), tiers.islandCapacity(99), "levels above the table are clamped");
        assertEquals(tiers.tier(1), tiers.tier(0));
    }

    @Test
    void brokenTablesFallBackToTheDefaults() {
        BankConfig config = new BankConfig();
        config.personalTiers = List.of("100k 0 150", "50k 10k 480");
        config.islandLevels = List.of("lots of money");
        BankTiers tiers = BankTiers.parse(config);
        assertEquals(2, tiers.problems().size());
        assertEquals(10, tiers.maxTier(), "defaults used");
        config.personalTiers = List.of("100k 5k 150");
        assertEquals(1, BankTiers.parse(config).problems().stream().filter(p -> p.contains("free")).count(), "tier 1 must be free");
        config.personalTiers = List.of("100k 0 150", "500k 0 480");
        assertTrue(BankTiers.parse(config).problems().size() > 0, "only tier 1 may be free");
    }

    @Test
    void pickerStepsFitTheAmount() {
        assertArrayEquals(new long[]{10_000, 100_000, 1_000_000}, BankMenu.steps(1_234_567));
        assertArrayEquals(new long[]{1, 10, 100}, BankMenu.steps(50));
        assertArrayEquals(new long[]{1, 10, 100}, BankMenu.steps(0));
        assertEquals("0.15", BankMenu.percent(0.15));
        assertEquals("2", BankMenu.percent(2.0));
    }

    @Test
    void menuLayoutUsesEverySlotOnceInsideTheFrame() {
        assertEquals(BankMenu.CONTENT.size(), BankMenu.CONTENT.stream().distinct().count());
        for (int slot : BankMenu.CONTENT) {
            int col = slot % 9;
            assertTrue(slot > 8 && slot < 45 && col != 0 && col != 8, "slot " + slot);
            for (int divider : BankMenu.DIVIDER) {
                assertTrue(slot != divider, "slot " + slot + " is the divider");
            }
        }
    }
}
