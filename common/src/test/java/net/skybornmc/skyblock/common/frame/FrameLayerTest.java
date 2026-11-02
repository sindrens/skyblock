package net.skybornmc.skyblock.common.frame;

import net.skybornmc.skyblock.common.pack.PackService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI-FONT section 10 (pure parts of the frame layer): the glyph gate, the switch dependencies, the title throttle, the
 * space glyph moves and the plaque fit, against the bundled frame-metrics.json.
 */
class FrameLayerTest {

    private static FrameMetrics metrics() {
        List<String> problems = new ArrayList<>();
        FrameMetrics m = FrameMetrics.bundled(FrameLayerTest.class.getClassLoader(), problems::add).orElse(null);
        assertTrue(m != null && problems.isEmpty(), () -> "bundled metrics: " + problems);
        return m;
    }

    @Test
    void glyphsOnlyForJavaWithTheLoadedPack() {
        assertTrue(Glyphs.allowed(PackService.Status.LOADED, false, false, true));
        for (PackService.Status s : PackService.Status.values()) {
            if (s != PackService.Status.LOADED) {
                assertFalse(Glyphs.allowed(s, false, false, true), s.name());
            }
        }
        assertFalse(Glyphs.allowed(null, false, false, true), "unknown pack state");
        assertFalse(Glyphs.allowed(PackService.Status.LOADED, true, false, true), "Bedrock");
        assertFalse(Glyphs.allowed(PackService.Status.LOADED, false, true, true), "Plain menus");
        assertFalse(Glyphs.allowed(PackService.Status.LOADED, false, false, false), "kill switch");
    }

    @Test
    void switchesDependOnPanelsAndTheMasterSwitch() {
        FrameSwitches noPanels = new FrameSwitches(true, false, true, true, true);
        assertFalse(noPanels.titlesOn(), "titles need panels");
        assertFalse(noPanels.tilesOn(), "tiles need panels");
        assertTrue(noPanels.logoOn(), "the logo does not need panels");
        FrameSwitches off = new FrameSwitches(false, true, true, true, true);
        assertFalse(off.panelsOn() || off.titlesOn() || off.tilesOn() || off.logoOn(), "the master switch turns everything off");
        assertTrue(FrameSwitches.ALL.titlesOn() && FrameSwitches.ALL.tilesOn());
    }

    @Test
    void oneTitleSwapPerWindow() {
        assertTrue(TitleThrottle.allow(1_000, 0, 250), "never sent");
        assertFalse(TitleThrottle.allow(1_100, 1_000, 250));
        assertTrue(TitleThrottle.allow(1_250, 1_000, 250));
    }

    @Test
    void spaceMovesAddUpExactly() {
        FrameMetrics m = metrics();
        for (int dx = -400; dx <= 400; dx++) {
            String move = Space.move(m, dx);
            int sum = 0;
            for (char c : move.toCharArray()) {
                sum += step(m, c);
            }
            assertEquals(dx, sum, "move " + dx);
            if (Math.abs(dx) < 256) {
                assertTrue(move.length() <= 8, "at most 8 glyphs for " + dx + ": " + move.length());
            }
        }
        assertEquals("", Space.move(m, 0));
    }

    private static int step(FrameMetrics m, char c) {
        for (FrameMetrics.SpaceGlyph g : m.positive()) {
            if (g.cp() == c) {
                return g.step();
            }
        }
        for (FrameMetrics.SpaceGlyph g : m.negative()) {
            if (g.cp() == c) {
                return g.step() > 0 ? -g.step() : g.step();
            }
        }
        throw new AssertionError("not a space glyph: " + (int) c);
    }

    @Test
    void plaqueNounsAreSanitisedAndFit() {
        FrameMetrics m = metrics();
        assertEquals("STONE & EARTH", PlaqueFit.sanitize("  stone   & earth "));
        assertEquals("", PlaqueFit.sanitize("Pöt"), "letters the font lacks");
        assertEquals("", PlaqueFit.sanitize("<red>SHOP"), "no markup");
        assertEquals("", PlaqueFit.sanitize("ABCDEFGHIJKLMNOPQ"), "17 letters");
        assertEquals("ABCDEFGH IJKLMNOP", PlaqueFit.sanitize("abcdefgh ijklmnop"), "16 letters plus a space");
        PlaqueFit.Fit shop = PlaqueFit.fit(m, "SHOP").orElseThrow();
        assertEquals(PlaqueSize.S, shop.size(), "a short noun takes the small plaque");
        assertEquals((m.windowWidth() - m.plaque(PlaqueSize.S).width()) / 2, shop.plaqueX(), "centred in the window");
        assertTrue(PlaqueFit.fit(m, "").isEmpty());
    }
}
