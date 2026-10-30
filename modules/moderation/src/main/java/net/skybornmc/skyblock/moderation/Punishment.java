package net.skybornmc.skyblock.moderation;

import java.util.UUID;

/**
 * One row of the {@code punishments} table. {@code expires == null} = permanent. {@code weight} = warn weight in tenths
 * (spec 12.7, 10 = 1); {@code acknowledgedAt} = when the player acknowledged a warning (spec 12.6), or {@code null};
 * {@code approvedBy}/{@code approvedByName} = the approver of a request (spec 13.4), {@code null} for everything else.
 */
public record Punishment(long id, Type type, UUID target, String targetName, String ip, boolean ipWide, String reason,
                         String template, Integer ladderStep, UUID issuer, String issuerName, long created, Long expires,
                         boolean silent, Long removedAt, UUID removedBy, String removedByName, String removedReason,
                         String source, int weight, Long acknowledgedAt, UUID approvedBy, String approvedByName) {

    /** A row without an approver (everything before P4, and every punishment that needed no approval). */
    public Punishment(long id, Type type, UUID target, String targetName, String ip, boolean ipWide, String reason,
                      String template, Integer ladderStep, UUID issuer, String issuerName, long created, Long expires,
                      boolean silent, Long removedAt, UUID removedBy, String removedByName, String removedReason,
                      String source, int weight, Long acknowledgedAt) {
        this(id, type, target, targetName, ip, ipWide, reason, template, ladderStep, issuer, issuerName, created, expires,
                silent, removedAt, removedBy, removedByName, removedReason, source, weight, acknowledgedAt, null, null);
    }

    /** A row with weight 1 and not acknowledged (everything before P3, and every non-warn). */
    public Punishment(long id, Type type, UUID target, String targetName, String ip, boolean ipWide, String reason,
                      String template, Integer ladderStep, UUID issuer, String issuerName, long created, Long expires,
                      boolean silent, Long removedAt, UUID removedBy, String removedByName, String removedReason,
                      String source) {
        this(id, type, target, targetName, ip, ipWide, reason, template, ladderStep, issuer, issuerName, created, expires,
                silent, removedAt, removedBy, removedByName, removedReason, source, 10, null);
    }

    /** Source prefix of punishments created by an automatic rule ({@code auto:warn-rule:2}). */
    public static final String AUTO_PREFIX = "auto:";

    /**
     * {@code ISLAND_*} / {@code COOP_*} (P7, spec 13.2): island punishments, enforced by the islands module. Island kick
     * and coop remove happen once (like a kick); island ban and coop block last until they expire or are pardoned.
     */
    public enum Type {
        BAN, MUTE, WARN, KICK, ISLAND_KICK, ISLAND_BAN, COOP_REMOVE, COOP_BLOCK;

        /** Island punishment (spec 13.2). */
        public boolean island() {
            return this == ISLAND_KICK || this == ISLAND_BAN || this == COOP_REMOVE || this == COOP_BLOCK;
        }

        /** Happens once and is never "active" afterwards (kick, island kick, coop remove). */
        public boolean instant() {
            return this == KICK || this == ISLAND_KICK || this == COOP_REMOVE;
        }

        /** Has a length (ban, mute, island ban, coop block). */
        public boolean timed() {
            return this == BAN || this == MUTE || this == ISLAND_BAN || this == COOP_BLOCK;
        }

        /** Message key part ({@code island_kick}) of {@code types.*}. */
        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Display status in history. */
    public enum Status { ACTIVE, EXPIRED, REMOVED, DONE }

    /** Created by an automatic rule (applied by a staff click or by the system), never by a plain staff action. */
    public boolean automatic() {
        return source != null && source.startsWith(AUTO_PREFIX);
    }

    /** Same row with a database id (after insert). */
    public Punishment withId(long newId) {
        return new Punishment(newId, type, target, targetName, ip, ipWide, reason, template, ladderStep, issuer, issuerName,
                created, expires, silent, removedAt, removedBy, removedByName, removedReason, source, weight, acknowledgedAt,
                approvedBy, approvedByName);
    }

    /** Same row approved by {@code by} (spec 13.4). */
    public Punishment withApprover(UUID by, String byName) {
        return new Punishment(id, type, target, targetName, ip, ipWide, reason, template, ladderStep, issuer, issuerName,
                created, expires, silent, removedAt, removedBy, removedByName, removedReason, source, weight, acknowledgedAt,
                by, byName);
    }

    /** Same row with another warn weight (tenths). */
    public Punishment withWeight(int tenths) {
        return new Punishment(id, type, target, targetName, ip, ipWide, reason, template, ladderStep, issuer, issuerName,
                created, expires, silent, removedAt, removedBy, removedByName, removedReason, source, tenths, acknowledgedAt,
                approvedBy, approvedByName);
    }

    /** A warning the player acknowledged ([I understand] or /ack). */
    public boolean acknowledged() {
        return acknowledgedAt != null;
    }

    public boolean permanent() {
        return expires == null;
    }

    public boolean removed() {
        return removedAt != null;
    }

    /** In force right now (bans/mutes/warnings that were not pardoned and have not expired). Kicks are never active. */
    public boolean active(long now) {
        return !type.instant() && !removed() && (expires == null || expires > now);
    }

    public Status status(long now) {
        if (removed()) {
            return Status.REMOVED;
        }
        if (type.instant()) {
            return Status.DONE;
        }
        return active(now) ? Status.ACTIVE : Status.EXPIRED;
    }

    public Long remainingMillis(long now) {
        return expires == null ? null : Math.max(0, expires - now);
    }
}
