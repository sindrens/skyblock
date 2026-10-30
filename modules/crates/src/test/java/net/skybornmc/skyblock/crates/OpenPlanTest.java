package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenPlanTest {

    private static OpenPlan.Plan plan(CrateCatalog.Crate crate, CrateMath.KeyUse keys, OpenPlan.Context ctx) {
        return OpenPlan.build(crate, keys, 0, ctx, new Random(1), UUID::randomUUID);
    }

    @Test
    void pouchesFollowTheTier() {
        CrateCatalog.Crate money = Fixtures.crate(0, Fixtures.reward("m", RewardType.MONEY, 1, false));
        OpenPlan.Plan p = plan(money, new CrateMath.KeyUse(1, 0), Fixtures.context(3, k -> false));
        assertEquals("money", p.grants().getFirst().kind());
        assertEquals(3 * 4_000, p.grants().getFirst().amount(), "3 minutes at tier 3");
        assertEquals(3 * 4_000, p.total(Currency.MONEY));
        CrateCatalog.Crate crystals = Fixtures.crate(0, Fixtures.reward("c", RewardType.CRYSTALS, 1, false));
        assertEquals(CrateMath.crystals(15, 2, 1.3),
                plan(crystals, new CrateMath.KeyUse(1, 0), Fixtures.context(2, k -> false)).total(Currency.CRYSTALS));
    }

    @Test
    void unavailableAndComingSoonPayTheirCrystalValue() {
        CrateCatalog.Crate crate = Fixtures.crate(0, Fixtures.reward("book", RewardType.ENCHANT_BOOK, 1, false));
        OpenPlan.Context off = new OpenPlan.Context(1, new long[]{1}, 1.3, k -> false, r -> false, pool -> List.of(), t -> List.of());
        OpenPlan.Grant g = plan(crate, new CrateMath.KeyUse(1, 0), off).grants().getFirst();
        assertTrue(g.fallback());
        assertEquals(10, g.amount(), "the reward's fallback base at tier 1");
        assertEquals(Currency.CRYSTALS, g.currency());
        assertTrue(g.inTransaction());
        OpenPlan.Grant soon = plan(Fixtures.crate(0, Fixtures.reward("x", RewardType.COMING_SOON, 1, false)),
                new CrateMath.KeyUse(1, 0), Fixtures.context(1, k -> false)).grants().getFirst();
        assertTrue(soon.fallback());
        OpenPlan.Context noBooks = new OpenPlan.Context(1, new long[]{1}, 1.3, k -> false, r -> true, pool -> List.of(), t -> List.of());
        assertTrue(plan(crate, new CrateMath.KeyUse(1, 0), noBooks).grants().getFirst().fallback(), "empty enchant pool");
    }

    @Test
    void itemsAreHandOutsWithTheirChoice() {
        CrateCatalog.Crate crate = Fixtures.crate(0, Fixtures.reward("book", RewardType.ENCHANT_BOOK, 1, false));
        OpenPlan.Plan p = plan(crate, new CrateMath.KeyUse(0, 1), Fixtures.context(1, k -> false));
        OpenPlan.Grant g = p.grants().getFirst();
        assertFalse(g.inTransaction());
        assertTrue(g.ref().equals("vein_rend:1") || g.ref().equals("swiftness:1"), g.ref());
        assertEquals(List.of(g), p.handouts());
        assertFalse(g.bound(), "opened with a tradeable key");
        OpenPlan.Grant spawner = plan(Fixtures.crate(0, Fixtures.reward("s", RewardType.SPAWNER, 1, false)),
                new CrateMath.KeyUse(1, 0), Fixtures.context(1, k -> false)).grants().getFirst();
        assertEquals("zombie", spawner.ref());
        assertTrue(spawner.bound());
    }

    @Test
    void boundKeysMakeBoundLootAndBoundKeys() {
        CrateCatalog.Reward key = new CrateCatalog.Reward("k", RewardType.KEY, 1, true, "rare", null, 0, 0, 2, "blaze", "common", 1,
                List.of(), 0, "", List.of(), 0);
        OpenPlan.Plan p = plan(Fixtures.crate(0, key), new CrateMath.KeyUse(2, 3), Fixtures.context(1, k -> false));
        assertEquals(5, p.grants().size());
        assertEquals(List.of(true, true, false, false, false), p.grants().stream().map(OpenPlan.Grant::bound).toList());
        assertEquals(List.of(new OpenPlan.KeyGrant("blaze", 4, 6)), p.keyGrants(), "R3-2: keys from bound keys stay bound");
        assertEquals(5, p.topCount());
    }

    @Test
    void ownedCosmeticBecomesCrystals() {
        CrateCatalog.Crate crate = Fixtures.crate(0, Fixtures.cosmetic("tag", RewardType.TAG, 1, false, List.of("spark")));
        OpenPlan.Grant g = plan(crate, new CrateMath.KeyUse(1, 0), Fixtures.context(1, k -> true)).grants().getFirst();
        assertTrue(g.fallback());
        OpenPlan.Grant fresh = plan(crate, new CrateMath.KeyUse(1, 0), Fixtures.context(1, k -> false)).grants().getFirst();
        assertEquals("tag", fresh.kind());
        assertEquals("spark", fresh.ref());
    }

    @Test
    void wholeBundledCratesPlanWithoutGaps() {
        for (CrateCatalog.Crate crate : Fixtures.bundled().crates()) {
            OpenPlan.Plan p = OpenPlan.build(crate, new CrateMath.KeyUse(40, 24), 0, Fixtures.context(5, k -> false), new Random(11),
                    UUID::randomUUID);
            assertEquals(64, p.grants().size(), crate.id());
            assertEquals(64, p.grants().stream().map(OpenPlan.Grant::openId).distinct().count(), "unique open ids");
            for (OpenPlan.Grant g : p.grants()) {
                assertTrue(g.amount() >= 0, crate.id() + "." + g.reward().id());
                assertTrue(g.ref().length() <= 64);
            }
        }
    }
}
