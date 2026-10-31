package net.skybornmc.skyblock.common.mob;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrikeQueueTest {

    /** Records every call with the tick it happened at. */
    private static final class Log implements StrikeQueue.Sink<String> {
        final List<String> events = new ArrayList<>();
        long now;

        @Override
        public void telegraph(StrikeQueue.Strike<String> s) {
            events.add(now + " telegraph " + s.payload());
        }

        @Override
        public void hit(StrikeQueue.Strike<String> s, int pulse) {
            events.add(now + " hit " + s.payload() + " " + pulse);
        }

        @Override
        public void end(StrikeQueue.Strike<String> s) {
            events.add(now + " end " + s.payload() + (s.cancelled() ? " cancelled" : ""));
        }
    }

    private static void run(StrikeQueue<String> q, Log log, long from, long to) {
        for (long t = from; t <= to; t++) {
            log.now = t;
            q.advance(t);
        }
    }

    @Test
    void telegraphAlwaysComesAtLeastOneSecondBeforeTheHit() {
        Log log = new Log();
        StrikeQueue<String> q = new StrikeQueue<>(log, 20);
        log.now = 100;
        StrikeQueue.Strike<String> s = q.start(100, 5, 1, 1, 0, "slam"); // asks for 5 ticks: raised to 20
        assertEquals(120, s.firstHitAt());
        run(q, log, 100, 130);
        assertEquals(List.of("100 telegraph slam", "120 hit slam 0", "120 end slam"), log.events);
        assertEquals(0, q.active());
    }

    @Test
    void pulsesFollowTheIntervalThenLingerThenEnd() {
        Log log = new Log();
        StrikeQueue<String> q = new StrikeQueue<>(log, 20);
        log.now = 0;
        q.start(0, 20, 3, 20, 0, "ring");
        log.now = 5;
        q.start(5, 30, 1, 1, 40, "shield");
        run(q, log, 0, 200);
        assertEquals(List.of("0 telegraph ring", "5 telegraph shield", "20 hit ring 0", "35 hit shield 0", "40 hit ring 1",
                "60 hit ring 2", "60 end ring", "75 end shield"), log.events);
    }

    @Test
    void schedulerRunningEveryFewTicksNeverHitsEarly() {
        Log log = new Log();
        StrikeQueue<String> q = new StrikeQueue<>(log, 20);
        log.now = 3;
        q.start(3, 20, 2, 15, 0, "leap");
        for (long t = 0; t <= 80; t += 4) { // a scheduler that runs every 4 ticks
            log.now = t;
            q.advance(t);
        }
        assertEquals(List.of("3 telegraph leap", "24 hit leap 0", "40 hit leap 1", "40 end leap"), log.events);
    }

    @Test
    void cancelledStrikesEndWithoutHitting() {
        Log log = new Log();
        StrikeQueue<String> q = new StrikeQueue<>(log, 20);
        log.now = 0;
        q.start(0, 20, 1, 1, 0, "a");
        q.start(0, 20, 1, 1, 0, "b");
        q.cancel("a"::equals);
        run(q, log, 0, 40);
        assertEquals(List.of("0 telegraph a", "0 telegraph b", "0 end a cancelled", "20 hit b 0", "20 end b"), log.events);
    }

    @Test
    void cancelDuringPulsesStopsTheRest() {
        Log log = new Log();
        StrikeQueue<String> q = new StrikeQueue<>(log, 20);
        log.now = 0;
        q.start(0, 20, 3, 20, 0, "ring");
        run(q, log, 0, 25);
        q.cancel(p -> true);
        run(q, log, 26, 100);
        assertEquals(List.of("0 telegraph ring", "20 hit ring 0", "26 end ring cancelled"), log.events);
    }

    @Test
    void clearEndsEverythingAtOnce() {
        Log log = new Log();
        StrikeQueue<String> q = new StrikeQueue<>(log, 20);
        log.now = 0;
        q.start(0, 20, 1, 1, 0, "a");
        q.start(0, 20, 1, 1, 0, "b");
        assertEquals(2, q.active());
        q.clear();
        assertEquals(0, q.active());
        assertEquals(List.of("0 telegraph a", "0 telegraph b", "0 end a cancelled", "0 end b cancelled"), log.events);
        run(q, log, 0, 40);
        assertEquals(4, log.events.size(), "nothing left to run");
    }

    @Test
    void telegraphOnlyStrike() {
        Log log = new Log();
        StrikeQueue<String> q = new StrikeQueue<>(log, 20);
        log.now = 0;
        q.start(0, 20, 0, 1, 0, "warn");
        run(q, log, 0, 30);
        assertEquals(List.of("0 telegraph warn", "20 end warn"), log.events);
        assertTrue(q.minTelegraphTicks() >= 1);
    }
}
