package net.skybornmc.skyblock.tutorial;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacesTest {

    @Test
    void defaultsParse() {
        List<String> warnings = new ArrayList<>();
        Places p = Places.parse(new TutorialConfig().places, warnings::add);
        assertEquals(List.of(), warnings);
        assertEquals("farm", p.get("farm").orElseThrow().command());
        assertEquals("is", p.get("ISLAND").orElseThrow().command());
        assertEquals("farm", p.at("grind", 10, -30).orElseThrow().id());
        assertEquals("mine", p.at("grind", 1010, 5).orElseThrow().id());
        assertTrue(p.at("grind", 500, 0).isEmpty());
        assertEquals("spawn", p.at("spawn", 9999, 9999).orElseThrow().id(), "the whole spawn world");
        assertTrue(p.at("islands", 0, 0).isEmpty(), "the island place has no area");
    }

    @Test
    void brokenLinesAreSkipped() {
        List<String> warnings = new ArrayList<>();
        Places p = Places.parse(List.of("nocolon", "a: x w 1 2", "b: /go w 1 2 x", "c: /go"), warnings::add);
        assertEquals(3, warnings.size());
        assertEquals("go", p.get("c").orElseThrow().command());
        assertFalse(p.get("a").isPresent());
    }
}
