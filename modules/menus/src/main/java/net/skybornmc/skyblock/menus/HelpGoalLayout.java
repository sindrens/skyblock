package net.skybornmc.skyblock.menus;

/**
 * Slots of {@code /help} "What do you want to do?" (plan 4 Nov C §1), a 6-row menu. Each goal is a group of three: its
 * card, then its 1-2 buttons to the right. Two groups per row (columns 1-3 and 5-7, the middle column stays empty), an
 * empty row between the rows of groups; a lone last group is centred (columns 3-5). Pure, unit-tested.
 * <pre>
 *  6 goals            4 goals            3 goals
 *  . C b b . C b b .  . . . . . . . . .  . . . . . . . . .
 *  . . . . . . . . .  . C b b . C b b .  . C b b . C b b .
 *  . C b b . C b b .  . . . . . . . . .  . . . . . . . . .
 *  . . . . . . . . .  . C b b . C b b .  . . . C b b . . .
 *  . C b b . C b b .  . . . . . . . . .  . . . . . . . . .
 *  ← A . . ✕ . . . .  ← A . . ✕ . . . .  ← A . . ✕ . . . .     A = All topics (46)
 * </pre>
 */
final class HelpGoalLayout {

    static final int ROWS = 6;
    /** "All topics" (the old categories): the first screen action slot of the bottom row. */
    static final int SLOT_ALL_TOPICS = 46;
    /** Most goals (three rows of two). */
    static final int MAX = 6;

    private HelpGoalLayout() {
    }

    /** Screen rows of the goal rows: 1 → {2}, 2 → {1, 3}, 3 → {0, 2, 4}. */
    static int[] rows(int goalRows) {
        return switch (goalRows) {
            case 0 -> new int[0];
            case 1 -> new int[]{2};
            case 2 -> new int[]{1, 3};
            case 3 -> new int[]{0, 2, 4};
            default -> throw new IllegalArgumentException("At most 3 rows of goals, got " + goalRows);
        };
    }

    /** Slot of the card of goal {@code index} (0-based) of {@code count} goals; its buttons follow at +1 and +2. */
    static int card(int index, int count) {
        int n = Math.min(MAX, count);
        if (index < 0 || index >= n) {
            throw new IllegalArgumentException("Goal " + index + " of " + n);
        }
        int goalRows = (n + 1) / 2;
        int row = rows(goalRows)[index / 2];
        boolean alone = index == n - 1 && n % 2 == 1;
        int column = alone ? 3 : index % 2 == 0 ? 1 : 5;
        return 9 * row + column;
    }

    /** Slot of button {@code button} (0 or 1) of the goal whose card is on {@code cardSlot}. */
    static int button(int cardSlot, int button) {
        if (button < 0 || button > 1) {
            throw new IllegalArgumentException("A goal has at most 2 buttons, got index " + button);
        }
        return cardSlot + 1 + button;
    }
}
