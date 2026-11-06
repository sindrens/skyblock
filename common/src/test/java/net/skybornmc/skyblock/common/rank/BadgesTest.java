package net.skybornmc.skyblock.common.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.text.PrivateUse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The rank badge as text (I7 phase 0): {@code [Name]} in the rank colour or gradient, and the Bedrock legacy form. */
class BadgesTest {

    private static final RankLooks LOOKS = RankLooks.defaults();

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void goldenNameColourPerRank() {
        assertEquals(TextColor.color(0x555555), Badges.nameColor(LOOKS, "default", BadgeOptions.PLAIN));
        assertEquals(TextColor.color(0xE3BE7A), Badges.nameColor(LOOKS, "hatchling", BadgeOptions.PLAIN));
        assertEquals(TextColor.color(0xFF8C1A), Badges.nameColor(LOOKS, "immortal", BadgeOptions.PLAIN));
        assertEquals(TextColor.color(0xFFFF55), Badges.nameColor(LOOKS, "owner", BadgeOptions.PLAIN));
        assertEquals(TextColor.color(0x55FFFF), Badges.nameColor(LOOKS, "mod", BadgeOptions.PLAIN));
        assertEquals(TextColor.color(0x55FF55), Badges.nameColor(LOOKS, "helper", BadgeOptions.PLAIN));
        assertEquals(TextColor.color(0xFFAA00), Badges.nameColor(LOOKS, "builder", BadgeOptions.PLAIN));
    }

    @Test
    void unknownGroupGetsTheDefaultLook() {
        assertEquals(TextColor.color(0x555555), Badges.nameColor(LOOKS, "no-such-group", BadgeOptions.PLAIN));
        Component c = Badges.render(LOOKS, "something-new", "Something", BadgeOptions.PLAIN);
        assertEquals("Something", plain(c));
        assertEquals(TextColor.color(0x555555), c.color());
    }

    @Test
    void singleColourIsOneText() {
        Component c = Badges.render(LOOKS, "hatchling", "Hatchling", BadgeOptions.PLAIN);
        assertEquals("Hatchling", plain(c));
        assertEquals(TextColor.color(0xE3BE7A), c.color());
        assertNull(c.font());
    }

    @Test
    void threeColourGradientColoursEachCharacter() {
        Component c = Badges.render(LOOKS, "immortal", "Imm", BadgeOptions.PLAIN);
        assertEquals("Imm", plain(c));
        assertEquals(3, c.children().size(), "one child per character, no brackets");
        assertEquals(TextColor.color(0xFFE27A), c.children().getFirst().color());
        assertEquals(TextColor.color(0xFF8C1A), c.children().get(1).color());
        assertEquals(TextColor.color(0xFF2D3F), c.children().getLast().color());
    }

    @Test
    void approvedGradientOverridesOnlyImmortal() {
        List<TextColor> frost = List.of(TextColor.color(0xA8DFFF), TextColor.color(0x2B2FA8));
        BadgeOptions o = BadgeOptions.PLAIN.withGradient(frost);
        Component immortal = Badges.render(LOOKS, "immortal", "Ab", o);
        assertEquals(TextColor.color(0xA8DFFF), immortal.children().getFirst().color());
        assertEquals(TextColor.color(0x2B2FA8), immortal.children().getLast().color());
        Component other = Badges.render(LOOKS, "hatchling", "Ab", o);
        assertEquals(TextColor.color(0xE3BE7A), other.color());
    }

    @Test
    void gradientSpreadsEvenlyAcrossThreeStops() {
        List<TextColor> g = Badges.gradient(List.of(TextColor.color(0x000000), TextColor.color(0xFF0000), TextColor.color(0x0000FF)), 5);
        assertEquals(TextColor.color(0x000000), g.get(0));
        assertEquals(TextColor.color(0xFF0000), g.get(2));
        assertEquals(TextColor.color(0x0000FF), g.get(4));
        assertEquals(List.of(TextColor.color(0x123456)), Badges.gradient(List.of(TextColor.color(0x123456)), 1));
    }

    @Test
    void bedrockUsesLegacyCodesAndNeverPrivateUse() {
        Component c = Badges.render(LOOKS, "hatchling", "Hatchling", BadgeOptions.BEDROCK);
        assertEquals("Hatchling", plain(c));
        assertEquals(NamedTextColor.YELLOW, c.children().isEmpty() ? c.color() : c.children().getFirst().color());
        String owner = plain(Badges.render(LOOKS, "owner", "Owner", BadgeOptions.BEDROCK));
        assertEquals("Owner", owner);
        assertFalse(PrivateUse.contains(owner));
    }

    @Test
    void bedrockSegmentsSplitTheNameAcrossTheCodes() {
        Component c = Badges.render(LOOKS, "immortal", "Immortal", BadgeOptions.BEDROCK);
        assertEquals("Immortal", plain(c));
        List<TextColor> colours = new java.util.ArrayList<>();
        for (Component part : c.children()) {
            if (part.color() != null && !colours.contains(part.color())) {
                colours.add(part.color());
            }
        }
        assertEquals(List.of(NamedTextColor.YELLOW, NamedTextColor.GOLD, NamedTextColor.RED), colours);
    }

    @Test
    void everyDefaultLookRendersCleanTextInBothForms() {
        for (String g : LOOKS.groups()) {
            assertEquals("Test", plain(Badges.render(LOOKS, g, "Test", BadgeOptions.PLAIN)), g);
            assertEquals("Test", plain(Badges.render(LOOKS, g, "Test", BadgeOptions.BEDROCK)), g);
        }
    }

    @Test
    void cleanNameStripsPrivateUseAndCodesAndIsNeverBlank() {
        assertEquals("Hatchling", plain(Badges.render(LOOKS, "hatchling", "", BadgeOptions.PLAIN)));
        assertEquals("Hatchling", plain(Badges.render(LOOKS, "hatchling", null, BadgeOptions.PLAIN)));
        assertEquals("Hatchling", plain(Badges.render(LOOKS, "hatchling", "\uE000", BadgeOptions.PLAIN)));
        assertEquals("6Hatchling", plain(Badges.render(LOOKS, "hatchling", "&6Hatchling", BadgeOptions.PLAIN))); // codes can't leak in
        assertEquals("Ab", plain(Badges.render(LOOKS, "default", "A\uE001b", BadgeOptions.PLAIN)));
    }

    @Test
    void optionsKeysDifferPerVariant() {
        assertNotEquals(BadgeOptions.PLAIN.key(), BadgeOptions.BEDROCK.key());
        BadgeOptions graded = BadgeOptions.PLAIN.withGradient(List.of(TextColor.color(1), TextColor.color(2)));
        assertNotEquals(BadgeOptions.PLAIN.key(), graded.key());
        assertEquals(BadgeOptions.PLAIN.key(), graded.viewerOnly().key());
    }
}
