package net.skybornmc.skyblock.shop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * {@code /shop <text>} (owner 3 Nov 2026: "/shop string to buy string, /shop spaw will understand spawners"): ranks the
 * shop's items against what the player typed. Pure.
 * <ol>
 *   <li>100: the id is the text ({@code stone}), also with spaces for underscores ({@code oak log})</li>
 *   <li>80: the id starts with it ({@code spaw} → {@code spawner_pig})</li>
 *   <li>60: one word of the id starts with it ({@code log} → {@code oak_log})</li>
 *   <li>40: the id contains it</li>
 *   <li>20: one word is one typo away ({@code stome} → {@code stone})</li>
 * </ol>
 * Ties: the shorter id first, then a-z, so {@code stone} beats {@code stone_bricks}.
 */
final class ShopSearch {

    record Hit(ShopCatalog.Item item, int score) {
    }

    private ShopSearch() {
    }

    static String norm(String s) {
        return s == null ? "" : s.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace("minecraft:", "");
    }

    static int score(String query, String id) {
        String q = norm(query);
        String i = norm(id);
        if (q.isEmpty()) {
            return 0;
        }
        if (i.equals(q)) {
            return 100;
        }
        if (i.startsWith(q)) {
            return 80;
        }
        for (String w : i.split("_")) {
            if (w.startsWith(q)) {
                return 60;
            }
        }
        if (i.contains(q)) {
            return 40;
        }
        if (q.length() >= 4) {
            for (String w : i.split("_")) {
                if (oneEdit(q, w)) {
                    return 20;
                }
            }
        }
        return 0;
    }

    /** True when {@code a} and {@code b} are at most one insert, delete or change apart. */
    static boolean oneEdit(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        int i = 0;
        int j = 0;
        int edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++edits > 1) {
                return false;
            }
            if (a.length() > b.length()) {
                i++;
            } else if (a.length() < b.length()) {
                j++;
            } else {
                i++;
                j++;
            }
        }
        return edits + (a.length() - i) + (b.length() - j) <= 1;
    }

    /** The best matches first (at most {@code limit}). */
    static List<Hit> search(String query, List<ShopCatalog.Item> items, int limit) {
        List<Hit> out = new ArrayList<>();
        for (ShopCatalog.Item item : items) {
            int s = score(query, item.id());
            if (s > 0) {
                out.add(new Hit(item, s));
            }
        }
        out.sort(Comparator.comparingInt(Hit::score).reversed().thenComparingInt(h -> h.item().id().length())
                .thenComparing(h -> h.item().id()));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }
}
