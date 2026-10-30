package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The ordered core steps from {@code flow.steps} (validated: unknown or doubled ids are dropped with a problem line).
 * Pure, unit-tested.
 */
final class TutorialFlow {

    private final List<String> steps;
    private final List<String> problems;

    private TutorialFlow(List<String> steps, List<String> problems) {
        this.steps = List.copyOf(steps);
        this.problems = List.copyOf(problems);
    }

    static TutorialFlow parse(Collection<String> raw) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        for (String r : raw == null ? List.<String>of() : raw) {
            String id = r == null ? "" : r.strip().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            if (!Steps.KNOWN.contains(id)) {
                problems.add("flow.steps: '" + r + "' is not a step (known: " + String.join(", ", Steps.KNOWN) + ").");
                continue;
            }
            if (!seen.add(id)) {
                problems.add("flow.steps: '" + id + "' is listed twice; the second one is ignored.");
            }
        }
        if (seen.isEmpty()) {
            problems.add("flow.steps is empty; using the default 11 steps.");
            seen.addAll(Steps.KNOWN);
        }
        return new TutorialFlow(new ArrayList<>(seen), problems);
    }

    List<String> steps() {
        return steps;
    }

    List<String> problems() {
        return problems;
    }

    int size() {
        return steps.size();
    }

    boolean contains(String step) {
        return steps.contains(step);
    }

    /** 0-based position, -1 when not in the flow. */
    int index(String step) {
        return steps.indexOf(step);
    }

    /** 1-based number shown to players ("Tutorial 4/11"), 0 when not in the flow. */
    int number(String step) {
        return index(step) + 1;
    }

    String first() {
        return steps.getFirst();
    }

    /** The first step not in {@code done}, or {@code null} when every step is done (ready to finish). */
    String firstOpen(Set<String> done) {
        for (String s : steps) {
            if (!done.contains(s)) {
                return s;
            }
        }
        return null;
    }

    /** True if {@code step} and every step before it are in {@code done} (false for unknown steps). */
    boolean doneThrough(String step, Set<String> done) {
        int i = index(step);
        if (i < 0) {
            return false;
        }
        for (int k = 0; k <= i; k++) {
            if (!done.contains(steps.get(k))) {
                return false;
            }
        }
        return true;
    }

    /** True if every step is done. */
    boolean allDone(Set<String> done) {
        return firstOpen(done) == null;
    }
}
