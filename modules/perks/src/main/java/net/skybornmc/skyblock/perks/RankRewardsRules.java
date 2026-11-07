package net.skybornmc.skyblock.perks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Ranks V2 rank rewards, pure (unit-tested): period keys, kit size, kit lines and the monthly pick. */
final class RankRewardsRules {

    /** Kit sizes by {@code skyblock.rank.kit.size.<n>}: 1 = S ... 4 = XL. */
    static final List<String> SIZES = List.of("S", "M", "L", "XL");

    /** One kit line: a vanilla item id and an amount. */
    record Line(String item, int amount) {
    }

    private RankRewardsRules() {
    }

    /** ISO week key in {@code zone} ({@code 2026-W45}); resets Monday 00:00. */
    static String weekKey(Instant now, ZoneId zone) {
        LocalDate d = LocalDate.ofInstant(now, zone);
        return String.format(Locale.ROOT, "%d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Month key in {@code zone} ({@code 2026-11}). */
    static String monthKey(Instant now, ZoneId zone) {
        LocalDate d = LocalDate.ofInstant(now, zone);
        return String.format(Locale.ROOT, "%d-%02d", d.getYear(), d.getMonthValue());
    }

    /** The kit size of the highest size node the player holds ({@code has(1..4)}), or null for none. */
    static String size(Predicate<Integer> has) {
        for (int n = SIZES.size(); n >= 1; n--) {
            if (has.test(n)) {
                return SIZES.get(n - 1);
            }
        }
        return null;
    }

    /**
     * Kit lines ({@code item amount}). Unknown items, amounts outside 1..2304, and tools, weapons, armour, spawners and
     * custom items are refused with a problem (a kit is food and blocks only, RANKS-V2 E2).
     */
    static List<Line> parse(List<String> raw, Predicate<String> validItem, Consumer<String> problems) {
        List<Line> out = new ArrayList<>();
        for (String line : raw == null ? List.<String>of() : raw) {
            String[] parts = line == null ? new String[0] : line.trim().split("\\s+");
            if (parts.length != 2) {
                problems.accept("'" + line + "' needs the form 'item amount' (skipped)");
                continue;
            }
            String item = parts[0].toLowerCase(Locale.ROOT);
            int amount;
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                problems.accept("'" + line + "' has no whole amount (skipped)");
                continue;
            }
            if (forbidden(item)) {
                problems.accept("'" + line + "': kits never hold tools, weapons, armour, spawners or custom items (skipped)");
            } else if (amount < 1 || amount > 2304 || !validItem.test(item)) {
                problems.accept("'" + line + "': unknown item or amount outside 1-2304 (skipped)");
            } else {
                out.add(new Line(item, amount));
            }
        }
        return out;
    }

    static boolean forbidden(String item) {
        return item.contains(":") || item.startsWith("tool_") || item.equals("spawner") || item.equals("trial_spawner")
                || item.matches(".*_(pickaxe|axe|shovel|hoe|sword|helmet|chestplate|leggings|boots|spear)$")
                || Set.of("bow", "crossbow", "trident", "mace", "shield", "elytra", "fishing_rod", "shears").contains(item);
    }

    /** The first pool id the player does not own yet, or null (pool empty or all owned). */
    static String nextCosmetic(List<String> pool, Collection<String> owned) {
        for (String id : pool == null ? List.<String>of() : pool) {
            String k = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
            if (!k.isEmpty() && !owned.contains(k)) {
                return k;
            }
        }
        return null;
    }

    /**
     * Owner 7 Nov 2026 (/reclaim): the points of the highest listed group the player has ({@code '<group>:<points>'}
     * lines, lowest rank first); 0 for none. A bad line counts as 0. Pure.
     */
    static long points(java.util.List<String> lines, java.util.function.Predicate<String> hasGroup) {
        long best = 0;
        for (String raw : lines == null ? java.util.List.<String>of() : lines) {
            String line = raw == null ? "" : raw.strip().toLowerCase(java.util.Locale.ROOT);
            int colon = line.lastIndexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                long pts = Long.parseLong(line.substring(colon + 1).strip());
                if (hasGroup.test(line.substring(0, colon).strip())) {
                    best = Math.max(best, pts);
                }
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        return best;
    }
}
