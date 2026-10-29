package net.skybornmc.skyblock.staff;

import java.util.Locale;
import java.util.UUID;

/** One player report. */
record Report(long id, long createdAt, UUID reporter, String reporterName, UUID target, String targetName,
              String reason, String details, String world, Integer x, Integer y, Integer z,
              Status status, UUID claimedBy, String claimedByName, Long claimedAt,
              UUID closedBy, String closedByName, Long closedAt, String outcome) {

    enum Status {
        OPEN, CLAIMED, CLOSED;

        String db() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Status of(String raw) {
            return valueOf(raw.toUpperCase(Locale.ROOT));
        }
    }

    /** Outcomes staff choose when closing a report. */
    enum Outcome {
        PUNISHED, WARNED, NO_EVIDENCE, INVALID, DUPLICATE;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }
}
