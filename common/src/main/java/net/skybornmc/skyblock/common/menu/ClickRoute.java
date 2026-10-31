package net.skybornmc.skyblock.common.menu;

import org.bukkit.event.inventory.ClickType;

/**
 * Which handler of a {@link MenuButton} a click runs (I9 rule 2 "one plain click does it"). Pure, unit-tested.
 * Rules, in this order:
 * <ol>
 *   <li>Bedrock: left, right and shift clicks run the plain action; anything else is ignored.</li>
 *   <li>Middle click: the middle handler when the button has one, else ignored.</li>
 *   <li>Shift + Right-click with a middle handler: the middle handler (a survival client sends no middle click).</li>
 *   <li>Other shift clicks with a shift handler: the shift handler.</li>
 *   <li>Right click with a right handler: the right handler; else left/right/shift run the plain action.</li>
 *   <li>Number keys, drop, double-click, offhand swap, creative drop and the rest: ignored.</li>
 * </ol>
 */
public enum ClickRoute {
    PLAIN,
    RIGHT,
    SHIFT,
    MIDDLE,
    IGNORED;

    public static ClickRoute of(ClickType type, boolean bedrock, boolean hasRight, boolean hasShift) {
        return of(type, bedrock, hasRight, hasShift, false);
    }

    public static ClickRoute of(ClickType type, boolean bedrock, boolean hasRight, boolean hasShift, boolean hasMiddle) {
        boolean basic = type == ClickType.LEFT || type == ClickType.RIGHT || type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT;
        if (bedrock) {
            return basic ? PLAIN : IGNORED;
        }
        if (type == ClickType.MIDDLE) {
            return hasMiddle ? MIDDLE : IGNORED;
        }
        if (!basic) {
            return IGNORED;
        }
        if (type == ClickType.SHIFT_RIGHT && hasMiddle) {
            return MIDDLE;
        }
        if (type.isShiftClick() && hasShift) {
            return SHIFT;
        }
        if (type.isRightClick() && hasRight) {
            return RIGHT;
        }
        return PLAIN;
    }
}
