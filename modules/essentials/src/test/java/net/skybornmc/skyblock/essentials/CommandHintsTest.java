package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.command.HelpEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QOL3 "Did you mean": which command a typo points to. */
class CommandHintsTest {

    private static final Set<String> LABELS = Set.of("home", "is", "spawn", "shop", "sell", "help", "menu", "ah", "bal",
            "baltop", "pay", "msg", "tpa", "tpaccept", "settings", "skills");

    @Test
    void commonTyposFindTheCommand() {
        assertEquals(Optional.of("home"), CommandHints.suggest("hoem", LABELS, 2), "swapped letters count as one");
        assertEquals(Optional.of("spawn"), CommandHints.suggest("spwan", LABELS, 2));
        assertEquals(Optional.of("settings"), CommandHints.suggest("setings", LABELS, 2));
        assertEquals(Optional.of("tpaccept"), CommandHints.suggest("tpacept", LABELS, 2));
        assertEquals(Optional.of("menu"), CommandHints.suggest("mneu", LABELS, 2));
    }

    @Test
    void shortWordsOnlyMatchOneEditAway() {
        assertEquals(Optional.of("is"), CommandHints.suggest("iss", LABELS, 2));
        assertTrue(CommandHints.suggest("x", LABELS, 2).isEmpty(), "one letter never matches");
        assertTrue(CommandHints.suggest("zz", LABELS, 2).isEmpty());
    }

    @Test
    void nothingCloseMeansNoSuggestion() {
        assertTrue(CommandHints.suggest("kit", Set.of("home", "spawn"), 2).isEmpty());
        assertTrue(CommandHints.suggest("warpz", Set.of("home", "spawn"), 2).isEmpty());
    }

    @Test
    void tiesGoToTheShorterThenFirstWord() {
        assertEquals(Optional.of("bal"), CommandHints.suggest("bak", LABELS, 2));
        assertEquals(Optional.of("sell"), CommandHints.suggest("shell", Set.of("shop", "sell", "spell"), 2));
    }

    @Test
    void labelStripsSlashNamespaceAndArguments() {
        assertEquals("hoem", CommandHints.label("/hoem"));
        assertEquals("hoem", CommandHints.label("Skyblock:HOEM bob 3"));
        assertEquals("tpa", CommandHints.label("tpa Steve"));
    }

    @Test
    void labelsUseTopWordsAndAliasesOnly() {
        List<HelpEntry> entries = List.of(
                new HelpEntry("is", "/is", "", "", "", List.of(), List.of("island"), true),
                new HelpEntry("is home", "/is home", "", "", "", List.of(), List.of("ignored"), true),
                new HelpEntry("Spawn", "/spawn", "", "", "", List.of(), List.of(), true));
        assertEquals(Set.of("is", "island", "spawn"), CommandHints.labels(entries));
    }

    @Test
    void distanceStopsAboveTheLimit() {
        assertEquals(1, CommandHints.distance("hoem", "home", 2));
        assertEquals(3, CommandHints.distance("abcdef", "uvwxyz", 2), "limit + 1 when too far");
        assertEquals(0, CommandHints.distance("pay", "pay", 2));
    }
}
