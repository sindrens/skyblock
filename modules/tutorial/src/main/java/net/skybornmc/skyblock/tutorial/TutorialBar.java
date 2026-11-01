package net.skybornmc.skyblock.tutorial;

import java.util.Collection;
import java.util.List;

/**
 * The tutorial boss bar fill (A3-R1-02), pure: always steps done out of the total steps of the flow. Never distance,
 * never the count inside a step, no minimum (the first step shows an empty bar). A step done early counts at once;
 * done steps that are not in the flow (legacy data) are ignored.
 */
final class TutorialBar {

    private TutorialBar() {
    }

    static float fill(Collection<String> done, TutorialFlow flow) {
        return fill(done, flow.steps());
    }

    static float fill(Collection<String> done, List<String> steps) {
        if (steps.isEmpty() || done == null || done.isEmpty()) {
            return 0f;
        }
        int count = 0;
        for (String step : steps) {
            if (done.contains(step)) {
                count++;
            }
        }
        return Math.max(0f, Math.min(1f, count / (float) steps.size()));
    }
}
