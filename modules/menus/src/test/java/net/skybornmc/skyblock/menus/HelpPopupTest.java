package net.skybornmc.skyblock.menus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-5: only commands that need no argument get a Run button in the Bedrock popup. */
class HelpPopupTest {

    @Test
    void commandsWithoutRequiredArgumentsRunAlone() {
        assertTrue(HelpChat.runsAlone("/spawn"));
        assertTrue(HelpChat.runsAlone("/is home [name]"));
        assertFalse(HelpChat.runsAlone("/pay <player> <amount>"));
        assertFalse(HelpChat.runsAlone(""));
        assertFalse(HelpChat.runsAlone(null));
    }
}
