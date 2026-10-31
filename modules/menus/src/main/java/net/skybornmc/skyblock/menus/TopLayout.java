package net.skybornmc.skyblock.menus;

import java.util.List;

/**
 * Slots of the {@code /menu → Top} screen (GUI_DESIGN §7.6, GUI-4c). Pure, unit-tested.
 * <pre>
 *  . . S T W M K . .      row 0: tabs Solo islands, Team islands, This week, Richest, Top skills (centred on 4)
 *  . . . . . . . . .
 *  . . . 2 1 3 . . .      the podium: #1 in the middle (22), #2 left (21), #3 right (23)
 *  . 4 5 6 7 8 9 10 .     #4-#10 on 28-34
 *  . . . . . . . . .
 *  ← Y K ? ✘ H F . .      45 Back, 46 you, 47 skill pick (Top skills), 48 ?, 49 Close, 50 Main menu, 51 full board
 * </pre>
 */
final class TopLayout {

    /** The places shown on the screen (podium + row). */
    static final int PLACES = 10;
    static final int SLOT_YOU = 46;
    static final int SLOT_SKILL = 47;
    static final int SLOT_FULL = 51;
    /** Where the "Loading" / "Nothing here yet" item goes (the podium's middle). */
    static final int SLOT_MIDDLE = 22;
    static final List<Integer> ROW = List.of(28, 29, 30, 31, 32, 33, 34);

    private TopLayout() {
    }

    /** Slot of a 1-based place, or -1 when the place is not on the screen. */
    static int slot(int place) {
        return switch (place) {
            case 1 -> 22;
            case 2 -> 21;
            case 3 -> 23;
            default -> place >= 4 && place <= PLACES ? ROW.get(place - 4) : -1;
        };
    }

    /** Tab slots for {@code count} visible tabs, centred on 4 (1 → [4], 2 → [3, 5], 5 → [2..6]). */
    static int[] tabSlots(int count) {
        int n = Math.max(0, Math.min(count, 9));
        int[] slots = new int[n];
        int start = 4 - (n / 2);
        // An even count sits one to the left of the middle and skips 4, so the tabs stay symmetric (2 → 3, 5).
        for (int i = 0, slot = start; i < n; i++, slot++) {
            if (n % 2 == 0 && slot == 4) {
                slot++;
            }
            slots[i] = slot;
        }
        return slots;
    }
}
