package net.skybornmc.skyblock.moderation;

import java.util.UUID;

/** One row of the {@code punishments} table. {@code expires == null} = permanent. */
public record Punishment(long id, Type type, UUID target, String targetName, String ip, boolean ipWide, String reason,
                         String template, Integer ladderStep, UUID issuer, String issuerName, long created, Long expires,
                         boolean silent, Long removedAt, UUID removedBy, String removedByName, String removedReason,
                         String source) {

    /** Source prefix of punishments created by an automatic rule ({@code auto:warn-rule:2}). */
    public static final String AUTO_PREFIX = "auto:";

    public enum Type { BAN, MUTE, WARN, KICK }

    /** Display status in history. */
    public enum Status { ACTIVE, EXPIRED, REMOVED, DONE }

    /** Created by an automatic rule (applied by a staff click or by the system), never by a plain staff action. */
    public boolean automatic() {
        return source != null && source.startsWith(AUTO_PREFIX);
    }

    /** Same row with a database id (after insert). */
    public Punishment withId(long newId) {
        return new Punishment(newId, type, target, targetName, ip, ipWide, reason, template, ladderStep, issuer, issuerName,
                created, expires, silent, removedAt, removedBy, removedByName, removedReason, source);
    }

    public boolean permanent() {
        return expires == null;
    }

    public boolean removed() {
        return removedAt != null;
    }

    /** In force right now (bans/mutes/warnings that were not pardoned and have not expired). Kicks are never active. */
    public boolean active(long now) {
        return type != Type.KICK && !removed() && (expires == null || expires > now);
    }

    public Status status(long now) {
        if (removed()) {
            return Status.REMOVED;
        }
        if (type == Type.KICK) {
            return Status.DONE;
        }
        return active(now) ? Status.ACTIVE : Status.EXPIRED;
    }

    public Long remainingMillis(long now) {
        return expires == null ? null : Math.max(0, expires - now);
    }
}
