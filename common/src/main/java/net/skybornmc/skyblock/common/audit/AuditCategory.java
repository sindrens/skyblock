package net.skybornmc.skyblock.common.audit;

import java.util.Locale;
import java.util.Optional;

/**
 * What an audit row is about. Stored as the lowercase key. Retention per category is configured in {@code audit.yml}
 * (owner decision: 30 days for chat, commands and items; forever for everything that matters later).
 */
public enum AuditCategory {
    ECONOMY,
    TRADE,
    AUCTION,
    ISLAND,
    PUNISHMENT,
    STAFF,
    CONFIG,
    COMMAND,
    CHAT,
    ITEM,
    AUTH,
    WORLD,
    /** Join tutorial (TUTORIAL.md §9.5): start, steps, finish, reward, staff skip/reset. */
    TUTORIAL,
    /** Crates and keys (PHASE-10 M10.1): keys given and taken, opens, reward hand-outs and reviews. */
    CRATES;

    /** Key stored in the database, e.g. {@code "economy"}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<AuditCategory> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(key.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
