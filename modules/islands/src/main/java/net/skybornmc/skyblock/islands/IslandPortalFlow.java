package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.PortalEntry;

/**
 * Pure decision of the spawn island portal (owner answer 159), unit-tested: what walking into it does for a player.
 * An existing island always wins (home), a running build is never started twice, and a new island needs the islands
 * world; everything else creates the island without the confirm menu.
 */
final class IslandPortalFlow {

    private IslandPortalFlow() {
    }

    /**
     * @param hasIsland   the player's active profile already has an island (any role)
     * @param building    an island of this profile is being built right now
     * @param worldLoaded the islands world is loaded
     */
    static PortalEntry decide(boolean hasIsland, boolean building, boolean worldLoaded) {
        if (hasIsland) {
            return building ? PortalEntry.BUSY : PortalEntry.HOME;
        }
        if (building) {
            return PortalEntry.BUSY;
        }
        if (!worldLoaded) {
            return PortalEntry.REFUSED;
        }
        return PortalEntry.CREATING;
    }
}
