package net.skybornmc.skyblock.islandtools;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The upgrades of the island tools (owner D4: 7 per tool, menu-only, paid with crystals; T7: crystals only, no tool level)
 * from {@code islandtools.yml upgrades} lines {@code kind:id:effect:max:base-cost:growth} (pure, unit-tested).
 */
final class Upgrades {

    /** What an upgrade does. ENCHANT uses {@code arg} = the vanilla enchantment id. */
    enum Effect {
        ENCHANT, HASTE, MAGNET, SMELT, DROPS, TIMBER, EXPERIENCE, LIFESTEAL, ROW, STORAGE, SHARDS, AUTOSELL;

        static Optional<Effect> of(String s) {
            try {
                return Optional.of(valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    /** One upgrade of one tool. */
    record Def(Kind kind, String id, Effect effect, String arg, int max, long baseCost, double growth) {

        /** Shards for going from {@code level} to {@code level + 1} (whole shards under 100, else rounded to 10). */
        long cost(int level) {
            double c = baseCost * Math.pow(growth, Math.max(0, level));
            return c < 100 ? Math.max(1, Math.round(c)) : Math.round(c / 10.0) * 10;
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

    /** Parses the lines; broken ones are reported and skipped. */
    static Upgrades parse(List<String> lines, Consumer<String> warn) {
        Map<Kind, List<Def>> out = new EnumMap<>(Kind.class);
        for (String line : lines) {
            String[] p = line.trim().split(":");
            if (p.length != 6) {
                warn.accept("islandtools.yml → upgrades: '" + line + "' must be kind:id:effect:max:base-cost:growth, skipped");
                continue;
            }
            Optional<Kind> kind = Kind.of(p[0]);
            String[] eff = p[2].split("=", 2);
            Optional<Effect> effect = Effect.of(eff[0]);
            try {
                int max = Integer.parseInt(p[3].trim());
                long base = Long.parseLong(p[4].trim());
                double growth = Double.parseDouble(p[5].trim());
                String id = p[1].trim().toLowerCase(Locale.ROOT);
                if (kind.isEmpty() || effect.isEmpty() || !id.matches("[a-z0-9_]{1,24}") || max < 1 || max > 20 || base < 1 || growth < 1) {
                    throw new IllegalArgumentException();
                }
                if (effect.get() == Effect.ENCHANT && eff.length < 2) {
                    throw new IllegalArgumentException();
                }
                List<Def> list = out.computeIfAbsent(kind.get(), k -> new ArrayList<>());
                if (list.stream().anyMatch(d -> d.id().equals(id))) {
                    warn.accept("islandtools.yml → upgrades: duplicate '" + id + "' for " + kind.get().id() + ", skipped");
                    continue;
                }
                list.add(new Def(kind.get(), id, effect.get(), eff.length > 1 ? eff[1].trim().toLowerCase(Locale.ROOT) : "", max, base, growth));
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
        levels.forEach((k, v) -> {
            if (v != null && v > 0) {
                sb.append(sb.isEmpty() ? "" : ",").append(k).append('=').append(v);
            }
        });
        return sb.toString();
    }
}
