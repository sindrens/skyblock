package net.skybornmc.skyblock.common.economy;

import java.util.Locale;
import java.util.Optional;

/**
 * The currencies of Phoenix Skyblock (DESIGN §4.1). Every balance is a whole-unit {@code long} (max ≈ 9.2 quintillion).
 * Whether a currency can be paid/traded between players is configured in {@code economy.yml} (decided: shards yes, gems no,
 * crystals no). Crystals are never transferable, whatever a config says ({@link #transferable()}, owner R3-5b).
 *
 * <p>Island crystals replaced tokens (owner decision O8 / #39). The old key {@code tokens} still resolves to
 * {@link #CRYSTALS} in {@link #byKey(String)}, so old configs, notes and data keep working.</p>
 */
public enum Currency {
    MONEY,
    CRYSTALS,
    GEMS,
    SHARDS;

    /** Lowercase key used in the database, configs, messages, commands and style tags ({@code <cur:money>}). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Owner R3-5b: false for island crystals. They can never move from one account to another (no /pay, trade, notes, AH),
     * regardless of {@code economy.yml → tradeable.crystals}; {@link Transaction#problem()} refuses such a move.
     */
    public boolean transferable() {
        return this != CRYSTALS;
    }

    /** Removed currency key that now means {@link #CRYSTALS}. */
    public static final String LEGACY_TOKENS_KEY = "tokens";

    /** The currency of a key (case-insensitive, trimmed). The legacy key {@code tokens} maps to {@link #CRYSTALS}. */
    public static Optional<Currency> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.trim().toLowerCase(Locale.ROOT);
        if (k.equals(LEGACY_TOKENS_KEY)) {
            return Optional.of(CRYSTALS);
        }
        for (Currency c : values()) {
            if (c.key().equals(k)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
