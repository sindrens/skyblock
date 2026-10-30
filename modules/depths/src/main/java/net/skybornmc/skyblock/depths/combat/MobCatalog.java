package net.skybornmc.skyblock.depths.combat;

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
 * The Depths mob catalog (M4.8) from {@code mobs.yml}: mob definitions ({@code mobs}), combat spawning per tier
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

    public static final MobCatalog EMPTY = new MobCatalog(Map.of(), Map.of(), Map.of());

    private final Map<String, MobDef> mobs;
    private final Map<Integer, ZoneSpawns> combat;
    private final Map<String, Map<Integer, AmbientSpawns>> ambient;

    private MobCatalog(Map<String, MobDef> mobs, Map<Integer, ZoneSpawns> combat, Map<String, Map<Integer, AmbientSpawns>> ambient) {
        this.mobs = Collections.unmodifiableMap(new LinkedHashMap<>(mobs));
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
        Map<String, MobDef> mobs = parseMobs(root.getConfigurationSection(MOBS), items, mobTypes, problem);
        Map<Integer, ZoneSpawns> combat = parseCombat(root.getConfigurationSection(COMBAT), mobs, problem);
        Map<String, Map<Integer, AmbientSpawns>> ambient = parseAmbient(root.getConfigurationSection(AMBIENT), mobs, problem);
        return new MobCatalog(mobs, combat, ambient);
    }

    static Map<String, MobDef> parseMobs(ConfigurationSection section, Set<String> items, Predicate<String> mobTypes,
                                         Consumer<String> problem) {
        Map<String, MobDef> out = new LinkedHashMap<>();
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            String id = key.trim().toLowerCase(Locale.ROOT);
            String where = MOBS + "." + key;
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null || !id.matches("[a-z0-9_]+")) {
                problem.accept(where + ": must be a section {type, health, ...} with an id of a-z, 0-9 and _, skipped");
                continue;
            }
            String type = s.getString("type", "").trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (!mobTypes.test(type)) {
                problem.accept(where + ".type: '" + type + "' is not a mob type that can be spawned, skipped");
                continue;
            }
            double health = range(s, "health", 0, 0, 1_000_000, where, problem);
            double damage = range(s, "damage", 0, 0, 10_000, where, problem);
            double speed = range(s, "speed", 0, 0, 2, where, problem);
            double scale = range(s, "scale", 1, 0.1, 8, where, problem);
            int size = (int) range(s, "size", 0, 0, 16, where, problem);
            double xp = range(s, "xp", 0, 0, 1_000_000, where, problem);
            int vanillaXp = (int) range(s, "vanilla-xp", 0, 0, 10_000, where, problem);
            double crystalChance = range(s, "crystal-chance", 0, 0, 1000, where, problem);
            int crystalAmount = (int) range(s, "crystal-amount", 1, 1, 1_000_000, where, problem);
            List<MobDef.Drop> drops = new ArrayList<>();
            List<Map<?, ?>> raw = s.getMapList("drops");
            for (int i = 0; i < raw.size(); i++) {
                MobDef.Drop drop = drop(raw.get(i), items, where + ".drops[" + i + "]", problem);
                if (drop != null) {
                    drops.add(drop);
                }
            }
            out.put(id, new MobDef(id, type, health, damage, speed, scale, size, xp, vanillaXp, crystalChance, crystalAmount,
                    drops, s.getBoolean("glowing", false), s.getBoolean("name-visible", false)));
        }
        return out;
    }

    private static MobDef.Drop drop(Map<?, ?> map, Set<String> items, String where, Consumer<String> problem) {
        Object itemRaw = map.get("item");
        String item = itemRaw == null ? "" : itemRaw.toString().trim().toLowerCase(Locale.ROOT);
        if (!items.contains(item)) {
            problem.accept(where + ".item: '" + item + "' is not a drop item (drop-items), skipped");
            return null;
        }
        int min = intOf(map.get("min"), map.get("amount") == null ? 1 : intOf(map.get("amount"), 1));
        int max = intOf(map.get("max"), min);
        double chance = doubleOf(map.get("chance"), 1.0);
        if (min < 0 || max < min || max > 1000) {
            problem.accept(where + ": min/max must be 0-1000 with max >= min, skipped");
            return null;
        }
        if (!(chance >= 0 && chance <= 1)) {
            problem.accept(where + ".chance: must be 0-1, skipped");
            return null;
        }
        return chance > 0 && max > 0 ? new MobDef.Drop(item, min, max, chance) : null;
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
        if (!s.contains(key)) {
            return def;
        }
        double v = s.getDouble(key, def);
        if (Double.isNaN(v) || v < min || v > max) {
            problem.accept(where + "." + key + ": must be " + fmt(min) + "-" + fmt(max) + ", using " + fmt(def));
            return def;
        }
        return v;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? Long.toString((long) v) : Double.toString(v);
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o == null ? def : Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double doubleOf(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return o == null ? def : Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
