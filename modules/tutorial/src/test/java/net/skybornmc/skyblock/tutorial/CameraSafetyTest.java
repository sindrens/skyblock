package net.skybornmc.skyblock.tutorial;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TUT-FIX: line of sight, input skips, the interruption table, the watchdog and the redirected restore. */
class CameraSafetyTest {

    /** Solid boxes (inclusive block coordinates); everything else open. */
    private static CameraRules.Blocks boxes(int[]... boxes) {
        return (x, y, z) -> {
            for (int[] b : boxes) {
                if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
                    return true;
                }
            }
            return false;
        };
    }

    private static final CameraRules.Blocks OPEN = (x, y, z) -> false;
    private static final CameraRules.Blocks UNLOADED = (x, y, z) -> null;

    // ------------------------------------------------------------------ line of sight

    @Test
    void openShotsPlayAsWritten() {
        assertEquals(CameraRules.Fix.OK, CameraRules.check(new double[]{0, 100, 0}, new double[]{0, 100, -30},
                new double[]{0, 90, -60}, OPEN));
        assertEquals(CameraRules.Fix.OK, CameraRules.check(new double[]{0, 100, 0}, new double[]{0, 100, 0},
                new double[]{0, 90, -60}, OPEN), "still shot");
    }

    @Test
    void aWallOnThePathTurnsTheShotIntoAStillShot() {
        int[] wall = {-5, 90, -16, 5, 120, -14};
        assertEquals(CameraRules.Fix.STILL_FROM, CameraRules.check(new double[]{0.5, 100, 0}, new double[]{0.5, 100, -30},
                new double[]{0.5, 100, -60}, boxes(wall)), "flies through the wall");
        assertEquals(CameraRules.Fix.STILL_TO, CameraRules.check(new double[]{0.5, 100, -15}, new double[]{0.5, 100, -30},
                new double[]{0.5, 100, -60}, boxes(wall)), "starts inside the wall");
        assertEquals(CameraRules.Fix.DROP, CameraRules.check(new double[]{0.5, 100, -15}, new double[]{0.5, 100, -15},
                new double[]{0.5, 100, -60}, boxes(wall)), "a still shot inside the wall");
    }

    @Test
    void aWallRightInFrontOfTheCameraIsBlockedButTheLookedAtThingMayBeSolid() {
        int[] wallAhead = {-5, 90, -3, 5, 120, -3};
        assertEquals(CameraRules.Fix.DROP, CameraRules.check(new double[]{0.5, 100, 0}, new double[]{0.5, 100, 0},
                new double[]{0.5, 100, -40}, boxes(wallAhead)));
        int[] statue = {-3, 90, -43, 3, 120, -37};
        assertEquals(CameraRules.Fix.OK, CameraRules.check(new double[]{0.5, 100, 0}, new double[]{0.5, 100, 0},
                new double[]{0.5, 100, -40}, boxes(statue)), "looking at a statue far away is fine");
    }

    @Test
    void unloadedChunksAreNeverGuessed() {
        assertEquals(CameraRules.Fix.DROP, CameraRules.check(new double[]{0, 100, 0}, new double[]{0, 100, -30},
                new double[]{0, 90, -60}, UNLOADED));
    }

    @Test
    void lineChecksEveryBlockItCrosses() {
        CameraRules.Blocks thin = boxes(new int[]{3, 100, 3, 3, 100, 3});
        assertFalse(CameraRules.clearLine(new double[]{0.5, 100.5, 0.5}, new double[]{6.5, 100.5, 6.5}, thin), "diagonal through one block");
        assertTrue(CameraRules.clearLine(new double[]{0.5, 101.5, 0.5}, new double[]{6.5, 101.5, 6.5}, thin), "one block higher");
    }

    /** The default spawn camera against the spawn builds' bounding boxes (server/builds/spawn_*.yml). */
    @Test
    void defaultSpawnCameraStaysOutOfTheSpawnBuilds() {
        CameraRules.Blocks spawn = boxes(
                new int[]{-18, 99, -30, 18, 135, -16},   // statue
                new int[]{-30, 97, -68, 30, 172, -33},   // keep
                new int[]{-36, 100, -102, 36, 140, -74}, // gatehouse
                new int[]{-30, 98, 16, -6, 114, 26},     // info office
                new int[]{-38, 97, 33, 38, 125, 92},     // market square
                new int[]{-111, 42, -111, 112, 101, 110}); // the ground of the base
        TutorialConfig c = new TutorialConfig();
        CameraShot.Parsed parsed = CameraShot.parse("spawn", c.cameraSpawn, c.cameraMaxSeconds * 20);
        assertTrue(parsed.shots().size() <= 3, "less is more: a short intro");
        for (CameraShot shot : parsed.shots()) {
            assertEquals(CameraRules.Fix.OK, CameraRules.check(abs(shot.from()), abs(shot.to()), abs(shot.look()), spawn), shot.toString());
        }
    }

    /** The default island camera path stays above / outside the starter island (islands_starter_meadow_v1 bounds). */
    @Test
    void defaultIslandCameraNeverFliesThroughTheIsland() {
        double[] home = {0.5, 101, 0.5};
        CameraRules.Blocks island = boxes(new int[]{-10, 65, -9, 10, 109, 9});
        TutorialConfig c = new TutorialConfig();
        for (CameraShot shot : CameraShot.parse("island", c.cameraIsland, c.cameraMaxSeconds * 20).shots()) {
            assertTrue(CameraRules.clearLine(rel(shot.from(), home), rel(shot.to(), home), island), shot.toString());
        }
    }

    @Test
    void oldCameraLinesAreReplacedOnlyWhileUnchanged() {
        TutorialConfig c = new TutorialConfig();
        Map<String, net.skybornmc.skyblock.common.config.ModuleConfig.ValueChange> v3 = c.valueChanges().get(3);
        assertTrue(v3.get("cameras.spawn").matches(new ArrayList<>(TutorialConfig.OLD_SPAWN)));
        assertFalse(v3.get("cameras.spawn").matches(List.of("spawn:0,120,0 look=spawn:0,100,-20 ticks=40")), "owner lines stay");
        assertEquals(c.cameraSpawn, v3.get("cameras.spawn").to());
        assertTrue(v3.get("cameras.island").matches(TutorialConfig.OLD_ISLAND));
        assertEquals(3, c.version());
    }

    private static double[] abs(CameraShot.CamPoint p) {
        return new double[]{p.x(), p.y(), p.z()};
    }

    private static double[] rel(CameraShot.CamPoint p, double[] base) {
        return new double[]{base[0] + p.x(), base[1] + p.y(), base[2] + p.z()};
    }

    // ------------------------------------------------------------------ input, watchdog, interruptions

    @Test
    void anyMoveKeySkipsAfterTheGraceSneakAlways() {
        assertTrue(CameraRules.skipOnInput(false, false, false, false, false, true, 0, 10), "sneak at once");
        assertFalse(CameraRules.skipOnInput(true, false, false, false, false, false, 3, 10), "a key held from before");
        assertTrue(CameraRules.skipOnInput(true, false, false, false, false, false, 10, 10));
        assertTrue(CameraRules.skipOnInput(false, true, false, false, false, false, 40, 10));
        assertTrue(CameraRules.skipOnInput(false, false, true, false, false, false, 40, 10));
        assertTrue(CameraRules.skipOnInput(false, false, false, true, false, false, 40, 10));
        assertTrue(CameraRules.skipOnInput(false, false, false, false, true, false, 40, 10), "jump");
        assertFalse(CameraRules.skipOnInput(false, false, false, false, false, false, 40, 10), "releasing keys");
    }

    @Test
    void watchdogEndsOverdueCameras() {
        assertFalse(CameraRules.overdue(0, 100, 5_000, 5), "100 ticks = 5 s, still within 5 + 5 s");
        assertFalse(CameraRules.overdue(0, 100, 10_000, 5));
        assertTrue(CameraRules.overdue(0, 100, 10_001, 5));
    }

    @Test
    void movingAwayFromACutSkips() {
        assertFalse(CameraRules.movedAway(0.3, 0, 0.3, 1.0));
        assertTrue(CameraRules.movedAway(0, -1.1, 0, 1.0));
    }

    @Test
    void everyInterruptionEndsOrIsHarmless() {
        for (CameraRules.Interruption i : CameraRules.Interruption.values()) {
            CameraRules.Action held = CameraRules.action(i, true);
            switch (i) {
                case MOVE_KEY, JUMP, SNEAK, COMMAND, LEFT_CAMERA, DEATH, WATCHDOG -> assertEquals(CameraRules.Action.SKIP, held, i.name());
                case TELEPORT -> assertEquals(CameraRules.Action.END_AT_DESTINATION, held);
                case QUIT, STAFF_CHANGE, MODULE_STOP -> assertEquals(CameraRules.Action.STOP, held, i.name());
                case CHAT, DAMAGE -> assertEquals(CameraRules.Action.IGNORE, held, "invulnerable; chat is harmless");
            }
        }
        // A titles-only camera changes nothing about the player: moving, teleports and death don't need to end it.
        assertEquals(CameraRules.Action.IGNORE, CameraRules.action(CameraRules.Interruption.MOVE_KEY, false));
        assertEquals(CameraRules.Action.IGNORE, CameraRules.action(CameraRules.Interruption.TELEPORT, false));
        assertEquals(CameraRules.Action.STOP, CameraRules.action(CameraRules.Interruption.QUIT, false));
        assertEquals(CameraRules.Action.SKIP, CameraRules.action(CameraRules.Interruption.WATCHDOG, false));
    }

    // ------------------------------------------------------------------ restore at a teleport's destination

    @Test
    void restoreAtAnotherPlaceKeepsEverythingElse() {
        CameraState saved = new CameraState(GameMode.SURVIVAL, "spawn", 0.5, 100, 0.5, 180f, 0f, false, false, 0.2f, 0.1f, false);
        CameraState moved = saved.at("islands", 1000.5, 101, 0.5, 90f, 5f);
        assertEquals(GameMode.SURVIVAL, moved.mode());
        assertEquals("islands", moved.world());
        assertEquals(1000.5, moved.x());
        assertEquals(saved.walkSpeed(), moved.walkSpeed());
        assertEquals(saved.invulnerable(), moved.invulnerable());
        List<String> calls = new ArrayList<>();
        moved.restore(new CameraState.Body() {
            @Override
            public void stopSpectating() {
                calls.add("stop");
            }

            @Override
            public void gameMode(GameMode mode) {
                calls.add("mode " + mode);
            }

            @Override
            public boolean teleport(String world, double x, double y, double z, float yaw, float pitch) {
                calls.add("tp " + world);
                return true;
            }

            @Override
            public void fallbackTeleport() {
                calls.add("spawn");
            }

            @Override
            public void allowFlight(boolean allow) {
            }

            @Override
            public void flying(boolean flying) {
            }

            @Override
            public void walkSpeed(float speed) {
            }

            @Override
            public void flySpeed(float speed) {
            }

            @Override
            public void invulnerable(boolean invulnerable) {
                calls.add("god " + invulnerable);
            }

            @Override
            public void resetFall() {
            }
        });
        assertEquals("stop", calls.getFirst(), "the spectator target is cleared first");
        assertEquals(List.of("stop", "mode SURVIVAL", "tp islands", "god false"), calls);
        assertNull(CameraState.decode("garbage").orElse(null));
    }
}
