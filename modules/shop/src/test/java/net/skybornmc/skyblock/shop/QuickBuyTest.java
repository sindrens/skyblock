package net.skybornmc.skyblock.shop;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Quick-buy click mapping and partial-buy math (SELL2). */
class QuickBuyTest {

    @Test
    void javaClicks() {
        assertEquals(QuickBuy.Action.BUY_ONE, QuickBuy.action(ClickType.LEFT, false));
        assertEquals(QuickBuy.Action.BUY_STACK, QuickBuy.action(ClickType.RIGHT, false));
        assertEquals(QuickBuy.Action.PICK_AMOUNT, QuickBuy.action(ClickType.SHIFT_LEFT, false));
        assertEquals(QuickBuy.Action.PICK_AMOUNT, QuickBuy.action(ClickType.SHIFT_RIGHT, false));
    }

    @Test
    void bedrockTapAlwaysOpensTheAmountScreen() {
        for (ClickType click : new ClickType[]{ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT}) {
            assertEquals(QuickBuy.Action.PICK_AMOUNT, QuickBuy.action(click, true), click.name());
        }
    }

    @Test
    void otherClicksDoNothing() {
        for (ClickType click : new ClickType[]{ClickType.DOUBLE_CLICK, ClickType.NUMBER_KEY, ClickType.DROP,
                ClickType.CONTROL_DROP, ClickType.MIDDLE, ClickType.CREATIVE, ClickType.SWAP_OFFHAND}) {
            assertEquals(QuickBuy.Action.NONE, QuickBuy.action(click, false), click.name());
            assertEquals(QuickBuy.Action.NONE, QuickBuy.action(click, true), click.name());
        }
        assertEquals(QuickBuy.Action.NONE, QuickBuy.action(null, false));
    }

    @Test
    void amountsPerAction() {
        assertEquals(1, QuickBuy.amount(QuickBuy.Action.BUY_ONE, 1, 64));
        assertEquals(64, QuickBuy.amount(QuickBuy.Action.BUY_STACK, 1, 64));
        assertEquals(0, QuickBuy.amount(QuickBuy.Action.PICK_AMOUNT, 1, 64));
        assertEquals(0, QuickBuy.amount(QuickBuy.Action.NONE, 1, 64));
        assertEquals(1, QuickBuy.amount(QuickBuy.Action.BUY_ONE, 0, 64), "never 0 items");
    }

    @Test
    void everythingFits() {
        assertEquals(new ShopMath.BuyPlan(64, false), ShopMath.plan(64, 2304, 2304));
        assertEquals(new ShopMath.BuyPlan(64, false), ShopMath.plan(64, 64, 2304));
    }

    @Test
    void partialBuyChargesOnlyWhatFits() {
        ShopMath.BuyPlan plan = ShopMath.plan(64, 40, 2304);
        assertEquals(40, plan.amount());
        assertEquals(true, plan.partial());
        assertEquals(40L * 8, ShopMath.total(8, plan.amount()).getAsLong(), "40 x $8, not 64 x $8");
    }

    @Test
    void noRoomBuysNothing() {
        assertEquals(new ShopMath.BuyPlan(0, false), ShopMath.plan(64, 0, 2304));
        assertEquals(new ShopMath.BuyPlan(0, false), ShopMath.plan(64, -3, 2304));
    }

    @Test
    void badRequestsBuyNothingAndMaxIsRespected() {
        assertEquals(0, ShopMath.plan(0, 100, 2304).amount());
        assertEquals(0, ShopMath.plan(-5, 100, 2304).amount());
        assertEquals(0, ShopMath.plan(10, 100, 0).amount());
        // Asking above the max buys the max (when it fits) and is not a "no more room" partial.
        assertEquals(new ShopMath.BuyPlan(2304, false), ShopMath.plan(5000, 10_000, 2304));
        assertEquals(new ShopMath.BuyPlan(100, true), ShopMath.plan(5000, 100, 2304));
    }
}
