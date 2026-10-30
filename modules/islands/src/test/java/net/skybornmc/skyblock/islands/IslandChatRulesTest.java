package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island chat routing, permission and color rules (M2.5). */
class IslandChatRulesTest {

    private static final List<String> PALETTE = List.of("gold", "red", "aqua", "green", "yellow", "light_purple", "blue", "white");

    @Test
    void onlyStoredMembersChat() {
        assertTrue(IslandChatRules.chats(Role.LEADER));
        assertTrue(IslandChatRules.chats(Role.ADMIN));
        assertTrue(IslandChatRules.chats(Role.MOD));
        assertTrue(IslandChatRules.chats(Role.MEMBER));
        assertFalse(IslandChatRules.chats(Role.COOP));
        assertFalse(IslandChatRules.chats(null)); // visitors and players without an island
    }

    @Test
    void onlyTheLeaderPicksColors() {
        assertTrue(IslandChatRules.canPickColors(Role.LEADER));
        for (Role role : List.of(Role.ADMIN, Role.MOD, Role.MEMBER, Role.COOP)) {
            assertFalse(IslandChatRules.canPickColors(role), role.name());
        }
        assertFalse(IslandChatRules.canPickColors(null));
    }

    @Test
    void parsesNamedAndHexColors() {
        assertEquals(NamedTextColor.GOLD, IslandChatRules.parse("gold"));
        assertEquals(NamedTextColor.LIGHT_PURPLE, IslandChatRules.parse(" Light_Purple "));
        assertEquals(TextColor.color(0xFF8A1F), IslandChatRules.parse("#FF8A1F"));
        assertNull(IslandChatRules.parse("#FFF"));
        assertNull(IslandChatRules.parse("pink"));
        assertNull(IslandChatRules.parse(""));
        assertNull(IslandChatRules.parse(null));
    }

    @Test
    void paletteSkipsInvalidAndDuplicateEntries() {
        IslandChatRules.Palette palette = IslandChatRules.palette(List.of("gold", "GOLD", "pink", "#4FD1FF", "aqua"));
        assertEquals(List.of("gold", "#4fd1ff", "aqua"), palette.colors());
        assertEquals(2, palette.problems().size());
        IslandChatRules.Palette empty = IslandChatRules.palette(List.of("nope"));
        assertTrue(empty.colors().isEmpty());
        assertEquals(2, empty.problems().size()); // the bad entry + "empty"
        assertEquals(8, IslandChatRules.palette(PALETTE).colors().size());
        assertTrue(IslandChatRules.palette(PALETTE).problems().isEmpty());
    }

    @Test
    void nextColorCyclesThroughThePalette() {
        assertEquals("red", IslandChatRules.next(PALETTE, "gold"));
        assertEquals("gold", IslandChatRules.next(PALETTE, "white")); // wraps
        assertEquals("gold", IslandChatRules.next(PALETTE, "dark_red")); // not in the palette: first one
        assertEquals("red", IslandChatRules.next(PALETTE, " GOLD "));
        assertEquals("gold", IslandChatRules.next(List.of(), "gold")); // nothing to pick: unchanged
    }

    @Test
    void effectiveColorFallsBackToTheDefault() {
        assertEquals("aqua", IslandChatRules.effective("aqua", PALETTE, "gold"));
        assertEquals("gold", IslandChatRules.effective(null, PALETTE, "gold"));
        assertEquals("gold", IslandChatRules.effective("dark_red", PALETTE, "gold")); // removed from the palette later
        assertEquals("white", IslandChatRules.effective(null, PALETTE, "nonsense")); // broken default
        assertEquals("#4fd1ff", IslandChatRules.effective(null, PALETTE, "#4FD1FF"));
    }

    @Test
    void spiesAreStaffWhoAreNotRecipients() {
        List<String> online = List.of("leader", "member", "helper", "admin", "player");
        Set<String> recipients = Set.of("leader", "member", "admin");
        Set<String> spying = Set.of("helper", "admin");
        assertEquals(List.of("helper"), IslandChatRules.spies(online, recipients, spying::contains));
        assertEquals(List.of(), IslandChatRules.spies(online, recipients, p -> false));
    }

    @Test
    void oneShotTextIsTrimmedAndCut() {
        assertEquals("hi", IslandChatRules.limit("  hi  ", 256));
        assertEquals("abc", IslandChatRules.limit("abcdef", 3));
        assertEquals("", IslandChatRules.limit(null, 10));
    }

    @Test
    void everyPaletteColorHasAWool() {
        for (String color : PALETTE) {
            assertTrue(IslandChatRules.wool(color).endsWith("_wool"), color);
        }
        assertEquals("orange_wool", IslandChatRules.wool("gold"));
        assertEquals("magenta_wool", IslandChatRules.wool("light_purple"));
        assertEquals("white_wool", IslandChatRules.wool("#123456"));
    }

    @Test
    void menuLayoutUsesEachContentSlotOnce() {
        Set<Integer> inner = new java.util.HashSet<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                inner.add(row * 9 + col);
            }
        }
        Set<Integer> seen = new java.util.HashSet<>();
        for (int slot : IslandMenu.CONTENT) {
            assertTrue(inner.contains(slot), "slot " + slot + " is on the frame");
            assertTrue(seen.add(slot), "slot " + slot + " used twice");
        }
        assertFalse(seen.contains(IslandMenu.SLOT_ISLAND));
        Set<Integer> colorSlots = new java.util.HashSet<>(ChatColorsMenu.SLOTS.values());
        assertEquals(IslandChatRules.COLOR_ROLES.size(), colorSlots.size());
        assertTrue(inner.containsAll(colorSlots));
        assertEquals(Set.copyOf(IslandChatRules.COLOR_ROLES), ChatColorsMenu.SLOTS.keySet());
    }
}
