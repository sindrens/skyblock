package net.skybornmc.skyblock.auction;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure rules: fee/tax math, durations, anti-abuse checks, slot permissions, blocked ids, categories. */
class LogicTest {

    private final AuctionConfig config = new AuctionConfig();

    @Test
    void feeIsOnePercentAndTaxFivePercentRoundedDown() {
        assertEquals(10_000, Pricing.fee(config, 1_000_000));
        assertEquals(50_000, Pricing.tax(config, 1_000_000));
        assertEquals(950_000, Pricing.net(config, 1_000_000));
        assertEquals(1, Pricing.fee(config, 150));      // 1.5 -> 1
        assertEquals(1, Pricing.fee(config, 10));       // 0.1 -> raised to fee.min 1
        assertEquals(7, Pricing.tax(config, 159));      // 7.95 -> 7
        assertEquals(0, Pricing.tax(config, 10));       // 0.5 -> 0, tax.min 0
        assertEquals(10_000_000_000L, Pricing.fee(config, 1_000_000_000_000L)); // no overflow at the max price
    }

    @Test
    void feeNeverExceedsThePrice() {
        config.feeMin = 1_000;
        assertEquals(10, Pricing.fee(config, 10));
        config.feePercent = 0;
        config.feeMin = 0;
        assertEquals(0, Pricing.fee(config, 1_000));
    }

    @Test
    void durationsComeFromTheConfigList() {
        assertEquals(List.of("12h", "24h", "48h"), Pricing.durations(config));
        assertEquals("24h", Pricing.defaultDuration(config));
        assertEquals(Duration.ofHours(48), Pricing.offered(config, "48H"));
        assertNull(Pricing.offered(config, "72h"));
        config.durations = List.of("nonsense", "30d", "6h");
        assertEquals(List.of("6h"), Pricing.durations(config));
        assertEquals("6h", Pricing.defaultDuration(config));
    }

    @Test
    void priceRange() {
        assertEquals(ListingRules.Problem.PRICE_TOO_LOW, ListingRules.price(config, 9).orElseThrow().problem());
        assertTrue(ListingRules.price(config, 10).isEmpty());
        assertEquals(ListingRules.Problem.PRICE_TOO_HIGH, ListingRules.price(config, config.priceMax + 1).orElseThrow().problem());
    }

    @Test
    void newAccountsAreKeptOutForThirtyMinutes() {
        long minute = 60_000L;
        var r = ListingRules.account(config, 10 * minute, true).orElseThrow();
        assertEquals(ListingRules.Problem.NEW_ACCOUNT, r.problem());
        assertEquals(20 * minute, r.value());
        assertTrue(ListingRules.account(config, 30 * minute, true).isEmpty());
        assertFalse(ListingRules.account(config, 10 * minute, false).isEmpty());
        config.newBlockBuying = false;
        assertTrue(ListingRules.account(config, 10 * minute, false).isEmpty());
    }

    @Test
    void slotsCooldownAndRelistLimit() {
        long now = 1_000_000;
        assertEquals(ListingRules.Problem.NO_SLOTS,
                ListingRules.history(config, new ListingRules.Stats(5, 0, 0), 5, now).orElseThrow().problem());
        var cd = ListingRules.history(config, new ListingRules.Stats(1, now - 4_000, 0), 5, now).orElseThrow();
        assertEquals(ListingRules.Problem.COOLDOWN, cd.problem());
        assertEquals(6_000, cd.value());
        assertEquals(ListingRules.Problem.RELIST_LIMIT,
                ListingRules.history(config, new ListingRules.Stats(1, now - 60_000, 3), 5, now).orElseThrow().problem());
        assertTrue(ListingRules.history(config, new ListingRules.Stats(4, now - 60_000, 2), 5, now).isEmpty());
        config.relistMaxSameItem = 0;
        assertTrue(ListingRules.history(config, new ListingRules.Stats(1, now - 60_000, 99), 5, now).isEmpty());
    }

    @Test
    void slotPermissionsRaiseTheLimitUpToTheCap() {
        assertEquals(5, ListingRules.slots(config, List.of()));
        assertEquals(8, ListingRules.slots(config, List.of("skyblock.auction.slots.8", "skyblock.auction.slots.6", "other")));
        assertEquals(5, ListingRules.slots(config, List.of("skyblock.auction.slots.3", "skyblock.auction.slots.*")));
        assertEquals(54, ListingRules.slots(config, List.of("skyblock.auction.slots.500")));
    }

    @Test
    void blockedIdsMatchExactlyOrByPrefix() {
        assertTrue(ListingRules.idBlocked("starter_pickaxe", config.blockedIds));
        assertTrue(ListingRules.idBlocked("TOOL_STARTER_AXE", config.blockedIds));
        assertFalse(ListingRules.idBlocked("tool_iron_pickaxe", config.blockedIds));
        assertFalse(ListingRules.idBlocked(null, config.blockedIds));
        assertTrue(ListingRules.idBlocked("note", List.of("note")));
        assertFalse(ListingRules.idBlocked("notes", List.of("note")));
    }

    @Test
    void auditCarriesTheItemDataUnlessItIsHuge() {
        ItemSnapshot small = new ItemSnapshot(new byte[]{1, 2, 3}, "diamond", null, "DIAMOND", 2, "Diamond", Category.MATERIALS);
        assertEquals("AQID", small.audit().get("data"));
        ItemSnapshot big = new ItemSnapshot(new byte[ItemSnapshot.AUDIT_DATA_MAX_BYTES + 1], "book", null, "WRITTEN_BOOK", 1, "Book",
                Category.SPECIAL);
        assertNull(big.audit().get("data"));
        assertEquals(ItemSnapshot.AUDIT_DATA_MAX_BYTES + 1, big.audit().get("data_bytes"));
    }

    @Test
    void categories() {
        assertEquals(Category.TOOLS, Category.categorize("DIAMOND_PICKAXE", null, false, false));
        assertEquals(Category.TOOLS, Category.categorize("BOW", null, false, false));
        assertEquals(Category.ARMOR, Category.categorize("NETHERITE_CHESTPLATE", null, false, false));
        assertEquals(Category.CONSUMABLES, Category.categorize("COOKED_BEEF", null, false, true));
        assertEquals(Category.CONSUMABLES, Category.categorize("SPLASH_POTION", null, false, false));
        assertEquals(Category.BLOCKS, Category.categorize("STONE", null, true, false));
        assertEquals(Category.MATERIALS, Category.categorize("IRON_INGOT", null, false, false));
        assertEquals(Category.TOOLS, Category.categorize("NAME_TAG", "tool", false, false));
        assertEquals(Category.COSMETICS, Category.categorize("NAME_TAG", "cosmetic", false, false));
        assertEquals(Category.SPECIAL, Category.categorize("PAPER", "crate-key", false, false));
    }
}
