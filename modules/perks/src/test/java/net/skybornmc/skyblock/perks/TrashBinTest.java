package net.skybornmc.skyblock.perks;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrashBinTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void closingWithoutConfirmGivesEverythingBack() {
        TrashBin<String> bin = new TrashBin<>();
        bin.hold(player, List.of("dirt x64", "diamond x1"));
        assertTrue(bin.holding(player));
        assertEquals(List.of("dirt x64", "diamond x1"), bin.release(player));
        assertFalse(bin.holding(player));
        assertEquals(List.of(), bin.confirm(player), "a returned batch can't be destroyed afterwards");
    }

    @Test
    void confirmedItemsAreDestroyedOnceAndNeverReturned() {
        TrashBin<String> bin = new TrashBin<>();
        bin.hold(player, List.of("cobblestone x32"));
        assertEquals(List.of("cobblestone x32"), bin.confirm(player));
        assertEquals(List.of(), bin.confirm(player), "a double click destroys nothing more");
        assertEquals(List.of(), bin.release(player), "the close after the confirm gives nothing back");
    }

    @Test
    void holdingAgainHandsBackTheOldBatch() {
        TrashBin<String> bin = new TrashBin<>();
        assertEquals(List.of(), bin.hold(player, List.of("a")));
        assertEquals(List.of("a"), bin.hold(player, List.of("b")), "the old batch must go back first");
        assertEquals(List.of("b"), bin.release(player));
    }

    @Test
    void playersAreKeptApart() {
        TrashBin<String> bin = new TrashBin<>();
        UUID other = UUID.randomUUID();
        bin.hold(player, List.of("mine"));
        bin.hold(other, List.of("theirs"));
        assertEquals(List.of("theirs"), bin.release(other));
        assertEquals(List.of("mine"), bin.confirm(player));
        assertTrue(bin.players().isEmpty());
    }
}
