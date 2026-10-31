package net.skybornmc.skyblock.production;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The order of using a Fuel Cell (PHASE-7 M7.1), without Bukkit: the item is <b>claimed first</b> (taken out of the
 * inventory), then the fuel is added in one transaction; a refusal or an error gives the item back. A cell that was used
 * before (a copy) stays used up. The caller runs it on the main thread; {@code giveBack} runs where the add completes and
 * must hop to the main thread itself.
 */
final class CellUse {

    /** What happened to the cell. */
    enum Outcome {
        /** The fuel was added; the item is gone. */
        ADDED,
        /** It does not fit into the tank (or the tank changed meanwhile): the item is back. */
        NO_ROOM,
        /** This cell (its unique id) was used before: the copy stays removed. */
        REUSED,
        /** The item was not in the inventory anymore: nothing happened. */
        MOVED,
        /** Saving failed: the item is back. */
        ERROR
    }

    private CellUse() {
    }

    /**
     * @param take     takes the item out (empty = it is gone, nothing else runs)
     * @param add      adds the fuel (the database transaction); completes with ADDED, NO_ROOM, REUSED or ERROR
     * @param giveBack gives a taken item back (called at most once, only for NO_ROOM and ERROR)
     */
    static <T> CompletableFuture<Outcome> run(Supplier<Optional<T>> take, Supplier<CompletableFuture<Outcome>> add, Consumer<T> giveBack) {
        Optional<T> taken = take.get();
        if (taken.isEmpty()) {
            return CompletableFuture.completedFuture(Outcome.MOVED);
        }
        T item = taken.get();
        CompletableFuture<Outcome> adding;
        try {
            adding = add.get();
        } catch (RuntimeException e) {
            adding = CompletableFuture.failedFuture(e);
        }
        return adding.handle((outcome, error) -> {
            Outcome result = error != null || outcome == null || outcome == Outcome.MOVED ? Outcome.ERROR : outcome;
            if (result == Outcome.NO_ROOM || result == Outcome.ERROR) {
                giveBack.accept(item);
            }
            return result;
        });
    }
}
