package net.skybornmc.skyblock.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** GUI-4 (owner U4): the scoreboard shows Crystals/Gems only while you have some. */
class OwnedPlaceholderTest {

    @Test
    void emptyWithoutABalance() {
        assertEquals("", EconomyModule.ownedText(null));
        assertEquals("", EconomyModule.ownedText(0L));
        assertEquals("", EconomyModule.ownedText(-5L));
    }

    @Test
    void theAmountOnceYouHaveSome() {
        assertFalse(EconomyModule.ownedText(1L).isBlank());
        assertFalse(EconomyModule.ownedText(1_500_000L).isBlank());
    }
}
