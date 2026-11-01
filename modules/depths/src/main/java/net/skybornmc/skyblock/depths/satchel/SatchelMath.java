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

    /** R3-22: true when a throttled warning may show again ({@code intervalMs} since the last one; 0 = always). */
    public static boolean warnDue(long lastMs, long nowMs, long intervalMs) {
        return intervalMs <= 0 || nowMs - lastMs >= intervalMs;
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

    /** {@code /satchel take} amount word: one stack. */
    public static final long WANT_STACK = 0;
    /** {@code /satchel take} amount word: everything that fits. */
    public static final long WANT_ALL = -1;

    /**
     * What a take-out asks for before the inventory check: {@link #WANT_STACK} = one stack, {@link #WANT_ALL} (or any
     * negative) = all held, a positive number = that many.
     */
    public static long requested(long wanted, long held, int maxStack) {
        if (wanted == WANT_STACK) {
            return Math.max(1, maxStack);
        }
        return wanted < 0 ? Math.max(0, held) : wanted;
    }

    /**
     * Parses the amount word of {@code /satchel take}: {@code stack} (or missing) = one stack, {@code all} = all that
     * fit, a whole number of at least 1 = that many. Returns {@code null} for anything else.
     */
    public static Long parseWanted(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("stack")) {
            return WANT_STACK;
        }
        if (raw.equalsIgnoreCase("all")) {
            return WANT_ALL;
        }
        try {
            long n = Long.parseLong(raw.strip());
            return n >= 1 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
