package net.skybornmc.skyblock.blackmarket;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Island Charter gating: above 9x9 only after 9x9 through normal upgrades, one step at a time (owner 157, P10-38). */
class CharterRulesTest {

    private final List<CharterRules.Step> steps = CharterRules.parseSteps(new BlackMarketSettings().charterSteps, 9, p -> {
        throw new AssertionError(p);
    });

    @Test
    void theDefaultStepsAreThePlanPrices() {
        assertEquals(List.of(new CharterRules.Step(11, 150, 250), new CharterRules.Step(13, 300, 500), new CharterRules.Step(15, 450, 750)), steps);
    }

    @Test
    void belowTheNormalMaximumNothingIsSold() {
        for (int size : new int[]{3, 5, 7}) {
            CharterRules.Next next = CharterRules.next(size, 9, steps, 15);
            assertEquals(CharterRules.State.NEEDS_NORMAL_MAX, next.state());
            assertNull(next.step());
        }
    }

    @Test
    void oneStepAtATime() {
        assertEquals(11, CharterRules.next(9, 9, steps, 15).step().size());
        assertEquals(13, CharterRules.next(11, 9, steps, 15).step().size());
        assertEquals(15, CharterRules.next(13, 9, steps, 15).step().size());
        assertEquals(CharterRules.State.MAXED, CharterRules.next(15, 9, steps, 15).state());
        assertEquals(CharterRules.State.MAXED, CharterRules.next(17, 9, steps, 31).state());
    }

    @Test
    void neverAboveTheIslandsCap() {
        assertEquals(CharterRules.State.MAXED, CharterRules.next(11, 9, steps, 11).state());
        assertEquals(13, CharterRules.next(11, 9, steps, 13).step().size());
        assertEquals(CharterRules.State.MAXED, CharterRules.next(13, 9, steps, 13).state());
    }

    @Test
    void anOffLadderSizeHasNoStep() {
        List<CharterRules.Step> gap = List.of(new CharterRules.Step(11, 1, 0), new CharterRules.Step(15, 3, 0));
        assertEquals(CharterRules.State.NO_STEP, CharterRules.next(11, 9, gap, 15).state());
    }

    @Test
    void badStepsAreReported() {
        List<String> problems = new ArrayList<>();
        List<CharterRules.Step> parsed = CharterRules.parseSteps(List.of("12: 1", "9: 1", "11: x", "13", "15: 5 7", "15: 1"), 9, problems::add);
        assertEquals(List.of(new CharterRules.Step(15, 5, 7)), parsed);
        assertEquals(5, problems.size());
    }

    @Test
    void theGemPriceIsOptional() {
        assertEquals(List.of(new CharterRules.Step(11, 150, 0)), CharterRules.parseSteps(List.of("11: 150"), 9, p -> { }));
        assertEquals(0, Purchases.priceOf(new CharterRules.Step(11, 150, 0), net.skybornmc.skyblock.common.economy.Currency.CREDITS_EARNED));
        assertEquals(150, Purchases.priceOf(new CharterRules.Step(11, 150, 0), net.skybornmc.skyblock.common.economy.Currency.SHARDS));
    }
}
