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
    /**
     * Private messages ({@code /msg}, {@code /r}). R3-9b / owner answer 318: split from {@link #CHAT} so helpers can read
     * chat without reading DMs (mods and up get {@code skyblock.staff.logs.category.dm}). Rows logged before the split
     * are moved by {@code db/core/V003__audit_dm_category.sql}.
     */
    DM,
    ITEM,
    AUTH,
    WORLD,
    /** Join tutorial (TUTORIAL.md §9.5): start, steps, finish, reward, staff skip/reset. */
    TUTORIAL,
    /** Crates and keys (PHASE-10 M10.1): keys given and taken, opens, reward hand-outs and reviews. */
    CRATES,
    /** Boosters (PHASE-10 M10.2): items started, banked time, global boosters, thanks. */
    BOOSTERS,
    /** Rewards (PHASE-10 M10.5/M10.6): daily calendar claims, streak rewards, playtime rewards, later votes. */
    REWARDS,
    /** Quests (PHASE-10 M10.7): quests rolled, swapped and finished, island missions, reward hand-outs, staff resets. */
    QUESTS,
    /** Battle pass (PHASE-10 M10.8): pass XP, tier claims, reward hand-outs, premium grants, staff tools. */
    PASS;

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
