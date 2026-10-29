package net.skybornmc.skyblock.depths.zones;

import java.util.Map;
import java.util.Objects;

/**
 * The Gate: the Depths hub at the world center with one wing gate per {@link Aspect}.
 *
 * @param wingGates the monumental gate openings (9 wide × 12 tall) by wing
 */
public record Hub(String name, boolean built, Box region, Point spawn, Map<Aspect, Box> wingGates, Map<String, String> palette) {

    public static final String ID = "gate";

    public Hub {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(spawn, "spawn");
        wingGates = Map.copyOf(wingGates);
        palette = Map.copyOf(palette);
    }
}
