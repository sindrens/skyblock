package net.skybornmc.skyblock.shop;

import org.bukkit.event.inventory.ClickType;

/**
 * The quick-buy grid's click mapping (SELL2, design board 4). Java: left-click buys one, right-click buys a stack,
 * shift-click opens the amount screen. Bedrock has no reliable right/shift click, so every tap opens the amount screen.
 */
final class QuickBuy {

    private QuickBuy() {
    }

    enum Action {
        /** Buy {@code quick-buy.left-amount} (1). */
        BUY_ONE,
        /** Buy {@code quick-buy.right-amount} (64). */
        BUY_STACK,
        /** Open the amount screen. */
        PICK_AMOUNT,
        /** Ignore (number keys, drop, double-click ...). */
        NONE
    }

    static Action action(ClickType click, boolean bedrock) {
        if (click == null) {
            return Action.NONE;
        }
        boolean known = click == ClickType.LEFT || click == ClickType.RIGHT
                || click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        if (!known) {
            return Action.NONE;
        }
        if (bedrock || click.isShiftClick()) {
            return Action.PICK_AMOUNT;
        }
        return click == ClickType.LEFT ? Action.BUY_ONE : Action.BUY_STACK;
    }

    /** How many items an action buys (0 for the amount screen / none). */
    static int amount(Action action, int leftAmount, int rightAmount) {
        return switch (action) {
            case BUY_ONE -> Math.max(1, leftAmount);
            case BUY_STACK -> Math.max(1, rightAmount);
            default -> 0;
        };
    }
}
