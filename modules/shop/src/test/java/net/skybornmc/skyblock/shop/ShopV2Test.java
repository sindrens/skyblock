package net.skybornmc.skyblock.shop;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shop v2 (GUI-3): buy page amounts, Fill inventory, F14 big buys and undo, spawner payback. */
class ShopV2Test {

    // ------------------------------------------------------------------ buy page amounts (S1, SHOP_DESIGN §4)

    @Test
    void quickAmountsFollowTheStackSize() {
        assertArrayEquals(new int[]{1, 16, 64}, ShopMath.quickAmounts(64));
        assertArrayEquals(new int[]{1, 8, 16}, ShopMath.quickAmounts(16), "Buy 16 buys 8 for 16-stackers");
        assertArrayEquals(new int[]{1}, ShopMath.quickAmounts(1), "buckets, beds, tools: only Buy 1 (+ Type)");
        assertArrayEquals(new int[]{1}, ShopMath.quickAmounts(0));
    }

    @Test
    void fillInventoryBuysWhatFitsAndWhatYouCanPay() {
        assertEquals(2_304, ShopMath.fillAmount(4, 1_000_000, 2_304, 2_304), "room and money for 36 stacks");
        assertEquals(250, ShopMath.fillAmount(4, 1_000, 2_304, 2_304), "money runs out first");
        assertEquals(40, ShopMath.fillAmount(4, 1_000, 40, 2_304), "room runs out first");
        assertEquals(100, ShopMath.fillAmount(4, 1_000_000, 2_304, 100), "never above buy.max-amount");
        assertEquals(0, ShopMath.fillAmount(4, 1_000, 0, 2_304), "full inventory");
        assertEquals(0, ShopMath.fillAmount(4, 3, 64, 2_304), "can't afford one");
        assertEquals(0, ShopMath.fillAmount(0, 1_000, 64, 2_304), "broken price");
    }

    // ------------------------------------------------------------------ F14 big buys

    @Test
    void bigBuyIsOverHalfYourMoney() {
        assertFalse(ShopMath.bigBuy(500, 1_000, 50), "exactly half is not over half");
        assertTrue(ShopMath.bigBuy(501, 1_000, 50));
        assertFalse(ShopMath.bigBuy(10, 1_000, 50));
        assertFalse(ShopMath.bigBuy(2_000, 1_000, 50), "can't pay it: fails with not enough money instead");
        assertFalse(ShopMath.bigBuy(999, 1_000, 100), "100 = never");
        assertTrue(ShopMath.bigBuy(Long.MAX_VALUE / 2, Long.MAX_VALUE - 1, 40), "no overflow");
        assertEquals(74, ShopMath.percentOf(740, 1_000));
        assertEquals(100, ShopMath.percentOf(1_000, 1_000));
        assertEquals(0, ShopMath.percentOf(5, 0));
    }

    @Test
    void secondClickOnTheSameButtonBuys() {
        BigBuyGuard guard = new BigBuyGuard();
        assertFalse(guard.click("amount:64", true, 1_000, 5_000), "first click only arms");
        assertTrue(guard.armed("amount:64", 2_000));
        assertTrue(guard.click("amount:64", true, 2_000, 5_000), "second click buys");
        assertFalse(guard.armed("amount:64", 2_001), "and disarms");
    }

    @Test
    void armedButtonDisarmsAfterFiveSecondsOrAnotherClick() {
        BigBuyGuard guard = new BigBuyGuard();
        assertFalse(guard.click("amount:64", true, 0, 5_000));
        assertTrue(guard.expired(5_000));
        assertFalse(guard.click("amount:64", true, 5_000, 5_000), "too late: arms again instead of buying");
        assertFalse(guard.click("fill", true, 6_000, 5_000), "another button: that one arms, the first disarms");
        assertFalse(guard.armed("amount:64", 6_001));
        assertTrue(guard.click("amount:1", false, 6_500, 5_000), "a small buy goes through and clears the armed one");
        assertFalse(guard.armed("fill", 6_501));
    }

    @Test
    void bigBuyToggleOffMeansOneClick() {
        // ShopModule.needsSecondClick = setting on && bigBuy(...): with the /settings toggle off the guard sees big = false.
        BigBuyGuard guard = new BigBuyGuard();
        boolean settingOn = false;
        assertTrue(guard.click("amount:64", settingOn && ShopMath.bigBuy(900, 1_000, 50), 0, 5_000));
        settingOn = true;
        assertFalse(guard.click("amount:64", settingOn && ShopMath.bigBuy(900, 1_000, 50), 0, 5_000));
    }

    // ------------------------------------------------------------------ F14 undo last buy

