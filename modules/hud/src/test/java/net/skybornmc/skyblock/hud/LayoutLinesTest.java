package net.skybornmc.skyblock.hud;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutLinesTest {

    private static List<String> texts(List<LayoutLines.Line> lines) {
        return lines.stream().map(LayoutLines.Line::text).toList();
    }

    @Test
    void parsesListItemsAndBlankLines() {
        LayoutLines layout = LayoutLines.parse("<newline> A<newline><newline> B");
        assertEquals(List.of("", " A", "", " B"), texts(layout.lines()));
        assertTrue(layout.lines().getFirst().blank());
        assertEquals(0, LayoutLines.parse(null).lines().size());
        assertEquals(0, LayoutLines.parse("").lines().size());
    }

    @Test
    void conditionalLinesHideWithoutAValue() {
        LayoutLines layout = LayoutLines.parse(" A<newline>if:island_next_note| Next  <ph:island_next_note><newline> B");
        assertEquals("island_next_note", layout.lines().get(1).condition());
        assertEquals(" Next  <ph:island_next_note>", layout.lines().get(1).text());
        assertEquals(List.of(" A", " B"), texts(layout.visible(k -> false, 15)));
        assertEquals(List.of(" A", " Next  <ph:island_next_note>", " B"), texts(layout.visible(Set.of("island_next_note")::contains, 15)));
    }

    @Test
    void islandSectionHidesUntilLevelsExist() {
        // Owner answer 275: no "Level: -" line and no empty "| Island" header while island levels don't exist.
        LayoutLines layout = LayoutLines.parse("if:island_level,island_next_note| Island<newline>if:island_level| Level<newline>"
                + "if:island_next_note| Next<newline> Profile");
        assertEquals("island_level,island_next_note", layout.lines().getFirst().condition());
        assertEquals(List.of(" Profile"), texts(layout.visible(k -> false, 15)), "no level, no note: no island section");
        assertEquals(List.of(" Island", " Next", " Profile"), texts(layout.visible(Set.of("island_next_note")::contains, 15)),
                "the header stays for the note");
        assertEquals(List.of(" Island", " Level", " Profile"), texts(layout.visible(Set.of("island_level")::contains, 15)));
        assertFalse(LayoutLines.present("-"), "island_level is '-' until levels exist");
        assertNull(LayoutLines.line("if:a,,b| x").condition(), "an empty key keeps the line plain");
    }

    @Test
    void brokenConditionsStayPlainText() {
        assertNull(LayoutLines.line("if:no bar here").condition());
        assertNull(LayoutLines.line("if:Bad-Key| x").condition());
        assertEquals("if:| x", LayoutLines.line("if:| x").text());
    }

    @Test
    void maxLinesCountsOnlyShownLines() {
        LayoutLines layout = LayoutLines.parse("1<newline>if:x|2<newline>3<newline>4");
        assertEquals(List.of("1", "3"), texts(layout.visible(k -> false, 2)));
        assertEquals(List.of("1", "2"), texts(layout.visible(k -> true, 2)));
        assertEquals(List.of(), layout.visible(k -> true, 0));
    }

    @Test
    void presentMeansARealValue() {
        assertTrue(LayoutLines.present("Build a farm"));
        assertFalse(LayoutLines.present(null));
        assertFalse(LayoutLines.present("  "));
        assertFalse(LayoutLines.present("-"));
        assertFalse(LayoutLines.present("—"));
    }
}
