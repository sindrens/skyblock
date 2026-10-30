package net.skybornmc.skyblock.box;

/**
 * The per-profile cap (box.yml {@code cap.*}). The cap never deletes and never refuses a return: above it items are still
 * stored, the player is warned (oldest items are listed first, take them out) and staff see it in the audit log.
 * Voluntary paths can check {@link #isFull} (via {@code ItemBox#full}) first and refuse before taking money.
 */
final class BoxCap {

    enum Level { OK, WARN, FULL }

    private BoxCap() {
    }

    /** OK below {@code warnPercent} of {@code max}, WARN from there, FULL at or over {@code max}. */
    static Level of(int count, int max, int warnPercent) {
        if (max <= 0) {
            return Level.OK;
        }
        if (count >= max) {
            return Level.FULL;
        }
        long warnAt = Math.max(1, (long) Math.ceil(max * (Math.max(0, Math.min(100, warnPercent)) / 100.0)));
        return warnPercent < 100 && count >= warnAt ? Level.WARN : Level.OK;
    }

    static boolean isFull(int count, int max) {
        return max > 0 && count >= max;
    }

    /**
     * SECFIX1 F-09: the hard safety cap ({@code cap.hard-max-entries}, far above the soft cap). From here sources that can
     * stop (breaking a spawner, claiming, buying) are refused with a message instead of growing the box forever. Items
     * that already left somewhere are still stored: nothing is ever lost. A hard cap below the soft cap counts as the
     * soft cap.
     */
    static boolean isHardFull(int count, int softMax, int hardMax) {
        int hard = Math.max(hardMax, softMax);
        return hard > 0 && count >= hard;
    }
}
