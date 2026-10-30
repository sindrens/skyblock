package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.item.Rarity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Book shop rolls and pool picks (ENCHANTS §3.1, §3.3, §3.5), pure and unit-tested with a seeded random.
 * A roll picks a rarity by weight, then an enchant of that rarity from the pool that the player's tier allows (equal
 * weights unless an enchant sets {@code weight}). If the rarity has no allowed enchant the pick falls back one rarity
 * at a time (Epic → Rare → …). Rolls never give Mythics.
 */
final class Rolls {

    private Rolls() {
    }

    /** {@code rare:50} → (RARE, 50); null when broken or Mythic/Special. */
    static Map.Entry<Rarity, Double> parseOdd(String raw) {
        if (raw == null) {
            return null;
        }
        int colon = raw.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        try {
            Rarity r = Rarity.valueOf(raw.substring(0, colon).trim().toUpperCase(Locale.ROOT));
            double w = Double.parseDouble(raw.substring(colon + 1).trim());
            if (r == Rarity.MYTHIC || r == Rarity.SPECIAL || !Double.isFinite(w) || w <= 0) {
                return null;
            }
            return Map.entry(r, w);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Map<Rarity, Double> parseOdds(List<String> raw) {
        Map<Rarity, Double> out = new EnumMap<>(Rarity.class);
        for (String s : raw) {
            Map.Entry<Rarity, Double> e = parseOdd(s);
            if (e != null) {
                out.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }
        return out;
    }

    /** A rarity by weight; empty when there are no odds. */
    static Optional<Rarity> rarity(Map<Rarity, Double> odds, RandomGenerator random) {
        double total = odds.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        double roll = random.nextDouble() * total;
        Rarity last = null;
        for (Map.Entry<Rarity, Double> e : odds.entrySet()) {
            last = e.getKey();
            roll -= e.getValue();
            if (roll < 0) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.ofNullable(last);
    }

    /** Enchants a pool can give at a tier and rarity (enabled, not Mythic, min tier reached). */
    static List<EnchantDef> candidates(EnchantRegistry registry, Pool pool, Rarity rarity, int tier) {
        List<EnchantDef> out = new ArrayList<>();
        for (EnchantDef d : registry.all()) {
            if (d.enabled() && !d.mythic() && d.rarity() == rarity && d.pools().contains(pool) && d.minTier() <= tier) {
                out.add(d);
            }
        }
        return out;
    }

    /** An enchant of {@code rarity} (or the next lower rarity that has one) from the pool, by weight. */
    static Optional<EnchantDef> pick(EnchantRegistry registry, Pool pool, Rarity rarity, int tier, RandomGenerator random) {
        for (int r = rarity.ordinal(); r >= 0; r--) {
            Rarity current = Rarity.values()[r];
            if (current == Rarity.MYTHIC || current == Rarity.SPECIAL) {
                continue;
            }
            List<EnchantDef> list = candidates(registry, pool, current, tier);
            if (!list.isEmpty()) {
                return Optional.of(weighted(list, random));
            }
        }
        return Optional.empty();
    }

    /** A full roll: rarity by the odds, then an enchant. */
    static Optional<EnchantDef> roll(EnchantRegistry registry, Pool pool, Map<Rarity, Double> odds, int tier, RandomGenerator random) {
        return rarity(odds, random).flatMap(r -> pick(registry, pool, r, tier, random));
    }

    private static EnchantDef weighted(List<EnchantDef> list, RandomGenerator random) {
        double total = list.stream().mapToDouble(EnchantDef::weight).sum();
        double roll = random.nextDouble() * total;
        for (EnchantDef d : list) {
            roll -= d.weight();
            if (roll < 0) {
                return d;
            }
        }
        return list.getLast();
    }

    /** The tier (1-based) a skill level opens: how many thresholds are at or below it (at least 1). */
    static int tier(List<Integer> thresholds, int skillLevel) {
        int count = 0;
        for (int t : thresholds) {
            if (skillLevel >= t) {
                count++;
            }
        }
        return Math.max(1, count);
    }
}
