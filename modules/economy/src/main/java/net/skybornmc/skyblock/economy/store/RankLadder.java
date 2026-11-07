package net.skybornmc.skyblock.economy.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The ranks sold for credits (owner 7 Nov 2026: Hatchling 500 … Immortal 10,000; an upgrade costs only the difference).
 * Parsed from {@code credit-shop.ranks} ({@code <group>:<credits>}, lowest first). Pure.
 */
public record RankLadder(List<Step> steps) {

    /** One rank: its LuckPerms group and full price in credits. */
    public record Step(String group, long price) {
    }

    /** Parses the config lines; a bad line is reported and skipped, prices must grow. */
    public static RankLadder parse(List<String> lines, Consumer<String> problems) {
        List<Step> out = new ArrayList<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            String line = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            int colon = line.lastIndexOf(':');
            if (colon <= 0) {
                problems.accept("economy.yml credit-shop.ranks: '" + raw + "' is not <group>:<credits> (skipped)");
                continue;
            }
            String group = line.substring(0, colon).trim();
            long price;
            try {
                price = Long.parseLong(line.substring(colon + 1).trim().replace(",", "").replace("_", ""));
            } catch (NumberFormatException e) {
                problems.accept("economy.yml credit-shop.ranks: '" + raw + "' has no whole number price (skipped)");
                continue;
            }
            if (!group.matches("[a-z0-9_.-]{1,36}") || price <= 0) {
                problems.accept("economy.yml credit-shop.ranks: '" + raw + "' needs a group name and a price above 0 (skipped)");
                continue;
            }
            if (!out.isEmpty() && price <= out.getLast().price()) {
                problems.accept("economy.yml credit-shop.ranks: '" + raw + "' must cost more than the rank before it (skipped)");
                continue;
            }
            out.add(new Step(group, price));
        }
        return new RankLadder(List.copyOf(out));
    }

    /** Index of the highest step the player has ({@code has} answers "has this group"), -1 for none. */
    public int highest(Predicate<String> has) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (has.test(steps.get(i).group())) {
                return i;
            }
        }
        return -1;
    }

    /** What step {@code index} costs a player whose highest step is {@code owned}: -1 when owned or lower. */
    public long price(int index, int owned) {
        if (index < 0 || index >= steps.size() || index <= owned) {
            return -1;
        }
        return steps.get(index).price() - (owned < 0 ? 0 : steps.get(owned).price());
    }
}
