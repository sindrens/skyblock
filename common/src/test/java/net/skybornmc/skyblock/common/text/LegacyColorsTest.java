package net.skybornmc.skyblock.common.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyColorsTest {

    @Test
    void convertsCodesAndHexToMiniMessage() {
        assertEquals("<reset><red><bold>ADMIN <reset><#FF8A1F>»", LegacyColors.toMiniMessage("&c&lADMIN &#ff8a1f»"));
        assertEquals("<reset><#C21F2B>Hi", LegacyColors.toMiniMessage("{#C21F2B}Hi"));
    }

    @Test
    void stripsCodes() {
        assertEquals("ADMIN »", LegacyColors.strip("&c&lADMIN &#FF8A1F»"));
        assertTrue(LegacyColors.hasCodes("&4x"));
    }
}
