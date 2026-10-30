package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The tutorial funnel for staff (TUTORIAL.md §10, TQ24), computed from {@code tutorial_events}: per step how many players
 * reached it and finished it, how many stopped there (still on that step, nothing for {@code dropAfterMs}), median and
 * 90th percentile time in the step and "I'm stuck" presses; camera skips, optional steps and idle reminders. Every
 * number can be split by client. Pure, unit-tested; the menu caches the result.
 */
final class FunnelStats {

    /** Which players count. */
    enum Client { ALL, JAVA, BEDROCK }

    /** One row of {@code tutorial_events}. */
    record Event(UUID player, String step, String kind, long at, boolean bedrock, long millis) {
    }

    record StepStats(String step, int reached, int done, int dropped, long medianMs, long p90Ms, int stuck) {

        /** Done of reached, 0-100. */
        int percent() {
            return reached == 0 ? 0 : (int) Math.round(done * 100.0 / reached);
        }
    }

    /** Plays and skips of one camera. */
    record CameraStats(String camera, int played, int skipped) {
    }

    record Result(int started, int finished, List<StepStats> steps, List<CameraStats> cameras, Map<String, Integer> optional,
                  int idleReminders, int players) {

        int finishedPercent() {
            return started == 0 ? 0 : (int) Math.round(finished * 100.0 / started);
        }

        /** The step that loses the most players (by dropped), or {@code null}. */
        StepStats worst() {
            StepStats worst = null;
            for (StepStats s : steps) {
                if (s.dropped() > 0 && (worst == null || s.dropped() > worst.dropped())) {
                    worst = s;
                }
            }
            return worst;
        }
    }

    private FunnelStats() {
    }

    /**
     * @param events      events of the period
     * @param steps       the flow's core steps in order
     * @param active      players whose tutorial is still running → their current step
     * @param client      which players count (by the client flag on their events)
     * @param now         current time
     * @param dropAfterMs a running player with no event for this long counts as stopped at their step (§10: 24 h)
     */
    static Result compute(Collection<Event> events, List<String> steps, Map<UUID, String> active, Client client, long now,
                          long dropAfterMs) {
        Set<UUID> started = new HashSet<>();
        Set<UUID> finished = new HashSet<>();
        Set<UUID> players = new HashSet<>();
        Map<String, Set<UUID>> reached = new HashMap<>();
        Map<String, Set<UUID>> done = new HashMap<>();
        Map<String, List<Long>> times = new HashMap<>();
        Map<String, Integer> stuck = new HashMap<>();
        Map<String, int[]> cameras = new TreeMap<>();
        Map<String, Set<UUID>> optional = new TreeMap<>();
        Map<UUID, Long> last = new HashMap<>();
        int idle = 0;
        for (Event e : events) {
            if (!matches(e, client)) {
                continue;
            }
            players.add(e.player());
            last.merge(e.player(), e.at(), Math::max);
            String kind = e.kind() == null ? "" : e.kind();
            String step = e.step();
            switch (kind) {
                case "start" -> started.add(e.player());
                case "finish" -> finished.add(e.player());
                case "done" -> {
                    if (step != null) {
                        done.computeIfAbsent(step, k -> new HashSet<>()).add(e.player());
                        if (e.millis() > 0) {
                            times.computeIfAbsent(step, k -> new ArrayList<>()).add(e.millis());
                        }
                    }
                }
                case "stuck" -> {
                    if (step != null) {
                        stuck.merge(step, 1, Integer::sum);
                    }
                }
                case "camera_play" -> cameras.computeIfAbsent(String.valueOf(step), k -> new int[2])[0]++;
                case "camera_skip" -> cameras.computeIfAbsent(String.valueOf(step), k -> new int[2])[1]++;
                case "opt_done" -> optional.computeIfAbsent(String.valueOf(step), k -> new HashSet<>()).add(e.player());
                case "idle" -> idle++;
                default -> {
                    // resume, quit, replay, skip, staff_* only mark the step as reached
                }
            }
            if (step != null && !kind.startsWith("camera_") && !kind.startsWith("opt_")) {
                reached.computeIfAbsent(step, k -> new HashSet<>()).add(e.player());
            }
        }
        Map<String, Integer> dropped = new HashMap<>();
        for (Map.Entry<UUID, String> a : active.entrySet()) {
            Long seen = last.get(a.getKey());
            if (seen != null && a.getValue() != null && now - seen >= dropAfterMs) {
                dropped.merge(a.getValue(), 1, Integer::sum);
            }
        }
        List<StepStats> out = new ArrayList<>();
        for (String s : steps) {
            List<Long> t = times.getOrDefault(s, List.of());
            int reachedCount = reached.getOrDefault(s, Set.of()).size();
            int doneCount = done.getOrDefault(s, Set.of()).size();
            out.add(new StepStats(s, Math.max(reachedCount, doneCount), doneCount, dropped.getOrDefault(s, 0), percentile(t, 0.5),
                    percentile(t, 0.9), stuck.getOrDefault(s, 0)));
        }
        List<CameraStats> cams = new ArrayList<>();
        cameras.forEach((id, v) -> cams.add(new CameraStats(id, v[0], v[1])));
        Map<String, Integer> opt = new LinkedHashMap<>();
        optional.forEach((id, set) -> opt.put(id, set.size()));
        return new Result(started.size(), finished.size(), List.copyOf(out), List.copyOf(cams), opt, idle, players.size());
    }

    private static boolean matches(Event e, Client client) {
        return switch (client) {
            case ALL -> true;
            case JAVA -> !e.bedrock();
            case BEDROCK -> e.bedrock();
        };
    }

    /** Nearest-rank percentile ({@code p} in 0-1) of the values; 0 when there are none. */
    static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int rank = (int) Math.ceil(p * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }
}
