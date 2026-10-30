package net.skybornmc.skyblock.depths.combat;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnMathTest {

    private static final ZoneSpawns ZONE = new ZoneSpawns(1, Map.of("a", 1), 8, 2, 30, null, 0);

    @Test
    void targetGrowsWithPlayersUpToTheCap() {
        assertEquals(0, SpawnMath.target(ZONE, 0), "nothing while empty");
        assertEquals(10, SpawnMath.target(ZONE, 1));
        assertEquals(18, SpawnMath.target(ZONE, 5));
        assertEquals(30, SpawnMath.target(ZONE, 11));
        assertEquals(30, SpawnMath.target(ZONE, 300), "300 players in one zone: still the cap");
        assertEquals(0, SpawnMath.target(null, 5));
    }

    @Test
    void spawnsFillGraduallyAndRespectTheGlobalCap() {
        assertEquals(4, SpawnMath.toSpawn(30, 0, 4, 300), "per-cycle limit");
        assertEquals(2, SpawnMath.toSpawn(30, 28, 4, 300), "only the missing ones");
        assertEquals(0, SpawnMath.toSpawn(30, 31, 4, 300), "over target: no spawns");
        assertEquals(1, SpawnMath.toSpawn(30, 0, 4, 1), "global room");
        assertEquals(0, SpawnMath.toSpawn(30, 0, 4, 0));
        assertEquals(0, SpawnMath.toSpawn(30, 0, 4, -5), "over the global cap after a config change");
        // 10 zones x 300 players: the whole server never passes the global cap.
        int alive = 0;
        int cap = 300;
        for (int cycle = 0; cycle < 50; cycle++) {
            for (int zone = 0; zone < 10; zone++) {
                alive += SpawnMath.toSpawn(SpawnMath.target(ZONE, 300), alive / 10, 4, cap - alive);
            }
        }
        assertEquals(300, alive);
    }

    @Test
    void despawnOnlyAfterTheZoneStayedEmpty() {
        assertFalse(SpawnMath.despawnDue(0, 100_000, 30_000), "not empty");
        assertFalse(SpawnMath.despawnDue(80_000, 100_000, 30_000));
        assertTrue(SpawnMath.despawnDue(70_000, 100_000, 30_000));
        assertTrue(SpawnMath.despawnDue(100_000, 100_000, 0), "0 = at once");
    }

    @Test
    void weightedPickFollowsTheWeights() {
        Map<String, Integer> w = new LinkedHashMap<>();
        w.put("spider", 3);
        w.put("cave", 1);
        Map<String, Integer> seen = new HashMap<>();
        SplittableRandom rng = new SplittableRandom(7);
        for (int i = 0; i < 40_000; i++) {
            seen.merge(SpawnMath.pick(w, rng), 1, Integer::sum);
        }
        assertEquals(30_000, seen.get("spider"), 600);
        assertEquals(10_000, seen.get("cave"), 600);
        assertNull(SpawnMath.pick(Map.of(), rng));
    }

    @Test
    void ambientMath() {
        assertEquals(2, SpawnMath.ambientToSpawn(2, 0, 300));
        assertEquals(0, SpawnMath.ambientToSpawn(2, 2, 300));
        assertEquals(1, SpawnMath.ambientToSpawn(2, 0, 1));
        assertEquals(0, SpawnMath.ambientToSpawn(2, 3, 300));
        assertTrue(SpawnMath.inRing(36, 5, 12));
        assertFalse(SpawnMath.inRing(16, 5, 12));
        assertFalse(SpawnMath.inRing(200, 5, 12));
    }

    @Test
    void miniBossClock() {
        long min = 60_000;
        MiniBossClock c = new MiniBossClock(0, 10 * min);
        assertFalse(c.due(9 * min, true));
        assertFalse(c.due(10 * min, false), "only with players there");
        assertTrue(c.due(10 * min, true));
        c.spawned();
        assertFalse(c.due(30 * min, true), "one at a time");
        assertEquals(0, c.remaining(30 * min));
        c.gone(30 * min, 10 * min);
        assertFalse(c.due(35 * min, true));
        assertEquals(5 * min, c.remaining(35 * min));
        assertTrue(c.due(40 * min, true));
    }
}
