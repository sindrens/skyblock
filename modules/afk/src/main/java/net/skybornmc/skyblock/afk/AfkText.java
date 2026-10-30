package net.skybornmc.skyblock.afk;

/** Short time texts for the action bar and scoreboard (pure). */
final class AfkText {

    private AfkText() {
    }

    /** A countdown: {@code 14:59}, or {@code 1:02:03} from an hour up. Rounded up so it never shows 0:00 early. */
    static String countdown(long millis) {
        long s = Math.max(0, (millis + 999) / 1000);
        long h = s / 3600;
        long m = s % 3600 / 60;
        long sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }

    /** Hours and minutes, rounded down: {@code 3h 15m}, {@code 45m}, {@code 8h}, {@code 0m}. */
    static String hoursMinutes(long millis) {
        long minutes = Math.max(0, millis / 60_000);
        long h = minutes / 60;
        long m = minutes % 60;
        if (h == 0) {
            return m + "m";
        }
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
