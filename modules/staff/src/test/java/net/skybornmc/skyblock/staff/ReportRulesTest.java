package net.skybornmc.skyblock.staff;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRulesTest {

    private static final List<String> REASONS = List.of("Hacking", "X-ray", "Inappropriate build", "Inappropriate name", "Other");

    @Test
    void refusalsInOrder() {
        assertEquals(Optional.of(ReportRules.Refusal.SELF), ReportRules.check(true, 0, 0, 0, 0, 3, false));
        assertEquals(Optional.of(ReportRules.Refusal.COOLDOWN), ReportRules.check(false, 5_000, 0, 0, 0, 3, false));
        assertEquals(Optional.of(ReportRules.Refusal.PLAYTIME), ReportRules.check(false, 0, 60_000, 5, 0, 3, false));
        assertEquals(Optional.of(ReportRules.Refusal.ALREADY_REPORTED), ReportRules.check(false, 0, 0, 0, 0, 3, true));
        assertEquals(Optional.of(ReportRules.Refusal.TOO_MANY_OPEN), ReportRules.check(false, 0, 0, 0, 3, 3, false));
        assertTrue(ReportRules.check(false, 0, 600_000, 5, 2, 3, false).isEmpty());
    }

    @Test
    void parsesReasonAndDetails() {
        assertArrayEquals(new String[]{"Hacking", "flying near spawn"}, ReportRules.parse("hacking flying near spawn", REASONS));
        assertArrayEquals(new String[]{"Inappropriate name", ""}, ReportRules.parse("Inappropriate name", REASONS));
        assertArrayEquals(new String[]{"Other", "stole my stuff"}, ReportRules.parse("stole my stuff", REASONS));
        assertArrayEquals(new String[]{"X-ray", "diamonds"}, ReportRules.parse("x-ray diamonds", REASONS));
    }

    @Test
    void cleansText() {
        assertEquals("a b c", ReportRules.clean("  a\n b\t\t c  ", 50));
        assertEquals("abc", ReportRules.clean("abcdef", 3));
    }
}
