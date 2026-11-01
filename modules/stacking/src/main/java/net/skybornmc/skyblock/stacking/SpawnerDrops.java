package net.skybornmc.skyblock.stacking;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/**
 * ECON-SPAWNERS step 8 (pure): the custom drop of each spawner mob ({@code spawners.drops}: {@code mob:item:amount}) and the
 * amount math. Spawner mobs with a drop line drop only that item; the rest keep vanilla loot.
 */
final class SpawnerDrops {

    /** One mob's drop: item id and items per mob at 1.0x loot. */
    record Drop(String item, double perMob) {
    }

    static final SpawnerDrops EMPTY = new SpawnerDrops(Map.of());

    private final Map<String, Drop> byMob;

    private SpawnerDrops(Map<String, Drop> byMob) {
        this.byMob = Collections.unmodifiableMap(byMob);
    }

    /**
     * @param validMob  true for a mob type id
     * @param validItem true for a registered drop item id
     */
    static SpawnerDrops parse(List<String> lines, Predicate<String> validMob, Predicate<String> validItem, Consumer<String> problems) {
        Map<String, Drop> out = new LinkedHashMap<>();
        for (String line : lines) {
            String where = "stacking.yml → spawners.drops: '" + line + "'";
            String[] parts = line == null ? new String[0] : line.split(":");
            if (parts.length != 3) {
                problems.accept(where + " needs the form mob:item:amount, skipped");
                continue;
            }
            String mob = SpawnerTiers.normalize(parts[0]);
            String item = parts[1].trim().toLowerCase(java.util.Locale.ROOT);
            double amount;
            try {
                amount = Double.parseDouble(parts[2].trim());
            } catch (NumberFormatException e) {
                problems.accept(where + " has no valid amount, skipped");
                continue;
            }
            if (!(amount > 0) || amount > 64) {
                problems.accept(where + " amount must be above 0 and at most 64, skipped");
            } else if (!validMob.test(mob)) {
                problems.accept(where + " '" + mob + "' is not a mob type, skipped");
            } else if (!validItem.test(item)) {
                problems.accept(where + " '" + item + "' is not in spawners.drop-items, skipped");
            } else if (out.containsKey(mob)) {
                problems.accept(where + " '" + mob + "' has a drop already, skipped");
            } else {
                out.put(mob, new Drop(item, amount));
            }
        }
        return new SpawnerDrops(out);
    }

    Optional<Drop> of(String mob) {
        return Optional.ofNullable(byMob.get(SpawnerTiers.normalize(mob)));
    }

    Map<String, Drop> all() {
        return byMob;
    }

    /**
     * Items for {@code killed} mobs plus {@code rolls} extra loot rolls (looting-style bonuses) at {@code loot}x: the
     * whole part, plus one more with the chance of the fraction (expected value exact). Never negative.
     */
    static long amount(long killed, long rolls, double perMob, double loot, DoubleSupplier random) {
        double expected = Math.max(0, killed + Math.max(0, rolls)) * Math.max(0, perMob) * Math.max(0, loot);
        long whole = (long) Math.floor(expected);
        double fraction = expected - whole;
        return whole + (fraction > 0 && random.getAsDouble() < fraction ? 1 : 0);
    }
}
