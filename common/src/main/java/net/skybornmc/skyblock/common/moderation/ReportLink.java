package net.skybornmc.skyblock.common.moderation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Report link (Phase P spec 12.9), provided by the staff module and used by moderation, so neither module touches the
 * other's classes. When a staff member punishes a player, the reports about that player <b>claimed by that same staff
 * member</b> are closed with outcome "punished" and their reporters are told (the normal report behaviour); unclaimed
 * reports are left alone and only counted.
 */
public interface ReportLink {

    /**
     * @param closed          ids of the reports that were closed now
     * @param openUnclaimed   reports about the target still open and claimed by nobody
     * @param claimedByOthers reports about the target claimed by other staff (left alone)
     */
    record Result(List<Long> closed, int openUnclaimed, int claimedByOthers) {
        public Result {
            closed = closed == null ? List.of() : List.copyOf(closed);
        }

        public static final Result NONE = new Result(List.of(), 0, 0);
    }

    /**
     * Closes {@code staff}'s claimed reports about {@code target} with outcome punished (atomically), tells the reporters,
     * audits each close. Completes on the main thread.
     *
     * @param punishmentId the punishment that closed them (kept in the audit details)
     */
    CompletableFuture<Result> punished(UUID target, String targetName, UUID staff, String staffName, long punishmentId);

    /** Counts only (no change): open unclaimed reports and reports claimed by others about {@code target}. */
    CompletableFuture<Result> count(UUID target, UUID staff);
}
