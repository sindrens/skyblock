package net.skybornmc.skyblock.tutorial;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TU3 cameras: shot lines, the Java camera path, and the crash-safe save/restore of the player's state. */
class CameraTest {

    // ------------------------------------------------------------------ shots

    @Test
    void defaultCamerasParseAndStayShort() {
        TutorialConfig c = new TutorialConfig();
        int max = c.cameraMaxSeconds * 20;
        for (List<String> lines : List.of(c.cameraSpawn, c.cameraIsland, c.cameraDepths, c.cameraMarket)) {
            CameraShot.Parsed p = CameraShot.parse("test", lines, max);
            assertEquals(List.of(), p.problems());
            assertEquals(lines.size(), p.shots().size());
            assertTrue(p.totalTicks() <= max, "max 8 s per camera");
        }
        // TUTORIAL §9.4: never more than 20 s of camera in the whole core (spawn + island + depths).
        int core = CameraShot.parse("s", c.cameraSpawn, max).totalTicks() + CameraShot.parse("i", c.cameraIsland, max).totalTicks()
                + CameraShot.parse("d", c.cameraDepths, max).totalTicks();
        assertTrue(core <= 20 * 20, "core cameras " + core + " ticks");
        assertEquals(List.of("@home"), CameraShot.parse("i", c.cameraIsland, max).anchors());
        assertEquals(List.of("@zone"), CameraShot.parse("d", c.cameraDepths, max).anchors());
        assertEquals(List.of("spawn"), CameraShot.parse("s", c.cameraSpawn, max).anchors());
    }

    @Test
    void shotLinesAreValidated() {
        CameraShot.Parsed p = CameraShot.parse("x", List.of(
                "spawn:0,100,0 spawn:0,110,-20 look=spawn:0,100,-50 ticks=40 text=One",
                "@HOME:1,2,3 look=@home:0,0,0 ticks=20",
                "spawn:0,100,0 look=spawn:0,0,0",
                "spawn:0,100 look=spawn:0,0,0 ticks=5",
                "spawn:0,100,0 look=spawn:0,0,0 ticks=0",
                "spawn:0,100,0 look=spawn:0,0,0 ticks=5 speed=2",
                ""), 400);
        assertEquals(2, p.shots().size());
        assertEquals(4, p.problems().size());
        CameraShot first = p.shots().getFirst();
        assertEquals("one", first.text());
        assertEquals(new CameraShot.CamPoint("spawn", 0, 110, -20), first.to());
        CameraShot still = p.shots().get(1);
        assertEquals("@home", still.from().anchor(), "anchors are lower case");
        assertTrue(still.from().relative());
        assertEquals(still.from(), still.to(), "one point = a still shot");
    }

    @Test
    void aLongCameraIsCut() {
        CameraShot.Parsed p = CameraShot.parse("x", List.of(
                "w:0,0,0 look=w:1,0,0 ticks=100",
                "w:0,0,0 look=w:1,0,0 ticks=100",
                "w:0,0,0 look=w:1,0,0 ticks=100"), 160);
        assertEquals(2, p.shots().size());
        assertEquals(160, p.totalTicks());
        assertEquals(60, p.shots().get(1).ticks());
        assertEquals(2, p.problems().size());
    }

    // ------------------------------------------------------------------ path

    @Test
    void anglesFollowMinecraft() {
        assertEquals(0f, CameraPath.yaw(0, 10), 1e-4, "south");
        assertEquals(90f, CameraPath.yaw(-10, 0), 1e-4, "west");
        assertEquals(-90f, CameraPath.yaw(10, 0), 1e-4, "east");
        assertEquals(180f, Math.abs(CameraPath.yaw(0, -10)), 1e-4, "north");
        assertEquals(45f, CameraPath.pitch(10, -10, 0), 1e-4, "down is positive");
        assertEquals(-90f, CameraPath.pitch(0, 5, 0), 1e-4, "straight up");
        assertEquals(0f, CameraPath.yaw(0, 0));
    }

    @Test
    void pathIsACutThenShortSmoothMoves() {
        List<CameraPath.Waypoint> path = CameraPath.waypoints(new double[]{0, 100, 0}, new double[]{0, 100, -100},
                new double[]{0, 100, -200}, 130, 40);
        assertEquals(0, path.getFirst().ticks(), "first waypoint is a cut");
        assertEquals(130, CameraPath.duration(path));
        assertEquals(5, path.size(), "130 ticks in moves of at most 40 = 4 moves");
        for (CameraPath.Waypoint w : path) {
            assertTrue(w.ticks() <= 40);
            assertEquals(180f, Math.abs(w.yaw()), 1e-3, "always facing the look-at point (north)");
        }
        CameraPath.Waypoint last = path.getLast();
        assertEquals(-100, last.z(), 1e-9);
        // A display entity smooths at most 59 ticks, whatever the setting says.
        for (CameraPath.Waypoint w : CameraPath.waypoints(new double[]{0, 0, 0}, new double[]{1, 0, 0}, new double[]{0, 0, 5}, 400, 500)) {
            assertTrue(w.ticks() <= CameraPath.MAX_SEGMENT_TICKS);
        }
    }

