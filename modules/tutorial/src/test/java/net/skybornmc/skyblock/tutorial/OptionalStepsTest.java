package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.tutorial.TutorialService.State;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TU6 optional steps: the O1-O9 lines, counting, visiting, tips. Also Pip's flight (TU5). */
class OptionalStepsTest {

    @Test
    void defaultStepsAreThePlansNine() {
        TutorialConfig c = new TutorialConfig();
        OptionalStep.Parsed p = OptionalStep.parse(c.optionalSteps, c.optionalReward);
        assertEquals(List.of(), p.problems());
        assertEquals(List.of("generator", "spawn_tour", "settings", "skills", "team", "upgrades", "enchanter", "wardrobe", "warzone"),
                p.steps().stream().map(OptionalStep::id).toList());
        OptionalStep generator = p.byId().get("generator");
        assertEquals(OptionalStep.Kind.BREAK, generator.kind());
        assertEquals(5, generator.needed());
        assertTrue(generator.islandOnly());
        assertEquals(5, generator.reward(), "TQ8: 5 crystals each");
        OptionalStep tour = p.byId().get("spawn_tour");
        assertEquals(4, tour.needed());
        assertEquals("market", tour.camera());
        assertEquals("npc:shop", tour.firstTarget());
        assertFalse(p.byId().get("upgrades").enabled(), "upgrades stay hidden until Phase 5 exists");
        assertEquals("role:enchanter", p.byId().get("enchanter").firstTarget());
        assertEquals(OptionalStep.Tip.NEAR, p.byId().get("warzone").tip());
        assertEquals(1, p.byId().get("settings").needed());
        assertNull(p.byId().get("settings").firstTarget());
    }

    @Test
    void badLinesAreReported() {
        OptionalStep.Parsed p = OptionalStep.parse(List.of(
                "ok: menu settings reward=2",
                "ok: menu skills",
                "no colon here",
                "Bad Id: menu x",
                "x1: fly away",
                "x2: break",
                "x3: break stone count=0",
                "x4: visit nowhere",
                "x5: near spawn:0,0,0 spawn:1,1,1",
                "x6: menu settings tip=sometimes",
                "x7: menu settings goto=nowhere",
                "x8: menu settings reward=-1",
                "x9: break stone stone",
                "x10: near spawn:0,0,0 radius=0"), 5);
        assertEquals(1, p.steps().size());
        assertEquals(2, p.steps().getFirst().reward());
        assertEquals(13, p.problems().size(), String.join("\n", p.problems()));
    }

    @Test
    void optionalStepsCountAfterTheTutorial() {
        assertTrue(OptionalRules.counts(State.FINISHED, false));
        assertTrue(OptionalRules.counts(State.LEGACY, false));
        assertTrue(OptionalRules.counts(State.STAFF_SKIPPED, false));
        assertFalse(OptionalRules.counts(State.ACTIVE, false), "not during the core tutorial by default");
        assertTrue(OptionalRules.counts(State.ACTIVE, true));
        assertFalse(OptionalRules.counts(State.NEW, true));
        assertFalse(OptionalRules.counts(null, true));
    }

    @Test
    void countingAndVisiting() {
        assertEquals(3, OptionalRules.addCount(2, 1, 5));
        assertEquals(5, OptionalRules.addCount(4, 9, 5), "never above the goal");
        assertEquals(2, OptionalRules.addCount(2, 0, 5));
        assertEquals(5, OptionalRules.addCount(Integer.MAX_VALUE - 1, 5, 5), "no overflow");

        OptionalStep tour = OptionalStep.parse(List.of("t: visit npc:a npc:b npc:c"), 5).steps().getFirst();
        OptionalRules.Entry e = new OptionalRules.Entry();
        e.value = OptionalRules.visit(e.value, 0);
        e.value = OptionalRules.visit(e.value, 0);
        assertEquals(1, OptionalRules.progress(tour, e), "visiting twice counts once");
        e.value = OptionalRules.visit(e.value, 2);
        assertFalse(OptionalRules.complete(tour, e));
        e.value = OptionalRules.visit(e.value, 1);
        assertTrue(OptionalRules.complete(tour, e));
        assertEquals(e.value, OptionalRules.visit(e.value, 99), "out of range is ignored");

        OptionalStep gen = OptionalStep.parse(List.of("g: break cobblestone count=5"), 5).steps().getFirst();
        OptionalRules.Entry g = new OptionalRules.Entry();
        g.value = 4;
        assertFalse(OptionalRules.complete(gen, g));
        g.doneAt = 1;
        assertEquals(5, OptionalRules.progress(gen, g), "done shows full");
        assertEquals(0, OptionalRules.progress(gen, null));
    }

    @Test
    void tipsShowOnceAndNotTooOften() {
        long min = 60_000;
        assertTrue(OptionalRules.tipDue(null, true, 0, 20 * min, 10 * min));
        assertFalse(OptionalRules.tipDue(null, false, 0, 20 * min, 10 * min), "tips off in /settings");
        assertFalse(OptionalRules.tipDue(null, true, 15 * min, 20 * min, 10 * min), "cooldown");
        OptionalRules.Entry tipped = new OptionalRules.Entry();
        tipped.tippedAt = 5;
        assertFalse(OptionalRules.tipDue(tipped, true, 0, 20 * min, 10 * min), "once per account");
        OptionalRules.Entry done = new OptionalRules.Entry();
        done.doneAt = 5;
        assertFalse(OptionalRules.tipDue(done, true, 0, 20 * min, 10 * min), "never for a done step");
    }

    // ------------------------------------------------------------------ Pip (TU5)

    @Test
    void pipFliesBesideYouTowardTheTarget() {
        double[] player = {0, 100, 0};
        // Target far north: Pip flies 1.6 blocks toward it, 2 blocks up.
        double[] d = PipMotion.desired(player, 0f, new double[]{0, 100, -50}, 6, 1.6, 2);
        assertEquals(0, d[0], 1e-9);
        assertEquals(102, d[1], 1e-9);
        assertEquals(-1.6, d[2], 1e-9);
        // Close to the target: Pip perches on it.
        double[] chest = {3, 101, 2};
        assertTrue(PipMotion.perched(PipMotion.desired(player, 0f, chest, 6, 1.6, 2), chest));
        // No target, facing south: over the left shoulder (east, +x) and a bit ahead.
        double[] free = PipMotion.desired(player, 0f, null, 6, 1.6, 2);
        assertTrue(free[0] > 1, "left of a player facing south is east");
        assertTrue(free[2] > 0, "a little ahead");
    }

    @Test
    void pipMovesInStepsAndJumpsFar() {
        double[] next = PipMotion.step(new double[]{0, 0, 0}, new double[]{10, 0, 0}, 1.5, 16);
        assertEquals(1.5, next[0], 1e-9);
        assertEquals(10, PipMotion.step(new double[]{0, 0, 0}, new double[]{10, 0, 0}, 1.5, 8)[0], 1e-9, "far: jump");
        assertEquals(1, PipMotion.step(new double[]{0, 0, 0}, new double[]{1, 0, 0}, 1.5, 16)[0], 1e-9, "close: arrive");
        assertEquals(4, PipMotion.step(null, new double[]{4, 5, 6}, 1.5, 16)[0], 1e-9, "new Pip: at the spot");
        assertFalse(PipMotion.perched(null, new double[]{0, 0, 0}));
    }
}
