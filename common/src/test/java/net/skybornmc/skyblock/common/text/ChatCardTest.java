package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCardTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void charactersHaveTheDefaultFontWidths() {
        assertEquals(6, ChatWidth.advance('A', false));
        assertEquals(7, ChatWidth.advance('A', true));
        assertEquals(2, ChatWidth.advance('i', false));
        assertEquals(3, ChatWidth.advance('l', false));
        assertEquals(4, ChatWidth.advance('t', false));
        assertEquals(4, ChatWidth.advance('I', false));
        assertEquals(5, ChatWidth.advance('f', false));
        assertEquals(4, ChatWidth.advance(' ', false));
        assertEquals(5, ChatWidth.advance(' ', true));
        assertEquals(2, ChatWidth.advance('.', false));
        assertEquals(7, ChatWidth.advance('@', false));
        assertEquals(ChatWidth.WIDE, ChatWidth.advance('✦', false));
        assertEquals(ChatWidth.NORMAL, ChatWidth.advance('»', false));
    }

    @Test
    void textWidthAddsAdvancesAndBoldAddsOnePerCharacter() {
        assertEquals(0, ChatWidth.width("", false));
        assertEquals(0, ChatWidth.width(null, true));
        // S H O P = 4 x 6
        assertEquals(24, ChatWidth.width("SHOP", false));
        assertEquals(28, ChatWidth.width("SHOP", true));
        // "lit" = 3 + 2 + 4
        assertEquals(9, ChatWidth.width("lit", false));
        // a surrogate pair counts once
        assertEquals(ChatWidth.WIDE, ChatWidth.width(new String(Character.toChars(0x1F525)), false));
    }

    @Test
    void padCentresInTheChatBox() {
        // 320 - 24 = 296 free, half 148, 37 spaces
        assertEquals(37, ChatWidth.padSpaces(24, 320));
        assertEquals(0, ChatWidth.padSpaces(320, 320));
        assertEquals(0, ChatWidth.padSpaces(400, 320));
        assertEquals(1, ChatWidth.padSpaces(314, 320)); // 3 px each side rounds to one space
        assertEquals(0, ChatWidth.padSpaces(318, 320));
        assertEquals(" ".repeat(40), ChatWidth.pad(0));
    }

    @Test
    void componentWidthFollowsInheritedBold() {
        Component plain = Component.text("SHOP");
        assertEquals(24, ChatCard.width(plain));
        Component boldParent = Component.text("").decoration(TextDecoration.BOLD, true)
                .append(Component.text("SH")).append(Component.text("OP").decoration(TextDecoration.BOLD, false));
        // SH bold (14) + OP not bold (12)
        assertEquals(26, ChatCard.width(boldParent));
        assertEquals(0, ChatCard.width(null));
    }

    @Test
    void cardHasBlankLinesCentredLinesAndAClickableAction() {
        Component card = ChatCard.render(Component.text("SHOP"), List.of(Component.text("Need blocks?"), Component.empty()),
                Component.text("» /shop «"), ClickEvent.runCommand("/shop"), Component.text("Click to open the shop"), true);
        String[] lines = plain(card).split("\n", -1);
        assertEquals(5, lines.length, "blank, title, one body line (the empty one is skipped), action, blank");
        assertTrue(lines[0].isBlank());
        assertTrue(lines[4].isBlank());
        assertEquals(" ".repeat(ChatWidth.padSpaces(ChatWidth.width("SHOP", true), 320)) + "SHOP", lines[1]);
        assertTrue(lines[2].startsWith(" ") && lines[2].endsWith("Need blocks?"));
        assertTrue(lines[3].trim().equals("» /shop «"));
    }

    @Test
    void bedrockCardsAreLeftAligned() {
        Component card = ChatCard.render(Component.text("SHOP"), List.of(Component.text("Need blocks?")),
                Component.text("» /shop «"), null, null, false);
        String[] lines = plain(card).split("\n", -1);
        assertEquals("SHOP", lines[1]);
        assertEquals("Need blocks?", lines[2]);
        assertEquals("» /shop «", lines[3]);
        assertFalse(lines[1].startsWith(" "));
    }

    @Test
    void titleIsBoldUnlessItSaysOtherwise() {
        Component line = ChatCard.line(Component.text("SHOP").decorationIfAbsent(TextDecoration.BOLD, TextDecoration.State.TRUE), false);
        assertEquals(TextDecoration.State.TRUE, line.decoration(TextDecoration.BOLD));
        Component notBold = Component.text("SHOP").decoration(TextDecoration.BOLD, false)
                .decorationIfAbsent(TextDecoration.BOLD, TextDecoration.State.TRUE);
        assertEquals(TextDecoration.State.FALSE, notBold.decoration(TextDecoration.BOLD));
    }
}
