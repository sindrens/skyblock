package net.skybornmc.skyblock.crates;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.3: which show an open gets, the reveal happens once, and the show only starts after the commit. */
class CrateShowTest {

    @Test
    void onlySingleOpensAnimate() {
        assertEquals(CrateShow.Mode.INSTANT, CrateShow.mode(10, CrateShow.Kind.MENU, false, true, true), "bulk = summary");
        assertEquals(CrateShow.Mode.INSTANT, CrateShow.mode(2, CrateShow.Kind.PEDESTAL, false, true, true));
        assertEquals(CrateShow.Mode.SPINNER, CrateShow.mode(1, CrateShow.Kind.MENU, false, true, true));
        assertEquals(CrateShow.Mode.WORLD, CrateShow.mode(1, CrateShow.Kind.PEDESTAL, false, true, true));
        assertEquals(CrateShow.Mode.INSTANT, CrateShow.mode(1, CrateShow.Kind.NONE, false, true, true));
    }

    @Test
    void fastOpeningAndSwitchesSkipTheSpin() {
        assertEquals(CrateShow.Mode.INSTANT, CrateShow.mode(1, CrateShow.Kind.MENU, true, true, true), "Fast crate opening");
        assertEquals(CrateShow.Mode.INSTANT, CrateShow.mode(1, CrateShow.Kind.MENU, false, false, true), "spinner off");
        assertEquals(CrateShow.Mode.WORLD, CrateShow.mode(1, CrateShow.Kind.PEDESTAL, true, true, true),
                "fast at a pedestal: no build-up, the reward still floats");
        assertEquals(CrateShow.Mode.INSTANT, CrateShow.mode(1, CrateShow.Kind.PEDESTAL, false, true, false), "crate yard off");
    }

    @Test
    void revealHappensOnce() {
        CrateShow.Reveal r = new CrateShow.Reveal();
        assertFalse(r.done());
        assertTrue(r.claim(), "end of the spin");
        assertFalse(r.claim(), "a skip click after the end");
        assertFalse(r.claim(), "closing the menu after the end");
        assertTrue(r.done());
    }

    @Test
    void broadcastsOnlyListedCrates() {
        List<String> crates = new CratesSettings().broadcastCrates;
        assertTrue(CrateShow.broadcasts(crates, "inferno"));
        assertTrue(CrateShow.broadcasts(crates, "phoenix"));
        assertTrue(CrateShow.broadcasts(crates, "eclipse"));
        assertTrue(CrateShow.broadcasts(crates, "conqueror"));
        assertFalse(CrateShow.broadcasts(crates, "ember"));
        assertFalse(CrateShow.broadcasts(crates, "blaze"));
        assertFalse(CrateShow.broadcasts(null, "inferno"));
        assertTrue(CrateShow.broadcasts(List.of(" Inferno "), "inferno"));
    }

    @Test
    void soundSettings() {
        var hat = CrateShow.sound(new CratesSettings().spinnerTickSound).orElseThrow();
        assertEquals("minecraft:block.note_block.hat", hat.name().asString());
        assertEquals(0.35f, hat.volume(), 1e-6);
        assertEquals(1.6f, hat.pitch(), 1e-6);
        assertTrue(CrateShow.sound("click").isEmpty(), "a sounds.yml key");
        assertTrue(CrateShow.sound("minecraft:x 99 1").isEmpty(), "volume out of range");
        assertTrue(CrateShow.sound("minecraft:x 1 abc").isEmpty());
        assertTrue(CrateShow.sound(null).isEmpty());
    }

    /**
     * The reward is decided and saved before any animation: the opener starts the show only in opened() (after the
     * commit succeeded), after the audit and the hand-out, never before the transaction.
     */
    @Test
    void showStartsOnlyAfterTheCommitAndHandOut() throws Exception {
        String src = Files.readString(Path.of("src/main/java/net/skybornmc/skyblock/crates/CrateOpener.java"));
        int opened = src.indexOf("private void opened(");
        assertTrue(opened > 0);
        int body = src.indexOf('{', opened);
        int handOut = src.indexOf("handOut(plan", body);
        int present = src.indexOf("m.show().present(", body);
        assertTrue(handOut > 0 && present > handOut, "present() comes after handOut() in opened()");
        assertEquals(present, src.indexOf("m.show().present("), "present() is called nowhere else");
        int commit = src.indexOf("OpenEngine.commit(");
        int okCase = src.indexOf("case OK ->");
        assertTrue(commit > 0 && okCase > commit && src.indexOf("opened(out.plan()", okCase) > okCase,
                "opened() only runs for a committed open (status OK)");
    }
}
