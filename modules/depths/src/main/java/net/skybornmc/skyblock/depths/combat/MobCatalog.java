package net.skybornmc.skyblock.depths.combat;

import net.skybornmc.skyblock.common.mob.AbilitySpec;
import net.skybornmc.skyblock.common.mob.MobCatalogParser;
import net.skybornmc.skyblock.common.mob.MobDef;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The Depths mob catalog (M4.8) from {@code mobs.yml}: mob definitions ({@code mobs}, read by the shared
 * {@link MobCatalogParser} since M8.1 together with {@code drop-tables} and {@code ability-defaults}), combat spawning per tier
 * ({@code combat-zones}) and the per-player ambient mobs of mine and forest tiers ({@code ambient}). Immutable; broken
 * entries are reported and skipped, the rest still load. The file holds no plain settings (those are in
 * {@code depths.yml → combat.*, ambient.*}), so every key in these sections is a catalog entry.
 */
public final class MobCatalog {

    public static final String MOBS = "mobs";
    public static final String COMBAT = "combat-zones";
    public static final String AMBIENT = "ambient";
    /** Wings with ambient mobs. */
    public static final Set<String> AMBIENT_WINGS = Set.of("mine", "forest");

    public static final MobCatalog EMPTY = new MobCatalog(MobCatalogParser.Result.EMPTY, Map.of(), Map.of());

    private final MobCatalogParser.Result shared;
    private final Map<String, MobDef> mobs;
    private final Map<Integer, ZoneSpawns> combat;
    private final Map<String, Map<Integer, AmbientSpawns>> ambient;

    private MobCatalog(MobCatalogParser.Result shared, Map<Integer, ZoneSpawns> combat, Map<String, Map<Integer, AmbientSpawns>> ambient) {
        this.shared = shared;
        this.mobs = shared.mobs();
        this.combat = Collections.unmodifiableMap(new LinkedHashMap<>(combat));
        Map<String, Map<Integer, AmbientSpawns>> a = new LinkedHashMap<>();
        ambient.forEach((k, v) -> a.put(k, Collections.unmodifiableMap(new LinkedHashMap<>(v))));
        this.ambient = Collections.unmodifiableMap(a);
    }

    public MobDef mob(String id) {
        return id == null ? null : mobs.get(id);
    }

    public Map<String, MobDef> mobs() {
        return mobs;
    }

    /** M8.1: shared drop tables ({@code drop-tables}), already merged into each mob's drops. */
    public Map<String, java.util.List<MobDef.Drop>> dropTables() {
        return shared.dropTables();
    }

    /** M8.1: a mob's ability with unset values from {@code ability-defaults} (then the code defaults). */
    public AbilitySpec resolve(AbilitySpec spec) {
        return shared.resolve(spec);
    }

    /** Spawning of a combat tier, or {@code null} (that zone stays empty). */
    public ZoneSpawns combat(int tier) {
        return combat.get(tier);
    }

    public Map<Integer, ZoneSpawns> combat() {
        return combat;
    }

    /** Ambient mobs of a mine/forest tier, or {@code null}. */
    public AmbientSpawns ambient(String wing, int tier) {
        Map<Integer, AmbientSpawns> byTier = ambient.get(wing);
        return byTier == null ? null : byTier.get(tier);
    }

    public Map<String, Map<Integer, AmbientSpawns>> ambient() {
        return ambient;
    }

    /**
     * Reads the three sections of {@code mobs.yml}.
     *
     * @param root      the whole file
     * @param items     drop item ids that exist (drops of other items are skipped)
     * @param mobTypes  true for entity type ids that can be spawned as a mob
     */
    public static MobCatalog parse(ConfigurationSection root, Set<String> items, Predicate<String> mobTypes, Consumer<String> problem) {
        if (root == null) {
            return EMPTY;
        }
        MobCatalogParser.Result shared = MobCatalogParser.parse(root, items, mobTypes, problem);
        Map<String, MobDef> mobs = shared.mobs();
        Map<Integer, ZoneSpawns> combat = parseCombat(root.getConfigurationSection(COMBAT), mobs, problem);
        Map<String, Map<Integer, AmbientSpawns>> ambient = parseAmbient(root.getConfigurationSection(AMBIENT), mobs, problem);
        return new MobCatalog(shared, combat, ambient);
    }

