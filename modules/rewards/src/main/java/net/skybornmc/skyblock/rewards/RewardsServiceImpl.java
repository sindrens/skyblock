package net.skybornmc.skyblock.rewards;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.milestones.MilestoneRewards;
import net.skybornmc.skyblock.common.pass.PassRewards;
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
 * payment. Battle pass tiers (M10.8) that are ready count as one source ({@link PassRewards#SOURCE}); claiming runs the pass
 * module's own claim (one transaction, its own chat lines) and the summary only counts the tiers.
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
        // Spec G1 / F12: with the /rewards menu on, the count equals what /rewards lists (votes stay in /vote).
        boolean votes = !m.settings.menuEnabled && m.votesSettings.enabled && player.hasPermission(RewardsModule.PERM_VOTE)
                && m.votes().queued(player.getUniqueId()) > 0;
        List<String> out = new ArrayList<>(5);
        if (daily) {
            out.add(DAILY);
        }
        if (votes) {
            out.add(VoteService.READY);
        }
        for (PeriodicRules.Period period : PeriodicRules.Period.values()) {
            if (m.periodic().ready(player, period)) {
                out.add(period.source());
            }
        }
        if (PassRewards.of(m.core.services()).map(p -> p.claimable(player) > 0).orElse(false)) {
            out.add(PassRewards.SOURCE);
        }
        if (MilestoneRewards.of(m.core.services()).map(ms -> ms.ready(player) > 0).orElse(false)) {
            out.add(MilestoneRewards.SOURCE);
        }
        return out;
    }

    @Override
    public CompletableFuture<ClaimSummary> claimAll(Player player) {
        List<String> ready = ready(player);
        CompletableFuture<ClaimSummary> daily = claimAll(ready, () -> m.daily().claim(player, true), r -> m.msg.component(player,
                "claim-all.claimed", Msg.number("day", r.day()), Msg.number("streak", r.streak()), Msg.c("rewards", r.inline())));
        CompletableFuture<ClaimSummary> withVotes = !ready.contains(VoteService.READY) ? daily
                // Waiting votes pay themselves with their own chat lines (one per vote); the summary only counts them.
                : daily.thenCombine(m.votes().payQueued(player), (summary, paid) -> withVotes(summary, paid,
                () -> m.msg.component(player, "claim-all.votes", Msg.number("votes", paid))));
        // Weekly and Monthly Reward: each its own claim (spec 4.3 step 6: one failure never stops the others).
        CompletableFuture<ClaimSummary> withPeriodic = withVotes;
        for (PeriodicRules.Period period : PeriodicRules.Period.values()) {
            if (ready.contains(period.source())) {
                withPeriodic = withPeriodic.thenComposeAsync(summary -> m.periodic().claim(player, period, true)
                        .thenApply(r -> withPeriodic(summary, r, period.source(), () -> m.msg.component(player,
                                "claim-all." + period.source(), Msg.c("rewards", r.inline())))), m.main());
            }
        }
        Optional<PassRewards> pass = PassRewards.of(m.core.services());
        CompletableFuture<ClaimSummary> withPassTiers = !ready.contains(PassRewards.SOURCE) || pass.isEmpty() ? withPeriodic
                // Pass tiers are claimed in the pass module's own transaction, which also writes its own chat lines.
                : withPeriodic.thenComposeAsync(summary -> pass.get().claimAll(player, true).thenApply(tiers -> withPass(summary, tiers,
                () -> m.msg.component(player, "claim-all.pass", Msg.number("count", tiers)))), m.main());
        Optional<MilestoneRewards> milestones = MilestoneRewards.of(m.core.services());
        if (!ready.contains(MilestoneRewards.SOURCE) || milestones.isEmpty()) {
            return withPassTiers;
        }
        // Milestone tiers: one claim per chain in the milestones module; the summary line says how many.
        return withPassTiers.thenComposeAsync(summary -> milestones.get().claimAll(player, false).thenApply(n -> withExtraOrFailed(
                summary, n, MilestoneRewards.SOURCE, () -> m.msg.component(player, "claim-all.milestones", Msg.number("count", n)))),
                m.main());
    }

    /** Like {@link #withPass} for any count-based source (milestones). */
    static ClaimSummary withExtraOrFailed(ClaimSummary before, int count, String source, Supplier<Component> line) {
        if (count < 0) {
            return before.outcome() == Outcome.NOTHING_READY ? ClaimSummary.failed() : before;
        }
        return withExtra(before, count, source, line);
    }

    /**
     * Adds claimed pass tiers to a summary (pure, unit-tested): {@code tiers > 0} adds the source and the line; a failed pass
     * claim ({@code tiers < 0}) turns "nothing ready" into "failed" (the pass already told the player why).
     */
    static ClaimSummary withPass(ClaimSummary before, int tiers, Supplier<Component> passLine) {
        if (tiers < 0) {
            return before.outcome() == Outcome.NOTHING_READY ? ClaimSummary.failed() : before;
        }
        return withExtra(before, tiers, PassRewards.SOURCE, passLine);
    }

    /** Adds a Weekly or Monthly claim to a summary (pure, unit-tested); a failed one turns "nothing ready" into "failed". */
    static ClaimSummary withPeriodic(ClaimSummary before, PeriodicService.Result result, String source, Supplier<Component> line) {
        return switch (result.outcome()) {
            case CLAIMED -> withExtra(before, 1, source, line);
            case FAILED -> before.outcome() == Outcome.NOTHING_READY ? ClaimSummary.failed() : before;
            default -> before;
        };
    }

    /** Adds paid waiting votes to a daily summary (pure, unit-tested). */
    static ClaimSummary withVotes(ClaimSummary daily, int paidVotes, Supplier<Component> votesLine) {
        return withExtra(daily, paidVotes, VoteService.READY, votesLine);
    }

    /** Adds {@code count} claimed rewards of another source to a summary ({@code count <= 0} = unchanged). */
    static ClaimSummary withExtra(ClaimSummary before, int count, String source, Supplier<Component> extraLine) {
        if (count <= 0) {
            return before;
        }
        List<String> claimed = new ArrayList<>(before.claimed());
        claimed.add(source);
        Component line = before.outcome() == Outcome.CLAIMED
                ? before.line().append(Component.space()).append(extraLine.get()) : extraLine.get();
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
