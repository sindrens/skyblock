package net.skybornmc.skyblock.common.economy;

import java.util.Locale;
import java.util.Optional;

/** Who owns an account: a player, or an island (island banks, M3.3). Stored as the lowercase key. */
public enum AccountType {
    PLAYER,
    ISLAND;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<AccountType> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (AccountType t : values()) {
            if (t.key().equalsIgnoreCase(key.trim())) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
