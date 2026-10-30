package net.skybornmc.skyblock.common.achievements;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Achievements (F3), published by the achievements module: {@code core.services().optional(AchievementService.class)}.
 * Progress belongs to the player's <b>active profile</b>; reward titles go to the account (owner decision PR1). Other
 * menus (e.g. {@code /tags}) link to the achievements menu through {@link #open}. Main thread.
 *
 * <p>AC1-AC3 (RANKS_TITLES_ACHIEVEMENTS §C): personal tiers give boost points to the profile (bonus families through
 * {@code BonusService}, plus extra auction slots); island achievements share progress over the island's members and give
 * island boosts ({@link IslandBoost}) and island level points.</p>
 */
public interface AchievementService {

    /** Opens the achievements menu of the player's active profile; Back returns to {@code parent} (may be null). */
    void open(Player player, Menu parent);

    /** Achievements the online player's active profile completed (every tier); 0 while loading or offline. */
    int completed(UUID player);

    /** Achievements that can be earned now (enabled ones). */
    int total();

    /**
     * Extra auction listing slots from the online player's personal Economy achievements (0 to the configured max, 2 by
     * default). 0 while the profile loads or the player is offline. The auction module adds this to its slot count.
     */
    default int extraAuctionSlots(UUID player) {
        return 0;
    }

    /** An island boost of the island in percent, after its cap (0 for unknown islands or before the data loaded). */
    default double islandBoostPercent(UUID islandId, IslandBoost boost) {
        return 0;
    }

    /** Island achievement points of the island (sum of its finished tiers' points; 0 for unknown islands). */
    default int islandPoints(UUID islandId) {
        return 0;
    }

    /** Opens the achievements menu on the Island tab (e.g. from {@code /is}); Back returns to {@code parent}. */
    default void openIsland(Player player, Menu parent) {
        open(player, parent);
    }

    /**
     * Season reset hook for the season script (PLAN12, owner 354): saves a summary of {@code endedSeason} per profile (the
     * History tab), then wipes every personal and island achievement progress in one transaction. Titles stay on the
     * accounts. Completes with the summary rows written, or -1 when that season was already reset. Main thread.
     */
    default CompletableFuture<Integer> seasonReset(int endedSeason, Actor actor) {
        return CompletableFuture.completedFuture(0);
    }
}
