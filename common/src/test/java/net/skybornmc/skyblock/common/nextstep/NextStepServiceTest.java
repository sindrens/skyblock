package net.skybornmc.skyblock.common.nextstep;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plan 4 Nov C §2: one shared next step for Kindle, /help and the scoreboard. */
class NextStepServiceTest {

    private static final Logger LOG = Logger.getLogger("test");

    private static NextStepProvider says(String text, String command) {
        return p -> Optional.of(new NextStep(text, command));
    }

    @Test
    void theHighestPriorityProviderWithAStepWins() {
        DefaultNextStepService s = new DefaultNextStepService(says("Talk to Rowan", "start"), LOG);
        s.register("quests", 10, says("Finish a quest", "quests"));
        s.register("tutorial", 100, says("Plant wheat", "farm"));
        s.register("silent", 1000, p -> Optional.empty());
        assertEquals("Plant wheat", s.current(null).orElseThrow().text());
        s.unregisterAll("tutorial");
        assertEquals("Finish a quest", s.current(null).orElseThrow().text());
        s.unregisterAll("quests");
        assertEquals("Talk to Rowan", s.current(null).orElseThrow().text(), "the start flow is the fallback");
        assertEquals(1, s.size());
    }

    @Test
    void samePriorityKeepsTheOrderTheyWereAdded() {
        DefaultNextStepService s = new DefaultNextStepService(null, LOG);
        s.register("a", 5, says("first", ""));
        s.register("b", 5, says("second", ""));
        assertEquals("first", s.current(null).orElseThrow().text());
    }

    @Test
    void aBrokenProviderIsSkipped() {
        DefaultNextStepService s = new DefaultNextStepService(null, LOG);
        s.register("broken", 50, p -> {
            throw new IllegalStateException("boom");
        });
        s.register("nulls", 40, p -> null);
        s.register("ok", 1, says("Open /menu", "menu"));
        assertEquals("menu", s.current(null).orElseThrow().command());
    }

    @Test
    void noProviderAndNoFallbackMeansNoStep() {
        assertTrue(new DefaultNextStepService(null, LOG).current(null).isEmpty());
        assertTrue(NextStepService.NONE.current(null).isEmpty());
        assertSame(NextStepService.NONE, NextStepService.of(null));
    }

    @Test
    void theObjectiveFallbackNeedsAText() {
        assertTrue(ObjectiveNextStep.step("", "start").isEmpty());
        assertTrue(ObjectiveNextStep.step(null, "start").isEmpty());
        assertTrue(ObjectiveNextStep.step("   ", "start").isEmpty());
        NextStep step = ObjectiveNextStep.step("Talk to Rowan at spawn", "/start").orElseThrow();
        assertEquals("start", step.command());
        ObjectiveNextStep provider = new ObjectiveNextStep(p -> Optional.of("Talk to Rowan"), () -> "start");
        assertEquals("Talk to Rowan", provider.next(null).orElseThrow().text());
    }

    @Test
    void commandsAreCleaned() {
        assertEquals("is home", new NextStep("Go home", " //is home ").command());
        assertEquals("is", new NextStep("Go home", "Is home").label());
        NextStep none = new NextStep("Look around", null);
        assertFalse(none.hasAction());
        assertEquals("", none.label());
        assertThrows(IllegalArgumentException.class, () -> new NextStep(" ", "start"));
    }
}
