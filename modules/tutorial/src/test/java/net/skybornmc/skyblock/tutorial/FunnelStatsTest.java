package net.skybornmc.skyblock.tutorial;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** TU7 stats: funnel, drop-off, time per step, stuck presses, cameras, optional steps, Java vs Bedrock. */
class FunnelStatsTest {

    private static final List<String> STEPS = List.of("portal", "arrive", "chest");
    private static final long HOUR = 3_600_000L;
    private static final long NOW = 1000 * HOUR;

    private static FunnelStats.Event ev(UUID p, String step, String kind, long at, boolean bedrock, long millis) {
        return new FunnelStats.Event(p, step, kind, at, bedrock, millis);
    }

    @Test
    void funnelPerStep() {
        UUID a = UUID.randomUUID(); // Java, finishes
        UUID b = UUID.randomUUID(); // Bedrock, stops at chest 2 days ago
        UUID c = UUID.randomUUID(); // Java, on arrive, active 1 h ago (not dropped yet)
        List<FunnelStats.Event> events = new ArrayList<>(List.of(
                ev(a, "portal", "start", NOW - 50 * HOUR, false, 0),
                ev(a, "portal", "done", NOW - 50 * HOUR, false, 20_000),
                ev(a, "arrive", "done", NOW - 50 * HOUR, false, 6_000),
                ev(a, "chest", "done", NOW - 50 * HOUR, false, 10_000),
                ev(a, null, "finish", NOW - 50 * HOUR, false, 0),
                ev(b, "portal", "start", NOW - 49 * HOUR, true, 0),
                ev(b, "portal", "done", NOW - 49 * HOUR, true, 40_000),
                ev(b, "arrive", "done", NOW - 49 * HOUR, true, 0),
                ev(b, "chest", "stuck", NOW - 48 * HOUR, true, 0),
                ev(b, "chest", "stuck", NOW - 48 * HOUR, true, 0),
                ev(b, "chest", "quit", NOW - 48 * HOUR, true, 0),
                ev(b, "island", "camera_play", NOW - 49 * HOUR, true, 0),
                ev(b, "island", "camera_skip", NOW - 49 * HOUR, true, 0),
                ev(c, "portal", "start", NOW - 2 * HOUR, false, 0),
                ev(c, "portal", "done", NOW - 2 * HOUR, false, 30_000),
                ev(c, "arrive", "idle", NOW - HOUR, false, 0),
                ev(a, "settings", "opt_done", NOW - 10 * HOUR, false, 0),
                ev(a, "settings", "opt_tip", NOW - 11 * HOUR, false, 0)));
        Map<UUID, String> active = Map.of(b, "chest", c, "arrive");

        FunnelStats.Result all = FunnelStats.compute(events, STEPS, active, FunnelStats.Client.ALL, NOW, 24 * HOUR);
        assertEquals(3, all.started());
        assertEquals(1, all.finished());
        assertEquals(33, all.finishedPercent());
        assertEquals(3, all.players());
        FunnelStats.StepStats portal = all.steps().get(0);
        assertEquals(3, portal.reached());
        assertEquals(3, portal.done());
        assertEquals(100, portal.percent());
        assertEquals(30_000, portal.medianMs(), "median of 20, 30, 40 s");
        assertEquals(40_000, portal.p90Ms());
        FunnelStats.StepStats arrive = all.steps().get(1);
        assertEquals(3, arrive.reached(), "c's idle event marks arrive as reached");
        assertEquals(2, arrive.done());
        assertEquals(0, arrive.dropped(), "c was active 1 h ago: not stopped yet");
        assertEquals(6_000, arrive.medianMs(), "an early or zero time is not counted");
        FunnelStats.StepStats chest = all.steps().get(2);
        assertEquals(2, chest.reached());
        assertEquals(1, chest.done());
        assertEquals(50, chest.percent());
        assertEquals(1, chest.dropped(), "b: still on chest, nothing for 48 h");
        assertEquals(2, chest.stuck());
        assertEquals("chest", all.worst().step());
        assertEquals(List.of(new FunnelStats.CameraStats("island", 1, 1)), all.cameras());
        assertEquals(Map.of("settings", 1), all.optional());
        assertEquals(1, all.idleReminders());

        FunnelStats.Result bedrock = FunnelStats.compute(events, STEPS, active, FunnelStats.Client.BEDROCK, NOW, 24 * HOUR);
        assertEquals(1, bedrock.started());
        assertEquals(0, bedrock.finished());
        assertEquals(1, bedrock.steps().get(2).dropped());
        FunnelStats.Result java = FunnelStats.compute(events, STEPS, active, FunnelStats.Client.JAVA, NOW, 24 * HOUR);
        assertEquals(2, java.started());
        assertEquals(0, java.steps().get(2).dropped(), "b is Bedrock");
        assertEquals(List.of(), java.cameras());
    }

    @Test
    void emptyPeriod() {
        FunnelStats.Result r = FunnelStats.compute(List.of(), STEPS, Map.of(UUID.randomUUID(), "chest"), FunnelStats.Client.ALL, NOW, HOUR);
        assertEquals(0, r.started());
        assertEquals(0, r.finishedPercent());
        assertEquals(3, r.steps().size());
        assertEquals(0, r.steps().get(2).dropped(), "no events in the period: not counted");
        assertEquals(0, r.steps().get(0).percent());
        assertNull(r.worst());
    }

    @Test
    void percentiles() {
        assertEquals(0, FunnelStats.percentile(List.of(), 0.5));
        assertEquals(5, FunnelStats.percentile(List.of(5L), 0.9));
        List<Long> ten = List.of(10L, 1L, 9L, 2L, 8L, 3L, 7L, 4L, 6L, 5L);
        assertEquals(5, FunnelStats.percentile(ten, 0.5));
        assertEquals(9, FunnelStats.percentile(ten, 0.9));
        assertEquals(10, FunnelStats.percentile(ten, 1.0));
    }
}
