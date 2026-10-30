package net.skybornmc.skyblock.economy.sell;

import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sell chest's crash copy (SELL2): save, restore, clear, broken data. */
class SellChestStoreTest {

    /** The player's persistent data, in memory. */
    static final class MemoryHolder implements SellChestStore.Holder {
        byte[] data;
        int writes;

        @Override
        public byte[] read() {
            return data;
        }

        @Override
        public void write(byte[] data) {
            this.data = data.clone();
            writes++;
        }

        @Override
        public void clear() {
            data = null;
        }
    }

    /** Stacks are strings like {@code "coal x64"}; "BROKEN" can't be read back. */
    static SellChestStore<String> store(MemoryHolder holder) {
        return new SellChestStore<>(holder, s -> s.getBytes(StandardCharsets.UTF_8), b -> {
            String s = new String(b, StandardCharsets.UTF_8);
            if (s.equals("BROKEN")) {
                throw new IllegalArgumentException("unreadable");
            }
            return s;
        });
    }

    private static final UUID PROFILE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void saveAndRestoreKeepsSlotsProfileAndTime() {
        MemoryHolder holder = new MemoryHolder();
        SellChestStore<String> store = store(holder);
        Map<Integer, String> items = new LinkedHashMap<>();
        items.put(0, "coal x64");
        items.put(7, "wheat x12");
        items.put(35, "iron_ingot x1");
        store.save(PROFILE, items, 1234L);

        assertTrue(store.present());
        var restored = store.read().orElseThrow();
        assertEquals(PROFILE, restored.profile());
        assertEquals(1234L, restored.savedAt());
        assertEquals(items, restored.items());
        assertEquals(List.of(new ArrayList<>(items.keySet())), List.of(new ArrayList<>(restored.items().keySet())));
        assertTrue(restored.broken().isEmpty());
        assertTrue(store.present(), "read does not clear");
    }

    @Test
    void emptyChestClearsTheCopy() {
        MemoryHolder holder = new MemoryHolder();
        SellChestStore<String> store = store(holder);
        store.save(PROFILE, Map.of(3, "coal x1"), 1);
        assertTrue(store.present());
        Map<Integer, String> empty = new LinkedHashMap<>();
        empty.put(3, null);
        store.save(PROFILE, empty, 2);
        assertFalse(store.present());
        assertNull(holder.data);
        assertTrue(store.read().isEmpty());
    }

    @Test
    void latestSaveWins() {
        MemoryHolder holder = new MemoryHolder();
        SellChestStore<String> store = store(holder);
        store.save(PROFILE, Map.of(0, "coal x64", 1, "coal x64"), 1);
        // The player takes slot 1 out: the copy must not hold it any more (never in inventory and copy at once).
        store.save(PROFILE, Map.of(0, "coal x64"), 2);
        assertEquals(Map.of(0, "coal x64"), store.read().orElseThrow().items());
    }

    @Test
    void clearRemovesEverything() {
        MemoryHolder holder = new MemoryHolder();
        SellChestStore<String> store = store(holder);
        store.save(PROFILE, Map.of(0, "coal x64"), 1);
        store.clear();
        assertFalse(store.present());
        assertTrue(store.read().isEmpty());
    }

    @Test
    void unreadableStacksAreReportedNotLost() {
        MemoryHolder holder = new MemoryHolder();
        SellChestStore<String> store = store(holder);
        store.save(PROFILE, Map.of(0, "coal x64", 1, "BROKEN"), 1);
        var restored = store.read().orElseThrow();
        assertEquals(Map.of(0, "coal x64"), restored.items());
        assertEquals(1, restored.broken().size());
        assertArrayEquals("BROKEN".getBytes(StandardCharsets.UTF_8), restored.broken().get(0));
    }

    @Test
    void foreignOrCutDataIsRefused() {
        MemoryHolder holder = new MemoryHolder();
        SellChestStore<String> store = store(holder);
        holder.data = new byte[]{1, 2, 3, 4, 5};
        assertThrows(IllegalArgumentException.class, store::read);

        byte[] good = SellChestStore.encode(PROFILE, 1, Map.of(0, "coal".getBytes(StandardCharsets.UTF_8)));
        holder.data = Arrays.copyOf(good, good.length - 2);
        assertThrows(IllegalArgumentException.class, store::read);
    }

    @Test
    void roundTripOfTheFormat() {
        Map<Integer, byte[]> items = new LinkedHashMap<>();
        items.put(4, new byte[]{9, 8, 7});
        items.put(5, new byte[0]);
        var decoded = SellChestStore.decode(SellChestStore.encode(PROFILE, 99, items));
        assertEquals(PROFILE, decoded.profile());
        assertEquals(99, decoded.savedAt());
        assertArrayEquals(new byte[]{9, 8, 7}, decoded.items().get(4));
        assertArrayEquals(new byte[0], decoded.items().get(5));
    }

    @Test
    void onlySafeVanillaActionsAreAllowedOnFreeSlots() {
        for (InventoryAction ok : List.of(InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF, InventoryAction.PLACE_ALL,
                InventoryAction.PLACE_ONE, InventoryAction.PLACE_SOME, InventoryAction.SWAP_WITH_CURSOR,
                InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
            assertTrue(SellMenu.allowedTopAction(ok), ok.name());
        }
        // Collecting to the cursor could pull frame panes; hotbar swaps and drops are never allowed.
        for (InventoryAction no : List.of(InventoryAction.COLLECT_TO_CURSOR, InventoryAction.HOTBAR_SWAP,
                InventoryAction.DROP_ALL_SLOT, InventoryAction.DROP_ONE_SLOT, InventoryAction.CLONE_STACK, InventoryAction.NOTHING)) {
            assertFalse(SellMenu.allowedTopAction(no), no.name());
        }
    }
}
