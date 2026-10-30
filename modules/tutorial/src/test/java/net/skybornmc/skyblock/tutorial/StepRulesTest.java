package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.tutorial.TutorialService.State;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TUTORIAL §3/§8: start, step progression, early steps, counters, resume after a flow change, staff tools. */
class StepRulesTest {

    private static final TutorialFlow FLOW = TutorialFlow.parse(new TutorialConfig().steps);

    private static Progress started() {
        Progress p = Progress.fresh(UUID.randomUUID());
        assertTrue(StepRules.start(p, FLOW, 1000));
        return p;
    }

    @Test
    void flowParsesTheElevenStepsAndDropsUnknownAndDoubleIds() {
        assertEquals(11, FLOW.size());
        assertEquals("portal", FLOW.first());
        TutorialFlow f = TutorialFlow.parse(List.of("portal", "PORTAL", "fly", "bank"));
        assertEquals(List.of("portal", "bank"), f.steps());
        assertEquals(2, f.problems().size());
        assertEquals(11, TutorialFlow.parse(List.of()).size(), "an empty flow falls back to the default steps");
    }

    @Test
    void startBeginsAtTheFirstStepOnlyOnce() {
        Progress p = started();
        assertEquals(State.ACTIVE, p.state);
        assertEquals("portal", p.step);
        assertEquals(1000L, p.startedAt);
        assertFalse(StepRules.start(p, FLOW, 2000), "a running tutorial is not started again");
        Progress done = new Progress(UUID.randomUUID(), State.FINISHED);
        assertFalse(StepRules.start(done, FLOW, 2000), "once per account: a finished tutorial never restarts by itself");
        Progress legacy = new Progress(UUID.randomUUID(), State.LEGACY);
        assertFalse(StepRules.start(legacy, FLOW, 2000));
    }

    @Test
    void doingTheCurrentStepAdvancesInOrder() {
        Progress p = started();
        assertEquals(StepRules.Outcome.ADVANCED, StepRules.done(p, FLOW, "portal", true, 2000));
        assertEquals("arrive", p.step);
        assertEquals(2000L, p.stepStartedAt);
        assertEquals(StepRules.Outcome.IGNORED, StepRules.done(p, FLOW, "portal", true, 3000), "a step counts once");
    }

    @Test
    void earlyStepsCountAndAreSkippedWhenReached() {
        Progress p = started();
        assertEquals(StepRules.Outcome.EARLY, StepRules.done(p, FLOW, "menu", true, 1500));
        assertEquals("portal", p.step);
        assertTrue(p.done.get("menu"), "marked early");
        for (String s : List.of("portal", "arrive", "chest")) {
            StepRules.done(p, FLOW, s, true, 2000);
        }
        assertEquals("crop", p.step);
        StepRules.done(p, FLOW, "crop", true, 2500);
        assertEquals("depths", p.step, "menu was done early, so it is skipped");
    }

    @Test
    void earlyStepsCanBeTurnedOff() {
        Progress p = started();
        assertEquals(StepRules.Outcome.IGNORED, StepRules.done(p, FLOW, "menu", false, 1500));
        assertFalse(p.isDone("menu"));
    }

    @Test
    void nothingCountsWhileNotRunning() {
        Progress p = Progress.fresh(UUID.randomUUID());
        assertEquals(StepRules.Outcome.IGNORED, StepRules.done(p, FLOW, "portal", true, 1));
        assertFalse(StepRules.count(p, FLOW, "mine", 1, 15, true));
        Progress fin = new Progress(UUID.randomUUID(), State.FINISHED);
        assertEquals(StepRules.Outcome.IGNORED, StepRules.done(fin, FLOW, "bank", true, 1));
    }

    @Test
    void theLastStepCompletesTheTutorial() {
        Progress p = started();
        StepRules.Outcome last = null;
        for (String s : FLOW.steps()) {
            last = StepRules.done(p, FLOW, s, true, 2000);
        }
        assertEquals(StepRules.Outcome.COMPLETE, last);
        StepRules.finished(p, 3000);
        assertEquals(State.FINISHED, p.state);
        assertNull(p.step);
        assertEquals(3000L, p.finishedAt);
    }

