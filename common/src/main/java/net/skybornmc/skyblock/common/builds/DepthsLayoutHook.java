package net.skybornmc.skyblock.common.builds;

import java.util.List;

/**
 * Implemented by the depths module ({@code core.services().optional(DepthsLayoutHook.class)}): merges the builders'
 * layout into {@code zones.yml} so the tier zones match the pasted builds, keeping names, tiers and unlock levels.
 * Main thread only (the depths module reloads afterwards).
 */
public interface DepthsLayoutHook {

    /**
     * Result of a merge.
     *
     * @param ok       false if nothing was written ({@code problems} says why)
     * @param changes  one line per changed zone/part
     * @param problems validation warnings of the merged layout (they don't stop the merge) or the error
     * @param backup   name of the backup copy of the previous zones.yml, or {@code null}
     */
    record Result(boolean ok, List<String> changes, List<String> problems, String backup) {

        public Result {
            changes = List.copyOf(changes);
            problems = List.copyOf(problems);
        }
    }

    Result merge(DepthsLayout layout);
}
