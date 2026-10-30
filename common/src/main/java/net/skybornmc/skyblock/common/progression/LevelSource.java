package net.skybornmc.skyblock.common.progression;

import java.util.Locale;
import java.util.Optional;

/**
 * The eight sources of the dynamic island level (PHASE-5 M5.2, owner P5-1). Earned sources never go down within a
 * season; live sources follow the island as it is now (owner 168). Stored and configured by {@link #id()}.
 */
public enum LevelSource {
    /** 1 point per shown pillar level (M5.1). */
    PILLARS(true),
    /** Upgrade levels, mastery ranks and Ascension (M5.4/M5.5; 0 until they exist). */
    UPGRADES(true),
    /** Island achievement points (AC; 0 until island achievements feed it). */
    ACHIEVEMENTS(true),
    /** Collection milestones (M10.10; 0 until the collection log exists). */
    COLLECTIONS(true),
    /** Average total skill level of members who have been on the island for 3+ days. */
    SKILLS(false),
    /** Best counted member's unlocked Depths tier per aspect. */
    DEPTHS(false),
    /** Lowest island bank balance of the last 24 h, log scale. */
    BANK(false),
    /** Worth of the placed blocks inside the border, square root (max 20% of the level, owner O3). */
    BLOCKS(false);

    private final boolean earned;

    LevelSource(boolean earned) {
        this.earned = earned;
    }

    /** True for earned sources (they count for the weekly boards; they never go down within a season). */
    public boolean earned() {
        return earned;
    }

    /** Lowercase id used in configs, the database and messages, e.g. {@code "blocks"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<LevelSource> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (LevelSource s : values()) {
            if (s.id().equalsIgnoreCase(id.trim())) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
