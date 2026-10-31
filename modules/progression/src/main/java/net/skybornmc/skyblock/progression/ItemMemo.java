package net.skybornmc.skyblock.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Per-menu cache of built slot items (PERF6). Building an upgrade item parses 20-30 messages (name, effect texts, task
 * lines, cost, state); the upgrade menus used to rebuild all of them on every refresh (every {@code menu.refresh-ticks}
 * and again when the island bank balance arrived, 80 ms for one refresh of {@code /is upgrade}). A slot keeps its item
 * while its <b>signature</b> (every input its text depends on, as a value-comparable list) stays equal, so a refresh
 * only rebuilds what really changed. Main thread, one instance per open menu. Pure, unit-tested.
 *
 * @param <T> the item type (ItemStack in the menus)
 */
final class ItemMemo<T> {

    private record Entry<T>(Object signature, T item) {
    }

    private final Map<Object, Entry<T>> slots = new HashMap<>();
    private int builds;

    /**
     * The item cached for {@code slot} if it was built from an equal signature, else a fresh one from {@code build}
     * (then cached). A {@code null} signature never caches.
     */
    T get(Object slot, Object signature, Supplier<T> build) {
        Entry<T> hit = signature == null ? null : slots.get(slot);
        if (hit != null && Objects.equals(hit.signature(), signature)) {
            return hit.item();
        }
        T item = build.get();
        builds++;
        if (signature == null) {
            slots.remove(slot);
        } else {
            slots.put(slot, new Entry<>(signature, item));
        }
        return item;
    }

    /** Forgets every slot (e.g. a reload of the catalog). */
    void clear() {
        slots.clear();
    }

    /** How many items were built so far (tests). */
    int builds() {
        return builds;
    }
}
