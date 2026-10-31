package net.skybornmc.skyblock.moderation;

import java.util.Arrays;
import java.util.Locale;

/**
 * Where the target's stacks sit on the confiscate screen (QOL3: the standard GUI-1 frame, content in the 7-wide grid
 * 10-16, 19-25, 28-34, 37-43). Three tabs in row 0, so every place has a fixed slot and nothing shifts while the
 * screen refreshes every second. Pure, unit-tested.
 * <pre>
 *  Hotbar &amp; armor: hotbar 1-7 on 10-16, hotbar 8-9 on 19-20, helmet/chest/legs/boots on 37-40, off hand on 42
 *  Backpack:       the 27 main slots (player slots 9-35) in reading order from 10
 *  Ender chest:    the ender chest (27 slots) in reading order from 10
 * </pre>
 */
final class ConfiscateLayout {

    /** The 28 content slots of a 6-row framed menu, in reading order. */
    static final int[] CONTENT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    /** Player inventory slots (Bukkit PlayerInventory numbering). */
    static final int HOTBAR_SIZE = 9;
    static final int MAIN_FIRST = 9;
    static final int MAIN_SIZE = 27;
    static final int BOOTS = 36;
    static final int LEGGINGS = 37;
    static final int CHESTPLATE = 38;
    static final int HELMET = 39;
    static final int OFF_HAND = 40;

    /** One tab of the screen; {@link #source()} is the inventory the slots belong to. */
    enum View {
        HOTBAR(Sanctions.Source.INVENTORY), BACKPACK(Sanctions.Source.INVENTORY), ENDER(Sanctions.Source.ENDER);

        private final Sanctions.Source source;

        View(Sanctions.Source source) {
            this.source = source;
        }

        Sanctions.Source source() {
            return source;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private ConfiscateLayout() {
    }

    /**
     * Menu slot (0-53) to the target's inventory slot for {@code view}; -1 = nothing there.
     *
     * @param enderSize size of the target's ender chest (only used for {@link View#ENDER})
     */
    static int[] slots(View view, int enderSize) {
        int[] map = new int[54];
        Arrays.fill(map, -1);
        switch (view) {
            case HOTBAR -> {
                for (int i = 0; i < HOTBAR_SIZE; i++) {
                    map[CONTENT[i]] = i;
                }
                map[37] = HELMET;
                map[38] = CHESTPLATE;
                map[39] = LEGGINGS;
                map[40] = BOOTS;
                map[42] = OFF_HAND;
            }
            case BACKPACK -> {
                for (int i = 0; i < MAIN_SIZE; i++) {
                    map[CONTENT[i]] = MAIN_FIRST + i;
                }
            }
            case ENDER -> {
                for (int i = 0; i < Math.min(enderSize, CONTENT.length); i++) {
                    map[CONTENT[i]] = i;
                }
            }
        }
        return map;
    }

    /** The message key (under {@code sanctions.confiscate-menu.place.}) and number for one inventory slot. */
    record Place(String key, int number) {
    }

    static Place place(View view, int slot) {
        if (view == View.ENDER) {
            return new Place("ender", slot + 1);
        }
        if (slot < HOTBAR_SIZE) {
            return new Place("hotbar", slot + 1);
        }
        return switch (slot) {
            case HELMET -> new Place("helmet", 0);
            case CHESTPLATE -> new Place("chestplate", 0);
            case LEGGINGS -> new Place("leggings", 0);
            case BOOTS -> new Place("boots", 0);
            case OFF_HAND -> new Place("off-hand", 0);
            default -> new Place("backpack", slot - MAIN_FIRST + 1);
        };
    }
}
