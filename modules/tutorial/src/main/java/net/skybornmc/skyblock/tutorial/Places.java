package net.skybornmc.skyblock.tutorial;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The places of {@code tutorial.yml → places} (pure, unit-tested): {@code "farm: farm grind 0 0 45"} = the command
 * {@code /farm} takes you there, and standing in world {@code grind} within 45 blocks of x 0, z 0 counts as being there.
 * Without x/z/radius the whole world counts; without a world nowhere counts (the command still works for clicks).
 */
final class Places {

    record Place(String id, String command, String world, boolean wholeWorld, double x, double z, double radius) {

        boolean contains(String worldName, double px, double pz) {
            if (world == null || !world.equals(worldName)) {
                return false;
            }
            if (wholeWorld) {
                return true;
            }
            double dx = px - x;
            double dz = pz - z;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    private final Map<String, Place> places;

    private Places(Map<String, Place> places) {
        this.places = Map.copyOf(places);
    }

    Optional<Place> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(places.get(id.toLowerCase(Locale.ROOT)));
    }

    /** The place a player stands in, if any (first match in file order is not guaranteed; places should not overlap). */
    Optional<Place> at(String world, double x, double z) {
        for (Place p : places.values()) {
            if (p.contains(world, x, z)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    java.util.Set<String> ids() {
        return places.keySet();
    }

    static Places parse(List<String> lines, Consumer<String> warn) {
        Map<String, Place> out = new LinkedHashMap<>();
        for (String raw : lines) {
            int colon = raw == null ? -1 : raw.indexOf(':');
            String[] p = colon < 0 ? new String[0] : raw.substring(colon + 1).strip().split("\\s+");
            String id = colon < 0 ? "" : raw.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            if (id.isEmpty() || p.length < 1 || p[0].isEmpty() || (p.length != 1 && p.length != 2 && p.length != 5)) {
                warn.accept("tutorial.yml -> places: '" + raw + "' is not 'id: <command> [world [x z radius]]' (skipped)");
                continue;
            }
            String command = p[0].startsWith("/") ? p[0].substring(1) : p[0];
            try {
                if (p.length == 5) {
                    out.put(id, new Place(id, command, p[1], false, Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                            Math.max(1, Double.parseDouble(p[4]))));
                } else {
                    out.put(id, new Place(id, command, p.length == 2 ? p[1] : null, p.length == 2, 0, 0, 0));
                }
            } catch (NumberFormatException e) {
                warn.accept("tutorial.yml -> places: '" + raw + "' has a number that is not a number (skipped)");
            }
        }
        return new Places(out);
    }
}
