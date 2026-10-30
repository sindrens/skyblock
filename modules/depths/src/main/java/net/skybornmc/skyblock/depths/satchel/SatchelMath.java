package net.skybornmc.skyblock.depths.satchel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Satchel numbers (M4.4): capacity from skills, upgrades and bonuses; how much fits; stacks for selling. Pure. */
public final class SatchelMath {

    /** One upgrade step: {@code capacity} more items for {@code price} money. */
    public record Upgrade(int capacity, long price) {
    }

    private SatchelMath() {
    }

    /** Parses {@code satchel.upgrades} entries ({@code extra-space=price}); broken entries are reported and skipped. */
    public static List<Upgrade> upgrades(List<String> raw, Consumer<String> problem) {
        List<Upgrade> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String[] parts = entry.split("=", 2);
            try {
                if (parts.length != 2) {
                    throw new NumberFormatException();
                }
                int capacity = Integer.parseInt(parts[0].trim());
                long price = Long.parseLong(parts[1].trim().replace("_", "").replace(",", ""));
                if (capacity <= 0 || price < 0) {
                    throw new NumberFormatException();
                }
                out.add(new Upgrade(capacity, price));
            } catch (NumberFormatException e) {
                problem.accept("satchel.upgrades: '" + entry + "' must be extra-space=price (e.g. 256=25000), skipped");
            }
        }
        return List.copyOf(out);
    }

    /**
     * Satchel capacity: base + per level of Mining and Foraging + the bought upgrade steps + bonuses, capped at
     * {@code max} (and at least 1).
     */
    public static long capacity(int base, int perMining, int mining, int perForaging, int foraging, List<Upgrade> upgrades,
                                int upgradeLevel, long bonus, int max) {
        long total = (long) base + (long) perMining * Math.max(0, mining) + (long) perForaging * Math.max(0, foraging);
        int steps = Math.min(Math.max(0, upgradeLevel), upgrades.size());
        for (int i = 0; i < steps; i++) {
            total += upgrades.get(i).capacity();
        }
        total += Math.max(0, bonus);
        return Math.max(1, Math.min(total, max));
    }

    /** True if no more drops go in (the break is refused). */
    public static boolean full(long used, long capacity) {
        return used >= capacity;
    }

    /** Free space (never negative). */
    public static long free(long used, long capacity) {
        return Math.max(0, capacity - used);
    }

    /** Used share in percent, 0-100. */
    public static int percent(long used, long capacity) {
        if (capacity <= 0) {
            return 100;
        }
        return (int) Math.min(100, used * 100 / capacity);
    }

    /** How many items of one kind fit in an inventory with {@code emptySlots} free slots and {@code partialRoom} on its partial stacks. */
    public static long fit(int emptySlots, int maxStack, long partialRoom) {
        return (long) Math.max(0, emptySlots) * Math.max(1, maxStack) + Math.max(0, partialRoom);
    }

    /** Take-out amount: what was asked, at most what is held and what fits. */
    public static long takeAmount(long requested, long held, long fits) {
        return Math.max(0, Math.min(requested, Math.min(held, fits)));
    }

    /** Stack sizes for {@code amount} items ({@code maxStack} each, the last one smaller). */
    public static int[] stacks(long amount, int maxStack) {
        int size = Math.max(1, maxStack);
        if (amount <= 0) {
            return new int[0];
        }
        long count = (amount + size - 1) / size;
        if (count > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("too many stacks: " + amount);
        }
        int[] out = new int[(int) count];
        long left = amount;
        for (int i = 0; i < out.length; i++) {
            out[i] = (int) Math.min(size, left);
            left -= out[i];
        }
        return out;
    }
}
