package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.production.FuelState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The pure rules of Island Fuel (PHASE-7 M7.1), no Bukkit: tank size, burn rate, one state step, Fuel Cell sizes and the
 * time text. Fuel is kept in milliseconds.
 */
final class FuelMath {

    static final long MINUTE = 60_000L;
    static final long HOUR = 60 * MINUTE;

    private FuelMath() {
    }

    /** The result of one step: fuel after it, the new state and whether the island just fell asleep. */
    record Step(long fuelMs, FuelState state, boolean fellAsleep) {
    }

    /** Tank size: {@code start + perLevel × level} hours (level below 0 counts as 0). */
    static long capacityMs(int startHours, int hoursPerLevel, int tankLevel) {
        long hours = (long) startHours + (long) hoursPerLevel * Math.max(0, tankLevel);
        return Math.max(MINUTE, hours * HOUR);
    }

    /** The Fuel Efficiency family after its cap ({@code [0, cap]}). */
    static double efficiency(double totalPercent, double capPercent) {
        double cap = Math.max(0, capPercent);
        if (!Double.isFinite(totalPercent) || totalPercent <= 0) {
            return 0;
        }
        return Math.min(totalPercent, cap);
    }

    /** Fuel minutes burned per real minute: {@code base ÷ (1 + efficiency/100)} ("fuel lasts +N%"). */
    static double burnRate(double base, double efficiencyPercent) {
        double b = !Double.isFinite(base) || base <= 0 ? 1.0 : base;
        return b / (1 + Math.max(0, efficiencyPercent) / 100.0);
    }

    /**
     * One step of the state machine over {@code elapsedMs} of real time.
     * <ul>
     *   <li>An active member on the island: +1 ms fuel per ms (never above the tank; a tank that shrank keeps what it has),
     *       state Active.</li>
     *   <li>Otherwise the tank burns {@code elapsed × burnRate}: state Fueled while fuel is left, Asleep at 0.</li>
     * </ul>
     *
     * @param before the state before the step (to tell when the island just fell asleep)
     */
    static Step step(long fuelMs, long capacityMs, FuelState before, boolean active, double burnRate, long elapsedMs) {
        long fuel = Math.max(0, fuelMs);
        long elapsed = Math.max(0, elapsedMs);
        if (active) {
            long next = fuel >= capacityMs ? fuel : Math.min(capacityMs, fuel + elapsed);
            return new Step(next, FuelState.ACTIVE, false);
        }
        long burn = (long) Math.ceil(elapsed * Math.max(0, burnRate));
        long next = Math.max(0, fuel - burn);
        FuelState state = next > 0 ? FuelState.FUELED : FuelState.ASLEEP;
        return new Step(next, state, state == FuelState.ASLEEP && before != FuelState.ASLEEP);
    }

    /** Fuel a grant may add: as much as fits (0 when the tank is full). */
    static long grantable(long fuelMs, long capacityMs, long addMs) {
        if (addMs <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(addMs, capacityMs - Math.max(0, fuelMs)));
    }

    /** True when a whole Fuel Cell fits into the tank (a cell is never partly wasted). */
    static boolean cellFits(long fuelMs, long capacityMs, long cellMs) {
        return cellMs > 0 && Math.max(0, fuelMs) + cellMs <= capacityMs;
    }

    /** The cell sizes (minutes), largest first, without duplicates or values below 1. */
    static long[] cellSizes(long... minutes) {
        return Arrays.stream(minutes).filter(m -> m >= 1).distinct().boxed()
                .sorted((a, b) -> Long.compare(b, a)).mapToLong(Long::longValue).toArray();
    }

    /**
     * Splits {@code minutes} into cells (largest first). A rest smaller than the smallest cell becomes one smallest cell;
     * at least one cell; at most {@code maxCells} cells (the rest is dropped then). Empty for no sizes.
     */
    static List<Long> splitIntoCells(long minutes, long[] sizesLargestFirst, int maxCells) {
        List<Long> out = new ArrayList<>();
        if (sizesLargestFirst == null || sizesLargestFirst.length == 0 || maxCells < 1) {
            return out;
        }
        long left = Math.max(0, minutes);
        for (long size : sizesLargestFirst) {
            while (left >= size && out.size() < maxCells) {
                out.add(size);
                left -= size;
            }
        }
        long smallest = sizesLargestFirst[sizesLargestFirst.length - 1];
        if ((left > 0 || out.isEmpty()) && out.size() < maxCells) {
            out.add(smallest);
        }
        return out;
    }

    /** {@code "7h 20m"}, {@code "45m"}, {@code "36h"}; hours are never turned into days (fuel reads in hours). */
    static String time(long ms) {
        long minutes = Math.max(0, ms) / MINUTE;
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + "m";
        }
        return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
    }

    /** {@code 0.71} style text of a burn rate (two decimals, trailing zeros cut). */
    static String rate(double rate) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", rate);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
