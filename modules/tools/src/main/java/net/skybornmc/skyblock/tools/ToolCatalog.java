package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.tools.ToolKind;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Tool tiers, blueprints, abilities, skins, island XP and smelt results from the catalog sections of {@code tools.yml}
 * (defaults: {@code tools-defaults.yml}). Broken entries are reported and skipped; a type without valid tiers is left out.
 */
final class ToolCatalog {

    static final List<String> SECTIONS = List.of("tiers", "abilities", "blueprints", "skins", "island-xp", "smelt");
    /** Plain settings ({@link ToolsConfig}) that live inside catalog sections; they are not catalog entries. */
    static final java.util.Set<String> SETTING_KEYS = java.util.Set.of("lifesteal-max-heal", "drops-enabled");

    /** Forge price: money plus materials (item id → amount). */
    record Recipe(long money, Map<String, Integer> materials) {
        static final Recipe FREE = new Recipe(0, Map.of());

        Recipe {
            money = Math.max(0, money);
            materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
        }

        /** What is still missing given what the player has (id → amount); empty = has everything. */
        Map<String, Integer> missing(Map<String, Integer> have) {
            Map<String, Integer> out = new LinkedHashMap<>();
            materials.forEach((id, need) -> {
                int got = have.getOrDefault(id, 0);
                if (got < need) {
                    out.put(id, need - got);
                }
            });
            return out;
        }
    }

    record TierDef(ToolKind kind, int tier, String id, Material material, String model, double efficiency, double damage,
                   double attackSpeed, Rarity rarity, int skillLevel, Recipe recipe) {
    }

    record AbilityDef(String id, Set<ToolKind> kinds, double value) {
    }

    record BlueprintDef(String id, ToolKind kind, int tier, String ability, Rarity rarity, String dropWing, int dropMinTier,
                        double dropChance, Recipe recipe) {
    }

    record SkinDef(String id, Set<ToolKind> kinds, Rarity rarity, String model) {
        boolean fits(ToolKind kind) {
            return kinds.contains(kind);
        }

        /** The Java item model for a tool type, or null for a name-only skin. */
        String modelFor(ToolKind kind) {
            return model == null || model.isBlank() ? null : model.replace("{kind}", kind.id());
        }
    }

    private final Map<ToolKind, List<TierDef>> tiers;
    private final Map<String, AbilityDef> abilities;
    private final Map<String, BlueprintDef> blueprints;
    private final Map<String, SkinDef> skins;
    private final Map<String, Double> islandXp;
    private final Map<String, String> smelt;

    ToolCatalog(Map<ToolKind, List<TierDef>> tiers, Map<String, AbilityDef> abilities, Map<String, BlueprintDef> blueprints,
                Map<String, SkinDef> skins, Map<String, Double> islandXp, Map<String, String> smelt) {
        Map<ToolKind, List<TierDef>> t = new EnumMap<>(ToolKind.class);
        tiers.forEach((k, v) -> t.put(k, List.copyOf(v)));
        this.tiers = Collections.unmodifiableMap(t);
        this.abilities = Collections.unmodifiableMap(new LinkedHashMap<>(abilities));
        this.blueprints = Collections.unmodifiableMap(new LinkedHashMap<>(blueprints));
        this.skins = Collections.unmodifiableMap(new LinkedHashMap<>(skins));
        this.islandXp = Collections.unmodifiableMap(new LinkedHashMap<>(islandXp));
        this.smelt = Collections.unmodifiableMap(new LinkedHashMap<>(smelt));
    }

