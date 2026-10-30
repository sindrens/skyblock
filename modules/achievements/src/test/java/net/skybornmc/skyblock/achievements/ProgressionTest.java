package net.skybornmc.skyblock.achievements;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unlock-once, counters vs. gauges, loading, and the profile/account rule (PR1). */
class ProgressionTest {

    private final AchievementRegistry registry = Fixtures.registry();
    private final UUID owner = UUID.randomUUID();

    private ProfileProgress loaded(UUID key) {
        ProfileProgress p = new ProfileProgress(key, owner);
        p.load(Map.of(), Map.of());
        return p;
    }

    private static List<String> ids(List<Progression.Unlock> unlocks) {
        return unlocks.stream().map(u -> u.def().id() + ":" + u.tier()).toList();
    }

    @Test
    void eachTierUnlocksOnce() {
        ProfileProgress p = loaded(owner);
        assertEquals(List.of("first_sale:1"), ids(Progression.apply(p, registry, Metric.ITEMS_SOLD, 1)));
        assertEquals(List.of(), ids(Progression.apply(p, registry, Metric.ITEMS_SOLD, 1)));
        // 2,500 more: merchant tier 1 (1,000) once, first_sale never again.
        assertEquals(List.of("merchant:1"), ids(Progression.apply(p, registry, Metric.ITEMS_SOLD, 2_500)));
        assertEquals(List.of(), ids(Progression.apply(p, registry, Metric.ITEMS_SOLD, 10)));
        assertEquals(2_512, p.value(Metric.ITEMS_SOLD));
    }

    @Test
    void bigJumpsUnlockEveryTierLowestFirstAndTheTitleOnlyOnTheLast() {
        ProfileProgress p = loaded(owner);
        List<Progression.Unlock> unlocks = Progression.apply(p, registry, Metric.SKILL_COMBAT, 150);
        assertEquals(List.of("warrior:1", "warrior:2", "warrior:3", "warrior:4", "warrior:5"), ids(unlocks));
        for (int i = 0; i < 4; i++) {
            assertNull(unlocks.get(i).title());
            assertFalse(unlocks.get(i).last());
        }
        assertTrue(unlocks.getLast().last());
        assertEquals("warrior", unlocks.getLast().title());
        assertEquals(owner, unlocks.getLast().owner());
    }

    @Test
    void gaugesKeepTheBestValueAndNeverGoBack() {
        ProfileProgress p = loaded(owner);
        Progression.apply(p, registry, Metric.SKILL_MINING, 30);
        assertEquals(List.of(), ids(Progression.apply(p, registry, Metric.SKILL_MINING, 5)), "season reset: skills drop, progress stays");
        assertEquals(30, p.value(Metric.SKILL_MINING));
        assertFalse(p.apply(Metric.ITEMS_SOLD, -5), "counters ignore negative amounts");
    }

    @Test
    void nothingUnlocksWhileLoadingAndLoadingMergesWhatWasEarned() {
        ProfileProgress p = new ProfileProgress(owner, owner);
        assertEquals(List.of(), Progression.apply(p, registry, Metric.ITEMS_SOLD, 400));
        assertEquals(List.of(), Progression.apply(p, registry, Metric.SKILL_MINING, 12));
        // Stored: 700 items, Mining 9 (from an earlier session), first_sale already unlocked.
        p.load(Map.of(Metric.ITEMS_SOLD, 700L, Metric.SKILL_MINING, 9L), Map.of("first_sale", 1));
        assertEquals(1_100, p.value(Metric.ITEMS_SOLD), "counter: stored + earned while loading");
        assertEquals(12, p.value(Metric.SKILL_MINING), "gauge: the higher value");
        assertEquals(List.of("prospector:1", "merchant:1"), ids(Progression.evaluateAll(p, registry)));
        assertEquals(List.of(), Progression.evaluateAll(p, registry), "catch-up runs once");
        // Only the 400 earned meanwhile is a new delta; Mining 12 > 9 must be written.
        assertEquals(List.of(new ProfileProgress.Row(Metric.ITEMS_SOLD, 400), new ProfileProgress.Row(Metric.SKILL_MINING, 12)), p.drain());
    }

    @Test
    void progressIsPerProfileButTitlesGoToTheAccount() {
        UUID profile1 = owner;
        UUID profile2 = UUID.randomUUID();
        ProfileProgress first = loaded(profile1);
        ProfileProgress second = loaded(profile2);
        List<Progression.Unlock> a = Progression.apply(first, registry, Metric.TRADES_COMPLETED, 250);
        assertEquals("dealmaker", a.getLast().title());
        assertEquals(profile1, a.getLast().profileKey());
        assertEquals(owner, a.getLast().owner());
        // Profile 2 starts from zero (separate progress) ...
        assertEquals(0, second.value(Metric.TRADES_COMPLETED));
        assertEquals(0, Progression.completed(second, registry));
        // ... and finishing it there names the same account: granting is idempotent, so the title is not given twice.
        List<Progression.Unlock> b = Progression.apply(second, registry, Metric.TRADES_COMPLETED, 250);
        assertEquals(4, b.size());
        assertEquals(profile2, b.getLast().profileKey());
        assertEquals(owner, b.getLast().owner());
        assertEquals(1, Progression.completed(first, registry));
        assertEquals(1, Progression.completed(second, registry));
    }

    @Test
    void tierCountsAndCompleted() {
        ProfileProgress p = loaded(owner);
        Progression.apply(p, registry, Metric.TRADES_COMPLETED, 10);
        int[] counts = Progression.tierCounts(p, registry);
        assertEquals(2, counts[0]);
        assertTrue(counts[1] > 60);
        assertEquals(0, Progression.completed(p, registry));
    }

    @Test
    void failedSavesAreRetried() {
        ProfileProgress p = loaded(owner);
        p.apply(Metric.ITEMS_SOLD, 5);
        p.apply(Metric.BALANCE_PEAK, 1_000);
        List<ProfileProgress.Row> rows = p.drain();
        assertFalse(p.dirty());
        p.apply(Metric.ITEMS_SOLD, 2);
        p.restore(rows);
        assertEquals(List.of(new ProfileProgress.Row(Metric.ITEMS_SOLD, 7), new ProfileProgress.Row(Metric.BALANCE_PEAK, 1_000)), p.drain());
    }
}
