package net.skybornmc.skyblock.economy.admin;

import java.util.List;

/**
 * Where the incremental ledger job may stop (M3.8), pure. Ledger ids come from AUTO_INCREMENT, so a transaction that is
 * still committing can leave a hole that is filled a moment later. Moving the cursor past such a hole would skip that row
 * forever. A hole is therefore waited for while the row after it is young; once that row is older than the grace time the
 * hole is a rolled-back transaction (its id is never used) and the job moves on.
 */
public final class CursorRules {

    private CursorRules() {
    }

    /**
     * How many of {@code rows} (ids ascending, all above {@code cursor}) may be processed now.
     *
     * @param cursor  the last processed id
     * @param ids     ids of the rows read, ascending
     * @param times   their times (epoch millis), same order
     * @param now     the current time
     * @param graceMs how long a hole is waited for
     * @return a count {@code n}: rows {@code [0, n)} are safe; the cursor becomes {@code ids[n-1]}
     */
    public static int safeCount(long cursor, List<Long> ids, List<Long> times, long now, long graceMs) {
        long expected = cursor + 1;
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            if (id < expected) {
                continue; // never happens with ascending ids above the cursor; ignore defensively
            }
            if (id > expected && now - times.get(i) < graceMs) {
                return i; // a hole before a young row: wait for it
            }
            expected = id + 1;
        }
        return ids.size();
    }
}
