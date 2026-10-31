package net.skybornmc.skyblock.stacking;

import java.util.OptionalInt;
import java.util.function.DoubleSupplier;

/**
 * The numbers behind stacking, free of Bukkit so they are unit-tested: merging into capped stacks, kills, loot scaling,
 * rare drops, breeding, spawner output, limits.
 */
final class StackMath {

    private StackMath() {
    }

    // ------------------------------------------------------------------ merging

    /**
     * R3-26: how many NEW stacks may still appear in a chunk that already holds {@code sameType} stacks of the spawning
     * type and {@code total} stacks of every type: the per-type limit (owner SK1: 8) and the chunk total (owner R3-26: 40)
     * both apply. Never negative.
     */
    static int freeNewStacks(int sameType, int total, int perTypeMax, int totalMax) {
        return Math.max(0, Math.min(perTypeMax - sameType, totalMax - total));
    }

    /** Free room in a stack of {@code current} under {@code max}. */
    static int room(long current, int max) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, max - current));
    }

    /**
     * How {@code amount} new mobs are spread: first into the existing stacks (in the given order) up to {@code max} each,
     * then into new stacks of up to {@code max}, at most {@code maxNewStacks} of them; the rest is dropped (owner SK1:
     * excess spawns merge or are cancelled).
     *
     * @param added     per existing stack, how many it gains
     * @param newStacks sizes of the new stacks to spawn
     * @param discarded mobs that fit nowhere
     */
    record Distribution(int[] added, int[] newStacks, long discarded) {

        long placed() {
            long sum = 0;
            for (int a : added) {
                sum += a;
            }
            for (int n : newStacks) {
                sum += n;
            }
            return sum;
        }
    }

    static Distribution distribute(long amount, int[] existing, int max, int maxNewStacks) {
        if (max < 1) {
            throw new IllegalArgumentException("max must be at least 1");
        }
        long left = Math.max(0, amount);
        int[] added = new int[existing.length];
        for (int i = 0; i < existing.length && left > 0; i++) {
            int take = (int) Math.min(left, room(existing[i], max));
            added[i] = take;
            left -= take;
        }
        int newCount = (int) Math.min(Math.max(0, maxNewStacks), (left + max - 1) / max);
        int[] fresh = new int[newCount];
        for (int i = 0; i < newCount; i++) {
            int take = (int) Math.min(left, max);
            fresh[i] = take;
            left -= take;
        }
        return new Distribution(added, fresh, left);
    }

    // ------------------------------------------------------------------ hoppers and inventories (S17, F12)

    /**
     * The stacks {@code amount} items fill at {@code maxStack} per stack: 64 cobblestone → [64], 130 → [64, 64, 2],
     * 40 ender pearls → [16, 16, 8]. Empty for 0 or less. A max below 1 counts as 1.
     */
    static int[] stackSizes(long amount, int maxStack) {
        if (amount <= 0) {
            return new int[0];
        }
        int max = Math.max(1, maxStack);
        long parts = (amount + max - 1) / max;
        if (parts > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("too many stacks: " + amount + " / " + max);
        }
        int[] sizes = new int[(int) parts];
        long left = amount;
        for (int i = 0; i < sizes.length; i++) {
            int n = (int) Math.min(left, max);
            sizes[i] = n;
            left -= n;
        }
        return sizes;
    }

    /**
     * Items a hopper (or any container) is offered from a stacked item entity of {@code count}: at most
     * {@code perTransfer} (owner S17: 64) and at most what the container could hold when empty ({@code slots} ×
     * {@code maxStack}), so a hopper is never handed more stacks than it has slots.
     */
    static long hopperOffer(long count, int perTransfer, int maxStack, int slots) {
        long capacity = (long) Math.max(0, slots) * Math.max(1, maxStack);
        return Math.max(0, Math.min(count, Math.min(Math.max(0, perTransfer), capacity)));
    }

    // ------------------------------------------------------------------ kills

    /**
     * Mobs killed by one lethal hit: the whole stack for grinder damage (owner S5), otherwise what the weapon allows
     * ({@code StackKillProvider}, 1 without one), never more than the stack.
     */
    static int killed(int stackSize, boolean wholeStackDamage, int weaponKills) {
        int size = Math.max(1, stackSize);
        if (wholeStackDamage) {
            return size;
        }
        return Math.max(1, Math.min(size, weaponKills));
    }

    /** XP for {@code killed} mobs, clamped to an int. */
    static int xp(int perMob, long killed) {
        long total = (long) Math.max(0, perMob) * Math.max(0, killed);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** XP for {@code killed} mobs with a percent bonus (Soul Reaper), rounded down, clamped to an int. */
    static int xp(int perMob, long killed, double bonusPercent) {
        double factor = 1 + (Double.isFinite(bonusPercent) ? Math.max(0, bonusPercent) : 0) / 100.0;
        double total = (double) Math.max(0, perMob) * Math.max(0, killed) * factor;
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(total));
    }

    /** Extra loot rolls for {@code killed} mobs with {@code percent} chance each (Plunder; at most one per mob). */
    static long extraRolls(long killed, double percent, DoubleSupplier random) {
        if (!Double.isFinite(percent) || percent <= 0) {
            return 0;
        }
        return binomial(killed, Math.min(100, percent) / 100.0, random);
    }

    /**
     * Mobs whose loot the stacking module rolls itself for one batch: every killed mob that has no death event of its own
     * (the dying mob of a lethal hit drops its loot through the event) plus the extra rolls.
     */
    static long lootMobs(int killed, boolean deathEvent, long extraRolls) {
        long own = Math.max(0, killed - (deathEvent ? 1 : 0));
        return own + Math.max(0, extraRolls);
    }

    // ------------------------------------------------------------------ loot

    /**
     * Scales the loot of {@code samples} rolled mobs to {@code mobs} mobs: {@code sum * mobs / samples}, the fraction
     * rounded up with that probability ({@code random} in [0, 1)) so the average is exact.
     */
    static long scale(long sampleSum, int samples, long mobs, double random) {
        if (samples <= 0 || mobs <= 0 || sampleSum <= 0) {
            return 0;
        }
        if (samples == mobs) {
            return sampleSum;
        }
        double exact = (double) sampleSum * mobs / samples;
        long whole = (long) Math.floor(exact);
        return whole + (random < exact - whole ? 1 : 0);
    }

    /** How many of {@code n} mobs drop a rare item with chance {@code p} each (exact for any n, cheap for big n). */
    static long binomial(long n, double p, DoubleSupplier random) {
        if (n <= 0 || p <= 0) {
            return 0;
        }
        if (p >= 1) {
            return n;
        }
        if (n <= 4096) {
            long hits = 0;
            for (long i = 0; i < n; i++) {
                if (random.getAsDouble() < p) {
                    hits++;
                }
            }
            return hits;
        }
        // Normal approximation for huge counts (never reached with 1,024-mob stacks, kept for safety).
        double mean = n * p;
        double sd = Math.sqrt(n * p * (1 - p));
        double u1 = Math.max(1e-12, random.getAsDouble());
        double u2 = random.getAsDouble();
        double z = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
        return Math.max(0, Math.min(n, Math.round(mean + z * sd)));
    }

    // ------------------------------------------------------------------ breeding (owner S10)

    enum BreedResult { OK, TOO_FEW, NO_FOOD, COOLDOWN, FULL }

    /**
     * Feeding a stack: every food item breeds one pair (15 cows + 5 wheat = 5 babies), limited by the parents not on
     * cooldown and the room in the stack. Babies join the stack.
     *
     * @param babies     babies born (= food used)
     * @param busyAfter  parents on cooldown afterwards
     */
    record Breed(BreedResult result, int babies, int busyAfter) {
    }

    static Breed breed(int count, int busyParents, int food, int maxStack) {
        int busy = Math.max(0, Math.min(busyParents, count));
        if (count < 2) {
            return new Breed(BreedResult.TOO_FEW, 0, busy);
        }
        if (food <= 0) {
            return new Breed(BreedResult.NO_FOOD, 0, busy);
        }
        int pairs = (count - busy) / 2;
        if (pairs <= 0) {
            return new Breed(BreedResult.COOLDOWN, 0, busy);
        }
        int room = room(count, maxStack);
        if (room <= 0) {
            return new Breed(BreedResult.FULL, 0, busy);
        }
        int babies = Math.min(Math.min(pairs, food), room);
        return new Breed(BreedResult.OK, babies, busy + 2 * babies);
    }

    // ------------------------------------------------------------------ spawners

    /** Mobs one spawn of a spawner block adds, with the fraction carried to the next spawn. */
    record Output(long mobs, double carry) {
    }

    /** Owner S23: full rate x stack, then the Spawner Rate mastery multiplier. */
    static Output spawnerOutput(double perSpawner, int stack, double multiplier, double carry) {
        double exact = Math.max(0, perSpawner) * Math.max(0, stack) * Math.max(0, multiplier) + Math.max(0, carry);
        long whole = (long) Math.floor(exact);
        return new Output(whole, exact - whole);
    }

    /** Spawners one block may hold: the mastery value or the start value, never above the max (owner S20). */
    static int stackLimit(OptionalInt mastery, int start, int max) {
        int value = mastery.isPresent() ? mastery.getAsInt() : start;
        return Math.max(1, Math.min(Math.max(1, max), value));
    }

    /** Owner S22: one spawner per break, sneak-break takes all. */
    static int breakAmount(int count, boolean sneaking) {
        return count <= 0 ? 0 : sneaking ? count : 1;
    }

    /** Ticks until the next spawn: uniform between the min and max seconds ({@code random} in [0, 1)). */
    static int intervalTicks(int minSeconds, int maxSeconds, double random) {
        int lo = Math.max(1, Math.min(minSeconds, maxSeconds));
        int hi = Math.max(lo, Math.max(minSeconds, maxSeconds));
        return (int) Math.round((lo + (hi - lo) * random) * 20);
    }

    // ------------------------------------------------------------------ limits

    /** An island entity limit with the upgrade bonus (owner SK2: +50% with the upgrade). */
    static int entityLimit(int base, int bonusPercent) {
        long value = (long) Math.max(0, base) * (100 + Math.max(0, bonusPercent)) / 100;
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
