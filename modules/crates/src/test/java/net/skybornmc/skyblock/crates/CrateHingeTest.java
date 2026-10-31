package net.skybornmc.skyblock.crates;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateHingeTest {

    private static final double HY = 9;
    private static final double HZ = 2;
    private static final double S = 1.05;

    @Test
    void closedIsTheIdentityPose() {
        CrateHinge.Pose p = CrateHinge.lid(HY, HZ, S, 0);
        assertEquals(0f, p.translation().length(), 1e-6);
        assertEquals(1f, p.rotation().w, 1e-6);
        assertEquals((float) S, p.scale(), 1e-6);
    }

    @Test
    void hingeStaysFixedAtEveryAngle() {
        Vector3f hinge = new Vector3f(0, (float) ((HY - 8) / 16), (float) ((HZ - 8) / 16));
        for (double a = 0; a <= 120; a += 10) {
            Vector3f out = CrateHinge.lid(HY, HZ, S, a).apply(hinge);
            Vector3f expected = new Vector3f(hinge).mul((float) S);
            assertEquals(expected.x, out.x, 1e-5, "x at " + a);
            assertEquals(expected.y, out.y, 1e-5, "y at " + a);
            assertEquals(expected.z, out.z, 1e-5, "z at " + a);
        }
    }

    @Test
    void positiveAngleOpensUpAndAwayFromTheFront() {
        // A point on the lid's front edge (south = +z): it must rise and move back toward the hinge side.
        Vector3f front = new Vector3f(0, 0.4f, 0.5f);
        Vector3f closed = CrateHinge.lid(HY, HZ, S, 0).apply(front);
        Vector3f open = CrateHinge.lid(HY, HZ, S, 100).apply(front);
        assertTrue(open.y > closed.y + 0.3, "front edge rises");
        assertTrue(open.z < closed.z, "front edge moves back, over the hinge");
        // The lid is swung nearly vertical: its front edge ends up above the hinge line.
        assertTrue(open.z < 0.0f + 0.2f);
    }

    @Test
    void ninetyDegreesStandsTheLidUp() {
        Vector3f hinge = CrateHinge.pivot(HY, HZ, 1.0);
        Vector3f front = new Vector3f(0, hinge.y, hinge.z + 0.75f); // 0.75 blocks in front of the hinge, same height
        Vector3f out = CrateHinge.lid(HY, HZ, 1.0, 90).apply(front);
        assertEquals(hinge.z, out.z, 1e-5);
        assertEquals(hinge.y + 0.75f, out.y, 1e-5);
    }

    @Test
    void rollKeepsTheCentreStill() {
        CrateHinge.Pose roll = CrateHinge.roll(S, 3);
        assertEquals(0f, roll.apply(new Vector3f()).length(), 1e-6);
    }

    @Test
    void pathEndsExactlyOnTargetAndIsMonotonicWithoutOvershoot() {
        float[] up = CrateHinge.path(0, 100, 4, 0);
        assertEquals(4, up.length);
        assertEquals(100f, up[3], 0f);
        for (int i = 1; i < up.length; i++) {
            assertTrue(up[i] > up[i - 1]);
        }
        float[] down = CrateHinge.path(100, 0, 5, 0);
        assertEquals(0f, down[4], 0f);
        for (int i = 1; i < down.length; i++) {
            assertTrue(down[i] < down[i - 1]);
        }
    }

    @Test
    void overshootSwingsPastOpenThenSettles() {
        float[] up = CrateHinge.path(0, 100, 6, 1.7);
        float max = 0;
        for (float a : up) {
            max = Math.max(max, a);
        }
        assertTrue(max > 100f && max < 120f);
        assertEquals(100f, up[5], 0f);
        assertEquals(1.0, CrateHinge.easeOutBack(1, 1.7), 1e-9);
        assertEquals(0.0, CrateHinge.easeOutBack(0, 1.7), 1e-9);
    }

    @Test
    void shortSegmentsKeepTheHingeCloseEvenMidInterpolation() {
        // The client lerps the translation linearly but slerps the rotation: the hinge drifts at most this much per segment.
        CrateHinge.Pose a = CrateHinge.lid(HY, HZ, S, 0);
        CrateHinge.Pose b = CrateHinge.lid(HY, HZ, S, 25);
        Vector3f hinge = CrateHinge.pivot(HY, HZ, 1.0);
        Vector3f t = new Vector3f(a.translation()).lerp(b.translation(), 0.5f);
        var q = new org.joml.Quaternionf(a.rotation()).slerp(b.rotation(), 0.5f);
        Vector3f mid = q.transform(new Vector3f(hinge).mul((float) S)).add(t);
        assertTrue(mid.distance(new Vector3f(hinge).mul((float) S)) < 0.02f);
    }

    @Test
    void segmentTicksAndYaw() {
        assertEquals(2, CrateHinge.segmentTicks(8, 4));
        assertEquals(1, CrateHinge.segmentTicks(2, 10));
        assertEquals(0f, CrateHinge.yaw("south"));
        assertEquals(90f, CrateHinge.yaw("WEST"));
        assertEquals(180f, CrateHinge.yaw("north"));
        assertEquals(-90f, CrateHinge.yaw(" east "));
        assertNull(CrateHinge.yaw("up"));
        assertNull(CrateHinge.yaw(null));
    }
}
