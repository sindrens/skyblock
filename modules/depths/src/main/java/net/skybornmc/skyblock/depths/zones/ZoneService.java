package net.skybornmc.skyblock.depths.zones;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * The Depths layout from {@code zones.yml}, published as a service ({@code core.services().get(ZoneService.class)}).
 * Tier locks, {@code /dungeon} (M4.6), forest (M4.7) and combat zones (M4.8) build on it. Main-thread only.
 */
public interface ZoneService {

    /** World of the Depths (all zones live in it). */
    String world();

    /** Every tier zone (all wings), in file order. */
    List<Zone> zones();

    /** Zones of one wing, ordered by tier. */
    List<Zone> zones(Aspect aspect);

    Optional<Zone> zone(String id);

    /** The tier zone at a location (its region box), or {@code null}. Causeways and The Gate are not zones. */
    Zone zoneAt(Location location);

    /** The Gate (hub), or {@code null} if zones.yml has none. */
    Hub hub();

    /** The world boss arena, or {@code null} if zones.yml has none. */
    BossArena bossArena();

    /** Lock check: the zone's required level of its wing's skill (open when no skill system runs). */
    ZoneAccess access(Player player, Zone zone);

    default boolean canEnter(Player player, Zone zone) {
        return access(player, zone).allowed();
    }

    /** Teleport target of a point in the Depths world, or {@code null} if that world is not loaded. */
    Location location(Point point);
}
