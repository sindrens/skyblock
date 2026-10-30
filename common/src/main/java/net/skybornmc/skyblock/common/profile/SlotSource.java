package net.skybornmc.skyblock.common.profile;

import java.util.Locale;
import java.util.Optional;

/**
 * Where a profile slot came from (owner decision P6/P8). Stored as the lowercase key. Only {@link #GEMS} survives a
 * season reset ({@link SlotRules#afterSeasonReset}).
 */
public enum SlotSource {
    /** Slot 1: everyone. */
    FREE,
    /** Bought with gems (kept forever, also after a season reset). */
    GEMS,
    /** From a rank permission ({@code skyblock.profiles.slots.<n>}); live, lost with the rank. */
    RANK,
    /** Given by staff or an event (lost at a season reset). */
    GRANT;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SlotSource> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (SlotSource s : values()) {
            if (s.key().equalsIgnoreCase(key.trim())) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
