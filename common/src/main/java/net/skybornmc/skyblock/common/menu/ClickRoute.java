package net.skybornmc.skyblock.common.menu;

import org.bukkit.event.inventory.ClickType;

/**
 * Which handler of a {@link MenuButton} a click runs (GUI_DESIGN §0.2 "one plain click does it"). Pure, unit-tested.
 * <ul>
 *   <li>Left and right click (with or without shift) run the button's plain action, so both mouse buttons work and a
 *       Bedrock tap always works.</li>
 *   <li>A button can add Java-only shortcuts ({@link MenuButton#withRight}, {@link MenuButton#withShift}): only then do
 *       right-click or shift-click do something else. Bedrock players always get the plain action.</li>
 *   <li>Number keys, drop, double-click, middle-click and offhand swap never run anything.</li>
 * </ul>
 */
public enum ClickRoute {
    PLAIN,
    RIGHT,
    SHIFT,
    IGNORED;

    public static ClickRoute of(ClickType type, boolean bedrock, boolean hasRight, boolean hasShift) {
        if (type != ClickType.LEFT && type != ClickType.RIGHT && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) {
            return IGNORED;
        }
        if (bedrock) {
            return PLAIN;
        }
        if (hasShift && type.isShiftClick()) {
            return SHIFT;
        }
        if (hasRight && type.isRightClick()) {
            return RIGHT;
        }
        return PLAIN;
    }
}
