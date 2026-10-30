package net.skybornmc.skyblock.progression;

import java.util.Locale;
import java.util.Optional;

/** Tabs of {@code /is upgrade} (PHASE-5 M5.4 mockup); the Masteries tab comes with M5.5. */
enum UpgradeTab {
    ISLAND,
    ECONOMY,
    DEPTHS,
    PRODUCTION;

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<UpgradeTab> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (UpgradeTab t : values()) {
            if (t.id().equals(key)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
