package net.skybornmc.skyblock.npcs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutTest {

    @Test
    void facingToYaw() {
        assertEquals(0f, Layout.yawOf("south"));
        assertEquals(90f, Layout.yawOf("West"));
        assertEquals(180f, Layout.yawOf(" north "));
        assertEquals(-90f, Layout.yawOf("east"));
        assertTrue(Float.isNaN(Layout.yawOf(null)));
        assertTrue(Float.isNaN(Layout.yawOf("up")));
    }

    @Test
    void directionMatchesYaw() {
        double[] south = Layout.direction(0f);
        assertEquals(0, south[0], 1e-9);
        assertEquals(1, south[1], 1e-9);
        double[] west = Layout.direction(90f);
        assertEquals(-1, west[0], 1e-9);
        assertEquals(0, west[1], 1e-9);
    }

    @Test
    void sizes() {
        assertArrayEquals(new int[]{5, 7}, Layout.size("[5, 7]", 1, 1));
        assertArrayEquals(new int[]{9, 5}, Layout.size("9x5", 1, 1));
        assertArrayEquals(new int[]{5, 3}, Layout.size(null, 5, 3));
        assertArrayEquals(new int[]{5, 3}, Layout.size("[a, b]", 5, 3));
        assertArrayEquals(new int[]{5, 3}, Layout.size("[0, 7]", 5, 3));
        assertArrayEquals(new int[]{5, 3}, Layout.size("[5, 7, 1]", 5, 3));
    }

    @Test
    void boardBottomAndCentring() {
        assertEquals(105, Layout.bottomY(108, 7), "Hall of Heroes board: backing y 105..111");
        assertEquals(102, Layout.bottomY(103, 4));
        assertEquals(112, Layout.bottomY(112, 1));
        assertEquals(106.0, Layout.textBottom(105, 7, 5.0), 1e-9);
        assertEquals(105.0, Layout.textBottom(105, 7, 9.0), 1e-9, "never below the board");
    }

    @Test
    void scaleFitsAndClamps() {
        // 12 lines on a 7 high board at 80%: 7*0.8/(12*0.27) = 1.728
        assertEquals(1.728f, Layout.scale(12, 7, 0.27, 0.8, 0.5, 2.0), 0.001f);
        assertEquals(2.0f, Layout.scale(1, 1, 0.27, 0.8, 0.5, 2.0), "one-line signposts hit the max");
        assertEquals(0.5f, Layout.scale(100, 1, 0.27, 0.8, 0.5, 2.0));
        assertEquals(1.0f, Layout.scale(0, 5, 0.27, 0.8, 0.5, 2.0));
    }

    @Test
    void lineCountAndCentre() {
        assertEquals(0, Layout.lineCount(""));
        assertEquals(1, Layout.lineCount("<c:flame>Tavern"));
        assertEquals(3, Layout.lineCount("a<newline>b<newline>c"));
        assertEquals(2.5, Layout.centre(2));
        assertEquals(-4.5, Layout.centre(-5));
        assertEquals(0.5, Layout.centre(0.5), "already a centre");
    }

    @Test
    void lookAtAndAngles() {
        float[] south = Layout.lookAt(0, 0, 5);
        assertEquals(0f, south[0], 0.01f);
        assertEquals(0f, south[1], 0.01f);
        float[] west = Layout.lookAt(-5, 0, 0);
        assertEquals(90f, west[0], 0.01f);
        float[] up = Layout.lookAt(0, 100, 1);
        assertEquals(-60f, up[1], 0.01f, "pitch clamped");
        assertEquals(2f, Layout.angleDiff(179f, -179f), 0.001f);
        assertEquals(0f, Layout.angleDiff(-90f, 270f), 0.001f);
    }

    @Test
    void bedrockSafeText() {
        assertEquals("Tier 2 · Bone Crypt »", Layout.bedrockSafe(" Tier 2 · Bone Crypt → "));
        assertEquals("Mine - Tier 1-12", Layout.bedrockSafe("Mine - Tier 1-12"));
        assertEquals("ab", Layout.bedrockSafe("a☃b"), "unknown symbol dropped");
        assertEquals(64, Layout.bedrockSafe("x".repeat(100)).length());
        assertEquals("", Layout.bedrockSafe(null));
    }
}
