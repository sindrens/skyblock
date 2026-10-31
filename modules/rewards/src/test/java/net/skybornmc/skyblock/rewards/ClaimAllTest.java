package net.skybornmc.skyblock.rewards;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.rewards.RewardsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-2: the /menu Rewards button's ready count and "claim all" (RewardsServiceImpl). */
class ClaimAllTest {

    private static final int LEN = 30;
    private static final int TODAY = 100;

    @Test
    void dailyIsReadyOnlyWhenOpenToday() {
        assertTrue(RewardsServiceImpl.dailyReady(true, true, Optional.of(DailyRules.State.NONE), TODAY));
        assertFalse(RewardsServiceImpl.dailyReady(false, true, Optional.of(DailyRules.State.NONE), TODAY), "calendar off");
        assertFalse(RewardsServiceImpl.dailyReady(true, false, Optional.of(DailyRules.State.NONE), TODAY), "no permission");
        assertFalse(RewardsServiceImpl.dailyReady(true, true, Optional.empty(), TODAY), "still loading");
        DailyRules.State after = DailyRules.claim(DailyRules.State.NONE, TODAY, LEN).orElseThrow().after();
        assertFalse(RewardsServiceImpl.dailyReady(true, true, Optional.of(after), TODAY), "claimed today");
        assertTrue(RewardsServiceImpl.dailyReady(true, true, Optional.of(after), TODAY + 1), "tomorrow again");
    }

    /** A claimer that behaves like DailyService: the stored state decides, a second claim on the same day is ALREADY. */
    private static final class FakeDaily {
        final AtomicReference<DailyRules.State> state = new AtomicReference<>(DailyRules.State.NONE);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger paid = new AtomicInteger();

        CompletableFuture<DailyService.Result> claim() {
            calls.incrementAndGet();
            Optional<DailyRules.Claim> c = DailyRules.claim(state.get(), TODAY, LEN);
            if (c.isEmpty()) {
                return CompletableFuture.completedFuture(DailyService.Result.of(DailyService.Outcome.ALREADY));
            }
            state.set(c.get().after());
            paid.incrementAndGet();
            return CompletableFuture.completedFuture(new DailyService.Result(DailyService.Outcome.CLAIMED, c.get().day(),
                    c.get().streakAfter(), Component.text("$100")));
        }

        List<String> ready() {
            return RewardsServiceImpl.dailyReady(true, true, Optional.of(state.get()), TODAY) ? List.of(RewardsService.DAILY) : List.of();
        }
    }

    @Test
    void claimAllIsIdempotent() {
        FakeDaily daily = new FakeDaily();
        RewardsService.ClaimSummary first = RewardsServiceImpl.claimAll(daily.ready(), daily::claim, r -> Component.text("day " + r.day())).join();
        assertEquals(RewardsService.Outcome.CLAIMED, first.outcome());
        assertEquals(List.of(RewardsService.DAILY), first.claimed());
        assertEquals(Component.text("day 1"), first.line());

        RewardsService.ClaimSummary second = RewardsServiceImpl.claimAll(daily.ready(), daily::claim, r -> Component.text("x")).join();
        assertEquals(RewardsService.Outcome.NOTHING_READY, second.outcome());
        assertEquals(List.of(), second.claimed());
        assertEquals(1, daily.calls.get(), "nothing ready: the claim path is not even called");
        assertEquals(1, daily.paid.get());
    }

    @Test
    void aStaleReadyListStillPaysOnce() {
        FakeDaily daily = new FakeDaily();
        List<String> stale = daily.ready(); // the button showed "1 ready" before both clicks
        RewardsServiceImpl.claimAll(stale, daily::claim, r -> Component.empty()).join();
        RewardsService.ClaimSummary again = RewardsServiceImpl.claimAll(stale, daily::claim, r -> Component.empty()).join();
        assertEquals(RewardsService.Outcome.NOTHING_READY, again.outcome(), "already claimed counts as nothing ready, not an error");
        assertEquals(1, daily.paid.get());
    }

    @Test
    void failuresStayFailures() {
        RewardsService.ClaimSummary failed = RewardsServiceImpl.claimAll(List.of(RewardsService.DAILY),
                () -> CompletableFuture.completedFuture(DailyService.Result.of(DailyService.Outcome.FAILED)), r -> Component.text("x")).join();
        assertEquals(RewardsService.Outcome.FAILED, failed.outcome());
        assertEquals(Component.empty(), failed.line());
    }
}
