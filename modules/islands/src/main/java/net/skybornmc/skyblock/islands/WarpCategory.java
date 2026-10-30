package net.skybornmc.skyblock.islands;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Warp categories (owner decision I14): shops, farms, parkour, builds, other. The stored key never changes; display
 * names are in messages/islands.yml ({@code warps.category.<key>}), default icons in islands.yml.
 */
public enum WarpCategory {
    SHOPS, FARMS, PARKOUR, BUILDS, OTHER;

    private static final WarpCategory[] VALUES = values();

    /** Stored key, also typed in {@code /is setwarp <name> [category]}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<WarpCategory> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (WarpCategory category : VALUES) {
            if (category.key().equals(k)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    /** Unknown or missing keys (e.g. rows from a newer version) fall back to {@link #OTHER}. */
    public static WarpCategory byKeyOrOther(String key) {
        return byKey(key).orElse(OTHER);
    }

    /** The next category (the edit menu cycles with one plain click, Bedrock-safe). */
    public WarpCategory next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static List<String> keys() {
        return Arrays.stream(VALUES).map(WarpCategory::key).toList();
    }
}