    @Test
    void undoIsClaimedExactlyOnce() {
        UndoLedger ledger = new UndoLedger();
        UUID player = UUID.randomUUID();
        UUID purchase = UUID.randomUUID();
        ledger.offer(player, new UndoLedger.Offer(purchase, UUID.randomUUID(), "torch", 64, 256, 15_000));
        assertTrue(ledger.peek(player, 1_000).isPresent());
        assertEquals(15, ledger.peek(player, 0).orElseThrow().secondsLeft(0));
        assertEquals(1, ledger.peek(player, 14_500).orElseThrow().secondsLeft(14_500));
        assertTrue(ledger.claim(player, purchase, 2_000), "first claim wins");
        assertFalse(ledger.claim(player, purchase, 2_001), "a double click can't refund twice");
        assertTrue(ledger.peek(player, 2_002).isEmpty());
    }

    @Test
    void undoRunsOutAfterItsWindowAndANewBuyReplacesIt() {
        UndoLedger ledger = new UndoLedger();
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ledger.offer(player, new UndoLedger.Offer(first, UUID.randomUUID(), "torch", 64, 256, 15_000));
        assertFalse(ledger.claim(player, first, 15_000), "15 s are over");
        assertTrue(ledger.peek(player, 15_000).isEmpty());
        ledger.offer(player, new UndoLedger.Offer(first, UUID.randomUUID(), "torch", 64, 256, 30_000));
        ledger.offer(player, new UndoLedger.Offer(second, UUID.randomUUID(), "dirt", 1, 4, 30_000));
        assertFalse(ledger.claim(player, first, 20_000), "only the last buy can be undone");
        assertTrue(ledger.claim(player, second, 20_000));
        ledger.offer(player, new UndoLedger.Offer(first, UUID.randomUUID(), "torch", 64, 256, 60_000));
        ledger.clear(player);
        assertTrue(ledger.peek(player, 40_000).isEmpty(), "quit / profile switch / setting off clears it");
    }

    @Test
    void undoTakesItemsFromTheMainInventoryFirst() {
        int[] held = new int[36];
        held[0] = 10;   // hotbar
        held[20] = 30;  // main
        held[35] = 40;  // main
        int[] plan = InventorySpace.takePlan(held, 64);
        assertEquals(10, plan[0], "the hotbar is touched last");
        assertEquals(6, plan[20]);
        assertEquals(0, plan[35]);
        assertNull(InventorySpace.takePlan(held, 81), "not all items there: nothing taken");
        int[] all = InventorySpace.takePlan(held, 80);
        assertEquals(0, all[0] + all[20] + all[35]);
    }

    // ------------------------------------------------------------------ spawners (S5)

    @Test
    void spawnerPaybackUsesTheNextSpawnersOutput() {
        assertEquals(117_000 / 1_900.0, ShopMath.paybackHours(117_000, 1_900, 100).orElseThrow(), 1e-9);
        assertEquals(2 * 117_000 / 1_900.0, ShopMath.paybackHours(117_000, 1_900, 50).orElseThrow(), 1e-9,
                "F2: the next one works at 50% = twice as long");
        assertTrue(ShopMath.paybackHours(117_000, 1_900, 0).isEmpty(), "0% never pays back");
        assertTrue(ShopMath.paybackHours(117_000, 0, 100).isEmpty());
    }

    @Test
    void spawnerPaybackColors() {
        ShopSettings s = new ShopSettings();
        assertEquals(100, s.paybackGoodHours);
        assertEquals(300, s.paybackOkHours);
        // tier 1 at 100%: 117,000 / 1,900 = 62 h (green); at 35%: 176 h (yellow); at 10%: 616 h (red, Poor deal)
        assertEquals(ShopMath.Payback.GOOD, ShopMath.payback(ShopMath.paybackHours(117_000, 1_900, 100), 100, 300));
        assertEquals(ShopMath.Payback.OK, ShopMath.payback(ShopMath.paybackHours(117_000, 1_900, 35), 100, 300));
        assertEquals(ShopMath.Payback.POOR, ShopMath.payback(ShopMath.paybackHours(117_000, 1_900, 10), 100, 300));
        assertEquals(ShopMath.Payback.GOOD, ShopMath.payback(java.util.OptionalDouble.of(100), 100, 300), "100 h is still green");
        assertEquals(ShopMath.Payback.OK, ShopMath.payback(java.util.OptionalDouble.of(300), 100, 300));
        assertEquals(ShopMath.Payback.POOR, ShopMath.payback(java.util.OptionalDouble.empty(), 100, 300), "never pays back");
    }

    @Test
    void settingsHaveIncomeForEveryTier() {
        ShopSettings s = new ShopSettings();
        assertEquals(1_900, s.incomePerHour(1));
        assertEquals(3_500, s.incomePerHour(2));
        assertEquals(0, s.incomePerHour(6));
        assertEquals(2, s.spawnerMaxTier);
        assertEquals(15, s.undoSeconds);
        assertEquals(50, s.bigBuyPercent);
        assertEquals(5, s.bigBuyArmSeconds);
    }
}
