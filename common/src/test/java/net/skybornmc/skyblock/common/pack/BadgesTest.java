package net.skybornmc.skyblock.common.pack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The badge string builder: gap, caps, fonts, gradient, Bedrock and no-pack fallbacks. */
class BadgesTest {

    private static final String GAP = "";
    private static final BadgeOptions PACK = new BadgeOptions(true, false, false, false, false, List.of());

    static PackIds ids;

    @BeforeAll
    static void load() throws IOException {
        ids = PackIds.parse(new StringReader("""
                version: "1"
                badges:
                  letter-gap: "\\uF801"
                  ranks:
                    default: {font: "phoenix:badge", color: "#B5ABA4", shine: false, family: "free", bedrock: "&7", caps: ["\\uE000", "\\uE001"]}
                    hatchling: {font: "phoenix:badge", color: "#FF9A4D", shine: false, family: "bought", bedrock: "&6", caps: ["\\uE002", "\\uE003"]}
                    pyrelord: {font: "phoenix:badge_shine", color: "#FF3B4A", shine: true, family: "bought", bedrock: "&c", caps: ["\\uE006", "\\uE007"]}
                    immortal: {font: "phoenix:badge_shine", color: "#FFE27A,#FF8C1A,#FF2D3F", cap-color: "#FFD21A", shine: true, family: "bought", bedrock: "&e&l>&6&l>&c&l", caps: ["\\uE008", "\\uE009"]}
                    firecrown: {font: "phoenix:badge_shine", color: "#FFFFFF", cap-color: "#FFD21A", shine: true, family: "won", bedrock: "&f&l", caps: ["\\uE00A", "\\uE00B"]}
                    owner: {font: "phoenix:badge_solid", color: "#FFC21A", shine: false, family: "staff", bedrock: "&e&l", caps: ["\\uE00E", "\\uE00F"]}
                """));
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static BadgeOptions with(boolean reduced, boolean simple, boolean large) {
        return new BadgeOptions(true, false, reduced, simple, large, List.of());
    }

    @Test
    void capsAndLettersWithTheGapBetweenEveryGlyph() {
        Component c = Badges.render(ids, "hatchling", "Hat", PACK);
        assertEquals("" + GAP + "H" + GAP + "a" + GAP + "t" + GAP + "", plain(c));
    }

    @Test
    void shadowIsOffAndTheBadgeFontIsSet() {
        Component c = Badges.render(ids, "hatchling", "Hatchling", PACK);
        assertEquals(ShadowColor.none(), c.shadowColor());
        assertEquals("phoenix:badge", c.font().asString());
    }

    @Test
    void singleColourPutsEverythingInOneRun() {
        Component c = Badges.render(ids, "hatchling", "Hatchling", PACK);
        assertEquals(1, c.children().size());
        assertEquals(TextColor.color(0xFF9A4D), c.children().getFirst().color());
    }

    @Test
    void capColourIsSeparateWhenSet() {
        Component c = Badges.render(ids, "firecrown", "Crown", PACK);
        List<Component> parts = c.children();
        assertEquals(3, parts.size());
        assertEquals(TextColor.color(0xFFD21A), parts.getFirst().color());
        assertEquals(TextColor.color(0xFFFFFF), parts.get(1).color());
        assertEquals(TextColor.color(0xFFD21A), parts.getLast().color());
    }

    @Test
    void fontSelection() {
        // shine only for shining ranks and only without reduced motion
        assertEquals("phoenix:badge_shine", Badges.render(ids, "pyrelord", "X", PACK).font().asString());
        assertEquals("phoenix:badge", Badges.render(ids, "pyrelord", "X", with(true, false, false)).font().asString());
        assertEquals("phoenix:badge", Badges.render(ids, "hatchling", "X", PACK).font().asString());
        // large inserts _large
        assertEquals("phoenix:badge_large", Badges.render(ids, "hatchling", "X", with(false, false, true)).font().asString());
        assertEquals("phoenix:badge_large_shine", Badges.render(ids, "pyrelord", "X", with(false, false, true)).font().asString());
        assertEquals("phoenix:badge_large", Badges.render(ids, "pyrelord", "X", with(true, false, true)).font().asString());
        // staff are solid, never shine, large keeps solid
        assertEquals("phoenix:badge_solid", Badges.render(ids, "owner", "Owner", PACK).font().asString());
        assertEquals("phoenix:badge_solid", Badges.render(ids, "owner", "Owner", with(true, false, false)).font().asString());
        assertEquals("phoenix:badge_large_solid", Badges.render(ids, "owner", "Owner", with(false, false, true)).font().asString());
    }

    @Test
    void gradientRunsAcrossTheLettersAndCapsUseTheCapColour() {
        Component c = Badges.render(ids, "immortal", "Immortal", PACK);
        List<Component> parts = c.children();
        // left cap, 8 letters (each its own colour), right cap
        assertEquals(10, parts.size());
        assertEquals(TextColor.color(0xFFD21A), parts.getFirst().color());
        assertEquals(TextColor.color(0xFFD21A), parts.getLast().color());
        assertEquals(TextColor.color(0xFFE27A), parts.get(1).color());
        assertEquals(TextColor.color(0xFF2D3F), parts.get(8).color());
        assertNotEquals(parts.get(2).color(), parts.get(3).color());
    }

    @Test
    void approvedGradientOverridesOnlyImmortal() {
        List<TextColor> frost = List.of(TextColor.color(0xA8DFFF), TextColor.color(0x2B2FA8));
        BadgeOptions o = PACK.withGradient(frost);
        Component immortal = Badges.render(ids, "immortal", "Ab", o);
        assertEquals(TextColor.color(0xA8DFFF), immortal.children().get(1).color());
        assertEquals(TextColor.color(0x2B2FA8), immortal.children().get(2).color());
        assertEquals(TextColor.color(0xFFD21A), immortal.children().getFirst().color()); // caps keep their colour
        Component other = Badges.render(ids, "hatchling", "Ab", o);
        assertEquals(TextColor.color(0xFF9A4D), other.children().getFirst().color());
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
    void nameColourIsTheMiddleOfTheGradient() {
        assertEquals(TextColor.color(0xFF8C1A), Badges.nameColor(ids, "immortal", BadgeOptions.PLAIN));
        assertEquals(TextColor.color(0xFF9A4D), Badges.nameColor(ids, "hatchling", BadgeOptions.PLAIN));
        assertEquals(TextColor.color(0xB5ABA4), Badges.nameColor(ids, "no-such-group", BadgeOptions.PLAIN));
    }

    @Test
    void unknownGroupGetsTheDefaultLook() {
        Component c = Badges.render(ids, "something-new", "Something", PACK);
        assertEquals("", plain(c).substring(0, 1));
        assertEquals(TextColor.color(0xB5ABA4), c.children().getFirst().color());
    }

    @Test
    void otherCharactersAreLeftOut() {
        assertEquals("Ab 1+-!'.", Badges.glyphLetters("Ab 1+-!'.&é#"));
        assertEquals("" + GAP + "A" + GAP + "b" + GAP + "",
                plain(Badges.render(ids, "default", "A&éb", PACK)));
    }

    @Test
    void simpleBadgeIsPlainBracketText() {
        assertEquals("[Hatchling]", plain(Badges.render(ids, "hatchling", "Hatchling", with(false, true, false))));
        Component c = Badges.render(ids, "hatchling", "Hatchling", with(false, true, true));
        assertEquals(TextColor.color(0xFF9A4D), c.color());
        assertNull(c.font());
    }

    @Test
    void simpleGradientColoursEachCharacter() {
        Component c = Badges.render(ids, "immortal", "Imm", with(false, true, false));
        assertEquals("[Imm]", plain(c));
        assertEquals(5, c.children().size());
        assertEquals(TextColor.color(0xFFE27A), c.children().getFirst().color());
        assertEquals(TextColor.color(0xFF2D3F), c.children().getLast().color());
    }

    @Test
    void javaWithoutThePackGetsPlainText() {
        for (String g : new String[]{"hatchling", "immortal", "owner", "unknown"}) {
            String text = plain(Badges.render(ids, g, "Name", BadgeOptions.PLAIN));
            assertEquals("[Name]", text, g);
            assertFalse(PrivateUse.contains(text), g);
        }
    }

    @Test
    void bedrockUsesLegacyCodesAndNeverPrivateUse() {
        BadgeOptions bedrock = new BadgeOptions(true, true, false, false, false, List.of());
        assertFalse(bedrock.pack()); // a Bedrock viewer never counts as a pack viewer
        Component c = Badges.render(ids, "hatchling", "Hatchling", bedrock);
        assertEquals("[Hatchling]", plain(c));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GOLD, c.children().isEmpty() ? c.color() : c.children().getFirst().color());
        String owner = plain(Badges.render(ids, "owner", "Owner", bedrock));
        assertEquals("[Owner]", owner);
        assertFalse(PrivateUse.contains(owner));
    }

    @Test
    void bedrockGradientSplitsTheNameAcrossTheCodes() {
        BadgeOptions bedrock = new BadgeOptions(false, true, false, false, false, List.of());
        Component c = Badges.render(ids, "immortal", "Immortal", bedrock);
        assertEquals("[Immortal]", plain(c));
        List<TextColor> colours = new java.util.ArrayList<>();
        for (Component part : c.children()) {
            if (part.color() != null && !colours.contains(part.color())) {
                colours.add(part.color());
            }
        }
        assertEquals(3, colours.size());
        assertFalse(PrivateUse.contains(plain(c)));
    }

    @Test
    void bedrockLineHelperMatchesTheBedrockForm() {
        assertEquals("[Hatchling]", plain(Badges.bedrockLine(ids, "hatchling", "Hatchling")));
    }

    @Test
    void blankOrFakedNamesFallBackToTheGroup() {
        assertEquals("[Hatchling]", plain(Badges.render(ids, "hatchling", "", BadgeOptions.PLAIN)));
        assertEquals("[Hatchling]", plain(Badges.render(ids, "hatchling", null, BadgeOptions.PLAIN)));
        assertEquals("[Hatchling]", plain(Badges.render(ids, "hatchling", "", BadgeOptions.PLAIN)));
        assertEquals("[6Hatchling]", plain(Badges.render(ids, "hatchling", "&6Hatchling", BadgeOptions.PLAIN))); // codes can't leak in
    }

    @Test
    void optionsKeysDifferPerVariant() {
        assertNotEquals(PACK.key(), with(true, false, false).key());
        assertNotEquals(PACK.key(), with(false, true, false).key());
        assertNotEquals(PACK.key(), with(false, false, true).key());
        assertNotEquals(PACK.key(), BadgeOptions.PLAIN.key());
        assertNotEquals(PACK.key(), PACK.withGradient(List.of(TextColor.color(1), TextColor.color(2))).key());
        assertEquals(PACK.key(), PACK.withGradient(List.of(TextColor.color(1))).viewerOnly().key());
    }

    @Test
    void realGeneratedFileRendersEveryBadgeInEveryForm() throws IOException {
        PackIds real;
        try (var r = new java.io.FileReader(java.nio.file.Path.of("../plugin/src/main/resources/pack/pack-ids.yml").toFile(),
                java.nio.charset.StandardCharsets.UTF_8)) {
            real = PackIds.parse(r);
        }
        for (String g : real.badges().keySet()) {
            for (boolean reduced : new boolean[]{false, true}) {
                for (boolean large : new boolean[]{false, true}) {
                    Component c = Badges.render(real, g, "Test", new BadgeOptions(true, false, reduced, false, large, List.of()));
                    assertTrue(PrivateUse.contains(plain(c)), g);
                    assertEquals(ShadowColor.none(), c.shadowColor());
                    assertTrue(c.font().asString().startsWith("phoenix:badge"), g);
                }
            }
            assertFalse(PrivateUse.contains(plain(Badges.render(real, g, "Test", BadgeOptions.PLAIN))), g);
            assertFalse(PrivateUse.contains(plain(Badges.bedrockLine(real, g, "Test"))), g);
        }
    }
}