    static Map<Integer, ZoneSpawns> parseCombat(ConfigurationSection section, Map<String, MobDef> mobs, Consumer<String> problem) {
        Map<Integer, ZoneSpawns> out = new LinkedHashMap<>();
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            String where = COMBAT + "." + key;
            Integer tier = tier(key, where, problem);
            ConfigurationSection s = tier == null ? null : section.getConfigurationSection(key);
            if (tier == null) {
                continue;
            }
            if (s == null) {
                problem.accept(where + ": must be a section {mobs, base, per-player, max, mini-boss}, skipped");
                continue;
            }
            Map<String, Integer> weights = weights(s.getConfigurationSection("mobs"), mobs, where, problem);
            if (weights.isEmpty()) {
                problem.accept(where + ".mobs: no valid mob, the zone stays empty");
                continue;
            }
            int base = (int) range(s, "base", 8, 0, 500, where, problem);
            int perPlayer = (int) range(s, "per-player", 2, 0, 100, where, problem);
            int max = (int) range(s, "max", 30, 0, 500, where, problem);
            String boss = s.getString("mini-boss", "").trim().toLowerCase(Locale.ROOT);
            int minutes = (int) range(s, "mini-boss-minutes", 10, 0, 1440, where, problem);
            if (boss.isEmpty()) {
                boss = null;
            } else if (!mobs.containsKey(boss)) {
                problem.accept(where + ".mini-boss: '" + boss + "' is not in mobs, no mini-boss here");
                boss = null;
            }
            out.put(tier, new ZoneSpawns(tier, weights, base, perPlayer, max, boss, minutes));
        }
        return out;
    }

    static Map<String, Map<Integer, AmbientSpawns>> parseAmbient(ConfigurationSection section, Map<String, MobDef> mobs,
                                                                Consumer<String> problem) {
        Map<String, Map<Integer, AmbientSpawns>> out = new LinkedHashMap<>();
        if (section == null) {
            return out;
        }
        for (String wingKey : section.getKeys(false)) {
            String wing = wingKey.trim().toLowerCase(Locale.ROOT);
            ConfigurationSection w = section.getConfigurationSection(wingKey);
            if (!AMBIENT_WINGS.contains(wing) || w == null) {
                problem.accept(AMBIENT + "." + wingKey + ": must be mine or forest with tiers inside, skipped");
                continue;
            }
            Map<Integer, AmbientSpawns> byTier = new LinkedHashMap<>();
            for (String key : w.getKeys(false)) {
                String where = AMBIENT + "." + wingKey + "." + key;
                Integer tier = tier(key, where, problem);
                if (tier == null) {
                    continue;
                }
                ConfigurationSection t = w.getConfigurationSection(key);
                Map<String, Integer> weights = weights(t == null ? null : t.getConfigurationSection("mobs"), mobs, where, problem);
                if (weights.isEmpty()) {
                    problem.accept(where + ".mobs: no valid mob, skipped");
                    continue;
                }
                byTier.put(tier, new AmbientSpawns(wing, tier, weights));
            }
            if (!byTier.isEmpty()) {
                out.put(wing, byTier);
            }
        }
        return out;
    }

    private static Map<String, Integer> weights(ConfigurationSection s, Map<String, MobDef> mobs, String where, Consumer<String> problem) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (s == null) {
            return out;
        }
        for (String key : s.getKeys(false)) {
            String id = key.trim().toLowerCase(Locale.ROOT);
            int weight = s.getInt(key, 0);
            if (!mobs.containsKey(id)) {
                problem.accept(where + ".mobs." + key + ": not in mobs, skipped");
            } else if (weight < 1 || weight > 10_000) {
                problem.accept(where + ".mobs." + key + ": weight must be 1-10000, skipped");
            } else {
                out.put(id, weight);
            }
        }
        return out;
    }

    private static Integer tier(String key, String where, Consumer<String> problem) {
        try {
            int tier = Integer.parseInt(key.trim());
            if (tier >= 1) {
                return tier;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        problem.accept(where + ": the key must be a tier number (1 or more), skipped");
        return null;
    }

    private static double range(ConfigurationSection s, String key, double def, double min, double max, String where,
                                Consumer<String> problem) {
        return MobCatalogParser.range(s, key, def, min, max, where, problem);
    }
}
