package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.skills.SkillType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * All sets from {@code sets.yml} (WARDROBE §2, §8.3), validated on load: a broken set is skipped with a readable
 * problem, the rest still load. Pure (no server), unit-tested against the bundled file.
 */
final class SetRegistry {

    static final String ROOT = "sets";
    static final int MAX_NAME = 16;
    static final int MAX_SHORT_NAME = 12;

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern WORD = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern TEXT_KEY = Pattern.compile("[a-z0-9-]{1,48}");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    static final List<String> WINGS = List.of("any", "mining", "foraging", "combat", "farming", "fishing");
    static final List<String> ACTIVITIES = List.of("mining", "foraging", "farming", "combat", "fishing", "conquest", "selling",
            "skills", "gathering", "all");

    private final Map<String, SetDef> sets;

    private SetRegistry(Map<String, SetDef> sets) {
        this.sets = Collections.unmodifiableMap(sets);
    }

    static SetRegistry empty() {
        return new SetRegistry(new LinkedHashMap<>());
    }

    Optional<SetDef> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(sets.get(id.toLowerCase(Locale.ROOT)));
    }

    Collection<SetDef> all() {
        return sets.values();
    }

    List<String> ids() {
        return List.copyOf(sets.keySet());
    }

    int size() {
        return sets.size();
    }

    // ------------------------------------------------------------------ loading

    /** Loads {@code sets.<id>} from the file; problems are reported (and the set skipped). */
    static SetRegistry load(ConfigurationSection yaml, Consumer<String> problems) {
        Map<String, SetDef> out = new LinkedHashMap<>();
        ConfigurationSection root = yaml == null ? null : yaml.getConfigurationSection(ROOT);
        if (root == null) {
            problems.accept("sets.yml: no 'sets' section, no sets loaded");
            return new SetRegistry(out);
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            List<String> errors = new ArrayList<>();
            SetDef def = s == null ? null : parse(id, s, errors);
            if (s == null) {
                errors.add("is not a section");
            }
            if (def == null || !errors.isEmpty()) {
                errors.forEach(e -> problems.accept("sets.yml → " + id + ": " + e + " (set skipped)"));
                continue;
            }
            out.put(id, def);
        }
        return new SetRegistry(out);
    }

    private static SetDef parse(String id, ConfigurationSection s, List<String> errors) {
        if (!ID.matcher(id).matches()) {
            errors.add("id must be a-z, 0-9 and _ (max 32)");
        }
        String name = s.getString("name", "").trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            errors.add("name must be 1-" + MAX_NAME + " characters (Bedrock)");
        }
        String shortName = s.getString("short-name", "").trim();
        if (shortName.isEmpty() || shortName.length() > MAX_SHORT_NAME) {
            errors.add("short-name must be 1-" + MAX_SHORT_NAME + " characters (piece names stay within 24)");
        }
        String activity = s.getString("activity", "").trim().toLowerCase(Locale.ROOT);
        if (!ACTIVITIES.contains(activity)) {
            errors.add("activity must be one of " + ACTIVITIES);
        }
        int tier = s.getInt("tier", 0);
        if (tier < 1 || tier > SetDef.MAX_TIER) {
            errors.add("tier must be 1-" + SetDef.MAX_TIER);
        }
        String enabledFrom = s.getString("enabled-from", "").trim().toLowerCase(Locale.ROOT);
        if (!enabledFrom.isEmpty() && !WORD.matcher(enabledFrom).matches()) {
            errors.add("enabled-from must be a feature id like conquest or fishing");
        }
        SetDef.Look look = look(s.getConfigurationSection("look"), errors);
        SetDef.Source source = source(s.getConfigurationSection("source"), errors);
        SetDef.Gate gate = gate(s.getConfigurationSection("gate"), errors);
        List<SetDef.Bonus> two = bonuses(s, "bonuses.two", errors);
        List<SetDef.Bonus> four = bonuses(s, "bonuses.four", errors);
        if (two.isEmpty()) {
            errors.add("needs at least one 2-piece bonus");
        }
        if (four.isEmpty()) {
            errors.add("needs at least one 4-piece bonus");
        }
        if (!errors.isEmpty() || look == null || source == null || gate == null) {
            return null;
        }
        return new SetDef(id, s.getBoolean("enabled", true), enabledFrom, name, shortName, activity, tier, look, source, gate, two, four);
    }

    private static SetDef.Look look(ConfigurationSection s, List<String> errors) {
        if (s == null) {
            errors.add("look is missing");
            return null;
        }
        Optional<SetDef.Base> base = SetDef.Base.byKey(s.getString("base"));
        if (base.isEmpty()) {
            errors.add("look.base must be leather, chainmail, iron, gold, diamond or netherite");
            return null;
        }
        String dyeRaw = s.getString("dye", "").trim();
        int dye = -1;
        if (!dyeRaw.isEmpty()) {
            if (!HEX.matcher(dyeRaw).matches()) {
                errors.add("look.dye must be #RRGGBB");
            } else if (base.get() != SetDef.Base.LEATHER) {
                errors.add("look.dye only works on leather");
            } else {
                dye = Integer.parseInt(dyeRaw.substring(1), 16);
            }
        }
        String pattern = s.getString("trim-pattern", "").trim().toLowerCase(Locale.ROOT);
        String material = s.getString("trim-material", "").trim().toLowerCase(Locale.ROOT);
        String upgraded = s.getString("upgraded-trim-material", material).trim().toLowerCase(Locale.ROOT);
        for (String w : List.of(pattern, material, upgraded)) {
            if (!w.isEmpty() && !WORD.matcher(w).matches()) {
                errors.add("look trim ids must be vanilla ids like wayfinder / copper");
            }
        }
        if (pattern.isEmpty() != material.isEmpty()) {
            errors.add("look needs both trim-pattern and trim-material, or neither");
        }
        return new SetDef.Look(base.get(), dye, pattern, material, upgraded);
    }

    private static SetDef.Source source(ConfigurationSection s, List<String> errors) {
        if (s == null) {
            errors.add("source is missing");
            return null;
        }
        Optional<SetDef.SourceType> type = SetDef.SourceType.byKey(s.getString("type"));
        if (type.isEmpty()) {
            errors.add("source.type must be money, crystals, credits or crate");
            return null;
        }
        long price = s.getLong("price", 0);
        if (type.get() != SetDef.SourceType.CRATE && price <= 0) {
            errors.add("source.price must be above 0");
        }
        if (price < 0) {
            errors.add("source.price can't be negative");
        }
        List<String> crates = new ArrayList<>();
        for (String c : s.getStringList("crates")) {
            String k = c.trim().toLowerCase(Locale.ROOT);
            if (!WORD.matcher(k).matches()) {
                errors.add("source.crates entries must be crate ids");
            } else {
                crates.add(k);
            }
        }
        if (type.get() == SetDef.SourceType.CRATE && crates.isEmpty()) {
            errors.add("a crate-only set must list its crates");
        }
        return new SetDef.Source(type.get(), type.get() == SetDef.SourceType.CRATE ? 0 : price, crates);
    }

    private static SetDef.Gate gate(ConfigurationSection s, List<String> errors) {
        if (s == null) {
            return SetDef.Gate.NONE;
        }
        String wing = s.getString("wing", "any").trim().toLowerCase(Locale.ROOT);
        if (!WINGS.contains(wing)) {
            errors.add("gate.wing must be one of " + WINGS);
        }
        int tier = s.getInt("tier", 0);
        int anySkill = s.getInt("any-skill", 0);
        String skill = s.getString("skill", "").trim().toLowerCase(Locale.ROOT);
        int skillLevel = s.getInt("skill-level", 0);
        if (tier < 0 || anySkill < 0 || skillLevel < 0) {
            errors.add("gate numbers can't be negative");
        }
        if (!skill.isEmpty() && SkillType.byId(skill).isEmpty()) {
            errors.add("gate.skill must be a skill id (mining, foraging, combat, farming, fishing)");
        }
        if (!skill.isEmpty() && skillLevel <= 0) {
            errors.add("gate.skill needs a skill-level above 0");
        }
        return new SetDef.Gate(wing, tier, anySkill, skill, skillLevel);
    }

    private static List<SetDef.Bonus> bonuses(ConfigurationSection s, String path, List<String> errors) {
        List<SetDef.Bonus> out = new ArrayList<>();
        List<Map<?, ?>> raw = s.getMapList(path);
        for (Map<?, ?> m : raw) {
            Optional<BonusFamily> family = BonusFamily.byId(String.valueOf(m.get("family")));
            if (family.isEmpty()) {
                errors.add(path + ": unknown bonus family '" + m.get("family") + "'");
                continue;
            }
            double value;
            try {
                value = Double.parseDouble(String.valueOf(m.get("value")));
            } catch (NumberFormatException e) {
                errors.add(path + ": value must be a number");
                continue;
            }
            if (!Double.isFinite(value) || value <= 0) {
                errors.add(path + ": value must be above 0");
                continue;
            }
            List<String> activities = new ArrayList<>();
            Object acts = m.get("activities");
            if (acts instanceof List<?> list) {
                for (Object a : list) {
                    String k = String.valueOf(a).trim().toLowerCase(Locale.ROOT);
                    if (!WORD.matcher(k).matches()) {
                        errors.add(path + ": activity '" + a + "' is not an id");
                    } else {
                        activities.add(k);
                    }
                }
            } else if (acts != null) {
                errors.add(path + ": activities must be a list");
            }
            String text = String.valueOf(m.get("text") == null ? "" : m.get("text")).trim();
            if (!TEXT_KEY.matcher(text).matches()) {
                errors.add(path + ": text must be a key under bonus-text in messages/wardrobe.yml");
                continue;
            }
            out.add(new SetDef.Bonus(family.get(), value, activities, text));
        }
        return out;
    }
}