    @Test
    void countersReachTheirGoalAndNeverOvershoot() {
        Progress p = started();
        assertFalse(StepRules.count(p, FLOW, "mine", 7, 15, true), "mining early counts (early steps on)");
        assertEquals(7, p.count("mine"));
        assertFalse(StepRules.count(p, FLOW, "mine", 0, 15, true));
        assertTrue(StepRules.count(p, FLOW, "mine", 20, 15, true));
        assertEquals(15, p.count("mine"));
        assertFalse(StepRules.count(p, FLOW, "crop", 1, 3, false), "early counting follows the early rule");
    }

    @Test
    void resumeContinuesAtTheFirstOpenStepOfTheCurrentFlow() {
        Progress p = started();
        StepRules.done(p, FLOW, "portal", true, 1);
        StepRules.done(p, FLOW, "arrive", true, 1);
        assertEquals("chest", p.step);
        // An update removed the chest and crop steps.
        TutorialFlow updated = TutorialFlow.parse(List.of("portal", "arrive", "menu", "bank"));
        assertFalse(StepRules.resume(p, updated, 2, 5000));
        assertEquals("menu", p.step);
        assertEquals(2, p.flowVersion);
        assertEquals(5000L, p.stepStartedAt);
        // Same flow, same step: the step clock is kept.
        assertFalse(StepRules.resume(p, updated, 2, 9000));
        assertEquals(5000L, p.stepStartedAt);
        // Every remaining step already done: finish now.
        p.done.put("menu", false);
        p.done.put("bank", true);
        assertTrue(StepRules.resume(p, updated, 2, 9000));
    }

    @Test
    void resumeLeavesCompleteAccountsAlone() {
        Progress p = new Progress(UUID.randomUUID(), State.LEGACY);
        assertFalse(StepRules.resume(p, FLOW, 3, 1));
        assertEquals(State.LEGACY, p.state);
    }

    @Test
    void staffSkipFinishesOnceWithoutChangingCompleteAccounts() {
        Progress p = started();
        assertTrue(StepRules.staffSkip(p, 10));
        assertEquals(State.STAFF_SKIPPED, p.state);
        assertFalse(StepRules.staffSkip(p, 20));
        assertFalse(StepRules.staffSkip(new Progress(UUID.randomUUID(), State.LEGACY), 1));
    }

    @Test
    void resetStartsOverButKeepsTheRewardMark() {
        Progress p = started();
        StepRules.done(p, FLOW, "portal", true, 1);
        StepRules.count(p, FLOW, "mine", 4, 15, true);
        p.rewarded = true;
        StepRules.reset(p);
        assertEquals(State.NEW, p.state);
        assertTrue(p.done.isEmpty());
        assertTrue(p.counts.isEmpty());
        assertTrue(p.rewarded, "a reset never pays the reward a second time");
        assertTrue(StepRules.start(p, FLOW, 50));
        assertEquals("portal", p.step);
    }

    @Test
    void jumpMarksEarlierStepsDone() {
        Progress p = Progress.fresh(UUID.randomUUID());
        assertTrue(StepRules.jump(p, FLOW, "mine", 77));
        assertEquals(State.ACTIVE, p.state);
        assertEquals("mine", p.step);
        assertEquals(6, p.done.size());
        assertTrue(FLOW.doneThrough("depths", p.doneSet()));
        assertFalse(StepRules.jump(p, FLOW, "nope", 1));
    }

    @Test
    void stuckAllowsASkipOnlyForSkippableStepsAfterEnoughPresses() {
        Progress p = started();
        List<String> skippable = new TutorialConfig().stuckSkippable;
        assertFalse(StepRules.stuck(p, "portal", 3, skippable));
        assertFalse(StepRules.stuck(p, "portal", 3, skippable));
        assertFalse(StepRules.stuck(p, "portal", 3, skippable), "portal is never skippable");
        assertFalse(StepRules.stuck(p, "help", 3, skippable));
        assertFalse(StepRules.stuck(p, "help", 3, skippable));
        assertTrue(StepRules.stuck(p, "help", 3, skippable));
        assertFalse(skippable.contains("sell"));
        assertFalse(skippable.contains("bank"));
    }
}
