package net.skybornmc.skyblock.perks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NickRulesTest {

    @Test
    void plainTierAcceptsLettersDigitsUnderscore() throws Exception {
        NickRules.Nick nick = NickRules.validate("Phoenix_42", NickRules.Tier.PLAIN, 3, 16);
        assertEquals("Phoenix_42", nick.plain());
        assertEquals("Phoenix_42", nick.formatted());
    }

    @ParameterizedTest
    @ValueSource(strings = {"&cRed", "<red>Red", "Re>d"})
    void plainTierRejectsFormatting(String input) {
        NickRules.Invalid e = assertThrows(NickRules.Invalid.class, () -> NickRules.validate(input, NickRules.Tier.PLAIN, 3, 16));
        assertEquals("nick.no-format", e.key());
    }

    @Test
    void colorTierConvertsLegacyCodes() throws Exception {
        NickRules.Nick nick = NickRules.validate("&c&lFire&6Bird", NickRules.Tier.COLOR, 3, 16);
        assertEquals("FireBird", nick.plain());
        assertEquals("<red><bold>Fire<gold>Bird", nick.formatted());
    }

    @Test
    void colorTierRejectsHexAndTags() {
        NickRules.Invalid e = assertThrows(NickRules.Invalid.class,
                () -> NickRules.validate("<#FF0000>Red", NickRules.Tier.COLOR, 3, 16));
        assertEquals("nick.no-hex", e.key());
    }

    @Test
    void hexTierAcceptsGradients() throws Exception {
        NickRules.Nick nick = NickRules.validate("<gradient:#FF3D2E:#FFD23F>Phoenix</gradient>", NickRules.Tier.HEX, 3, 16);
        assertEquals("Phoenix", nick.plain());
    }

    @ParameterizedTest
    @ValueSource(strings = {"&kMagic", "<obf>Magic", "<click:run_command:'/op me'>Hi</click>", "<hover:show_text:'x'>Hi",
            "Two Words", "Fire!", "§cRed"})
    void rejectsUnsafeOrInvalid(String input) {
        assertThrows(NickRules.Invalid.class, () -> NickRules.validate(input, NickRules.Tier.HEX, 3, 16));
    }

    @Test
    void lengthIsCountedWithoutColors() {
        assertEquals("nick.too-short", assertThrows(NickRules.Invalid.class,
                () -> NickRules.validate("&cAb", NickRules.Tier.COLOR, 3, 16)).key());
        assertEquals("nick.too-long", assertThrows(NickRules.Invalid.class,
                () -> NickRules.validate("ABCDEFGHIJKLMNOPQ", NickRules.Tier.PLAIN, 3, 16)).key());
    }

    @Test
    void reservedWordsAndImpersonation() {
        assertEquals("admin", NickRules.reservedWord("TheAdminGuy", List.of("admin", "owner")));
        assertNull(NickRules.reservedWord("Phoenix", List.of("admin")));
        assertTrue(NickRules.impersonates("steve", "Alex", List.of("Steve", "Alex")));
        assertFalse(NickRules.impersonates("alex", "Alex", List.of("Steve", "Alex")));
        assertFalse(NickRules.impersonates("Nobody", "Alex", List.of("Steve", "Alex")));
    }
}
