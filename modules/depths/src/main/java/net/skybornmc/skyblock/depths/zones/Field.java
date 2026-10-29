package net.skybornmc.skyblock.depths.zones;

import java.util.List;
import java.util.Objects;

/**
 * An ore or log field inside a zone: a cuboid registered with the per-player blocks engine as region
 * {@code <zone>_<id>}.
 *
 * @param id        short id unique within the zone (e.g. {@code ore}, {@code logs})
 * @param box       the field cuboid (only resource blocks + air inside, BUILDER_BRIEF §9.1)
 * @param resources resource blocks of the field (block ids as written in zones.yml)
 */
public record Field(String id, Box box, List<Resource> resources) {

    public Field {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(box, "box");
        resources = List.copyOf(resources);
    }

    /**
     * One resource block.
     *
     * @param block          real block id, e.g. {@code coal_ore}
     * @param depleted       block (state) the player sees while depleted, {@code null} = engine default
     * @param respawnSeconds seconds until it returns, {@code null} = engine default (10 s)
     */
    public record Resource(String block, String depleted, Integer respawnSeconds) {

        public Resource {
            Objects.requireNonNull(block, "block");
        }
    }
}
