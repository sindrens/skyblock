package net.skybornmc.skyblock.common.progression;

import java.util.Locale;
import java.util.Optional;

/**
 * The five island pillars (DESIGN §3.5, PHASE-5 M5.1). Members raise them with the base skill XP they earn and a few
 * island actions; Conquest stays hidden until its first source exists ({@code pillars.yml → conquest.enabled}).
 * Stored by {@link #id()} (never rename an id: it is the database value).
 */
public enum Pillar {
    MINING,
    FORAGING,
    FARMING,
    COMBAT,
    CONQUEST;

    /** Lowercase id used in configs, the database, placeholders and messages, e.g. {@code "mining"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Pillar> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim();
        for (Pillar pillar : values()) {
            if (pillar.id().equalsIgnoreCase(key)) {
                return Optional.of(pillar);
            }
        }
        return Optional.empty();
    }
}
