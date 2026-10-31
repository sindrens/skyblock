package net.skybornmc.skyblock.menus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-4c: Bedrock players read help pages in a popup; Next walks through the topic, the last page only has Back. */
class HelpPagePopupTest {

    @Test
    void nextOnlyWhileThereIsAnotherPage() {
        assertTrue(HelpPages.hasNext(0, 3));
        assertTrue(HelpPages.hasNext(1, 3));
        assertFalse(HelpPages.hasNext(2, 3), "the last page has no Next");
        assertFalse(HelpPages.hasNext(0, 1));
        assertFalse(HelpPages.hasNext(-1, 3));
    }

    @Test
    void theFirstButtonIsNextOnlyWhenShown() {
        assertTrue(HelpPages.pressedNext(0, true));
        assertFalse(HelpPages.pressedNext(1, true), "the second button is Back");
        assertFalse(HelpPages.pressedNext(0, false), "without Next the only button is Back");
    }
}
