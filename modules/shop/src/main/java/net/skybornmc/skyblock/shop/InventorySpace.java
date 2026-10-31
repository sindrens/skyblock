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

    /** How many items similar to {@code template} the storage slots hold (F14 undo: are the bought items still there?). */
    public static long count(PlayerInventory inv, ItemStack template) {
        long total = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack in = inv.getItem(i);
            if (in != null && !in.isEmpty() && in.isSimilar(template)) {
                total += in.getAmount();
            }
        }
        return total;
    }

    /**
     * Which amounts the slots keep after taking {@code amount} items (pure): {@code held[i]} = similar items in slot i
     * (0 for other or empty slots). Takes from the main inventory first (slots 35 down to 9), then the hotbar, so what
     * the player holds goes last. Null if there are fewer than {@code amount}.
     */
    public static int[] takePlan(int[] held, long amount) {
        long have = 0;
        for (int h : held) {
            have += Math.max(0, h);
        }
        if (amount < 0 || have < amount) {
            return null;
        }
        int[] out = held.clone();
        long left = amount;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int i = out.length - 1; i >= 0 && left > 0; i--) {
                boolean hotbar = i < 9;
                if ((pass == 0) == hotbar || out[i] <= 0) {
                    continue;
                }
                int take = (int) Math.min(out[i], left);
                out[i] -= take;
                left -= take;
            }
        }
        return out;
    }

    /** Takes exactly {@code amount} items similar to {@code template} out of the storage slots; false (nothing taken) if fewer. */
    public static boolean take(PlayerInventory inv, ItemStack template, long amount) {
        int[] held = new int[STORAGE_SLOTS];
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack in = inv.getItem(i);
            held[i] = in != null && !in.isEmpty() && in.isSimilar(template) ? in.getAmount() : 0;
        }
        int[] plan = takePlan(held, amount);
        if (plan == null) {
            return false;
        }
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (plan[i] != held[i]) {
                ItemStack in = inv.getItem(i);
                if (plan[i] <= 0) {
                    inv.setItem(i, null);
                } else if (in != null) {
                    in.setAmount(plan[i]);
                    inv.setItem(i, in);
                }
            }
        }
        return true;
    }
}
