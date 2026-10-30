package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.List;

/**
 * Offence counting for the ladders (spec 12.3). An earlier punishment for the same reason is an offence when it was not
 * pardoned/undone and is younger than the reason's {@code offence-expire}. History is never deleted; only the counting
 * changes. Pure, so the boundaries (89 d counts, 91 d doesn't) are unit-tested.
 */
final class Offences {

    private Offences() {
    }

    /**
     * The cutoff for {@code expire}: punishments created after it count. {@code null} = never expires
     * ({@link Long#MIN_VALUE}, everything counts).
     */
    static long cutoff(Duration expire, long now) {
        return expire == null ? Long.MIN_VALUE : now - expire.toMillis();
    }

    /** Earlier punishments for this reason that still count (= offence number - 1). */
    static int counting(List<Punishment> history, String template, long cutoff) {
        int n = 0;
        for (Punishment p : history) {
            if (template.equals(p.template()) && !p.removed() && p.created() > cutoff) {
                n++;
            }
        }
        return n;
    }

    /** Earlier punishments for this reason that no longer count because they are too old (not pardoned ones). */
    static int expired(List<Punishment> history, String template, long cutoff) {
        int n = 0;
        for (Punishment p : history) {
            if (template.equals(p.template()) && !p.removed() && p.created() <= cutoff) {
                n++;
            }
        }
        return n;
    }
}
