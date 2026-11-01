package net.skybornmc.skyblock.depths.drops;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * ECON-SELL-SHOP-AH D2.2: every Depths drop must reach the player and be worth something. At module enable and on reload
 * every item id named by {@code depths.yml drops}, {@code drop-items} and every {@code mobs.yml} combat drop is checked:
 * it must be a registered custom item and have a sell price ({@code sell.yml custom-items.depths-*}). Unknown ids are
 * already skipped while the tables are parsed, so a broken line can never reach the inventory hand-out; this check makes
 * the problem visible (one WARNING per problem, the count in {@code /depths debug}). Pure.
 */
public final class DropSelfCheck {

    private DropSelfCheck() {
    }

    /**
     * Checks the referenced ids.
     *
     * @param referenced every item id a table, a drop-items line or a mob drop names
     * @param registered true when the id is a registered custom item
     * @param priced     true when the id has a sell price ({@code null} = selling is off, prices are not checked)
     * @return one English line per problem, in id order (empty = all good)
     */
    public static List<String> check(Collection<String> referenced, Predicate<String> registered, Predicate<String> priced) {
        List<String> problems = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>(referenced);
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!registered.test(id)) {
                problems.add("drop item '" + id + "' is not registered (check drop-items in depths.yml / mobs.yml)");
                continue;
            }
            if (priced != null && !priced.test(id)) {
                problems.add("drop item '" + id + "' has no sell price (add custom-items.depths-*." + id + " to sell.yml)");
            }
        }
        return problems;
    }
}
