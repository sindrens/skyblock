package net.skybornmc.skyblock.common.rewards;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Rewards that are ready to claim, for the /menu Rewards button (GUI_DESIGN §7.1, owner G7: it glows gold with a count
 * and one click claims everything ready). Provided by the rewards module; use {@code services().optional(...)}.
 *
 * <p>Sources today: the daily calendar. Playtime rewards are paid automatically, so they are never "ready" here; vote
 * rewards join later. Every claim runs through the owning module's own crash-safe claim path (one transaction with an
 * idempotency key), so claiming twice never pays twice.</p>
 */
public interface RewardsService {

    /** Source id of the daily calendar (for {@link #ready} and {@link ClaimSummary#claimed}). */
    String DAILY = "daily";

    /** Number of rewards the player can claim right now (cheap, from cached state; main thread). */
    default int readyCount(Player player) {
        return ready(player).size();
    }

    /** Ids of the sources with a reward ready right now, e.g. {@code [daily]} (cheap; main thread). */
    List<String> ready(Player player);

    /**
     * Claims every ready reward. The future completes on the main thread. Problems (frozen, box full, busy, database
     * error) are told to the player by the rewards module itself and come back as {@link Outcome#FAILED}.
     */
    CompletableFuture<ClaimSummary> claimAll(Player player);

    enum Outcome {
        /** At least one reward was claimed; {@link ClaimSummary#line} says what. */
        CLAIMED,
        /** Nothing was ready (for example claimed a moment ago somewhere else). Nothing was sent to the player. */
        NOTHING_READY,
        /** Nothing was claimed; the player already got a message why. */
        FAILED
    }

    /**
     * @param claimed source ids that were claimed
     * @param line    one-line, player-facing summary (rewards module texts); empty component when nothing was claimed
     */
    record ClaimSummary(Outcome outcome, List<String> claimed, Component line) {

        public ClaimSummary {
            claimed = claimed == null ? List.of() : List.copyOf(claimed);
            line = line == null ? Component.empty() : line;
        }

        public static ClaimSummary nothingReady() {
            return new ClaimSummary(Outcome.NOTHING_READY, List.of(), Component.empty());
        }

        public static ClaimSummary failed() {
            return new ClaimSummary(Outcome.FAILED, List.of(), Component.empty());
        }
    }
}
