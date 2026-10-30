package net.skybornmc.skyblock.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Pure helpers for the flicker-free sidebar: invisible unique entries and line diffs. */
final class SidebarLines {

    private static final char[] CODES = "0123456789abcdef".toCharArray();

    private SidebarLines() {
    }

    /**
     * An invisible, unique scoreboard entry for line {@code index} (0–31): two legacy color codes and a reset.
     * The visible text lives in the team prefix, so updating a line never removes/re-adds a score (no flicker).
     */
    static String entry(int index) {
        if (index < 0 || index >= 32) {
            throw new IllegalArgumentException("line index " + index);
        }
        return "§" + CODES[index % 16] + "§" + CODES[index / 16] + "§r";
    }

    /** Indexes whose content differs between {@code before} and {@code after} (same length assumed for the overlap). */
    static <T> List<Integer> changed(List<T> before, List<T> after) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Math.min(before.size(), after.size()); i++) {
            if (!Objects.equals(before.get(i), after.get(i))) {
                out.add(i);
            }
        }
        return out;
    }

    /** Players are spread over the update interval: does this player update on {@code tick}? */
    static boolean due(int playerHash, long tick, int interval) {
        return Math.floorMod(playerHash, interval) == Math.floorMod(tick, interval);
    }

    /** Layout for a world without a module rule: the layout named after the world (e.g. {@code depths}) if it exists, else {@code default}. */
    static String layout(String world, Predicate<String> exists) {
        return world != null && !world.isBlank() && exists.test(world) ? world : "default";
    }
}
