package net.skybornmc.skyblock.common.moderation;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Report blocks (Phase P spec 14.5), provided by the staff (reports) module and used by moderation: the ladder step
 * {@code report-block <time|perm>} stops a player from using {@code /report} until it ends. Moderation stores the
 * punishment; this service enforces it, so neither module touches the other's classes. Without this service the step is
 * "not available" and moderation uses the next step of the ladder.
 */
public interface ReportBlocking {

    /**
     * Blocks {@code player} from {@code /report} until {@code until} (epoch millis, {@code null} = until lifted). Several
     * blocks may exist; the longest one counts. Async; completes off the main thread.
     *
     * @param reference who set it ({@code punishment:<id>}), used to lift exactly this block later
     */
    CompletableFuture<Void> blockReporting(UUID player, Long until, String reference);

    /** Lifts the block set with {@code reference} (undo, pardon, rollback). Completes with whether one was active. */
    CompletableFuture<Boolean> liftReportBlock(String reference);
}
