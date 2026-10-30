package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.DefaultBonusService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bonus counting (WARDROBE §3.4, WD17), the level multiplier (WD14) and the family caps through the BonusService (§2.5). */
class SetBonusesTest {

    private static final SetRegistry R = Fixtures.registry();

    private static SetBonuses.Result worn(SetBonuses.Worn... pieces) {
        return SetBonuses.compute(R, List.of(pieces), level -> SetBonuses.multiplier(level, false, 1.0, 50, 2.0));
    }

    private static SetBonuses.Worn w(String set) {
        return new SetBonuses.Worn(set, null, 0);
    }

    @Test
    void onePieceGivesNothingTwoGiveTheTwoPieceBonus() {
        assertEquals(0, worn(w("delver")).value(BonusFamily.SPEED, "mining"));
        SetBonuses.Result two = worn(w("delver"), w("delver"));
        assertEquals(4, two.value(BonusFamily.SPEED, "mining"), 1e-9);
        assertEquals(0, two.value(BonusFamily.CRYSTALS, "mining"), "4-piece bonus needs 4");
        assertEquals(0, two.value(BonusFamily.SPEED, "foraging"), "only where the activity happens");
        assertEquals(0, two.value(BonusFamily.SPEED, null), "a context without activity gets no scoped bonus");
    }

    @Test
    void fourPiecesAddTheFourPieceBonus() {
        SetBonuses.Result four = worn(w("deepforge"), w("deepforge"), w("deepforge"), w("deepforge"));
        assertEquals(10, four.value(BonusFamily.EXTRA_DROPS, "mining"), 1e-9);
        assertEquals(16, four.value(BonusFamily.CRYSTALS, "mining"), 1e-9);
        assertEquals(Map.of("deepforge", 4), four.pieces());
    }

    @Test
    void twoPlusTwoGivesBothTwoPieceBonuses() {
        SetBonuses.Result mix = worn(w("delver"), w("delver"), w("coinmaster"), w("coinmaster"));
        assertEquals(4, mix.value(BonusFamily.SPEED, "mining"), 1e-9);
        assertEquals(10, mix.value(BonusFamily.SELL, "sell"), 1e-9);
        assertEquals(10, mix.value(BonusFamily.SELL, null), "unscoped bonuses count everywhere");
        assertEquals(0, mix.value(BonusFamily.CRYSTALS, "mining"));
    }

    @Test
    void mergedEntriesCountForBothSetsAndTheLowestLevelSetsTheMultiplier() {
        IntToDouble levels = level -> SetBonuses.multiplier(level, true, 1.0, 50, 2.0);
        // 2 merged Deepforge·Coinmaster pieces at Lv 3 + 2 pure Deepforge pieces at Lv 50.
        SetBonuses.Result r = SetBonuses.compute(R, List.of(
                new SetBonuses.Worn("deepforge", "coinmaster", 3), new SetBonuses.Worn("deepforge", "coinmaster", 3),
                new SetBonuses.Worn("deepforge", null, 50), new SetBonuses.Worn("deepforge", null, 50)), levels::of);
        assertEquals(4, r.pieces().get("deepforge"));
        assertEquals(2, r.pieces().get("coinmaster"));
        assertEquals(16 * 0.12, r.value(BonusFamily.CRYSTALS, "mining"), 1e-9, "Deepforge 4-piece at Lv 3 (0.12x)");
        assertEquals(10 * 0.12, r.value(BonusFamily.SELL, "sell"), 1e-9, "Coinmaster 2-piece from the merged pieces");
    }

    @FunctionalInterface
    interface IntToDouble {
        double of(int level);
    }

    @Test
    void levelMultiplier() {
        assertEquals(0, SetBonuses.multiplier(0, true, 1, 50, 2), 1e-9);
        assertEquals(0.04, SetBonuses.multiplier(1, true, 1, 50, 2), 1e-9);
        assertEquals(1, SetBonuses.multiplier(25, true, 1, 50, 2), 1e-9);
        assertEquals(2, SetBonuses.multiplier(50, true, 1, 50, 2), 1e-9);
        assertEquals(2, SetBonuses.multiplier(80, true, 1, 50, 2), 1e-9, "clamped at max");
        assertEquals(1, SetBonuses.multiplier(0, false, 1, 50, 2), 1e-9, "W1: levels off = fixed 1x");
        assertTrue(worn(new SetBonuses.Worn("delver", null, 0), new SetBonuses.Worn("delver", null, 0)).value(BonusFamily.SPEED, "mining") > 0);
        SetBonuses.Result zero = SetBonuses.compute(R, List.of(w("delver"), w("delver")), level -> SetBonuses.multiplier(level, true, 1, 50, 2));
        assertTrue(zero.lines().isEmpty(), "level 0 = 0x gives nothing (W2)");
    }

    @Test
    void unknownSetsGiveNothing() {
        assertTrue(worn(w("nope"), w("nope"), w("nope"), w("nope")).lines().isEmpty());
    }

    @Test
    void familyCapsApplyOnceWithOtherSources() {
        DefaultBonusService bonus = new DefaultBonusService(() -> 300, null);
        SetBonuses.Result coin = worn(w("coinmaster"), w("coinmaster"), w("coinmaster"), w("coinmaster"));
        bonus.register("wardrobe", (f, c) -> coin.value(f, c.activity()));
        BonusContext sell = new BonusContext(null, null, null, "sell");
        assertEquals(25, bonus.total(BonusFamily.SELL, sell), 1e-9);
        assertFalse(bonus.isCapped(BonusFamily.SELL, sell));
        bonus.register("booster", (f, c) -> f == BonusFamily.SELL ? 290 : 0);
        assertEquals(315, bonus.total(BonusFamily.SELL, sell), 1e-9);
        assertEquals(300, bonus.capped(BonusFamily.SELL, sell), 1e-9, "the +300% sell cap (#46)");
        assertTrue(bonus.isCapped(BonusFamily.SELL, sell));
        assertEquals(4_000, bonus.apply(BonusFamily.SELL, sell, 1_000));
    }

    @Test
    void newFamiliesHaveTheirCaps() {
        DefaultBonusService bonus = new DefaultBonusService(() -> 300, null);
        assertEquals(100, bonus.cap(BonusFamily.CROP_GROWTH), 1e-9);
        assertEquals(60, bonus.cap(BonusFamily.CONQUEST), 1e-9);
        SetBonuses.Result conq = worn(w("conqueror"), w("conqueror"), w("conqueror"), w("conqueror"),
                w("banneret"), w("banneret"));
        bonus.register("wardrobe", (f, c) -> conq.value(f, c.activity()));
        bonus.register("perk", (f, c) -> f == BonusFamily.CONQUEST && "envoy_luck".equals(c.activity()) ? 50 : 0);
        BonusContext envoy = new BonusContext(null, null, null, "envoy_luck");
        assertEquals(15 + 10 + 50, bonus.total(BonusFamily.CONQUEST, envoy), 1e-9);
        assertEquals(60, bonus.capped(BonusFamily.CONQUEST, envoy), 1e-9);
        assertEquals(25, conq.value(BonusFamily.CONQUEST, "death_loss"), 1e-9, "per stat: death loss is its own activity");
    }
}
