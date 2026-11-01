package net.skybornmc.skyblock.hud;

/**
 * The scoreboard timer text (UI_STYLE §2), pure: {@code 45s}, {@code 37m}, {@code 2h 15m}, {@code 3d 4h}. Minutes and
 * hours round <b>up</b>, so the text changes at most once a minute (fewer scoreboard updates) and never says {@code 0m}
 * while time is left.
 */
final class TimerText {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;

    private TimerText() {
    }

    static String format(long remainingMillis) {
        if (remainingMillis <= 0) {
            return "";
        }
        long seconds = ceil(remainingMillis, SECOND);
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = ceil(remainingMillis, MINUTE);
        if (minutes < 60) {
            return minutes + "m";
        }
        if (minutes < 24 * 60) {
            long h = minutes / 60;
            long m = minutes % 60;
            return m == 0 ? h + "h" : h + "h " + m + "m";
        }
        long hours = ceil(remainingMillis, HOUR);
        long d = hours / 24;
        long h = hours % 24;
        return h == 0 ? d + "d" : d + "d " + h + "h";
    }

    private static long ceil(long value, long unit) {
        return value / unit + (value % unit == 0 ? 0 : 1);
    }
}
