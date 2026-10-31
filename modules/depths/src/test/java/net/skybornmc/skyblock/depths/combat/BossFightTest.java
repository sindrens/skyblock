package net.skybornmc.skyblock.depths.combat;

import net.skybornmc.skyblock.depths.zones.Aspect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REVIEW3 R3-10: mini-boss eligibility, health scaling and the one-time payout. */
class BossFightTest {

    private static final long WINDOW = 90_000;
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final UUID D = new UUID(0, 4);
    private static final UUID E = new UUID(0, 5);

    // ------------------------------------------------------------------ scaling math

    @Test
    void healthGrows25PercentPerExtraFighterUpTo10() {
        assertEquals(1.0, BossFight.scale(0, 25, 10), 1e-9);
        assertEquals(1.0, BossFight.scale(1, 25, 10), 1e-9, "solo: no scaling");
        assertEquals(1.25, BossFight.scale(2, 25, 10), 1e-9);
        assertEquals(2.0, BossFight.scale(5, 25, 10), 1e-9);
        assertEquals(3.5, BossFight.scale(11, 25, 10), 1e-9, "10 extra = +250%");
        assertEquals(3.5, BossFight.scale(40, 25, 10), 1e-9, "never more than 10 extra");
        assertEquals(1.0, BossFight.scale(5, -10, 10), 1e-9);
        assertEquals(1.0, BossFight.scale(5, 25, 0), 1e-9);
    }

    @Test
    void rescalingKeepsTheHealthShare() {
        assertEquals(250, BossFight.rescaled(100, 200, 500), 1e-9, "half health stays half");
        assertEquals(500, BossFight.rescaled(200, 200, 500), 1e-9);
        assertEquals(40, BossFight.rescaled(100, 500, 200), 1e-9, "shrinking keeps the share too");
        assertTrue(BossFight.rescaled(0.001, 1000, 100) > 0, "a living boss never drops to 0 by scaling");
    }

    @Test
    void activeFightersNeedARecentHitAndNoAfkAndTheIpCap() {
        BossFight f = new BossFight(1000);
        f.hit(A, "1.1.1.1", 10, 0);
        f.hit(B, "1.1.1.1", 20, 0);
        f.hit(C, "1.1.1.1", 30, 0);
        f.hit(D, "1.1.1.1", 40, 0);
        f.hit(E, "2.2.2.2", 1, 0);
        assertEquals(List.of(D, C, B, E), f.active(1_000, WINDOW, 3, id -> false), "4th account on one IP doesn't count");
        assertEquals(List.of(D, C, B), f.active(1_000, WINDOW, 3, id -> id.equals(E)), "AFK doesn't count");
        assertEquals(List.of(), f.active(WINDOW + 1, WINDOW, 3, id -> false), "nobody hit in the last 90 s");
    }

    // ------------------------------------------------------------------ eligibility

    @Test
    void tenPercentOfTheDamageIsNeeded() {
        BossFight f = new BossFight(1000);
        f.hit(A, "1", 800, 0);
        f.hit(B, "2", 100, 0);
        f.hit(C, "3", 99, 0);
        assertEquals(List.of(A, B), f.eligible(1_000, 1000, 0.1, WINDOW, 3, id -> false));
    }

    @Test
    void theKillerIsNotEligibleWithoutTheShare() {
        BossFight f = new BossFight(1000);
        f.hit(A, "1", 995, 0);
        f.hit(B, "2", 5, 0); // last hit, only 0.5%
        assertEquals(List.of(A), f.eligible(1_000, 1000, 0.1, WINDOW, 3, id -> false));
    }

    @Test
    void oldHitsAfkAndExtraAccountsGetNothing() {
        BossFight f = new BossFight(1000);
        f.hit(A, "same", 300, 0);
        f.hit(B, "same", 250, 100_000);
        f.hit(C, "same", 200, 100_000);
        f.hit(D, "same", 150, 100_000);
        f.hit(E, "other", 100, 100_000);
        long now = 100_000;
        // A's last hit is 100 s old; B, C, D share an IP (3 allowed); E is AFK.
        assertEquals(List.of(B, C, D), f.eligible(now, 1000, 0.1, WINDOW, 3, id -> id.equals(E)));
        assertEquals(List.of(B, C), f.eligible(now, 1000, 0.1, WINDOW, 2, id -> id.equals(E)), "cap keeps the most damage");
        assertEquals(List.of(A, B, C, D, E), f.eligible(now, 1000, 0.1, 0, 0, null), "0 = no window and no IP cap");
    }

    @Test
    void scaledHealthRaisesTheShareNeeded() {
        BossFight f = new BossFight(1000);
        f.hit(A, "1", 2400, 0);
        f.hit(B, "2", 150, 0); // 10% of 1000 but not of the scaled 2,500
        assertEquals(List.of(A), f.eligible(1_000, 2500, 0.1, WINDOW, 3, id -> false));
    }

    @Test
    void rewardsArePaidExactlyOnce() {
        BossFight f = new BossFight(100);
        f.hit(A, "1", 60, 0);
        f.hit(B, "2", 40, 0);
        assertFalse(f.paid());
        assertEquals(Set.of(A, B), Set.copyOf(f.claim(1_000, 100, 0.1, WINDOW, 3, id -> false)));
        assertTrue(f.paid());
        assertEquals(List.of(), f.claim(1_000, 100, 0.1, WINDOW, 3, id -> false), "a second death event pays nobody");
    }

    @Test
    void badInputIsIgnored() {
        BossFight f = new BossFight(-5);
        assertEquals(1, f.baseHealth(), 1e-9);
        f.hit(null, "1", 10, 0);
        f.hit(A, "1", Double.NaN, 0);
        f.hit(A, "1", -3, 0);
        assertEquals(0, f.total(), 1e-9);
        assertEquals(List.of(), f.eligible(0, 1, 0.1, WINDOW, 3, id -> false), "no damage, no reward");
        f.scale(Double.NaN);
        assertEquals(1.0, f.scale(), 1e-9);
    }

    // ------------------------------------------------------------------ R3-24

    @Test
    void combatXpOnlyInsideCombatZones() {
        assertTrue(CombatZones.paysCombatXp(Aspect.COMBAT));
        assertFalse(CombatZones.paysCombatXp(Aspect.MINE));
        assertFalse(CombatZones.paysCombatXp(Aspect.FOREST));
        assertFalse(CombatZones.paysCombatXp(null), "outside every zone (gate, paths, islands)");
    }
}
