package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The [Undo] rules (spec 12.1): within {@code undo-window} of being created, a punishment can be reversed by the staff
 * member who applied it, or by anyone with {@code skyblock.staff.punish.undo.others}. Kicks can't be undone. An undone
 * punishment is a normal pardon (removed_* set, reason "Undone by ... (mistake)"): it no longer counts as an offence and
 * shows as reversed in {@code /pstats}. Pure; the store runs {@link #check} inside the target's lock.
 */
final class Undo {

    enum Result {
        /** Allowed: undo it now. */
        OK,
        /** No such punishment. */
        NOT_FOUND,
        /** Kicks are history only. */
        NOT_UNDOABLE,
        /** Already pardoned or undone. */
        ALREADY_REMOVED,
        /** The window is over: use /unmute, /unban or /unwarn. */
        EXPIRED,
        /** Someone else applied it and the actor lacks undo.others. */
        NOT_YOURS
    }

    private Undo() {
    }

    /** True while {@code p} is inside the undo window at {@code now}. */
    static boolean inWindow(Punishment p, Duration window, long now) {
        long age = now - p.created();
        return window != null && age >= 0 && age < window.toMillis();
    }

    static Result check(Punishment p, UUID by, boolean others, Duration window, long now) {
        if (p == null) {
            return Result.NOT_FOUND;
        }
        if (p.type().instant()) {
            return Result.NOT_UNDOABLE;
        }
        if (p.removed()) {
            return Result.ALREADY_REMOVED;
        }
        if (!inWindow(p, window, now)) {
            return Result.EXPIRED;
        }
        boolean own = by != null && by.equals(p.issuer());
        return own || others ? Result.OK : Result.NOT_YOURS;
    }

    /**
     * The newest punishment in {@code history} that {@code viewer} could undo right now (for the {@code /p} head:
     * "Last action: ... (click to undo)", the Bedrock path), or {@code null}.
     */
    static Punishment latestUndoable(List<Punishment> history, UUID viewer, boolean others, Duration window, long now) {
        Punishment best = null;
        for (Punishment p : history) {
            if (check(p, viewer, others, window, now) == Result.OK && (best == null || p.created() > best.created()
                    || p.created() == best.created() && p.id() > best.id())) {
                best = p;
            }
        }
        return best;
    }
}
