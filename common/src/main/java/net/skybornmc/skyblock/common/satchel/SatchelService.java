package net.skybornmc.skyblock.common.satchel;

import net.skybornmc.skyblock.common.audit.Actor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The Depths satchel (M4.4): virtual, per-<b>profile</b> storage that Depths drops go into instead of the inventory.
 * Published by the depths module: {@code core.services().optional(SatchelService.class)}. Main-thread only.
 *
 * <p>Contents of online players are cached and authoritative while they play (like an inventory); the database is
 * updated in short batches, and every take-out and sale is one atomic database transaction. Item ids are custom item ids
 * ({@code depths_coal}, {@code depths_oak_log} ...), the same ids the Forge recipes and {@code sell.yml} use.</p>
 *
 * <p>Users: the Forge (M4.2) counts satchel materials and takes what the inventory lacks with {@link #consume};
 * Deep Pockets (mastery MA4) and other bonuses add capacity with {@link #capacityBonus}.</p>
 */
public interface SatchelService {

    /** True once the player's satchel for their active profile is loaded (false for a few ms after join/switch). */
    boolean loaded(Player player);

    /** How many of an item the player's active profile holds (0 while not loaded). */
    long amount(Player player, String itemId);

    /** Every item with its amount (copy; empty while not loaded). */
    Map<String, Long> contents(Player player);

    /** Items held in total. */
    long used(Player player);

    /** How many items fit (skills + upgrades + bonuses). */
    long capacity(Player player);

    /**
     * Takes items out for a recipe (the Forge). All or nothing: returns false and changes nothing if the satchel is not
     * loaded or lacks any of them. The change is written to the database right away (not batched) and audited.
     *
     * @param reason short snake_case reason for the audit row, e.g. {@code forge}
     */
    boolean consume(Player player, Map<String, Integer> amounts, String reason, Actor actor);

    /** Adds or replaces a capacity bonus source (e.g. {@code deep_pockets}); the function returns extra items. */
    void capacityBonus(String id, ToIntFunction<Player> bonus);

    /** Removes a capacity bonus source (on module disable). */
    void removeCapacityBonus(String id);
}
