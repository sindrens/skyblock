package net.skybornmc.skyblock.tutorial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogTextTest {

    @Test
    void commandsBecomeSegments() {
        List<DialogText.Segment> s = DialogText.segments("Open /menu, then {/is level}. Not a/path.");
        assertEquals("Open ", s.get(0).text());
        assertEquals("menu", s.get(1).command());
        assertEquals(", then ", s.get(2).text());
        assertEquals("is level", s.get(3).command());
        assertEquals("/is level", s.get(3).text());
        assertEquals(". Not a/path.", s.get(4).text());
        assertEquals("Open /menu, then /is level. Not a/path.", DialogText.plain("Open /menu, then {/is level}. Not a/path."));
    }

    @Test
    void wrapsAtThirtyAndSplitsIntoTwoLinePieces() {
        String line = "Welcome to Phoenix Skyblock! I'm Rowan, and I'll show you around.";
        List<String> wrapped = DialogText.wrap(line, 30);
        wrapped.forEach(l -> assertTrue(l.length() <= 30, l));
        assertEquals(line, String.join(" ", wrapped));
        List<String> pieces = DialogText.pieces(line, 30, 2);
        assertEquals(2, pieces.size());
        assertEquals(2, pieces.getFirst().split("\n").length);
        assertEquals(List.of("abcd", "efgh", "ij"), DialogText.wrap("abcdefghij", 4));
    }

    @Test
    void typewriterSpeed() {
        assertEquals(0, DialogText.typed(0, 40, 10));
        assertEquals(2, DialogText.typed(1, 40, 10));
        assertEquals(10, DialogText.typed(100, 40, 10));
        assertEquals(1, DialogText.typed(1, 5, 10), "at least one letter per tick");
        assertEquals(15, DialogText.typingTicks(30, 40));
    }
}
