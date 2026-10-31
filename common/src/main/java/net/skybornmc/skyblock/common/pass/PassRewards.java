package net.skybornmc.skyblock.common.pass;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Battle pass tiers that are ready to claim, for the /menu Rewards button (GUI-2, owner G7). Published by the pass module
 * (PHASE-10 M10.8); the rewards module adds {@link #SOURCE} to {@code RewardsService.ready} and claims through
 * {@link #claimAll}. Every claim runs through the pass module's crash-safe claim (claim rows + payout in one transaction),
 * so claiming twice never pays twice.
 */
public interface PassRewards {

    /** Source id in {@code RewardsService.ready} (texts: navigation.yml {@code hub.rewards.sources.pass}). */
    String SOURCE = "pass";

    static Optional<PassRewards> of(ServiceRegistry services) {
        return services.optional(PassRewards.class);
    }

    /** Tiers (free and, with premium, premium) the player's active profile can claim right now. Cached; main thread. */
    int claimable(Player player);

    /**
     * Claims every claimable tier in one transaction. Completes on the main thread with the number of tiers claimed
     * (0 = nothing was ready, -1 = failed; the pass module already told the player why).
     *
     * @param announce true = the pass module sends its own chat line with the rewards; false = the caller does
     */
    CompletableFuture<Integer> claimAll(Player player, boolean announce);
}
