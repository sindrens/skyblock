package net.skybornmc.skyblock.moderation;

import java.time.Duration;

/**
 * False report penalty (spec 14.5), pure. Only reports closed as {@code invalid} count ({@code no-evidence} and
 * {@code duplicate} are honest mistakes). When a reporter reaches {@code threshold} invalid reports inside {@code window},
 * a suggestion for the reason {@code report-abuse} is created (staff click [Apply] or [Dismiss]; never automatic), at
 * most one per player per {@code cap} (a waiting suggestion is only refreshed).
 */
final class FalseReportRules {

    private FalseReportRules() {
    }

    /**
     * {@code extras.false-reports}.
     *
     * @param threshold invalid reports that create a suggestion (3)
     * @param window    how far back they count (30d)
     * @param cap       at most one suggestion per player per this time (7d)
     * @param reason    the reason id whose ladder is suggested ({@code report-abuse})
     */
    record Settings(boolean enabled, int threshold, Duration window, Duration cap, String reason) {
        static final Settings DEFAULT = new Settings(true, 3, Duration.ofDays(30), Duration.ofDays(7), "report-abuse");
    }

    /** Report outcome names (the reports module's enum names) that count as a false report. */
    static boolean counts(String outcome) {
        return "INVALID".equalsIgnoreCase(outcome);
    }

    /** What to do for a reporter with {@code invalidInWindow} invalid reports. */
    enum Step { NOTHING, SUGGEST }

    static Step step(Settings s, int invalidInWindow) {
        return s != null && s.enabled() && s.threshold() > 0 && invalidInWindow >= s.threshold() ? Step.SUGGEST : Step.NOTHING;
    }

    /** Suggestions created at or after this count against the weekly cap. */
    static long capSince(Settings s, long now) {
        return now - s.cap().toMillis();
    }

    /** True if a suggestion created at {@code lastCreated} still blocks a new one ({@code null} = none yet). */
    static boolean capped(Long lastCreated, Settings s, long now) {
        return lastCreated != null && lastCreated >= capSince(s, now);
    }
}
