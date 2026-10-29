package net.skybornmc.skyblock.depths.zones;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One tier zone of a Depths wing, as defined in {@code zones.yml}. Real builds change only these coordinates, never
 * code.
 *
 * @param id            unique id, e.g. {@code mine_t04}
 * @param aspect        wing
 * @param tier          tier number in the wing (1 = first)
 * @param name          theme name shown to players ("The Sunken Vault")
 * @param resourceName  what is mined/chopped/fought here ("Gold", "Oak", "Zombies")
 * @param requiredLevel skill level of {@link Aspect#skill()} needed to enter
 * @param built         true once a builder's real build stands here: {@code /depths generate} never touches it
 * @param region        the zone cuboid (≈ 80×80, chunk aligned)
 * @param spawn         arrival pad / teleport target
 * @param gate          the tier gate opening on the zone edge (5 wide × 5 tall)
 * @param exit          opening toward the next tier, {@code null} for the last tier
 * @param causeway      walled path leading into this zone (from the previous tier or The Gate), {@code null} if none
 * @param fields        ore/log fields (empty for combat zones)
 * @param arena         combat fighting area, {@code null} for mine and forest
 * @param biome         biome set by the placeholder build (colors of grass/leaves), {@code null} = leave as is
 * @param palette       placeholder blocks by role (floor, base, wall, trim, light, pad, ground, leaves, arena)
 */
public record Zone(String id, Aspect aspect, int tier, String name, String resourceName, int requiredLevel, boolean built,
                   Box region, Point spawn, Box gate, Box exit, Box causeway, List<Field> fields, Box arena, String biome,
                   Map<String, String> palette) {

    public Zone {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(gate, "gate");
        fields = List.copyOf(fields);
        palette = Map.copyOf(palette);
    }

    /** Region id the engine uses for one of this zone's fields. */
    public String regionId(Field field) {
        return id + "_" + field.id();
    }
}
