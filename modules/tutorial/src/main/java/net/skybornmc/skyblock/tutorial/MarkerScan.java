package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure helpers for the guide markers (PERF4, "tutorial-guides"). The farm-spot and ore scans walk ~1,000-1,300 blocks
 * every 2 s per tutorial player; they used to re-strip and lowercase every {@code mine-blocks} rule for every block and
 * sorted the hits with a comparator that built two {@code Location}s per comparison. Now the rules are compiled once
 * per config list and each hit's distance is computed once; the stable sort keeps the old order, ties included.
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
}
