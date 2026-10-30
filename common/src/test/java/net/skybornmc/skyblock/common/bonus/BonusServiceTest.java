package net.skybornmc.skyblock.common.bonus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bonus families: values add up, the cap applies once; sell always uses the economy's cap (+300%, #46). */
class BonusServiceTest {

    private static final BonusContext CTX = BonusContext.of(null);

    @Test
    void mathAddsThenCaps() {
        assertEquals(85, BonusMath.sum(List.of(10.0, 25.0, 50.0)));
        assertEquals(10, BonusMath.sum(List.of(10.0, Double.NaN, Double.POSITIVE_INFINITY)));
        assertEquals(300, BonusMath.capped(450, 300));
        assertEquals(-100, BonusMath.capped(-250, 300));
        assertEquals(0, BonusMath.capped(50, -5), "a negative cap counts as 0");
        assertTrue(BonusMath.isCapped(450, 300));
        assertFalse(BonusMath.isCapped(300, 300));
        assertEquals(4.0, BonusMath.multiplier(999, 300));
    }

    @Test
    void sellCapFollowsTheEconomy() {
        AtomicReference<Double> economyCap = new AtomicReference<>(300.0);
        DefaultBonusService bonus = new DefaultBonusService(economyCap::get, null);
        bonus.register("rank", (f, c) -> f == BonusFamily.SELL ? 150 : 0);
        bonus.register("booster", (f, c) -> f == BonusFamily.SELL ? 200 : 0);
        assertEquals(350, bonus.total(BonusFamily.SELL, CTX));
        assertEquals(300, bonus.capped(BonusFamily.SELL, CTX));
        assertEquals(4_000, bonus.apply(BonusFamily.SELL, CTX, 1_000), "+300% → ×4");
        economyCap.set(100.0);
        assertEquals(2_000, bonus.apply(BonusFamily.SELL, CTX, 1_000), "an economy.yml change counts at once");
        bonus.caps(Map.of(BonusFamily.SELL, 1_000.0));
        assertEquals(100, bonus.cap(BonusFamily.SELL), "caps() can't raise the sell cap");
    }

    @Test
    void brokenSourcesAreSkippedAndCanBeRemoved() {
        DefaultBonusService bonus = new DefaultBonusService(() -> 300, null);
        bonus.register("good", (f, c) -> f == BonusFamily.CRYSTALS ? 40 : 0);
        bonus.register("broken", (f, c) -> {
            throw new IllegalStateException("boom");
        });
        assertEquals(40, bonus.total(BonusFamily.CRYSTALS, CTX));
        assertEquals(List.of(new BonusPart("good", 40)), bonus.breakdown(BonusFamily.CRYSTALS, CTX));
        bonus.unregister("good");
        assertEquals(0, bonus.total(BonusFamily.CRYSTALS, CTX));
        assertEquals(1.0, bonus.multiplier(BonusFamily.CRYSTALS, CTX));
    }

    @Test
    void defaultCapsFromThePlan() {
        DefaultBonusService bonus = new DefaultBonusService(() -> 300, null);
        assertEquals(200, bonus.cap(BonusFamily.CRYSTALS));
        assertEquals(200, bonus.cap(BonusFamily.EXTRA_DROPS));
        assertEquals(300, bonus.cap(BonusFamily.SKILL_XP));
        assertEquals(100, bonus.cap(BonusFamily.TOOL_XP));
        assertEquals(150, bonus.cap(BonusFamily.RARE_FINDS));
        assertEquals(200, bonus.cap(BonusFamily.MOB_DAMAGE));
        assertEquals(60, bonus.cap(BonusFamily.DAMAGE_REDUCTION));
        assertEquals(10, bonus.cap(BonusFamily.HEALTH));
        assertEquals(BonusFamily.EXTRA_DROPS, BonusFamily.byId("extra-drops").orElseThrow());
    }
}
