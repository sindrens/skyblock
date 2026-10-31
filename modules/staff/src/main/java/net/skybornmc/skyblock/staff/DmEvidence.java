package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.audit.AuditQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * R3-9a: the private messages a {@code /report} attaches as evidence. DMs live in the audit log (category {@code dm},
 * action {@code dm}; REV3-FIX moved them out of {@code chat}): actor = sender, target = the recipient's uuid. The last
 * {@code reports.dm-evidence.count} messages between the reporter and the reported player (either direction) are copied
 * into {@code report_dm_evidence}, because the audit keeps DMs only 30 days and the evidence is kept 180. Pure.
 */
final class DmEvidence {

    /** Longest stored message text (the column size). */
    static final int MAX_BODY = 1024;
    /** Longest stored name (the column size). */
    static final int MAX_NAME = 32;
    static final String ACTION = "dm";

    /** One attached message, oldest first when listed. */
    record Line(long sentAt, UUID sender, String senderName, String recipientName, String body, boolean ignored) {
    }

    private DmEvidence() {
    }

    /** The audit filter: DMs between {@code a} and {@code b} in either direction, newest first, at most {@code count}. */
    static AuditQuery query(UUID a, UUID b, int count) {
        return AuditQuery.create().categories(AuditCategory.DM).actions(ACTION).between(a, b).page(0, Math.max(1, count));
    }

    /**
     * The evidence lines from audit rows (newest first, as queries return them): only real DMs between the two players
     * (a defensive re-check of the filter), at most {@code count}, returned oldest first so they read like a chat.
     */
    static List<Line> lines(List<AuditEntry> newestFirst, UUID a, UUID b, int count) {
        if (count <= 0 || newestFirst == null || a == null || b == null || a.equals(b)) {
            return List.of();
        }
        List<Line> out = new ArrayList<>();
        for (AuditEntry e : newestFirst) {
            if (out.size() >= count) {
                break;
            }
            if (e.category() != AuditCategory.DM || !ACTION.equals(e.action()) || e.actor() == null || e.actor().uuid() == null) {
                continue;
            }
            UUID sender = e.actor().uuid();
            UUID recipient = parse(e.target());
            boolean between = (a.equals(sender) && b.equals(recipient)) || (b.equals(sender) && a.equals(recipient));
            if (!between) {
                continue;
            }
            Object message = e.details().get("message");
            out.add(new Line(e.time(), sender, cut(e.actor().name(), MAX_NAME), cut(e.targetName(), MAX_NAME),
                    cut(message == null ? "" : String.valueOf(message), MAX_BODY), e.details().containsKey("blocked")));
        }
        Collections.reverse(out);
        return List.copyOf(out);
    }

    /** Evidence stored before this time is deleted ({@code keepDays} days before {@code now}). */
    static long cutoff(long now, int keepDays) {
        return now - Math.max(1, keepDays) * 86_400_000L;
    }

    static String cut(String text, int max) {
        String s = text == null ? "" : text;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static UUID parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
