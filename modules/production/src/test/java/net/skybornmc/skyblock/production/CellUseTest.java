package net.skybornmc.skyblock.production;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fuel Cells are claimed before the fuel is added; a refused or failed add gives the item back exactly once. */
class CellUseTest {

    private final List<String> given = new ArrayList<>();
    private final AtomicInteger adds = new AtomicInteger();

    private CellUse.Outcome run(Optional<String> taken, CompletableFuture<CellUse.Outcome> add) {
        return CellUse.run(() -> taken, () -> {
            adds.incrementAndGet();
            return add;
        }, given::add).join();
    }

    @Test
    void addedKeepsTheItemTaken() {
        assertEquals(CellUse.Outcome.ADDED, run(Optional.of("cell"), CompletableFuture.completedFuture(CellUse.Outcome.ADDED)));
        assertEquals(List.of(), given);
    }

    @Test
    void noRoomGivesTheItemBack() {
        assertEquals(CellUse.Outcome.NO_ROOM, run(Optional.of("cell"), CompletableFuture.completedFuture(CellUse.Outcome.NO_ROOM)));
        assertEquals(List.of("cell"), given);
    }

    @Test
    void aFailedTransactionGivesTheItemBack() {
        assertEquals(CellUse.Outcome.ERROR, run(Optional.of("cell"), CompletableFuture.failedFuture(new IllegalStateException("db down"))));
        assertEquals(List.of("cell"), given);
    }

    @Test
    void anExceptionWhileStartingGivesTheItemBack() {
        CellUse.Outcome o = CellUse.run(() -> Optional.of("cell"), () -> {
            throw new IllegalStateException("boom");
        }, given::add).join();
        assertEquals(CellUse.Outcome.ERROR, o);
        assertEquals(List.of("cell"), given);
    }

    @Test
    void aReusedCopyStaysRemoved() {
        assertEquals(CellUse.Outcome.REUSED, run(Optional.of("copy"), CompletableFuture.completedFuture(CellUse.Outcome.REUSED)));
        assertEquals(List.of(), given);
    }

    @Test
    void aMovedItemAddsNothing() {
        assertEquals(CellUse.Outcome.MOVED, run(Optional.empty(), CompletableFuture.completedFuture(CellUse.Outcome.ADDED)));
        assertEquals(0, adds.get(), "nothing claimed, nothing added");
        assertEquals(List.of(), given);
    }
}
