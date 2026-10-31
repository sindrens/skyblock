package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure helpers for the guide markers (PERF4, "tutorial-guides"). The farm-spot and ore scans walk ~1,000-1,300 blocks
 * every 2 s per tutorial player; they used to re-strip and lowercase every {@code mine-blocks} rule for every block and
 * sorted the hits with a comparator that built two {@code Location}s per comparison. Now the rules are compiled once
 * per config list and each hit's distance is computed once; the stable sort keeps the old order, ties included.
 *
 * <p>PERF7: instead of testing every block of the box and then sorting the hits, a scan walks the box's positions
 * <b>nearest first</b> ({@link #order}, computed once per centre and cached by the caller) and stops as soon as it has
 * enough markers ({@link #firstMatches}). The positions are sorted with the same distance and the same stable tie
 * order as the old hits, so the chosen markers are exactly the same; usually only a few dozen blocks are read instead
 * of 1,000-1,300.</p>
 */
final class MarkerScan {

    private MarkerScan() {
    }

    /** Compiled {@code guide.markers.mine-blocks}: exact ids and {@code *suffix} rules. */
    record Matcher(Set<String> exact, List<String> suffixes) {

        static Matcher compile(List<String> rules) {
            Set<String> exact = new HashSet<>();
            List<String> suffixes = new ArrayList<>();
            for (String rule : rules) {
                String r = rule == null ? "" : rule.strip().toLowerCase(Locale.ROOT);
                if (r.startsWith("*")) {
                    suffixes.add(r.substring(1));
                } else {
                    exact.add(r);
                }
            }
            return new Matcher(Set.copyOf(exact), List.copyOf(suffixes));
        }

        /** Same rule as before: {@code *_ore} matches ids ending in {@code _ore}, anything else must be equal. */
        boolean matches(String key) {
            if (exact.contains(key)) {
                return true;
            }
            for (String s : suffixes) {
                if (key.endsWith(s)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A block found by a scan, with its squared distance to the scan centre computed once. */
    record Hit(int x, int y, int z, double distanceSquared) {

        /** Distance from the block's corner ({@code Block#getLocation()}) to the centre, as the old comparator used. */
        static Hit of(int x, int y, int z, double cx, double cy, double cz) {
            double dx = x - cx;
            double dy = y - cy;
            double dz = z - cz;
            return new Hit(x, y, z, dx * dx + dy * dy + dz * dz);
        }
    }

    /** Sorts hits nearest first (stable: equal distances keep scan order). */
    static List<Hit> nearestFirst(List<Hit> hits) {
        List<Hit> sorted = new ArrayList<>(hits);
        sorted.sort(Comparator.comparingDouble(Hit::distanceSquared));
        return sorted;
    }

    /** Box loop order of a scan: the order positions were visited by the old loops (it decides ties). */
    enum Loop {
        /** {@code dx}, then {@code dz}, then {@code dy} (farm spots). */
        X_Z_Y,
        /** {@code dx}, then {@code dy}, then {@code dz} (ore). */
        X_Y_Z
    }

    /**
     * Every position of the box {@code block ± (r, dyMin..dyMax, r)} (dy range used for the y axis), as hits sorted
     * nearest first from {@code (cx, cy, cz)}, ties in {@code loop} order. Same distances as {@link Hit#of}.
     */
    static List<Hit> order(int bx, int by, int bz, double cx, double cy, double cz, int r, int dyMin, int dyMax, Loop loop) {
        int radius = Math.max(0, r);
        List<Hit> all = new ArrayList<>((2 * radius + 1) * (2 * radius + 1) * Math.max(1, dyMax - dyMin + 1));
        for (int dx = -radius; dx <= radius; dx++) {
            if (loop == Loop.X_Z_Y) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = dyMin; dy <= dyMax; dy++) {
                        all.add(Hit.of(bx + dx, by + dy, bz + dz, cx, cy, cz));
                    }
                }
            } else {
                for (int dy = dyMin; dy <= dyMax; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        all.add(Hit.of(bx + dx, by + dy, bz + dz, cx, cy, cz));
                    }
                }
            }
        }
        return nearestFirst(all);
    }

    /** The first {@code limit} positions of {@code order} that {@code accept} (tested in order, stops when full). */
    static List<Hit> firstMatches(List<Hit> order, Predicate<Hit> accept, int limit) {
        List<Hit> out = new ArrayList<>(Math.max(0, Math.min(limit, 16)));
        if (limit <= 0) {
            return out;
        }
        for (Hit hit : order) {
            if (accept.test(hit)) {
                out.add(hit);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    /** Chunk coordinates ({@code x >> 4}) the box {@code centre ± r} touches on one axis: {@code [min, max]}. */
    static int[] chunkSpan(int centre, int r) {
        return new int[]{(centre - Math.max(0, r)) >> 4, (centre + Math.max(0, r)) >> 4};
    }

    /** Cache key of an {@link #order}: centre, block and box. A record, so equal inputs compare equal. */
    record OrderKey(String world, int bx, int by, int bz, double cx, double cy, double cz, int r, int dyMin, int dyMax, Loop loop) {
    }
}
