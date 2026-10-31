package net.skybornmc.skyblock.crates;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyModelTest {

    @Test
    void keyModelMatchesThePackIds() {
        assertEquals("crate/key/phoenix", KeyItems.keyModel("Phoenix"));
        assertEquals("crate/key/ember", KeyItems.keyModel("ember"));
    }
}
