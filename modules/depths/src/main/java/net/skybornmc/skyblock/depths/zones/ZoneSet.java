package net.skybornmc.skyblock.depths.zones;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Everything {@code zones.yml} defines: the world, The Gate, the boss arena and every tier zone.
 *
 * @param floorY standing level of hub floors (players stand at this Y; the floor block is one below)
 */
public record ZoneSet(String world, int floorY, Hub hub, BossArena arena, List<Zone> zones) {

    public ZoneSet {
        zones = List.copyOf(zones);
    }

    public static ZoneSet empty(String world, int floorY) {
        return new ZoneSet(world, floorY, null, null, List.of());
    }

    public Optional<Zone> zone(String id) {
        return zones.stream().filter(z -> z.id().equalsIgnoreCase(id)).findFirst();
    }

    /** Zones of one wing, ordered by tier. */
    public List<Zone> zones(Aspect aspect) {
        List<Zone> list = new ArrayList<>();
        for (Zone z : zones) {
            if (z.aspect() == aspect) {
                list.add(z);
            }
        }
        list.sort(Comparator.comparingInt(Zone::tier));
        return list;
    }
}
