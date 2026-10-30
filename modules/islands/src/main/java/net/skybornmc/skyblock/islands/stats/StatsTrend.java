package net.skybornmc.skyblock.islands.stats;

/** "This week vs last week" for {@code /is stats} (M2.6). Pure. */
public final class StatsTrend {

    /** How this week compares to last week. */
    public enum Kind { UP, DOWN, SAME, NEW, NONE }

    /** @param percent whole percent change (always positive; 0 for SAME/NEW/NONE) */
    public record Trend(Kind kind, long percent) {
    }

    private StatsTrend() {
    }

    /**
     * {@code NONE} when both weeks are empty, {@code NEW} when only this week has something, otherwise the change in whole
     * percent (rounded half up), {@code SAME} when it rounds to 0.
     */
    public static Trend of(long thisWeek, long lastWeek) {
        if (lastWeek <= 0) {
            return new Trend(thisWeek > 0 ? Kind.NEW : Kind.NONE, 0);
        }
        double change = (thisWeek - (double) lastWeek) * 100.0 / lastWeek;
        long percent = Math.round(Math.abs(change));
        if (percent == 0) {
            return new Trend(Kind.SAME, 0);
        }
        return new Trend(change > 0 ? Kind.UP : Kind.DOWN, percent);
    }
}
