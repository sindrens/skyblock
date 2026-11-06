package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.util.NiceNumbers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The upgrades (perks) of the island hoe and pickaxe, from {@code islandtools.yml upgrades} lines
 * {@code kind:id:effect:max:unlock-level:costs[:prestige]} (owner 4 Nov 2026; pure, unit-tested):
 * <ul>
 *   <li>{@code effect}: what a level does ({@link Effect}); {@code enchant=<vanilla id>} for enchant-like perks;</li>
 *   <li>{@code max}: levels (long tracks 100 to 1,000; enchant-like perks their vanilla levels; one-time unlocks 1);</li>
 *   <li>{@code unlock-level}: the tool level that unlocks the perk (owner: perks spread over the levels 1 to 50);</li>
 *   <li>{@code costs}: {@code base+step} = a long track, {@code cost(L) = base + step × L^exponent} nice-rounded, or
 *       {@code a/b/c} = the price of each level (the last repeats);</li>
 *   <li>{@code prestige}: optional, the tool prestige needed as well (Sweep and Vein: 1).</li>
 * </ul>
 */
final class Upgrades {

    /** What an upgrade does. ENCHANT uses {@code arg} = the vanilla enchantment id. */
    enum Effect {
        ENCHANT, HASTE, SMELT, DROPS, STORAGE, SHARDS, AUTOSELL, GROWTH, SWEEP, WISDOM, SELL, LUCKY;

        static Optional<Effect> of(String s) {
            try {
                return Optional.of(valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    /** One upgrade of one tool. {@code prices} is empty for a long track (then {@code base}/{@code step} apply). */
    record Def(Kind kind, String id, Effect effect, String arg, int max, int unlock, int prestige, long base, double step,
               List<Long> prices) {

        boolean track() {
            return prices.isEmpty();
        }

        /**
         * Shards for level {@code level} (1 = the first level), before the prestige discount: a long track
         * {@code base + step × level^exponent}, else the listed price (the last repeats).
         */
        double rawCost(int level, double exponent) {
            int l = Math.max(1, level);
            if (track()) {
                return base + step * Math.pow(l, exponent);
            }
            return prices.get(Math.min(prices.size(), l) - 1);
        }

        /** The nice-rounded price of level {@code level} with the prestige price factor (1 = full price). */
        long cost(int level, double exponent, double factor) {
            return Math.max(1, NiceNumbers.nice(rawCost(level, exponent) * Math.max(0, factor)));
        }

        /** How many of {@code wanted} levels can still be bought from {@code have}. */
        int buyable(int have, int wanted) {
            return Math.max(0, Math.min(wanted, max - have));
        }

        /** The summed price of the next {@code count} levels after {@code have} (x1 / x10 / x100 buttons). */
        long sumCost(int have, int count, double exponent, double factor) {
            long sum = 0;
            for (int i = 1; i <= buyable(have, count); i++) {
                sum = Math.addExact(sum, cost(have + i, exponent, factor));
            }
            return sum;
        }
    }

    private final Map<Kind, List<Def>> byKind;

    private Upgrades(Map<Kind, List<Def>> byKind) {
        this.byKind = byKind;
    }

    List<Def> of(Kind kind) {
        return byKind.getOrDefault(kind, List.of());
    }

    Optional<Def> get(Kind kind, String id) {
        return of(kind).stream().filter(d -> d.id().equals(id)).findFirst();
    }

    /** The first upgrade of {@code kind} with this effect. */
    Optional<Def> byEffect(Kind kind, Effect effect) {
        return of(kind).stream().filter(d -> d.effect() == effect).findFirst();
    }

    /** Parses the lines; broken ones are reported and skipped. */
    static Upgrades parse(List<String> lines, Consumer<String> warn) {
        Map<Kind, List<Def>> out = new EnumMap<>(Kind.class);
        for (String line : lines) {
            String[] p = line.trim().split(":");
            if (p.length != 6 && p.length != 7) {
                warn.accept("islandtools.yml → upgrades: '" + line + "' must be kind:id:effect:max:unlock-level:costs[:prestige], skipped");
                continue;
            }
            Optional<Kind> kind = Kind.of(p[0].trim());
            String[] eff = p[2].split("=", 2);
            Optional<Effect> effect = Effect.of(eff[0]);
            try {
                String id = p[1].trim().toLowerCase(Locale.ROOT);
                int max = Integer.parseInt(p[3].trim());
                int unlock = Integer.parseInt(p[4].trim());
                int prestige = p.length == 7 ? Integer.parseInt(p[6].trim()) : 0;
                String costs = p[5].trim();
                long base = 0;
                double step = 0;
                List<Long> prices = new ArrayList<>();
                if (costs.contains("+")) {
                    String[] bs = costs.split("\\+", 2);
                    base = Long.parseLong(bs[0].trim());
                    step = Double.parseDouble(bs[1].trim());
                    if (base < 1 || step < 0) {
                        throw new IllegalArgumentException();
                    }
                } else {
                    for (String c : costs.split("/")) {
                        long v = Long.parseLong(c.trim().replace("_", ""));
                        if (v < 1) {
                            throw new IllegalArgumentException();
                        }
                        prices.add(v);
                    }
                }
                if (kind.isEmpty() || effect.isEmpty() || !id.matches("[a-z0-9_]{1,24}") || max < 1 || max > 10_000 || unlock < 0
                        || prestige < 0 || (effect.get() == Effect.ENCHANT && eff.length < 2)) {
                    throw new IllegalArgumentException();
                }
                List<Def> list = out.computeIfAbsent(kind.get(), k -> new ArrayList<>());
                if (list.stream().anyMatch(d -> d.id().equals(id))) {
                    warn.accept("islandtools.yml → upgrades: duplicate '" + id + "' for " + kind.get().id() + ", skipped");
                    continue;
                }
                list.add(new Def(kind.get(), id, effect.get(), eff.length > 1 ? eff[1].trim().toLowerCase(Locale.ROOT) : "", max,
                        unlock, prestige, base, step, List.copyOf(prices)));
            } catch (IllegalArgumentException e) {
                warn.accept("islandtools.yml → upgrades: '" + line + "' has a bad kind, effect or number, skipped");
            }
        }
        Map<Kind, List<Def>> frozen = new EnumMap<>(Kind.class);
        out.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return new Upgrades(frozen);
    }

    /** {@code eff=3,fortune=1} → map (unknown ids kept, so a removed upgrade comes back if re-added). */
    static Map<String, Integer> decode(String s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (s == null || s.isBlank()) {
            return out;
        }
        for (String part : s.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                try {
                    int v = Integer.parseInt(kv[1].trim());
                    if (v > 0) {
                        out.put(kv[0].trim(), v);
                    }
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        return out;
    }

    static String encode(Map<String, Integer> levels) {
        StringBuilder sb = new StringBuilder();
        new java.util.TreeMap<>(levels).forEach((k, v) -> {
            if (v != null && v > 0) {
                sb.append(sb.isEmpty() ? "" : ",").append(k).append('=').append(v);
            }
        });
        return sb.toString();
    }
}
