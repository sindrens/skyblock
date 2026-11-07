package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Owner 7 Nov 2026: /is name rules. */
class IslandNamingTest {

    @Test
    void names() {
        assertEquals(IslandNaming.Check.OK, IslandNaming.check("Sky Haven"));
        assertEquals(IslandNaming.Check.OK, IslandNaming.check("Bob's Isle-2"));
        assertEquals(IslandNaming.Check.EMPTY, IslandNaming.check(" "));
        assertEquals(IslandNaming.Check.EMPTY, IslandNaming.check("a"));
        assertEquals(IslandNaming.Check.TOO_LONG, IslandNaming.check("a".repeat(25)));
        assertEquals(IslandNaming.Check.CHARACTERS, IslandNaming.check("<red>Hi"));
        assertEquals(IslandNaming.Check.CHARACTERS, IslandNaming.check("two  spaces"));
    }
}
