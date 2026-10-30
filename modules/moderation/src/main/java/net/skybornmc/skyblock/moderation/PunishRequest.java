package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.UUID;

/**
 * One row of {@code punishment_requests} (spec 13.4): a punishment a staff member without
 * {@code skyblock.staff.punish.approve} asked for. Nothing is applied and the target is told nothing until an approver
 * clicks [Approve]. {@code durationMs == null} = permanent. {@code merges} = how many later requests for the same player
 * and reason were merged into this one. {@code notified} = the requester was told the outcome. {@code evidenceEntry} = the
 * {@code /logs} chat line the request was made from (spec 13.11), used as evidence when it is approved, or {@code null}.
 */
record PunishRequest(long id, UUID target, String targetName, String reasonKey, Punishment.Type type, Long durationMs,
                     boolean ipWide, String ip, String reason, String template, Integer ladderStep, int weight, boolean silent,
                     String source, UUID requester, String requesterName, long created, long expires, Status status,
                     Long handledAt, UUID handler, String handlerName, String denyReason, Long punishmentId, int merges,
                     boolean notified, Long evidenceEntry, boolean requesterTrainee) {

    /** P5 shape: the requester is no trainee (P8, spec 14.6: {@code requesterTrainee} shows "Alex (trainee)"). */
    PunishRequest(long id, UUID target, String targetName, String reasonKey, Punishment.Type type, Long durationMs,
                  boolean ipWide, String ip, String reason, String template, Integer ladderStep, int weight, boolean silent,
                  String source, UUID requester, String requesterName, long created, long expires, Status status,
                  Long handledAt, UUID handler, String handlerName, String denyReason, Long punishmentId, int merges,
                  boolean notified, Long evidenceEntry) {
        this(id, target, targetName, reasonKey, type, durationMs, ipWide, ip, reason, template, ladderStep, weight, silent,
                source, requester, requesterName, created, expires, status, handledAt, handler, handlerName, denyReason,
                punishmentId, merges, notified, evidenceEntry, false);
    }

    /** A request without log evidence (P4 shape). */
    PunishRequest(long id, UUID target, String targetName, String reasonKey, Punishment.Type type, Long durationMs,
                  boolean ipWide, String ip, String reason, String template, Integer ladderStep, int weight, boolean silent,
                  String source, UUID requester, String requesterName, long created, long expires, Status status,
                  Long handledAt, UUID handler, String handlerName, String denyReason, Long punishmentId, int merges,
                  boolean notified) {
        this(id, target, targetName, reasonKey, type, durationMs, ipWide, ip, reason, template, ladderStep, weight, silent,
                source, requester, requesterName, created, expires, status, handledAt, handler, handlerName, denyReason,
                punishmentId, merges, notified, null);
    }

    enum Status {
        /** Waiting for an approver (until {@code expires}). */
        PENDING,
        /** Approved and applied ({@code punishmentId}). */
        APPROVED,
        /** Denied with a reason. */
        DENIED,
        /** Approved, but nothing was applied: the player was already punished for longer (never shorten). */
        SKIPPED
    }

    boolean pending(long now) {
        return status == Status.PENDING && expires > now;
    }

    boolean expired(long now) {
        return status == Status.PENDING && expires <= now;
    }

    Duration duration() {
        return durationMs == null ? null : Duration.ofMillis(durationMs);
    }

    /** The punishment asked for, as a ladder step (for texts). */
    Templates.Step step() {
        return new Templates.Step(type, duration(), ipWide, weight);
    }
}
