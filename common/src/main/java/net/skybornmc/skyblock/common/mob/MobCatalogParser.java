package net.skybornmc.skyblock.common.mob;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Reads the shared parts of a mob catalog file (M8.1): {@code mobs} (definitions with the M4.8 fields and the optional
 * M8.1 stats), {@code drop-tables} (shared drop rolls, a list catalog) and {@code ability-defaults} (per-ability tuning).
 * Pure: no server state, only a {@link ConfigurationSection}. Broken entries and values are reported through
 * {@code problem} and skipped (or fall back to their default); the rest still loads. Every M8.1 key is optional, so a
 * catalog written for M4.8 reads exactly as before and reports nothing new.
 */
public final class MobCatalogParser {

    public static final String MOBS = "mobs";
    public static final String DROP_TABLES = "drop-tables";
    public static final String ABILITY_DEFAULTS = "ability-defaults";

    /** Keys of an ability entry that are not params. */
    private static final Set<String> ABILITY_KEYS = Set.of("id", "cooldown", "chance", "params");

    /**
     * What a catalog file holds besides its module-specific sections.
     *
     * @param mobs       mob id → definition (insertion order)
     * @param dropTables table id → drops (insertion order)
     * @param defaults   per-ability defaults from the file (missing abilities use the code defaults)
     */
    public record Result(Map<String, MobDef> mobs, Map<String, List<MobDef.Drop>> dropTables, Map<AbilityId, AbilitySpec> defaults) {

        public static final Result EMPTY = new Result(Map.of(), Map.of(), Map.of());

        public Result {
            mobs = Collections.unmodifiableMap(new LinkedHashMap<>(mobs));
            Map<String, List<MobDef.Drop>> t = new LinkedHashMap<>();
            dropTables.forEach((k, v) -> t.put(k, List.copyOf(v)));
            dropTables = Collections.unmodifiableMap(t);
            defaults = defaults.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(defaults));
        }

