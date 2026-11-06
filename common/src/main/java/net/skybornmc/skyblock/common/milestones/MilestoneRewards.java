package net.skybornmc.skyblock.common.milestones;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Milestone tiers that are ready to claim, for {@code /rewards} and the /menu Rewards button (build plan slice 4: "/rewards
 * gets the Milestones card and source"). Published by the milestones module; every claim runs through its own claim (one
 * transaction per tier, a tier is claimed once per island or profile), so claiming twice never gives a perk twice.
 */
public interface MilestoneRewards {

    /** Source id in {@code RewardsService.ready}. */
    String SOURCE = "milestones";

    static Optional<MilestoneRewards> of(ServiceRegistry services) {
        return services.optional(MilestoneRewards.class);
    }

    /** Chains (island and personal) with a tier ready to claim for this player right now. Cached; main thread. */
    int ready(Player player);

    /**
     * Claims the highest ready tier of every ready chain (the tiers below it are marked too). Completes on the main thread
     * with the number of chains claimed (0 = nothing was ready, -1 = failed; the player was told why).
     *
     * @param announce true = the milestones module sends its own "claimed N" line; false = the caller does
     */
    CompletableFuture<Integer> claimAll(Player player, boolean announce);

    /** Opens {@code /milestones}. */
    void open(Player player);

    /**
     * Milestone leaderboard (owner F13a/G6, the "Island Milestones" and "Personal Milestones" boards): the most completed
     * tiers, highest first. Database work off the main thread; the future may complete on any thread.
     * <ul>
     *   <li>{@code island = true}: {@link TopLine#player()} is the <b>island id</b> and {@link TopLine#name()} is
     *   {@code null} (the caller resolves the island name on the main thread).</li>
     *   <li>{@code island = false}: one line per player from their best live profile (P7), with the last known name.</li>
     * </ul>
     * {@link TopLine#value()} is the number of completed tiers (milestone points).
     */
    default CompletableFuture<java.util.List<net.skybornmc.skyblock.common.leaderboard.TopLine>> top(boolean island, int limit) {
        return CompletableFuture.completedFuture(java.util.List.of());
    }
}
