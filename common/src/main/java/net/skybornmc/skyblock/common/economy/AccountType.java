package net.skybornmc.skyblock.common.economy;

import java.util.Locale;
import java.util.Optional;

/**
 * Who owns an account: a player (profile key), an island (island bank, M3.3) or a personal bank (M3.3, one per profile:
 * {@link AccountRef#bank}). Stored as the lowercase key. New types are only ever appended: the order is the lock order.
 */
public enum AccountType {
    PLAYER,
    ISLAND,
    /** A profile's personal bank (owner decision P2: per profile); the owner id is the profile key. */
    BANK;

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
