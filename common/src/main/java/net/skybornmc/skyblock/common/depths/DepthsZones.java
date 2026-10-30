package net.skybornmc.skyblock.common.depths;

import org.bukkit.Location;

import java.util.Optional;

/**
 * Read-only view of the Depths zones for other modules (published by the depths module:
 * {@code core.services().optional(DepthsZones.class)}). The tutorial asks where a player is ({@code mine_t01}) and where
 * a zone's arrival point is. Main thread.
 */
public interface DepthsZones {

    /** The id of the tier zone at {@code location} ({@code mine_t01}), empty outside every zone (The Gate is no zone). */
    Optional<String> zoneAt(Location location);

    /** The arrival point of a zone (where {@code /dungeon} teleports to), empty if unknown or its world is not loaded. */
    Optional<Location> spawnOf(String zoneId);

    /** True if {@code location} is in the Depths world. */
    boolean inDepths(Location location);
}
