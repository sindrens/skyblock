package net.skybornmc.skyblock.tutorial;

import java.util.Locale;
import java.util.Optional;

/**
 * A configured guide target (TUTORIAL.md §9.3): {@code world:x,y,z}, {@code npc:<id>}, {@code role:<npc role>} or
 * {@code build:<category>:<id>}. Pure, unit-tested; resolving to a location happens in {@link Targets}.
 */
sealed interface TargetSpec {

    record Point(String world, double x, double y, double z) implements TargetSpec {
    }

    record Npc(String id) implements TargetSpec {
    }

    record Role(String role) implements TargetSpec {
    }

    record Build(String category, String id) implements TargetSpec {
    }

    static Optional<TargetSpec> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String text = raw.strip();
        int colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            return Optional.empty();
        }
        String head = text.substring(0, colon).strip();
        String rest = text.substring(colon + 1).strip();
        switch (head.toLowerCase(Locale.ROOT)) {
            case "npc" -> {
                return Optional.of(new Npc(rest));
            }
            case "role" -> {
                return Optional.of(new Role(rest.toLowerCase(Locale.ROOT)));
            }
            case "build" -> {
                int c = rest.indexOf(':');
                if (c <= 0 || c == rest.length() - 1) {
                    return Optional.empty();
                }
                return Optional.of(new Build(rest.substring(0, c).strip(), rest.substring(c + 1).strip()));
            }
            default -> {
                String[] xyz = rest.split(",");
                if (xyz.length != 3) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(new Point(head, Double.parseDouble(xyz[0].strip()), Double.parseDouble(xyz[1].strip()),
                            Double.parseDouble(xyz[2].strip())));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            }
        }
    }
}
