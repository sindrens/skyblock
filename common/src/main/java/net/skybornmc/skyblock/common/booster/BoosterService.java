package net.skybornmc.skyblock.common.booster;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Boosters as seen by other modules (PHASE-10-REWARDS.md M10.2), published by the boosters module:
 * {@code core.services().optional(BoosterService.class)}. Every booster adds to its type's
 * {@link net.skybornmc.skyblock.common.bonus.BonusFamily} through the boosters module's bonus source, so the family caps
 * (and the +300% sell cap, O11) always apply.
 *
 * <ul>
 *   <li><b>Items</b> (crates, later the calendar and quests): {@link #createPersonal} / {@link #createGlobal}. Personal
 *       boosters are always bound to the profile that earned them (RX39). Right-click starts them.</li>
 *   <li><b>Banked time</b> (the AFK pool): {@link #bank} inside the caller's own transaction, then {@link #banked} after
 *       the commit. Banked time starts by itself when the type is free and no {@link #hold} applies.</li>
 * </ul>
 */
public interface BoosterService {

    /** The published service, if the boosters module runs. */
    static Optional<BoosterService> of(ServiceRegistry services) {
        return services.optional(BoosterService.class);
    }

    /** True if an item of this type, strength and length can be made (percent and minutes inside the configured limits). */
    boolean valid(BoosterType type, int percent, int minutes, boolean global);

    /**
     * A new personal booster item (unique, bound to {@code profileKey}). Empty when the numbers are not {@link #valid}.
     *
     * @param origin where it came from, stored on the item for the dupe scanner (e.g. {@code crate:blaze})
     */
    Optional<ItemStack> createPersonal(BoosterType type, int percent, int minutes, UUID profileKey, String origin);

    /**
     * A new global booster item (unique). It is bound to {@code profileKey} when {@code bound} is true or the module's
     * {@code global.always-bound} setting is on. Empty when the numbers are not {@link #valid}.
     */
    Optional<ItemStack> createGlobal(BoosterType type, int percent, int minutes, UUID profileKey, boolean bound, String origin);

    /**
     * Banks personal booster time inside the caller's transaction (e.g. the AFK pool's reward). The newest strength
     * applies to the whole bank of that type. Off the main thread.
     *
     * @param maxBankedMillis most time the bank of this type may hold (0 or less = the module's own limit)
     * @return the banked time of this type afterwards
     */
    long bank(Connection c, UUID profileKey, BoosterType type, int percent, long millis, long maxBankedMillis, String source)
            throws SQLException;

    /** Call after a {@link #bank} transaction committed: refreshes the cache and writes the audit row. Any thread. */
    void banked(UUID profileKey, BoosterType type, int percent, long millis, long bankedAfter, String source, Actor actor);

    /**
     * Keeps banked boosters from starting while {@code hold} is true for a player (the AFK pool: while inside the pool,
     * so a won booster is never wasted while AFK). Main thread; replaces the hold of the same owner.
     */
    void hold(String owner, Predicate<Player> hold);

    /** Removes a hold (on module disable). */
    void release(String owner);
}
