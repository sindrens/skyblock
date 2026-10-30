package net.skybornmc.skyblock.common.builds;

import java.util.List;
import java.util.Map;

/**
 * The Depths layout from the builders ({@code builds/depths_layout.yml}) plus the wiring data of the Depths builds
 * being applied. Neutral data handed by the worlds module to the depths module ({@link DepthsLayoutHook}).
 *
 * @param floorY standing level of the hub and zone floors
 * @param gate   cuboid of The Gate build, or {@code null}
 * @param zones  every planned tier zone, in file order
 * @param built  zones whose real build is being applied (marked built, wired from their metadata)
 * @param hub    The Gate build being applied, or {@code null}
 */
public record DepthsLayout(int floorY, BuildBox gate, List<LayoutZone> zones, List<BuiltZone> built, BuiltHub hub) {

    public DepthsLayout {
        zones = List.copyOf(zones);
        built = List.copyOf(built);
    }

    /** A feet position with facing ({@code yaw}: 0 south, 90 west, 180 north, -90 east). */
    public record Pos(double x, double y, double z, float yaw, float pitch) {
    }

    /**
     * One planned zone of the layout.
     *
     * @param id       layout id ({@code mine_t01_coal}); the depths module matches zones by wing + tier
     * @param wing     {@code mine}, {@code forest} or {@code combat}
     * @param entrance side the causeway arrives on ({@code north|south|east|west})
     * @param exit     side toward the next tier, {@code null} for the last
     */
    public record LayoutZone(String id, String wing, int tier, BuildBox region, int centerX, int centerZ, String entrance,
                             String exit) {
    }

    /** An ore/log field of a built zone. {@code depleted}/{@code respawnSeconds} may be {@code null} (defaults). */
    public record BuiltField(String id, BuildBox box, String block, String depleted, Integer respawnSeconds) {
    }

    /**
     * A zone whose real build is applied: everything the code needs from its metadata.
     *
     * @param build   build id ({@code depths_mine_t01_coal_v1})
     * @param arrival arrival pad feet position (the {@code /dungeon} and {@code /depths tp} target)
     * @param gate    tier gate opening, {@code null} = keep
     * @param exit    opening toward the next tier, {@code null} = keep
     * @param arena   combat fighting area, {@code null} = keep
     * @param fields  ore/log fields (empty = keep the current ones)
     */
    public record BuiltZone(String build, String wing, int tier, Pos arrival, BuildBox gate, BuildBox exit, BuildBox arena,
                            List<BuiltField> fields) {

        public BuiltZone {
            fields = List.copyOf(fields);
        }
    }

    /**
     * The Gate build being applied.
     *
     * @param spawn     arrival / world spawn
     * @param wingGates causeway starts by wing ({@code mine}, {@code forest}, {@code combat}), may be empty
     */
    public record BuiltHub(String build, Pos spawn, Map<String, BuildBox> wingGates) {

        public BuiltHub {
            wingGates = Map.copyOf(wingGates);
        }
    }
}
