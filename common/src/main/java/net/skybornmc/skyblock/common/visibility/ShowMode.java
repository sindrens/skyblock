package net.skybornmc.skyblock.common.visibility;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The player's {@code /settings → Show players} choice (REVIEW3 D4). Applies only where {@code visibility.yml} says so
 * (the Depths and the AFK pool by default); everywhere else everyone stays visible.
 */
public enum ShowMode {
    /** See everyone (default). */
    EVERYONE,
    /** See only island members and coop players (and later friends, through {@link VisibilityService#addRelation}). */
    FRIENDS,
    /** See nobody (staff that is not vanished stays visible). */
    NOBODY;

    /** Stored/typed id: {@code everyone}, {@code friends}, {@code nobody}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<String> ids() {
        return java.util.Arrays.stream(values()).map(ShowMode::id).toList();
    }

    public static Optional<ShowMode> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (ShowMode mode : values()) {
            if (mode.id().equalsIgnoreCase(id.trim())) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