    static ToolCatalog empty() {
        return new ToolCatalog(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    // ------------------------------------------------------------------ queries

    List<TierDef> tiers(ToolKind kind) {
        return tiers.getOrDefault(kind, List.of());
    }

    Set<ToolKind> kinds() {
        return tiers.isEmpty() ? EnumSet.noneOf(ToolKind.class) : EnumSet.copyOf(tiers.keySet());
    }

    int maxTier(ToolKind kind) {
        return tiers(kind).size();
    }

    Optional<TierDef> tier(ToolKind kind, int tier) {
        List<TierDef> list = tiers(kind);
        return tier >= 1 && tier <= list.size() ? Optional.of(list.get(tier - 1)) : Optional.empty();
    }

    /** The tier by its id ({@code iron}) or number ({@code 3}). */
    Optional<TierDef> tier(ToolKind kind, String idOrNumber) {
        if (idOrNumber == null) {
            return Optional.empty();
        }
        String key = idOrNumber.trim().toLowerCase(Locale.ROOT);
        for (TierDef def : tiers(kind)) {
            if (def.id().equals(key) || String.valueOf(def.tier()).equals(key)) {
                return Optional.of(def);
            }
        }
        return Optional.empty();
    }

    Map<String, AbilityDef> abilities() {
        return abilities;
    }

    Optional<AbilityDef> ability(String id) {
        return Optional.ofNullable(id == null ? null : abilities.get(id));
    }

    Map<String, BlueprintDef> blueprints() {
        return blueprints;
    }

    Optional<BlueprintDef> blueprint(String id) {
        return Optional.ofNullable(id == null ? null : blueprints.get(id.toLowerCase(Locale.ROOT)));
    }

    Map<String, SkinDef> skins() {
        return skins;
    }

    Optional<SkinDef> skin(String id) {
        return Optional.ofNullable(id == null ? null : skins.get(id.toLowerCase(Locale.ROOT)));
    }

    double islandXp(Material block) {
        return islandXp.getOrDefault(block.getKey().getKey(), 0.0);
    }

    Optional<Material> smelted(Material drop) {
        String to = smelt.get(drop.getKey().getKey());
        return Optional.ofNullable(to == null ? null : Material.matchMaterial(to));
    }

    // ------------------------------------------------------------------ loading

    /** Material checks; the server's registries answer them at runtime, unit tests pass simple ones. */
    record Checks(java.util.function.Predicate<Material> item, java.util.function.Predicate<Material> block) {
        static final Checks RUNTIME = new Checks(m -> m.isItem() && !m.isAir(), Material::isBlock);
    }

    static ToolCatalog load(ConfigurationSection root, Consumer<String> problems) {
        return load(root, problems, Checks.RUNTIME);
    }

    static ToolCatalog load(ConfigurationSection root, Consumer<String> problems, Checks checks) {
        Map<ToolKind, List<TierDef>> tiers = new EnumMap<>(ToolKind.class);
        ConfigurationSection tierRoot = root.getConfigurationSection("tiers");
        if (tierRoot != null) {
            for (String kindKey : tierRoot.getKeys(false)) {
                Optional<ToolKind> kind = ToolKind.byId(kindKey);
                if (kind.isEmpty()) {
                    problems.accept("tools.yml tiers." + kindKey + ": unknown tool type (pickaxe, axe, sword, hoe, rod)");
                    continue;
                }
                List<TierDef> list = loadTiers(kind.get(), tierRoot.getMapList(kindKey), problems, checks);
                if (!list.isEmpty()) {
                    tiers.put(kind.get(), list);
                }
            }
        }
        Map<String, AbilityDef> abilities = new LinkedHashMap<>();
        ConfigurationSection ab = root.getConfigurationSection("abilities");
        if (ab != null) {
            for (String id : ab.getKeys(false)) {
                if (SETTING_KEYS.contains(id)) {
                    continue;
                }
                ConfigurationSection s = ab.getConfigurationSection(id);
                if (s == null || !validId(id)) {
                    problems.accept("tools.yml abilities." + id + ": not a valid ability");
                    continue;
                }
                abilities.put(id, new AbilityDef(id, kinds(s.getStringList("kinds"), "abilities." + id, problems), s.getDouble("value", 0)));
            }
        }
        Map<String, BlueprintDef> blueprints = new LinkedHashMap<>();
        ConfigurationSection bp = root.getConfigurationSection("blueprints");
        if (bp != null) {
            for (String id : bp.getKeys(false)) {
                if (SETTING_KEYS.contains(id)) {
                    continue;
                }
                BlueprintDef def = loadBlueprint(id, bp.getConfigurationSection(id), tiers, abilities, problems);
                if (def != null) {
                    blueprints.put(id, def);
                }
            }
        }
        Map<String, SkinDef> skins = new LinkedHashMap<>();
        ConfigurationSection sk = root.getConfigurationSection("skins");
        if (sk != null) {
            for (String id : sk.getKeys(false)) {
                ConfigurationSection s = sk.getConfigurationSection(id);
                if (s == null || !validId(id)) {
                    problems.accept("tools.yml skins." + id + ": not a valid skin");
                    continue;
                }
                String model = s.getString("model", "");
                if (model != null && !model.isBlank() && NamespacedKey.fromString(model.replace("{kind}", "pickaxe")) == null) {
                    problems.accept("tools.yml skins." + id + ".model: '" + model + "' is not an item model key; using none");
                    model = "";
                }
                skins.put(id, new SkinDef(id, kinds(s.getStringList("kinds"), "skins." + id, problems),
                        rarity(s.getString("rarity"), Rarity.EPIC, "skins." + id, problems), model));
            }
        }
        Map<String, Double> islandXp = new LinkedHashMap<>();
        ConfigurationSection ix = root.getConfigurationSection("island-xp");
        if (ix != null) {
            for (String id : ix.getKeys(false)) {
                Material m = Material.matchMaterial(id);
                double xp = ix.getDouble(id, -1);
                if (m == null || !checks.block().test(m) || xp < 0) {
                    problems.accept("tools.yml island-xp." + id + ": needs a block id and a number >= 0");
                    continue;
                }
                islandXp.put(m.getKey().getKey(), xp);
            }
        }
        Map<String, String> smelt = new LinkedHashMap<>();
        ConfigurationSection sm = root.getConfigurationSection("smelt");
        if (sm != null) {
            for (String id : sm.getKeys(false)) {
                Material from = Material.matchMaterial(id);
                Material to = Material.matchMaterial(String.valueOf(sm.getString(id)));
                if (from == null || to == null || !checks.item().test(to)) {
                    problems.accept("tools.yml smelt." + id + ": needs two item ids");
                    continue;
                }
                smelt.put(from.getKey().getKey(), to.getKey().getKey());
            }
        }
        return new ToolCatalog(tiers, abilities, blueprints, skins, islandXp, smelt);
    }

    private static List<TierDef> loadTiers(ToolKind kind, List<Map<?, ?>> raw, Consumer<String> problems, Checks checks) {
        List<TierDef> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Map<?, ?> m = raw.get(i);
            int tier = out.size() + 1;
            String where = "tools.yml tiers." + kind.id() + "[" + (i + 1) + "]";
            String id = str(m.get("id"));
            Material material = Material.matchMaterial(str(m.get("material")));
            if (!validId(id) || material == null || !checks.item().test(material)) {
                problems.accept(where + ": needs an id (a-z, 0-9, _) and an item material; this tier and the ones after it are skipped");
                break;
            }
            if (out.stream().anyMatch(d -> d.id().equals(id))) {
                problems.accept(where + ": id '" + id + "' is used twice; this tier and the ones after it are skipped");
                break;
            }
            String model = str(m.get("model"));
            if (!model.isBlank() && NamespacedKey.fromString(model) == null) {
                problems.accept(where + ".model: '" + model + "' is not an item model key; using the material's look");
                model = "";
            }
            Recipe recipe = tier == 1 ? Recipe.FREE : recipe(m.get("money"), m.get("materials"), where, problems);
            out.add(new TierDef(kind, tier, id, material, model.isBlank() ? null : model, num(m.get("efficiency"), 0),
                    num(m.get("damage"), 0), num(m.get("attack-speed"), 0), rarity(str(m.get("rarity")), Rarity.COMMON, where, problems),
                    (int) Math.max(0, num(m.get("skill-level"), 0)), recipe));
        }
        return out;
    }

    private static BlueprintDef loadBlueprint(String id, ConfigurationSection s, Map<ToolKind, List<TierDef>> tiers,
                                              Map<String, AbilityDef> abilities, Consumer<String> problems) {
        String where = "tools.yml blueprints." + id;
        if (s == null || !validId(id)) {
            problems.accept(where + ": not a valid blueprint");
            return null;
        }
        Optional<ToolKind> kind = ToolKind.byId(s.getString("kind"));
        if (kind.isEmpty() || !tiers.containsKey(kind.get())) {
            problems.accept(where + ".kind: needs a tool type that has tiers");
            return null;
        }
        int tier = s.getInt("tier", 1);
        if (tier < 1 || tier > tiers.get(kind.get()).size()) {
            problems.accept(where + ".tier: must be 1-" + tiers.get(kind.get()).size());
            return null;
        }
        String ability = s.getString("ability", "");
        AbilityDef def = abilities.get(ability);
        if (def == null || !def.kinds().contains(kind.get())) {
            problems.accept(where + ".ability: '" + ability + "' is not an ability for a " + kind.get().id());
            return null;
        }
        String wing = s.getString("drop.wing", "");
        if (!wing.isBlank() && !List.of("mine", "forest", "combat").contains(wing)) {
            problems.accept(where + ".drop.wing: use mine, forest or combat; drops are off");
            wing = "";
        }
        double chance = Math.max(0, Math.min(1, s.getDouble("drop.chance", 0)));
        Map<String, Object> mats = new LinkedHashMap<>();
        ConfigurationSection ms = s.getConfigurationSection("materials");
        if (ms != null) {
            mats.putAll(ms.getValues(false));
        }
        return new BlueprintDef(id, kind.get(), tier, ability, rarity(s.getString("rarity"), Rarity.RARE, where, problems),
                wing, Math.max(1, s.getInt("drop.min-tier", 1)), chance, recipe(s.get("money"), mats, where, problems));
    }

    private static Recipe recipe(Object money, Object materials, String where, Consumer<String> problems) {
        long cost = (long) Math.max(0, num(money, 0));
        Map<String, Integer> mats = new LinkedHashMap<>();
        Map<?, ?> raw = materials instanceof Map<?, ?> map ? map
                : materials instanceof ConfigurationSection section ? section.getValues(false) : Map.of();
        raw.forEach((k, v) -> {
            String id = String.valueOf(k).toLowerCase(Locale.ROOT).replace("minecraft:", "");
            int amount = (int) num(v, 0);
            if (!id.matches("[a-z0-9_]{1,64}") || amount <= 0) {
                problems.accept(where + ".materials." + k + ": needs an item id and an amount above 0; skipped");
                return;
            }
            mats.put(id, amount);
        });
        return new Recipe(cost, mats);
    }

    private static Set<ToolKind> kinds(List<String> raw, String where, Consumer<String> problems) {
        Set<ToolKind> out = EnumSet.noneOf(ToolKind.class);
        for (String s : raw) {
            ToolKind.byId(s).ifPresentOrElse(out::add, () -> problems.accept("tools.yml " + where + ".kinds: unknown tool type '" + s + "'"));
        }
        return out.isEmpty() ? EnumSet.allOf(ToolKind.class) : out;
    }

    private static Rarity rarity(String raw, Rarity fallback, String where, Consumer<String> problems) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        for (Rarity r : Rarity.values()) {
            if (r.id().equalsIgnoreCase(raw.trim())) {
                return r;
            }
        }
        problems.accept(where + ".rarity: unknown rarity '" + raw + "', using " + fallback.id());
        return fallback;
    }

    static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9_]{1,48}");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static double num(Object o, double fallback) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o).trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }
}
