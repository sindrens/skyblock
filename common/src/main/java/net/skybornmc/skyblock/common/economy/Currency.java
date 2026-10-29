package net.skybornmc.skyblock.common.economy;

import java.util.Locale;
import java.util.Optional;

/**
 * The currencies of Phoenix Skyblock (DESIGN §4.1). Every balance is a whole-unit {@code long} (max ≈ 9.2 quintillion).
 * Whether a currency can be paid/traded between players is configured in {@code economy.yml} (decided: shards yes, gems no).
 */
public enum Currency {
    MONEY,
    TOKENS,
    GEMS,
    SHARDS;

    /** Lowercase key used in the database, configs, messages, commands and style tags ({@code <cur:money>}). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Currency> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (Currency c : values()) {
            if (c.key().equals(k)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
