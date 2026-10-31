package net.skybornmc.skyblock.common.collection;

import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The collection log (PHASE-10-REWARDS.md M10.10), published by the collection module:
 * {@code core.services().optional(CollectionLog.class)}. Per profile (owner 130). An entry unlocks the first time a
 * profile gets the item itself (drops, crates, later quests, fishing, bosses ...); trades, AH buys and /pay never count.
 *
 * <p>Sources that live in other modules call {@link #found} when a player obtains something; the log ignores keys that
 * are not in its catalog and keys the profile already has, so callers can report every drop.</p>
 */
public interface CollectionLog {

    /**
     * Reports that {@code player} got the thing with this catalog key themselves (main thread). Unknown keys and entries
     * the profile already has are ignored. Saved in batches; page milestones are written in one transaction.
     *
     * @param key    catalog key, e.g. a custom item id ({@code depths_coal}) or {@code crate.<crate>.<reward>}
     * @param source short snake_case source for the log, e.g. {@code fishing}
     */
    void found(Player player, String key, String source);

    /** Entries the profile has found (0 when not loaded yet). Main thread. */
    int foundCount(UUID profileKey);

    /** Entries in the whole log right now. */
    int totalCount();

    /** Opens {@code /collection}; {@code parent} = Back target or null. Main thread. */
    void open(Player player, Menu parent);
}
