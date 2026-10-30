package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.tools.ToolKind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One enchant from {@code enchants.yml} (ENCHANTS §6), validated by {@link EnchantRegistry}.
 *
 * @param id         snake_case id (also the message key {@code enchant.<id>.*} and the perk id on tools)
 * @param rarity     Common … Mythic (§2.3)
 * @param maxLevel   1-10
 * @param targets    tools/pieces it goes on
 * @param slotCost   1, or 2 for Dragonfire Blast / Wildfire (EN3)
 * @param conflicts  conflict groups (§2.5); Mythics always have {@link #MYTHIC_GROUP} as well
 * @param pools      book pools it drops from (empty for Mythics: special sources only)
 * @param minTier    lowest tier of the pool's wing it drops from ("From" column)
 * @param family     bonus family of its effect (§8.1), or null
 * @param passive    key in {@code values} that is a flat percent of {@code family}, reported to the BonusService; null
 *                   when the effect is a chance/proc that E2/E3 implement
 * @param values     per-level numbers by key, each list exactly {@code maxLevel} long
 * @param params     constant numbers by key (cooldowns, radius …)
 * @param pvpPercent strength in warzone/duels in percent of the normal value (0 = off, §8.3)
 * @param weight     weight inside its rarity when a pool picks an enchant (§3.1, default 1)
 * @param enabled    false = kill switch (EN19): stays on items, does nothing, can't be applied or rolled
 */
record EnchantDef(String id, Rarity rarity, int maxLevel, Set<EnchantTarget> targets, int slotCost, List<String> conflicts,
                  Set<Pool> pools, int minTier, BonusFamily family, String passive, Map<String, List<Double>> values,
                  Map<String, Double> params, double pvpPercent, double weight, boolean enabled) {

    static final String MYTHIC_GROUP = "mythic";

    EnchantDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rarity, "rarity");
        targets = Set.copyOf(targets);
        conflicts = List.copyOf(conflicts);
        pools = Set.copyOf(pools);
        Map<String, List<Double>> v = new LinkedHashMap<>();
        values.forEach((k, list) -> v.put(k, List.copyOf(list)));
        values = Collections.unmodifiableMap(v);
        params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /** Mythics fit only the prestige slot (EN2). */
    boolean mythic() {
        return rarity == Rarity.MYTHIC;
    }

    boolean fits(ToolKind kind) {
        return EnchantTarget.of(kind).map(targets::contains).orElse(false);
    }

    /** Only armor pieces (needs the wardrobe, E5). */
    boolean armorOnly() {
        return targets.stream().allMatch(EnchantTarget::armor);
    }

    /** True when both share a conflict group. Never true for the same enchant. */
    boolean conflictsWith(EnchantDef other) {
        if (other == null || other.id.equals(id)) {
            return false;
        }
        for (String group : conflicts) {
            if (other.conflicts.contains(group)) {
                return true;
            }
        }
        return false;
    }

    /** A per-level value ({@code values.<key>[level-1]}) or a constant ({@code params.<key>}); NaN if missing. */
    double value(String key, int level) {
        List<Double> list = values.get(key);
        if (list != null) {
            if (level < 1 || list.isEmpty()) {
                return Double.NaN;
            }
            return list.get(Math.min(level, list.size()) - 1);
        }
        Double constant = params.get(key);
        return constant == null ? Double.NaN : constant;
    }

    /** The passive family value at a level (0 when the enchant has none or is disabled). */
    double passiveValue(int level) {
        if (!enabled || passive == null || family == null) {
            return 0;
        }
        double v = value(passive, level);
        return Double.isFinite(v) ? v : 0;
    }

    /** The first per-level list (for the guide's "per level" line), or an empty list. */
    List<Double> mainValues() {
        if (passive != null && values.containsKey(passive)) {
            return values.get(passive);
        }
        return values.isEmpty() ? List.of() : values.values().iterator().next();
    }
}