    // ------------------------------------------------------------------ state save / restore

    private static CameraState sample() {
        return new CameraState(GameMode.SURVIVAL, "islands_v2;a b", 10.5, 101, -3.25, 90f, 12.5f, true, true, 0.2f, 0.1f, false);
    }

    @Test
    void stateSurvivesEncoding() {
        CameraState s = sample();
        assertEquals(Optional.of(s), CameraState.decode(s.encode()), "world names with ; and spaces survive");
        CameraState creative = new CameraState(GameMode.CREATIVE, "spawn", 0, 0, 0, 0, 0, false, false, 0f, 0.5f, true);
        assertEquals(Optional.of(creative), CameraState.decode(creative.encode()));
    }

    @Test
    void brokenStateIsIgnored() {
        assertTrue(CameraState.decode(null).isEmpty());
        assertTrue(CameraState.decode("").isEmpty());
        assertTrue(CameraState.decode("c1;SURVIVAL;spawn;1;2").isEmpty());
        assertTrue(CameraState.decode(sample().encode().replace("SURVIVAL", "FLYING")).isEmpty());
        assertTrue(CameraState.decode(sample().encode().replace("c1;", "c9;")).isEmpty());
        assertTrue(CameraState.decode("c1;SURVIVAL;spawn;x;0;0;0;0;0;0;0.2;0.1;0").isEmpty());
        assertTrue(CameraState.decode("c1;SURVIVAL;spawn;0;0;0;0;0;2;0;0.2;0.1;0").isEmpty(), "flags are 0 or 1");
    }

    /** Records what the restore did, in order. */
    private static final class FakeBody implements CameraState.Body {
        final List<String> calls = new ArrayList<>();
        boolean worldExists = true;
        boolean allow;

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
            calls.add("tp " + world + " " + x + "," + y + "," + z + " " + yaw + "/" + pitch);
            return worldExists;
        }

        @Override
        public void fallbackTeleport() {
            calls.add("spawn");
        }

        @Override
        public void allowFlight(boolean allow) {
            this.allow = allow;
            calls.add("allow " + allow);
        }

        @Override
        public void flying(boolean flying) {
            calls.add("fly " + flying);
        }

        @Override
        public void walkSpeed(float speed) {
            calls.add("walk " + speed);
        }

        @Override
        public void flySpeed(float speed) {
            calls.add("flyspeed " + speed);
        }

        @Override
        public void invulnerable(boolean invulnerable) {
            calls.add("god " + invulnerable);
        }

        @Override
        public void resetFall() {
            calls.add("fall");
        }
    }

    @Test
    void restoreGivesEverythingBackInASafeOrder() {
        FakeBody body = new FakeBody();
        sample().restore(body);
        assertEquals(List.of("stop", "mode SURVIVAL", "tp islands_v2;a b 10.5,101.0,-3.25 90.0/12.5", "allow true", "fly true",
                "walk 0.2", "flyspeed 0.1", "fall", "god false"), body.calls, "invulnerable is given back last");
    }

    @Test
    void restoreWithoutTheWorldGoesToSpawnAndNeverFliesWithoutFlight() {
        FakeBody body = new FakeBody();
        body.worldExists = false;
        new CameraState(GameMode.ADVENTURE, "gone", 0, 0, 0, 0, 0, false, true, 5f, Float.NaN, true).restore(body);
        assertTrue(body.calls.contains("spawn"));
        assertTrue(body.calls.contains("fly false"), "flying only when flight is allowed");
        assertTrue(body.calls.contains("walk 0.2"), "a broken speed falls back to the default");
        assertTrue(body.calls.contains("flyspeed 0.1"));
        assertEquals("god true", body.calls.getLast());
        assertFalse(body.calls.contains("mode SPECTATOR"));
    }

    @Test
    void frozenSpeedsAreRestoredAsTheyWere() {
        // A player frozen by staff (walk speed 0) before the camera keeps speed 0 afterwards.
        FakeBody body = new FakeBody();
        new CameraState(GameMode.SURVIVAL, "spawn", 0, 0, 0, 0, 0, false, false, 0f, 0f, false).restore(body);
        assertTrue(body.calls.contains("walk 0.0"));
        assertTrue(body.calls.contains("flyspeed 0.0"));
    }

    @Test
    void watchAgainPlaysTheStepCameras() {
        assertEquals(Cameras.SPAWN, Replays.camera(Steps.PORTAL));
        assertEquals(Cameras.ISLAND, Replays.camera(Steps.ARRIVE));
        assertEquals(Cameras.DEPTHS, Replays.camera(Steps.DEPTHS));
        assertEquals(null, Replays.camera(Steps.MINE));
    }
}
