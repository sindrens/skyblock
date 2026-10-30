package net.skybornmc.skyblock.islands;

import java.time.LocalTime;

/** Minecraft time for the {@code real} island time (ISL2, pure): tick 0 is 06:00, 1000 ticks per hour. */
final class IslandClock {

    static final int DAY_TICKS = 24_000;

    private IslandClock() {
    }

    /** The tick of the day (0-23999) that matches {@code time} on a real clock. */
    static long realTicks(LocalTime time) {
        long ticks = time.toSecondOfDay() * 1000L / 3600L - 6000L;
        return Math.floorMod(ticks, DAY_TICKS);
    }
}
