package net.skybornmc.skyblock.blackmarket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Island Charter (PHASE-10 M10.11 fixed row, PHASE-5 M5.6, owner 157/171, N2-2, P5-14, REVIEW3 F6): island sizes above the
 * normal 9x9 sold one step at a time, for shards (and gems where a gem price is set). Pure, unit-tested.
 */
final class CharterRules {

    /** One size step: border width in chunks and its prices (0 = not sold for that currency). */
    record Step(int size, long shards, long gems) {
    }

    /** Why there is no step to buy right now (or {@link #READY}). */
    enum State {
        /** The next step can be bought. */
        READY,
        /** The island is still below the normal maximum (9x9): grow it with /is upgrade first. */
        NEEDS_NORMAL_MAX,
        /** Already at the biggest size sold here (or the islands module's max). */
        MAXED,
        /** The island's size is off the step ladder (e.g. a staff size); no step fits. */
        NO_STEP
    }

    record Next(State state, Step step) {
    }

    private CharterRules() {
    }

    /** Parses {@code ["11: 150 250", ...]} ({@code <size>: <shards> [gems]}); sizes must be odd and above {@code fromSize}. */
    static List<Step> parseSteps(List<String> raw, int fromSize, Consumer<String> problems) {
        List<Step> out = new ArrayList<>();
        for (String line : raw == null ? List.<String>of() : raw) {
            try {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    throw new IllegalArgumentException("needs '<size>: <shards> [gems]'");
                }
                int size = Integer.parseInt(line.substring(0, colon).trim());
                String[] prices = line.substring(colon + 1).trim().split("\\s+");
                long shards = Long.parseLong(prices[0]);
                long gems = prices.length > 1 ? Long.parseLong(prices[1]) : 0;
                if (size % 2 == 0 || size <= fromSize) {
                    throw new IllegalArgumentException("the size must be odd and bigger than " + fromSize);
                }
                if (shards < 0 || gems < 0) {
                    throw new IllegalArgumentException("prices can't be negative");
                }
                if (out.stream().anyMatch(s -> s.size() == size)) {
                    throw new IllegalArgumentException("size " + size + " is listed twice");
                }
                out.add(new Step(size, shards, gems));
            } catch (RuntimeException e) {
                problems.accept("blackmarket.yml charter.steps: '" + line + "' skipped: "
                        + (e instanceof IllegalArgumentException && !(e instanceof NumberFormatException) ? e.getMessage() : "not a number"));
            }
        }
        out.sort(Comparator.comparingInt(Step::size));
        return List.copyOf(out);
    }

    /**
     * The step after {@code current}: only once the island reached {@code fromSize} (the normal maximum, 9x9) and only
     * the very next size ({@code current + 2}), never above {@code islandMax}.
     */
    static Next next(int current, int fromSize, List<Step> steps, int islandMax) {
        if (current < fromSize) {
            return new Next(State.NEEDS_NORMAL_MAX, null);
        }
        int biggest = steps.stream().mapToInt(Step::size).filter(s -> s <= islandMax).max().orElse(fromSize);
        if (current >= biggest) {
            return new Next(State.MAXED, null);
        }
        Optional<Step> step = steps.stream().filter(s -> s.size() == current + 2 && s.size() <= islandMax).findFirst();
        return step.map(s -> new Next(State.READY, s)).orElseGet(() -> new Next(State.NO_STEP, null));
    }
}
