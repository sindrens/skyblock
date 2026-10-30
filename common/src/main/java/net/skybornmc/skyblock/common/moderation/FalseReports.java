package net.skybornmc.skyblock.common.moderation;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * False report penalty (Phase P spec 14.5), provided by the moderation module and used by the staff (reports) module.
 * Only the outcome {@code invalid} counts ({@code no-evidence} and {@code duplicate} are honest mistakes). The reports
 * module counts the reporter's invalid reports inside {@link #window()} and calls {@link #invalidReport}; moderation
 * decides (threshold, at most one suggestion per player per week) and creates a suggestion for the reason
 * {@code report-abuse} that staff apply or dismiss. Nothing is ever applied automatically.
 */
public interface FalseReports {

    /** What {@link #invalidReport} did. */
    enum Outcome {
        /** Fewer invalid reports than the threshold: nothing happens. */
        BELOW_THRESHOLD,
        /** A suggestion was created (staff were told). */
        SUGGESTED,
        /** The waiting suggestion for this player was refreshed (no second line to staff). */
        REFRESHED,
        /** Threshold reached, but this player already got a suggestion within the weekly cap. */
        CAPPED,
        /** Switched off, or the reason id is missing from punishments.yml. */
        DISABLED
    }

    /** {@code threshold} = invalid reports that create a suggestion (for the staff message). */
    record Result(Outcome outcome, long suggestionId, int threshold) {
    }

    /** How far back invalid reports count ({@code extras.false-reports.window}, default 30 days). */
    Duration window();

    /**
     * A staff member closed one of {@code reporter}'s reports as invalid.
     *
     * @param invalidInWindow the reporter's invalid reports inside {@link #window()}, this one included
     * @param staffName       who closed it (audited)
     */
    CompletableFuture<Result> invalidReport(UUID reporter, String reporterName, int invalidInWindow, UUID staff,
                                            String staffName);
}