        /** A mob's ability with every unset value filled from the file's defaults (code defaults stay for the rest). */
        public AbilitySpec resolve(AbilitySpec spec) {
            return spec == null ? null : spec.withDefaults(defaults.get(spec.id()));
        }
    }

    private MobCatalogParser() {
    }

    /**
     * Reads {@code ability-defaults}, {@code drop-tables} and {@code mobs} from the whole file.
     *
     * @param items    drop item ids that exist (drops of other items are skipped)
     * @param mobTypes true for entity type ids that can be spawned as a mob
     */
    public static Result parse(ConfigurationSection root, Set<String> items, Predicate<String> mobTypes, Consumer<String> problem) {
        if (root == null) {
            return Result.EMPTY;
        }
        Map<AbilityId, AbilitySpec> defaults = abilityDefaults(root.getConfigurationSection(ABILITY_DEFAULTS), problem);
        Map<String, List<MobDef.Drop>> tables = root.contains(DROP_TABLES)
                ? dropTables(root.getList(DROP_TABLES), items, problem) : Map.of();
        Map<String, MobDef> mobs = mobs(root.getConfigurationSection(MOBS), tables, items, mobTypes, problem);
        return new Result(mobs, tables, defaults);
    }

    // ------------------------------------------------------------------ mobs

    public static Map<String, MobDef> mobs(ConfigurationSection section, Map<String, List<MobDef.Drop>> tables, Set<String> items,
                                           Predicate<String> mobTypes, Consumer<String> problem) {
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
            String table = null;
            if (s.contains("drop-table")) {
                String t = s.getString("drop-table", "").trim().toLowerCase(Locale.ROOT);
                if (tables.containsKey(t)) {
                    table = t;
                    drops.addAll(tables.get(t));
                } else {
                    problem.accept(where + ".drop-table: '" + t + "' is not in " + DROP_TABLES + ", only the inline drops are used");
                }
            }
            List<Map<?, ?>> raw = s.getMapList("drops");
            for (int i = 0; i < raw.size(); i++) {
                MobDef.Drop drop = drop(raw.get(i), items, where + ".drops[" + i + "]", problem);
                if (drop != null) {
                    drops.add(drop);
                }
            }
            MobStats stats = stats(s, table, where, problem);
            out.put(id, new MobDef(id, type, health, damage, speed, scale, size, xp, vanillaXp, crystalChance, crystalAmount,
                    drops, s.getBoolean("glowing", false), s.getBoolean("name-visible", false), stats));
        }
        return out;
    }

    /** The optional M8.1 stats; {@link MobStats#NONE} when none is set. */
    static MobStats stats(ConfigurationSection s, String table, String where, Consumer<String> problem) {
        boolean any = table != null;
        for (String k : List.of("armor", "knockback-resistance", "attack-cooldown", "follow-range", "abilities", "elite-chance")) {
            any |= s.contains(k);
        }
        if (!any) {
            return MobStats.NONE;
        }
        double armor = 0;
        if (s.contains("armor")) {
            double a = s.getDouble("armor", Double.NaN);
            if (Double.isNaN(a) || a < 0) {
                problem.accept(where + ".armor: must be 0-" + fmt(MobStats.MAX_ARMOR) + " (percent), using 0");
            } else if (a > MobStats.MAX_ARMOR) {
                problem.accept(where + ".armor: at most " + fmt(MobStats.MAX_ARMOR) + "%, using " + fmt(MobStats.MAX_ARMOR));
                armor = MobStats.MAX_ARMOR;
            } else {
                armor = a;
            }
        }
        double knockback = range(s, "knockback-resistance", -1, 0, 1, where, problem);
        int cooldown = (int) range(s, "attack-cooldown", 0, 0, 200, where, problem);
        double follow = range(s, "follow-range", 0, 0, 128, where, problem);
        double elite = range(s, "elite-chance", 0, 0, 1, where, problem);
        List<AbilitySpec> abilities = abilities(s.getList("abilities"), where + ".abilities", problem);
        return new MobStats(armor, knockback, cooldown, follow, abilities, table, elite);
    }

    // ------------------------------------------------------------------ abilities

    /** A mob's {@code abilities} list: entries are maps {@code {id, cooldown, chance, params...}} or a bare id. */
    public static List<AbilitySpec> abilities(List<?> raw, String where, Consumer<String> problem) {
        List<AbilitySpec> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        Set<AbilityId> seen = java.util.EnumSet.noneOf(AbilityId.class);
        for (int i = 0; i < raw.size(); i++) {
            String at = where + "[" + i + "]";
            Object entry = raw.get(i);
            Map<?, ?> map = entry instanceof Map<?, ?> m ? m : entry instanceof String str ? Map.of("id", str) : null;
            if (map == null) {
                problem.accept(at + ": must be {id, cooldown, chance, ...} or an ability id, skipped");
                continue;
            }
            Object idRaw = map.get("id");
            AbilityId id = AbilityId.of(idRaw == null ? null : idRaw.toString());
            if (id == null) {
                problem.accept(at + ".id: '" + idRaw + "' is not an ability (" + String.join(", ", AbilityId.ids()) + "), skipped");
                continue;
            }
            if (!seen.add(id)) {
                problem.accept(at + ": " + id.id() + " is listed twice, skipped");
                continue;
            }
            if (out.size() >= MobStats.MAX_ABILITIES) {
                problem.accept(at + ": at most " + MobStats.MAX_ABILITIES + " abilities per mob, skipped");
                continue;
            }
            out.add(ability(id, map, at, problem));
        }
        return out;
    }

    /** {@code ability-defaults}: a map {@code <ability id>: {cooldown, chance, params...}}. */
    public static Map<AbilityId, AbilitySpec> abilityDefaults(ConfigurationSection section, Consumer<String> problem) {
        Map<AbilityId, AbilitySpec> out = new EnumMap<>(AbilityId.class);
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            String at = ABILITY_DEFAULTS + "." + key;
            AbilityId id = AbilityId.of(key);
            ConfigurationSection s = section.getConfigurationSection(key);
            if (id == null || s == null) {
                problem.accept(at + ": must be an ability (" + String.join(", ", AbilityId.ids()) + ") with {cooldown, chance, ...}, skipped");
                continue;
            }
            out.put(id, ability(id, s.getValues(false), at, problem));
        }
        return out;
    }

    static AbilitySpec ability(AbilityId id, Map<?, ?> map, String at, Consumer<String> problem) {
        double cooldown = Double.NaN;
        if (map.containsKey("cooldown") && !id.healthTriggered()) {
            double c = doubleOf(map.get("cooldown"), Double.NaN);
            if (!(c >= AbilityId.MIN_COOLDOWN && c <= AbilityId.MAX_COOLDOWN)) {
                problem.accept(at + ".cooldown: must be " + fmt(AbilityId.MIN_COOLDOWN) + "-" + fmt(AbilityId.MAX_COOLDOWN)
                        + " seconds, using the default");
            } else {
                cooldown = c;
            }
        }
        double chance = Double.NaN;
        if (map.containsKey("chance")) {
            double c = doubleOf(map.get("chance"), Double.NaN);
            if (!(c >= 0 && c <= 1)) {
                problem.accept(at + ".chance: must be 0-1, using the default");
            } else {
                chance = c;
            }
        }
        Map<Object, Object> params = new LinkedHashMap<>();
        if (map.get("params") instanceof Map<?, ?> nested) {
            params.putAll(nested);
        } else if (map.get("params") instanceof ConfigurationSection nested) {
            params.putAll(nested.getValues(false));
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!ABILITY_KEYS.contains(String.valueOf(e.getKey()))) {
                params.put(e.getKey(), e.getValue());
            }
        }
        Map<String, Double> numbers = new LinkedHashMap<>();
        Map<String, String> texts = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> e : params.entrySet()) {
            String key = String.valueOf(e.getKey()).trim().toLowerCase(Locale.ROOT);
            AbilityId.Param p = id.param(key);
            if (p == null) {
                problem.accept(at + "." + key + ": " + id.id() + " has no such setting (" + String.join(", ", id.params().keySet())
                        + "), ignored");
            } else if (p.text()) {
                String v = String.valueOf(e.getValue()).trim().toLowerCase(Locale.ROOT);
                if (p.choices().contains(v)) {
                    texts.put(key, v);
                } else {
                    problem.accept(at + "." + key + ": must be " + String.join(" or ", p.choices()) + ", using " + p.textDefault());
                }
            } else {
                double v = doubleOf(e.getValue(), Double.NaN);
                if (v >= p.min() && v <= p.max()) {
                    numbers.put(key, v);
                } else {
                    problem.accept(at + "." + key + ": must be " + fmt(p.min()) + "-" + fmt(p.max()) + ", using " + fmt(p.def()));
                }
            }
        }
        return new AbilitySpec(id, cooldown, chance, numbers, texts);
    }

    // ------------------------------------------------------------------ drops

    /** {@code drop-tables}: a list of {@code {id, drops: [{item, min, max, chance}]}}. */
    public static Map<String, List<MobDef.Drop>> dropTables(List<?> raw, Set<String> items, Consumer<String> problem) {
        Map<String, List<MobDef.Drop>> out = new LinkedHashMap<>();
        if (raw == null) {
            problem.accept(DROP_TABLES + ": must be a list of {id, drops}, ignored");
            return out;
        }
        for (int i = 0; i < raw.size(); i++) {
            String at = DROP_TABLES + "[" + i + "]";
            if (!(raw.get(i) instanceof Map<?, ?> map)) {
                problem.accept(at + ": must be {id, drops}, skipped");
                continue;
            }
            Object idRaw = map.get("id");
            String id = idRaw == null ? "" : idRaw.toString().trim().toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_]+")) {
                problem.accept(at + ".id: '" + id + "' must be a-z, 0-9 and _, skipped");
                continue;
            }
            if (out.containsKey(id)) {
                problem.accept(at + ": table '" + id + "' is listed twice, skipped");
                continue;
            }
            List<MobDef.Drop> drops = new ArrayList<>();
            if (map.get("drops") instanceof List<?> list) {
                for (int j = 0; j < list.size(); j++) {
                    String dAt = at + ".drops[" + j + "]";
                    if (list.get(j) instanceof Map<?, ?> d) {
                        MobDef.Drop drop = drop(d, items, dAt, problem);
                        if (drop != null) {
                            drops.add(drop);
                        }
                    } else {
                        problem.accept(dAt + ": must be {item, min, max, chance}, skipped");
                    }
                }
            }
            if (drops.isEmpty()) {
                problem.accept(at + ": table '" + id + "' has no valid drop (it is kept, empty)");
            }
            out.put(id, drops);
        }
        return out;
    }

    /** One drop {@code {item, min, max, chance}} ({@code amount} = min = max); {@code null} if broken or never drops. */
    public static MobDef.Drop drop(Map<?, ?> map, Set<String> items, String where, Consumer<String> problem) {
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

    // ------------------------------------------------------------------ helpers

    /** A number in [min, max]; missing = {@code def}, out of range = reported and {@code def}. */
    public static double range(ConfigurationSection s, String key, double def, double min, double max, String where,
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

    public static String fmt(double v) {
        return v == Math.floor(v) && !Double.isInfinite(v) ? Long.toString((long) v) : Double.toString(v);
    }

    static int intOf(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o == null ? def : Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static double doubleOf(Object o, double def) {
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
