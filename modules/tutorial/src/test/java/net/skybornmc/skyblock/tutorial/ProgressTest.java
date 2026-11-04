package net.skybornmc.skyblock.tutorial;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steps in any order inside the current chapter, counters, chapter completion, unavailable steps and skipping. */
class ProgressTest {

    private final Catalog catalog = CatalogTest.bundled(new ArrayList<>());
    private final Predicate<Catalog.Step> all = s -> true;

    @Test
    void theFirstChapterIsCurrentAndItsFirstStepIsNext() {
        Progress p = new Progress(UUID.randomUUID());
        assertEquals("welcome", p.current(catalog).orElseThrow().id());
        assertEquals("rowan", p.next(catalog, all).orElseThrow().id());
        assertFalse(p.finished(catalog));
    }

    @Test
    void stepsOfTheCurrentChapterCountInAnyOrderLaterChaptersWait() {
        Progress p = new Progress(UUID.randomUUID());
        assertFalse(p.apply(catalog, Signal.of(StepType.GRIND, "farm", 1), all, 1).any(), "the farm chapter waits");
        Progress.Change c = p.apply(catalog, Signal.of(StepType.MENU, "hub", 1), all, 1);
        assertEquals(1, c.stepsDone().size());
        assertEquals("rowan", p.next(catalog, all).orElseThrow().id(), "the first open step stays next");
        p.apply(catalog, Signal.of(StepType.NPC, "guide", 1), all, 2);
        Progress.Change last = p.apply(catalog, Signal.of(StepType.ISLAND), all, 3);
        assertEquals("welcome", last.chaptersDone().getFirst().id());
        assertEquals("island", p.current(catalog).orElseThrow().id());
        assertEquals(3, p.changedAt());
    }

    @Test
    void countersFinishAtTheirAmount() {
        Progress p = new Progress(UUID.randomUUID());
        p.restore(Set.of(), Map.of(), Set.of("welcome", "island"), 0, false);
        Catalog.Step harvest = catalog.step("farm.harvest").orElseThrow();
        for (int i = 0; i < 49; i++) {
            assertTrue(p.apply(catalog, Signal.of(StepType.GRIND, "farm", 1), all, i).counted());
        }
        assertEquals(49, p.counter(harvest));
        assertFalse(p.done(harvest));
        Progress.Change c = p.apply(catalog, Signal.of(StepType.GRIND, "farm", 1), all, 50);
        assertEquals(java.util.List.of(harvest), c.stepsDone());
        assertTrue(p.done(harvest));
        assertEquals(50, p.counter(harvest));
    }

    @Test
    void aBankDepositCountsItsMoney() {
        Progress p = new Progress(UUID.randomUUID());
        p.restore(Set.of(), Map.of(), Set.of("welcome", "island", "farm", "mine"), 0, false);
        p.apply(catalog, Signal.of(StepType.BANK, "", 60), all, 1);
        assertFalse(p.done(catalog.step("money.deposit").orElseThrow()));
        p.apply(catalog, Signal.of(StepType.BANK, "", 60), all, 2);
        assertTrue(p.done(catalog.step("money.deposit").orElseThrow()));
    }

    @Test
    void unavailableStepsCountAsDoneSoAMissingNpcNeverBlocks() {
        Progress p = new Progress(UUID.randomUUID());
        p.restore(Set.of(), Map.of(), Set.of("welcome", "island"), 0, false);
        Predicate<Catalog.Step> noMerchant = s -> !s.target().equals("farm_merchant");
        assertEquals("go", p.next(catalog, noMerchant).orElseThrow().id());
        p.apply(catalog, Signal.of(StepType.PLACE, "farm", 1), noMerchant, 1);
        assertEquals("harvest", p.next(catalog, noMerchant).orElseThrow().id(), "Bramble is skipped while not placed");
        Catalog.Chapter farm = catalog.chapter("farm").orElseThrow();
        assertEquals(2, p.doneCount(farm, noMerchant));
    }

    @Test
    void chaptersMadeOnlyOfUnavailableStepsSettleAtOnce() {
        Progress p = new Progress(UUID.randomUUID());
        assertEquals(catalog.chapters().size(), p.settle(catalog, s -> false, 5).size());
        assertTrue(p.finished(catalog));
    }

    @Test
    void skippingEndsTheTour() {
        Progress p = new Progress(UUID.randomUUID());
        p.skip(10);
        assertTrue(p.finished(catalog));
        assertTrue(p.next(catalog, all).isEmpty());
        assertFalse(p.apply(catalog, Signal.of(StepType.MENU, "hub", 1), all, 11).any());
        assertEquals(1.0, p.overall(catalog, all));
    }

    @Test
    void staffCompleteMarksOneStep() {
        Progress p = new Progress(UUID.randomUUID());
        Catalog.Step rowan = catalog.step("welcome.rowan").orElseThrow();
        assertEquals(1, p.complete(catalog, rowan, all, 1).stepsDone().size());
        assertFalse(p.complete(catalog, rowan, all, 2).any());
        assertTrue(p.dirty());
    }
}
