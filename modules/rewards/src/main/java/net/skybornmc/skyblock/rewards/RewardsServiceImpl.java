package net.skybornmc.skyblock.rewards;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.rewards.RewardsService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link RewardsService} for the /menu Rewards button (GUI-2, owner G7). Ready = today's daily reward is open (from the
 * cached calendar state). Playtime rewards are paid automatically while playing, so "claim all" claims the daily reward
 * through the normal crash-safe daily claim ({@link DailyService#claim(Player, boolean)}: one transaction with an
 * idempotency key) and its summary line says that playtime pays by itself. Votes are paid when they arrive; only votes
 * that waited for an offline player and are still unpaid count as ready, and claiming pays them through the normal vote
 * payment.
 */
final class RewardsServiceImpl implements RewardsService {

    private final RewardsModule m;

    RewardsServiceImpl(RewardsModule module) {
        this.m = module;
    }

    @Override
    public List<String> ready(Player player) {
        boolean daily = dailyReady(m.settings.calendarEnabled, player.hasPermission(RewardsModule.PERM_DAILY),
                m.daily().state(m.profiles().profileKey(player)), m.daily().today());
        boolean votes = m.votesSettings.enabled && player.hasPermission(RewardsModule.PERM_VOTE) && m.votes().queued(player.getUniqueId()) > 0;
        List<String> out = new ArrayList<>(2);
        if (daily) {
            out.add(DAILY);
        }
        if (votes) {
            out.add(VoteService.READY);
        }
        return out;
    }

    @Override
    public CompletableFuture<ClaimSummary> claimAll(Player player) {
        List<String> ready = ready(player);
        CompletableFuture<ClaimSummary> daily = claimAll(ready, () -> m.daily().claim(player, true), r -> m.msg.component(player,
                "claim-all.claimed", Msg.number("day", r.day()), Msg.number("streak", r.streak()), Msg.c("rewards", r.inline())));
        if (!ready.contains(VoteService.READY)) {
            return daily;
        }
        // Waiting votes pay themselves with their own chat lines (one per vote); the summary only counts them.
        return daily.thenCombine(m.votes().payQueued(player), (summary, paid) -> withVotes(summary, paid,
                () -> m.msg.component(player, "claim-all.votes", Msg.number("votes", paid))));
    }

    /** Adds paid waiting votes to a daily summary (pure, unit-tested). */
    static ClaimSummary withVotes(ClaimSummary daily, int paidVotes, Supplier<Component> votesLine) {
        if (paidVotes <= 0) {
            return daily;
        }
        List<String> claimed = new ArrayList<>(daily.claimed());
        claimed.add(VoteService.READY);
        Component line = daily.outcome() == Outcome.CLAIMED
                ? daily.line().append(Component.space()).append(votesLine.get()) : votesLine.get();
        return new ClaimSummary(Outcome.CLAIMED, claimed, line);
    }

    // ------------------------------------------------------------------ pure parts (unit-tested)

    /** Today's daily reward can be claimed: calendar on, allowed, state loaded and not claimed today. */
    static boolean dailyReady(boolean enabled, boolean permitted, Optional<DailyRules.State> state, int today) {
        return enabled && permitted && state.isPresent() && !DailyRules.claimedToday(state.get(), today);
    }

    /**
     * Claims each ready source once. Nothing ready = nothing is called. A source that turns out to be claimed already
     * (another click or server was faster) counts as nothing ready, so a repeated "claim all" never pays twice and
     * never shows an error.
     */
    static CompletableFuture<ClaimSummary> claimAll(List<String> ready, Supplier<CompletableFuture<DailyService.Result>> daily,
                                                    Function<DailyService.Result, Component> line) {
        if (!ready.contains(DAILY)) {
            return CompletableFuture.completedFuture(ClaimSummary.nothingReady());
        }
        return daily.get().thenApply(r -> switch (r.outcome()) {
            case CLAIMED -> {
                List<String> claimed = new ArrayList<>();
                claimed.add(DAILY);
                yield new ClaimSummary(Outcome.CLAIMED, claimed, line.apply(r));
            }
            case ALREADY -> ClaimSummary.nothingReady();
            case FAILED -> ClaimSummary.failed();
        });
    }
}
