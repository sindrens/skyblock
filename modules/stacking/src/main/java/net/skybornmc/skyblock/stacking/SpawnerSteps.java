package net.skybornmc.skyblock.stacking;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ECON-SPAWNERS step 5 (pure): the three upgrade step lists of {@code stacking.yml} (speed seconds, mobs per spawner, loot
 * multiplier), indexed by the island's levels from the progression hooks. A level above the list uses the last step; the
 * interval never goes below {@code spawners.interval-floor-seconds}. Combined default maximum: x2.0 speed, x2.0 mobs,
 * x1.5 loot = x6.0.
 */
final class SpawnerSteps {

    static final SpawnerSteps DEFAULTS = new SpawnerSteps(List.of(10.0, 9.0, 8.0, 7.0, 6.0, 5.0), List.of(1.0, 1.25, 1.5, 1.75, 2.0),
            List.of(1.0, 1.1, 1.2, 1.3, 1.4, 1.5), 4);

    private final List<Double> seconds;
    private final List<Double> mobs;
    private final List<Double> loot;
    private final int floorSeconds;

    SpawnerSteps(List<Double> seconds, List<Double> mobs, List<Double> loot, int floorSeconds) {
        this.seconds = List.copyOf(seconds);
        this.mobs = List.copyOf(mobs);
        this.loot = List.copyOf(loot);
        this.floorSeconds = Math.max(1, floorSeconds);
    }

    /** Parses the lists; broken entries are reported and skipped, an empty list falls back to its default. */
    static SpawnerSteps parse(List<String> seconds, List<String> mobs, List<String> loot, int floorSeconds, Consumer<String> problems) {
        return new SpawnerSteps(list("spawners.speed-steps-seconds", seconds, 1, 3600, DEFAULTS.seconds, problems),
                list("spawners.mob-steps", mobs, 0.01, 100, DEFAULTS.mobs, problems),
                list("spawners.loot-steps", loot, 0, 100, DEFAULTS.loot, problems), floorSeconds);
    }

    private static List<Double> list(String path, List<String> raw, double min, double max, List<Double> fallback, Consumer<String> problems) {
        List<Double> out = new ArrayList<>();
        for (String entry : raw) {
            try {
                double v = Double.parseDouble(entry.trim());
                if (v < min || v > max || Double.isNaN(v)) {
                    throw new NumberFormatException();
                }
                out.add(v);
            } catch (RuntimeException e) {
                problems.accept("stacking.yml → " + path + ": '" + entry + "' must be a number from " + min + " to " + max + ", skipped");
            }
        }
        if (out.isEmpty()) {
            problems.accept("stacking.yml → " + path + " is empty, using the defaults " + fallback);
            return fallback;
        }
        return out;
    }

    private static double at(List<Double> list, int level) {
        return list.get(Math.max(0, Math.min(list.size() - 1, level)));
    }

    /** Seconds between spawns at a speed level (never below the floor). */
    double seconds(int level) {
        return Math.max(floorSeconds, at(seconds, level));
    }

    /** Mobs each spawner adds per spawn at a mobs level. */
    double mobs(int level) {
        return at(mobs, level);
    }

    /** Custom drop multiplier at a loot level. */
    double loot(int level) {
        return at(loot, level);
    }

    /** Ticks between spawns at a speed level, spread by {@code jitterPercent} with {@code random} in [0, 1). */
    long intervalTicks(int level, int jitterPercent, double random) {
        double base = seconds(level) * 20.0;
        double spread = base * Math.max(0, jitterPercent) / 100.0;
        double ticks = base - spread + 2 * spread * random;
        return Math.max(floorSeconds * 20L, Math.round(ticks));
    }
}
