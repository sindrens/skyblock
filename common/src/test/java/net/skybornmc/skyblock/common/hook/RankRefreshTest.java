package net.skybornmc.skyblock.common.hook;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankRefreshTest {

    /** A fake main-thread scheduler: tasks run when {@link #advance} passes their tick. */
    private static final class FakeTicks implements RankChangeDebouncer.TickScheduler {
        private record Job(long at, Runnable task) {
        }

        private final List<Job> jobs = new ArrayList<>();
        private long now;

        @Override
        public void later(long delayTicks, Runnable task) {
            jobs.add(new Job(now + Math.max(1, delayTicks), task));
        }

        void advance(long ticks) {
            for (long t = 0; t < ticks; t++) {
                now++;
                for (Job job : List.copyOf(jobs)) {
                    if (job.at() <= now) {
                        jobs.remove(job);
                        job.task().run();
                    }
                }
            }
        }
    }

    @Test
    void burstOfEventsGivesOneFastAndOneSettleRefresh() {
        FakeTicks ticks = new FakeTicks();
        List<Long> refreshedAt = new ArrayList<>();
        RankChangeDebouncer debouncer = new RankChangeDebouncer(ticks, id -> refreshedAt.add(ticks.now));
        UUID player = UUID.randomUUID();
        // "lp user X parent set mod": remove parents, add parent, set stored primary -> 3 events at once
        debouncer.changed(player);
        debouncer.changed(player);
        debouncer.changed(player);
        ticks.advance(30);
        assertEquals(List.of(1L, RankChangeDebouncer.SETTLE_TICKS), refreshedAt);
    }

    @Test
    void lateEventStillGetsARefreshAfterIt() {
        FakeTicks ticks = new FakeTicks();
        List<Long> refreshedAt = new ArrayList<>();
        RankChangeDebouncer debouncer = new RankChangeDebouncer(ticks, id -> refreshedAt.add(ticks.now));
        UUID player = UUID.randomUUID();
        debouncer.changed(player);
        ticks.advance(5);
        debouncer.changed(player); // the last step of the command arrives later
        ticks.advance(30);
        // fast(1), fast after the late event (6), settle of the first burst (10)
        assertEquals(List.of(1L, 6L, 10L), refreshedAt);
        long lastEvent = 5;
        assertEquals(true, refreshedAt.stream().anyMatch(t -> t > lastEvent), "a refresh must follow the last event");
        assertEquals(true, refreshedAt.getLast() <= 20, "everything settles within one second");
    }

    @Test
    void playersAreIndependent() {
        FakeTicks ticks = new FakeTicks();
        List<UUID> refreshed = new ArrayList<>();
        RankChangeDebouncer debouncer = new RankChangeDebouncer(ticks, refreshed::add);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        debouncer.changed(a);
        debouncer.changed(b);
        ticks.advance(20);
        assertEquals(List.of(a, b, a, b), refreshed);
    }

    @Test
    void primaryIsTheHeaviestDirectGroup() {
        var parents = List.of(new RankProvider.GroupWeight("default", 0), new RankProvider.GroupWeight("mod", 60),
                new RankProvider.GroupWeight("vip", 10));
        assertEquals("mod", RankProvider.pickPrimary(parents, "default"));
    }

    @Test
    void primaryTieKeepsTheFirstAndFallsBack() {
        assertEquals("a", RankProvider.pickPrimary(List.of(new RankProvider.GroupWeight("a", 5),
                new RankProvider.GroupWeight("b", 5)), null));
        assertEquals("vip", RankProvider.pickPrimary(List.of(), "vip"));
        assertEquals("default", RankProvider.pickPrimary(List.of(), null));
        assertEquals("default", RankProvider.pickPrimary(List.of(), " "));
    }
}
