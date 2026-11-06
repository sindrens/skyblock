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
 * ECON-SPAWNERS step 8 (pure): the custom drops of each spawner mob ({@code spawners.drops}: {@code mob:item:amount}) and the
 * amount math. Spawner mobs with drop lines drop only those items; the rest keep vanilla loot. Owner 7 Nov 2026: a mob may
 * have several lines (cow: 1 beef and 1 leather), one per item.
 */
final class SpawnerDrops {

    /** One mob's drop: item id and items per mob at 1.0x loot. */
    record Drop(String item, double perMob) {
    }

    static final SpawnerDrops EMPTY = new SpawnerDrops(Map.of());

    /**
     * Owner 3 Nov 2026 ("zombie rotten flesh, blaze blaze rods"): a drop can be a plain vanilla item ({@code zombie:rotten_flesh:1})
     * instead of a custom one; the island's loot upgrade and the payback math work the same. The vanilla material, or null.
     */
    static org.bukkit.Material vanilla(String item) {
        if (item == null || item.startsWith("mobdrop_")) {
            return null;
        }
        // Name lookup only (isItem()/isAir() need a running server's registries, so unit tests could not call them).
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(item);
        return m != null && m != org.bukkit.Material.AIR ? m : null;
    }

    /** The sell.yml key of a drop: the vanilla name, or {@code custom:<id>}. */
    static String priceKey(String item) {
        return vanilla(item) != null ? item.toLowerCase(java.util.Locale.ROOT) : "custom:" + item;
    }

    private final Map<String, List<Drop>> byMob;

    private SpawnerDrops(Map<String, List<Drop>> byMob) {
        Map<String, List<Drop>> copy = new LinkedHashMap<>();
        byMob.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.byMob = Collections.unmodifiableMap(copy);
    }

    /**
     * @param validMob  true for a mob type id
     * @param validItem true for a registered drop item id
     */
    static SpawnerDrops parse(List<String> lines, Predicate<String> validMob, Predicate<String> validItem, Consumer<String> problems) {
        Map<String, List<Drop>> out = new LinkedHashMap<>();
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
            } else if (out.getOrDefault(mob, List.of()).stream().anyMatch(d -> d.item().equals(item))) {
                problems.accept(where + " '" + mob + "' drops '" + item + "' already, skipped");
            } else {
                out.computeIfAbsent(mob, k -> new java.util.ArrayList<>()).add(new Drop(item, amount));
            }
        }
        return new SpawnerDrops(out);
    }

    /** Every drop of a mob, in file order (empty: no custom drops, the mob keeps vanilla loot). */
    List<Drop> of(String mob) {
        return byMob.getOrDefault(SpawnerTiers.normalize(mob), List.of());
    }

    /** The first drop of a mob (the shop lore names it). */
    Optional<Drop> first(String mob) {
        List<Drop> list = of(mob);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    Map<String, List<Drop>> all() {
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

    /** Owner 7 Nov 2026: the cooked form of raw meat (a mob that dies burning drops it cooked); other items stay. Pure. */
    static org.bukkit.Material cooked(org.bukkit.Material raw) {
        return switch (raw) {
            case BEEF -> org.bukkit.Material.COOKED_BEEF;
            case PORKCHOP -> org.bukkit.Material.COOKED_PORKCHOP;
            case CHICKEN -> org.bukkit.Material.COOKED_CHICKEN;
            case MUTTON -> org.bukkit.Material.COOKED_MUTTON;
            case RABBIT -> org.bukkit.Material.COOKED_RABBIT;
            case COD -> org.bukkit.Material.COOKED_COD;
            case SALMON -> org.bukkit.Material.COOKED_SALMON;
            default -> raw;
        };
    }
}
