package net.skybornmc.skyblock.depths.drops;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Depths drop tables (M4.9) from the {@code drops} section of {@code depths.yml}: per wing ({@code mine},
 * {@code forest}) and tier the drop item, its amount, the crystal chance and the rare finds. Immutable; lookups are O(1).
 */
public final class DropTable {

    /** One rare find of a tier: {@code chance} per block (before rare-find bonuses). */
    public record RareDrop(String item, double chance) {
    }

    /**
     * What one block/log of a tier gives.
     *
     * @param item          drop item id (custom item, also the sell.yml key and the Forge material)
     * @param amount        drops per block before fortune
     * @param crystalChance chance per block of a crystal hit (before crystal bonuses)
     * @param crystalAmount crystals per hit
     * @param rare          rare finds (may be empty)
     */
    public record TierDrops(String item, int amount, double crystalChance, int crystalAmount, List<RareDrop> rare) {
        public TierDrops {
            rare = List.copyOf(rare);
        }

        /** Average crystals per block before bonuses. */
        public double crystalsPerBlock() {
            return crystalChance * crystalAmount;
        }
    }

    public static final DropTable EMPTY = new DropTable(Map.of());

    private final Map<String, Map<Integer, TierDrops>> tiers;

    private DropTable(Map<String, Map<Integer, TierDrops>> tiers) {
        this.tiers = tiers;
    }

    /** The drops of a wing and tier, or {@code null} when the table has none (that zone gives nothing). */
    public TierDrops get(String wing, int tier) {
        Map<Integer, TierDrops> byTier = tiers.get(wing);
        return byTier == null ? null : byTier.get(tier);
    }

    /** Wings with at least one tier. */
    public Set<String> wings() {
        return Collections.unmodifiableSet(tiers.keySet());
    }

    /** Tiers of a wing, in file order. */
    public Map<Integer, TierDrops> tiers(String wing) {
        return Collections.unmodifiableMap(tiers.getOrDefault(wing, Map.of()));
    }

    /** Every item id a table entry can give (drop items and rare finds). */
    public Set<String> items() {
        Set<String> out = new java.util.LinkedHashSet<>();
        tiers.values().forEach(m -> m.values().forEach(t -> {
            out.add(t.item());
            t.rare().forEach(r -> out.add(r.item()));
        }));
        return out;
    }

    /**
     * Reads the {@code drops} section. Broken entries are reported and skipped; the rest still load.
     *
     * @param known drop item ids that exist (entries with other items are skipped)
     */
    /** Plain settings ({@code DepthsConfig}) that live inside the {@code drops} section; they are not wings. */
    static final Set<String> SETTING_KEYS = Set.of("max-per-block", "rare-sound");

    public static DropTable parse(ConfigurationSection section, Set<String> known, Consumer<String> problem) {
        if (section == null) {
            return EMPTY;
        }
        Map<String, Map<Integer, TierDrops>> out = new LinkedHashMap<>();
        for (String wingKey : section.getKeys(false)) {
            String wing = wingKey.toLowerCase(Locale.ROOT);
            if (SETTING_KEYS.contains(wing)) {
                continue; // plain settings (DepthsConfig) that share the drops section
            }
            ConfigurationSection w = section.getConfigurationSection(wingKey);
            if (w == null) {
                problem.accept("drops." + wingKey + ": must be a section of tiers, skipped");
                continue;
            }
            Map<Integer, TierDrops> byTier = new LinkedHashMap<>();
            for (String tierKey : w.getKeys(false)) {
                String where = "drops." + wingKey + "." + tierKey;
                int tier;
                try {
                    tier = Integer.parseInt(tierKey.trim());
                } catch (NumberFormatException e) {
                    problem.accept(where + ": the key must be a tier number, skipped");
                    continue;
                }
                ConfigurationSection t = w.getConfigurationSection(tierKey);
                if (t == null || tier < 1) {
                    problem.accept(where + ": must be a section with item, crystal-chance ..., skipped");
                    continue;
                }
                String item = t.getString("item", "").trim().toLowerCase(Locale.ROOT);
                if (!known.contains(item)) {
                    problem.accept(where + ".item: '" + item + "' is not in drop-items, skipped");
                    continue;
                }
                int amount = t.getInt("amount", 1);
                double chance = t.getDouble("crystal-chance", 0);
                int crystals = t.getInt("crystal-amount", 1);
                if (amount < 1 || amount > 64) {
                    problem.accept(where + ".amount: must be 1-64, using 1");
                    amount = 1;
                }
                if (chance < 0 || chance > 64 || Double.isNaN(chance)) {
                    problem.accept(where + ".crystal-chance: must be 0-64 (above 1 = several hits), using 0");
                    chance = 0;
                }
                if (crystals < 1 || crystals > 1_000_000) {
                    problem.accept(where + ".crystal-amount: must be 1-1000000, using 1");
                    crystals = 1;
                }
                List<RareDrop> rare = new ArrayList<>();
                ConfigurationSection r = t.getConfigurationSection("rare");
                if (r != null) {
                    for (String rareKey : r.getKeys(false)) {
                        String rareItem = rareKey.trim().toLowerCase(Locale.ROOT);
                        double p = r.getDouble(rareKey, 0);
                        if (!known.contains(rareItem)) {
                            problem.accept(where + ".rare." + rareKey + ": not in drop-items, skipped");
                        } else if (p < 0 || p > 1 || Double.isNaN(p)) {
                            problem.accept(where + ".rare." + rareKey + ": chance must be 0-1, skipped");
                        } else if (p > 0) {
                            rare.add(new RareDrop(rareItem, p));
                        }
                    }
                }
                byTier.put(tier, new TierDrops(item, amount, chance, crystals, rare));
            }
            if (!byTier.isEmpty()) {
                out.put(wing, byTier);
            }
        }
        Map<String, Map<Integer, TierDrops>> frozen = new HashMap<>();
        out.forEach((k, v) -> frozen.put(k, Collections.unmodifiableMap(new LinkedHashMap<>(v))));
        return new DropTable(Collections.unmodifiableMap(frozen));
    }
}
