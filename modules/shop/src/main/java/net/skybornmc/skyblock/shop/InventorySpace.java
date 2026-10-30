package net.skybornmc.skyblock.shop;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * How many of an item fit in a player's storage slots (hotbar + main inventory; armor and offhand are never filled),
 * counted <b>before</b> anything is paid, so a purchase is refused instead of dropping items. The math is pure
 * ({@link #fits(List, int)}), unit-tested.
 */
public final class InventorySpace {

    /** Storage slots of a player inventory (0-8 hotbar, 9-35 main). */
    public static final int STORAGE_SLOTS = 36;

    /** One storage slot as the math sees it. */
    public record Slot(boolean empty, boolean similar, int amount) {

        public static Slot emptySlot() {
            return new Slot(true, false, 0);
        }

        public static Slot same(int amount) {
            return new Slot(false, true, amount);
        }

        public static Slot other() {
            return new Slot(false, false, 0);
        }
    }

    private InventorySpace() {
    }

    /** Items of one kind that fit: every empty slot holds a full stack, every slot of the same item fills up to a stack. */
    public static long fits(List<Slot> slots, int maxStack) {
        if (maxStack < 1) {
            return 0;
        }
        long total = 0;
        for (Slot s : slots) {
            if (s.empty()) {
                total += maxStack;
            } else if (s.similar()) {
                total += Math.max(0, maxStack - s.amount());
            }
        }
        return total;
    }

    /** How many of {@code item} fit in {@code inv}'s storage slots right now. Main thread. */
    public static long fits(PlayerInventory inv, ItemStack item) {
        List<Slot> slots = new ArrayList<>(STORAGE_SLOTS);
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack in = inv.getItem(i);
            if (in == null || in.isEmpty()) {
                slots.add(Slot.emptySlot());
            } else if (in.isSimilar(item)) {
                slots.add(Slot.same(in.getAmount()));
            } else {
                slots.add(Slot.other());
            }
        }
        return fits(slots, item.getMaxStackSize());
    }
}
