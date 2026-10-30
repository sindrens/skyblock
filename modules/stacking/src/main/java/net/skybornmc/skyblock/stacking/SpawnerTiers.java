package net.skybornmc.skyblock.stacking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Spawner types and their tier (owner SK3–SK8), read from {@code stacking.yml → spawners.tier-1..5}. Types are entity
 * ids ({@code zombie}, {@code iron_golem}). The never-allowed types (SK8) are refused even if the config lists them.
 */
final class SpawnerTiers {

    /** Owner SK8: never as spawners. */
    static final Set<String> NEVER = Set.of("villager", "warden", "wither", "ender_dragon", "elder_guardian", "allay");

    static final int TIERS = 5;

    private final Map<String, Integer> tiers;

    private SpawnerTiers(Map<String, Integer> tiers) {
        this.tiers = Collections.unmodifiableMap(tiers);
    }

    /**
     * @param lists    tier 1..5 lists in order
     * @param valid    true if an id is a spawnable living entity type (checked against the server registry at runtime)
     * @param problems receives one line per skipped entry
     */
    static SpawnerTiers load(List<List<String>> lists, Predicate<String> valid, java.util.function.Consumer<String> problems) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < lists.size() && i < TIERS; i++) {
            for (String raw : lists.get(i)) {
                String id = normalize(raw);
                String where = "stacking.yml → spawners.tier-" + (i + 1) + ": '" + raw + "'";
                if (id.isEmpty()) {
                    continue;
                }
                if (NEVER.contains(id)) {
                    problems.accept(where + " can never be a spawner (owner SK8), skipped");
                } else if (!valid.test(id)) {
                    problems.accept(where + " is not a mob type, skipped");
                } else if (out.containsKey(id)) {
                    problems.accept(where + " is already in tier " + out.get(id) + ", skipped");
                } else {
                    out.put(id, i + 1);
                }
            }
        }
        return new SpawnerTiers(out);
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    Optional<Integer> tier(String type) {
        return Optional.ofNullable(tiers.get(normalize(type)));
    }

    boolean allowed(String type) {
        return tiers.containsKey(normalize(type));
    }

    /** Every type, tier 1 first. */
    List<String> types() {
        return new ArrayList<>(tiers.keySet());
    }

    /** A strong drop of a spawner mob with its own chance per mob (owner SK10). */
    record RareDrop(String mob, String item, double chance) {
    }

    /**
     * Parses {@code mob:item:chance} lines ({@code spawners.rare-drops}); bad lines are reported and skipped.
     *
     * @param validMob  true for a mob type id
     * @param validItem true for an item id
     */
    static List<RareDrop> rareDrops(List<String> lines, Predicate<String> validMob, Predicate<String> validItem,
                                    java.util.function.Consumer<String> problems) {
        List<RareDrop> out = new ArrayList<>();
        for (String line : lines) {
            String where = "stacking.yml → spawners.rare-drops: '" + line + "'";
            String[] parts = line == null ? new String[0] : line.split(":");
            if (parts.length != 3) {
                problems.accept(where + " needs the form mob:item:chance, skipped");
                continue;
            }
            String mob = normalize(parts[0]);
            String item = normalize(parts[1]);
            double chance;
            try {
                chance = Double.parseDouble(parts[2].trim());
            } catch (NumberFormatException e) {
                problems.accept(where + " has no valid chance (0-1), skipped");
                continue;
            }
            if (chance < 0 || chance > 1 || Double.isNaN(chance)) {
                problems.accept(where + " chance must be between 0 and 1, skipped");
            } else if (!validMob.test(mob)) {
                problems.accept(where + " '" + mob + "' is not a mob type, skipped");
            } else if (!validItem.test(item)) {
                problems.accept(where + " '" + item + "' is not an item, skipped");
            } else {
                out.add(new RareDrop(mob, item, chance));
            }
        }
        return List.copyOf(out);
    }
}
