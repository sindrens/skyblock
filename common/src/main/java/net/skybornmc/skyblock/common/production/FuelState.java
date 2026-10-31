package net.skybornmc.skyblock.common.production;

import java.util.Locale;
import java.util.Optional;

/**
 * The three states of an island's Island Fuel (PHASE-7 M7.1), checked every {@code production.yml → fuel.check-seconds}.
 * <ul>
 *   <li>{@link #ACTIVE}: a member is on the island and did something in the last {@code fuel.active-minutes}: production
 *       runs and the tank fills one minute per minute (per island, never per member).</li>
 *   <li>{@link #FUELED}: no active member, the tank has fuel: production runs and the tank burns.</li>
 *   <li>{@link #ASLEEP}: no active member and the tank is empty: production pauses (spawners and crop growth).</li>
 * </ul>
 */
public enum FuelState {
    ACTIVE,
    FUELED,
    ASLEEP;

    /** Lowercase key stored in the database and used in message keys, e.g. {@code fueled}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True while production runs (Active or Fueled). */
    public boolean running() {
        return this != ASLEEP;
    }

    public static Optional<FuelState> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (FuelState s : values()) {
            if (s.key().equalsIgnoreCase(key.trim())) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
