package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.tutorial.Gates;
import net.skybornmc.skyblock.common.tutorial.TutorialService.State;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which step opens which feature (TUTORIAL.md §4, TQ4) and the open/locked decision. Pure, unit-tested.
 *
 * <p>Rules: staff bypass → open; progress not loaded yet → locked (safe: the lock lifts a moment later); tutorial
 * finished, legacy or staff-skipped → open; a feature not in {@code gates} → open; {@code finish} → open only when the
 * tutorial is complete; otherwise open when the named step <b>and every step before it</b> are done (a money step done
 * early does not open money features while earlier steps are missing). A gate whose step was removed from the flow opens
 * at the finish.</p>
 */
final class GateRules {

    /** Parsed gate lines and readable problems. {@code required}: gate → step id or {@link Steps#FINISH}. */
    record Parsed(Map<String, String> required, List<String> problems) {
    }

    private GateRules() {
    }

    /** Lines like {@code pay: bank}, {@code pay:bank}, {@code pay=bank} or a YAML map read as {@code {pay=bank}}. */
    static Parsed parse(Collection<String> lines, TutorialFlow flow) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            String line = raw == null ? "" : raw.strip();
            if (line.startsWith("{") && line.endsWith("}")) {
                line = line.substring(1, line.length() - 1).strip();
            }
            if (line.isEmpty()) {
                continue;
            }
            int cut = indexOfSeparator(line);
            if (cut <= 0 || cut == line.length() - 1) {
                problems.add("gates: '" + raw + "' must look like 'feature: step'.");
                continue;
            }
            String gate = line.substring(0, cut).strip().toLowerCase(Locale.ROOT);
            String step = line.substring(cut + 1).strip().toLowerCase(Locale.ROOT);
            if (!Gates.known(gate)) {
                problems.add("gates: '" + gate + "' is not a feature (known: " + String.join(", ", Gates.ALL) + ").");
                continue;
            }
            if (!step.equals(Steps.FINISH) && !Steps.KNOWN.contains(step)) {
                problems.add("gates: '" + step + "' is not a step or 'finish' (line '" + raw + "').");
                continue;
            }
            if (!step.equals(Steps.FINISH) && !flow.contains(step)) {
                problems.add("gates: step '" + step + "' is not in flow.steps; " + gate + " opens at the finish.");
                step = Steps.FINISH;
            }
            out.put(gate, step);
        }
        return new Parsed(Map.copyOf(out), List.copyOf(problems));
    }

    private static int indexOfSeparator(String line) {
        int colon = line.indexOf(':');
        int equals = line.indexOf('=');
        if (colon < 0) {
            return equals;
        }
        return equals < 0 ? colon : Math.min(colon, equals);
    }

    /**
     * @param state  the account's state ({@code null} = not loaded yet)
     * @param done   steps done
     * @param bypass staff bypass permission
     */
    static boolean open(String gate, Map<String, String> required, TutorialFlow flow, State state, Set<String> done, boolean bypass) {
        if (bypass) {
            return true;
        }
        String need = gate == null ? null : required.get(gate.toLowerCase(Locale.ROOT));
        if (need == null) {
            return true;
        }
        if (state == null) {
            return false;
        }
        if (state.complete()) {
            return true;
        }
        if (need.equals(Steps.FINISH)) {
            return false;
        }
        return state == State.ACTIVE && flow.doneThrough(need, done);
    }
}
