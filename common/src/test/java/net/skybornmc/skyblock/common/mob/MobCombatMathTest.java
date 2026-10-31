package net.skybornmc.skyblock.common.mob;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobCombatMathTest {

    @Test
    void armorAndShield() {
        assertEquals(10, MobCombatMath.playerHitOnMob(10, 0, false, 0.8), 1e-9);
        assertEquals(7.5, MobCombatMath.playerHitOnMob(10, 25, false, 0.8), 1e-9);
        assertEquals(4, MobCombatMath.playerHitOnMob(10, 90, false, 0.8), 1e-9, "armor never above 60%");
        assertEquals(2, MobCombatMath.playerHitOnMob(10, 0, true, 0.8), 1e-9);
        assertEquals(1.5, MobCombatMath.playerHitOnMob(10, 25, true, 0.8), 1e-9);
        assertEquals(0.5, MobCombatMath.playerHitOnMob(10, 0, true, 1.0), 1e-9, "a shield never makes a mob immune");
        assertEquals(0, MobCombatMath.playerHitOnMob(-3, 0, false, 0));
    }

    @Test
    void meleeCooldown() {
        assertTrue(MobCombatMath.meleeAllowed(Long.MIN_VALUE, 5, 20), "first hit");
        assertTrue(MobCombatMath.meleeAllowed(100, 101, 0), "vanilla");
        assertFalse(MobCombatMath.meleeAllowed(100, 119, 20));
        assertTrue(MobCombatMath.meleeAllowed(100, 120, 20));
        assertEquals(12, MobCombatMath.scaledCooldown(20, 0.6));
        assertEquals(1, MobCombatMath.scaledCooldown(1, 0.2));
        assertEquals(0, MobCombatMath.scaledCooldown(0, 0.6));
        assertEquals(20, MobCombatMath.scaledCooldown(20, 5), "never slower");
    }

    @Test
    void enrageOnceBelowTheThreshold() {
        assertFalse(MobCombatMath.enrageDue(40, 100, 0.3, false));
        assertTrue(MobCombatMath.enrageDue(30, 100, 0.3, false));
        assertFalse(MobCombatMath.enrageDue(10, 100, 0.3, true), "only once");
        assertFalse(MobCombatMath.enrageDue(0, 100, 0.3, false), "a dead mob doesn't rage");
    }

    @Test
    void ringHasNoHolesOrDuplicatesAndStaysOnTheRadius() {
        for (double r : new double[]{1, 1.5, 2, 3, 4, 8, 12}) {
            List<int[]> ring = MobCombatMath.ring(r);
            Set<Long> seen = new HashSet<>();
            for (int[] o : ring) {
                assertTrue(seen.add(((long) o[0] << 32) ^ (o[1] & 0xffffffffL)), "duplicate at r=" + r);
                double d = Math.hypot(o[0], o[1]);
                assertTrue(d >= r - 0.5 - 1e-9 && d < r + 0.5, "r=" + r + " d=" + d);
            }
            // every direction crosses the ring (8 compass points)
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int k = (int) Math.floor(r);
                assertTrue(seen.contains(((long) (dir[0] * k) << 32) ^ ((dir[1] * k) & 0xffffffffL)), "r=" + r);
            }
        }
    }

    @Test
    void laneGoesStraightAway() {
        List<int[]> lane = MobCombatMath.lane(1, 0, 5);
        assertEquals(5, lane.size());
        for (int i = 0; i < 5; i++) {
            assertArrayEquals(new int[]{i + 1, 0}, lane.get(i));
        }
        assertTrue(MobCombatMath.lane(0, 0, 5).isEmpty());
        List<int[]> diagonal = MobCombatMath.lane(1, 1, 4);
        int[] last = diagonal.getLast();
        assertEquals(3, last[0]);
        assertEquals(3, last[1]);
    }

    @Test
    void insideAndSegmentDistance() {
        assertTrue(MobCombatMath.inside(3, 4, 5));
        assertFalse(MobCombatMath.inside(3, 4.1, 5));
        assertEquals(1, MobCombatMath.distanceToSegment(5, 1, 0, 0, 10, 0), 1e-9);
        assertEquals(5, MobCombatMath.distanceToSegment(-3, 4, 0, 0, 10, 0), 1e-9, "behind the start");
        assertEquals(2, MobCombatMath.distanceToSegment(2, 0, 0, 0, 0, 0), 1e-9, "zero-length lane");
    }

    @Test
    void fanIsCenteredAndEven() {
        assertArrayEquals(new double[]{-20, -10, 0, 10, 20}, MobCombatMath.fan(5, 40), 1e-9);
        assertArrayEquals(new double[]{0}, MobCombatMath.fan(1, 40), 1e-9);
        assertArrayEquals(new double[]{-15, 15}, MobCombatMath.fan(2, 30), 1e-9);
    }
}
