package net.skybornmc.skyblock.moderation;

import java.util.UUID;

/**
 * One row of {@code punishment_suggestions}: a punishment suggested by an automatic rule (the warn rules) or by a
 * detector through {@link net.skybornmc.skyblock.common.moderation.SuggestionService} (kind {@code api}, spec 12.8:
 * {@code reasonId} + {@code details}). Staff handle it with [Apply] / [Dismiss]; the first click wins (atomic claim).
 * {@code warnTenths} = the weighted warn count when a rule fired (spec 12.7), {@code null} for older rows.
 */
record Suggestion(long id, UUID target, String targetName, String kind, Integer ruleAt, boolean repeat, String action,
                  String reason, String source, int warnCount, long created, Long expires, Status status, Long handledAt,
                  UUID handledBy, String handledByName, Long punishmentId, String note, Integer warnTenths, String reasonId,
                  String details) {

    /** A warn-rule row (no weight, reason id or details). */
    Suggestion(long id, UUID target, String targetName, String kind, Integer ruleAt, boolean repeat, String action,
               String reason, String source, int warnCount, long created, Long expires, Status status, Long handledAt,
               UUID handledBy, String handledByName, Long punishmentId, String note) {
        this(id, target, targetName, kind, ruleAt, repeat, action, reason, source, warnCount, created, expires, status,
                handledAt, handledBy, handledByName, punishmentId, note, null, null, null);
    }

    static final String KIND_WARN_RULE = "warn-rule";
    /** From a detector through the suggestion API (spec 12.8). */
    static final String KIND_API = "api";

    /** Notes on SKIPPED rows. */
    static final String NOTE_EXEMPT = "exempt";
    static final String NOTE_ALREADY = "already-punished";
    /** Note on REMOVED rows: the warn that fired it was undone (spec 12.1). */
    static final String NOTE_UNDONE = "undone";

    enum Status {
        /** Waiting for staff (until {@code expires}). */
        PENDING,
        /** A staff member clicked [Apply]. */
        APPLIED,
        /** A staff member clicked [Dismiss]. */
        DISMISSED,
        /** Applied by the system ({@code warn-rules.mode: auto}). */
        AUTO,
        /** Nothing applied: the target is exempt or already muted/banned for longer. */
        SKIPPED,
        /** Withdrawn: the warn that fired it was undone (spec 12.1). Counts as if it never fired. */
        REMOVED
    }

    /** Still waiting for staff at {@code now}. */
    boolean pending(long now) {
        return status == Status.PENDING && (expires == null || expires > now);
    }

    /** PENDING but nobody handled it in time: it disappears and does not count as a trigger (see WarnRules). */
    boolean expired(long now) {
        return status == Status.PENDING && expires != null && expires <= now;
    }

    /** A handled trigger: restarts the counter (reset-after-trigger) and opens the repeat window. */
    boolean handled() {
        return status != Status.PENDING && status != Status.REMOVED;
    }

    /** When it was handled ({@code created} for rows that were handled at once). */
    long handledTime() {
        return handledAt == null ? created : handledAt;
    }

    /** The weighted warn count as text ("1.5"); whole count for rows from before P3. */
    String countText() {
        return warnTenths == null ? String.valueOf(warnCount) : Weights.text(warnTenths);
    }

    boolean api() {
        return KIND_API.equals(kind);
    }

    Templates.Step step() {
        return Templates.Step.parse(action);
    }
}
