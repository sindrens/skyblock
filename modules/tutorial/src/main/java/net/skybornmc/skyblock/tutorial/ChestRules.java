package net.skybornmc.skyblock.tutorial;

import java.util.List;

/**
 * When the chest step counts an open and when it is done (TUT-FIX, owner report 31 Oct: "it doesn't detect the chest").
 * Pure, unit-tested.
 * <ul>
 *   <li>An open counts when the container is the remembered starter chest (either half of a double chest). When no
 *   point is known for the player's current island (the remembered one belonged to an older island after a staff reset
 *   or a new island, the starter container was a barrel, the chest was not found at paste), any storage container on
 *   the player's own island counts. The stale point used to block the step for good.</li>
 *   <li>The step is done once a counted container was opened and the player holds the tool kind
 *   ({@code steps.chest.need-tool}), or the container held no such tool to take (a profile whose starter kit was
 *   already claimed gets no tools in the chest, which used to block the step for good).</li>
 * </ul>
 */
final class ChestRules {

    /** One block: world and coordinates. */
    record Spot(String world, int x, int y, int z) {
    }

    private ChestRules() {
    }

    /**
     * True when opening a container made of {@code blocks} counts for the chest step.
     *
     * @param known       the remembered starter chest of the player's current island, or {@code null}
     * @param onOwnIsland whether the container stands on an island the player is a member of; {@code null} when there is
     *                    no islands module (then every container counts while no point is known)
     */
    static boolean counts(List<Spot> blocks, Spot known, Boolean onOwnIsland) {
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
        if (known != null) {
            return blocks.contains(known);
        }
        return onOwnIsland == null || onOwnIsland;
    }

    /**
     * True when the chest step is done.
     *
     * @param opened          a counted container was opened during this session
     * @param needTool        a tool kind is required ({@code steps.chest.need-tool} not empty)
     * @param hasTool         the player holds that tool kind
     * @param containerHasTool the container still holds that tool kind ({@code true} when unknown)
     */
    static boolean done(boolean opened, boolean needTool, boolean hasTool, boolean containerHasTool) {
        if (!opened) {
            return false;
        }
        return !needTool || hasTool || !containerHasTool;
    }
}
