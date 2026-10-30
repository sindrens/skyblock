package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * One hand-in from the inventory for a deliver task (PHASE-5 M5.3), in the order that makes a crash <b>lose at worst,
 * never duplicate</b> (SECFIX1, {@link PlayerSaves}):
 * <ol>
 *   <li>reserve room in the task (two members at once can never hand in more than the goal);</li>
 *   <li>take the items out of the inventory;</li>
 *   <li>save the player file without them; only then</li>
 *   <li>write the progress in one database transaction.</li>
 * </ol>
 * A failed save skips the database and gives the items back; a failed write gives them back too. A crash after the save
 * but before the commit loses the items (logged by the caller), never keeps them in both places. Unit-tested with fake
 * steps.
 */
final class Delivery {

    /** The side effects, supplied by the manager (real inventory, player file, database) or by tests. */
    interface Steps {
        /** Removes up to {@code max} matching items from the inventory; returns how many were removed. Main thread. */
        long take(long max);

        /** Writes the player file now; false when it failed. Main thread. */
        boolean save();

        /** Saves {@code taken} items of progress in one database transaction. */
        CompletableFuture<Void> commit(long taken);

        /** Gives {@code taken} items back (inventory, overflow to the box). Main thread. */
        void giveBack(long taken);
    }

    enum Status { DELIVERED, NOT_NEEDED, NO_ITEMS, FAILED }

    /** @param ready tasks that reached their goal with this hand-in (claimed: complete them) */
    record Outcome(Status status, long taken, List<TaskDef> ready, Throwable error) {
        static Outcome of(Status status) {
            return new Outcome(status, 0, List.of(), null);
        }
    }

    private Delivery() {
    }

    /**
     * Runs a hand-in. Main thread; the outcome completes on {@code main}.
     *
     * @param available matching items the player has (an upper bound; {@link Steps#take} decides what really moves)
     */
    static CompletableFuture<Outcome> run(TaskProgress progress, UUID island, TaskDef def, long available, Steps steps, Executor main) {
        if (progress.room(island, def) <= 0) {
            return CompletableFuture.completedFuture(Outcome.of(Status.NOT_NEEDED));
        }
        long reserved = progress.reserve(island, def, available);
        if (reserved <= 0) {
            return CompletableFuture.completedFuture(Outcome.of(Status.NO_ITEMS));
        }
        long taken;
        try {
            taken = Math.min(reserved, Math.max(0, steps.take(reserved)));
        } catch (RuntimeException e) {
            progress.release(island, def, reserved, 0);
            return CompletableFuture.completedFuture(new Outcome(Status.FAILED, 0, List.of(), e));
        }
        if (taken <= 0) {
            progress.release(island, def, reserved, 0);
            return CompletableFuture.completedFuture(Outcome.of(Status.NO_ITEMS));
        }
        CompletableFuture<Outcome> out = new CompletableFuture<>();
        PlayerSaves.commitAfterSave(steps::save, "a task delivery", () -> steps.commit(taken)).whenCompleteAsync((ok, error) -> {
            if (error != null) {
                progress.release(island, def, reserved, 0);
                try {
                    steps.giveBack(taken);
                } finally {
                    out.complete(new Outcome(Status.FAILED, 0, List.of(), error));
                }
                return;
            }
            List<TaskDef> ready = progress.release(island, def, reserved, taken);
            out.complete(new Outcome(Status.DELIVERED, taken, ready, null));
        }, main);
        return out;
    }
}
