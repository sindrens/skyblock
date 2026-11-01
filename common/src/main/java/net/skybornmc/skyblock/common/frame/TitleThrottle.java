package net.skybornmc.skyblock.common.frame;

/**
 * At most one frame title swap per player per {@code gui.font.title-resend-min-ms} (GUI-FONT 6.6, edge case "a menu
 * whose live refresh flips a tile every tick cannot spam titles"). Pure.
 */
public final class TitleThrottle {

    private TitleThrottle() {
    }

    /** True when a title may be sent now: never sent before ({@code lastSent <= 0}) or at least {@code minMs} ago. */
    public static boolean allow(long now, long lastSent, int minMs) {
        return lastSent <= 0 || now - lastSent >= minMs;
    }
}
