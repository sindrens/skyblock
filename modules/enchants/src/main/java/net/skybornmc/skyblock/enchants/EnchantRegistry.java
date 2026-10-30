package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The enchant registry from {@code enchants.yml → enchants.<id>} (ENCHANTS §6, §13.1). Every entry is validated; a
 * broken entry is skipped with a readable problem (the rest still loads), so a typo never takes the module down.
 * Pure: no server needed (unit-tested against the bundled file).
 */
final class EnchantRegistry {

    static final String ROOT = "enchants";
    static final int MAX_LEVEL = 10;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");

    private final Map<String, EnchantDef> byId;

    private EnchantRegistry(Map<String, EnchantDef> byId) {
        this.byId = Collections.unmodifiableMap(byId);
    }

    static EnchantRegistry empty() {
        return new EnchantRegistry(new LinkedHashMap<>());
    }

    static EnchantRegistry of(Collection<EnchantDef> defs) {
        Map<String, EnchantDef> map = new LinkedHashMap<>();
        defs.forEach(d -> map.put(d.id(), d));
        return new EnchantRegistry(map);
    }

    Optional<EnchantDef> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    /** All definitions in file order. */
    Collection<EnchantDef> all() {
        return byId.values();
    }

    Set<String> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(byId.keySet()));
    }

    int size() {
        return byId.size();
    }

    // ------------------------------------------------------------------ loading

    /**
     * @param file     the whole {@code enchants.yml}
     * @param problems one line per problem ("enchants.yml → enchants.vein_rend.max-level: …")
     */
    static EnchantRegistry load(ConfigurationSection file, Consumer<String> problems) {
        Map<String, EnchantDef> out = new LinkedHashMap<>();
        ConfigurationSection root = file == null ? null : file.getConfigurationSection(ROOT);
        if (root == null) {
            problems.accept("enchants.yml: section '" + ROOT + "' is missing; no enchants loaded");
            return new EnchantRegistry(out);
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            String where = "enchants.yml → " + ROOT + "." + id;
            if (s == null) {
                problems.accept(where + ": not a section (skipped)");
                continue;
            }
            List<String> errors = new ArrayList<>();
            EnchantDef def = parse(id, s, errors);
            if (!errors.isEmpty() || def == null) {
                errors.forEach(e -> problems.accept(where + e + " (enchant skipped)"));
                continue;
            }
            out.put(id, def);
        }
        return new EnchantRegistry(out);
    }

    private static EnchantDef parse(String id, ConfigurationSection s, List<String> errors) {
        if (!ID.matcher(id).matches()) {
            errors.add(": id must be lowercase a-z, 0-9 and _");
            return null;
        }
        Rarity rarity = null;
        String rarityRaw = s.getString("rarity", "");
        try {
            rarity = Rarity.valueOf(rarityRaw.trim().toUpperCase(Locale.ROOT));
            if (rarity == Rarity.SPECIAL) {
                errors.add(".rarity: 'special' is for event cosmetics, not enchants");
            }
        } catch (IllegalArgumentException e) {
            errors.add(".rarity: unknown rarity '" + rarityRaw + "' (common, uncommon, rare, epic, legendary, mythic)");
        }
        int max = s.getInt("max-level", 0);
        if (max < 1 || max > MAX_LEVEL) {
            errors.add(".max-level: must be 1-" + MAX_LEVEL + " (is " + max + ")");
        }
        if (rarity == Rarity.MYTHIC && max != 1) {
            errors.add(".max-level: Mythic enchants have one level");
        }
        Set<EnchantTarget> targets = EnumSet.noneOf(EnchantTarget.class);
        for (String raw : s.getStringList("tools")) {
            Optional<Set<EnchantTarget>> t = EnchantTarget.parse(raw);
            if (t.isEmpty()) {
                errors.add(".tools: unknown '" + raw + "' (pickaxe, axe, hoe, sword, rod, helmet, chestplate, leggings, boots, tools, armor)");
            } else {
                targets.addAll(t.get());
            }
        }
        if (targets.isEmpty()) {
            errors.add(".tools: needs at least one tool or armor piece");
        }
        boolean armor = targets.stream().anyMatch(EnchantTarget::armor);
        boolean tool = targets.stream().anyMatch(t -> !t.armor());
        if (armor && tool) {
            errors.add(".tools: mixes tools and armor pieces");
        }
        int slotCost = s.getInt("slot-cost", 1);
        if (slotCost < 1 || slotCost > 2) {
            errors.add(".slot-cost: must be 1 or 2");
        }
        if (slotCost == 2 && rarity == Rarity.MYTHIC) {
            errors.add(".slot-cost: Mythics fit the prestige slot only, so they cost 1");
        }
        List<String> conflicts = new ArrayList<>();
        for (String raw : s.getStringList("conflicts")) {
            String g = raw.trim().toLowerCase(Locale.ROOT);
            if (!ID.matcher(g).matches()) {
                errors.add(".conflicts: group '" + raw + "' must be lowercase a-z, 0-9 and _");
            } else if (!conflicts.contains(g)) {
                conflicts.add(g);
            }
        }
        if (rarity == Rarity.MYTHIC && !conflicts.contains(EnchantDef.MYTHIC_GROUP)) {
            conflicts.add(EnchantDef.MYTHIC_GROUP);
        }
        Set<Pool> pools = EnumSet.noneOf(Pool.class);
        for (String raw : s.getStringList("pools")) {
            Optional<Pool> p = Pool.byId(raw);
            if (p.isEmpty()) {
                errors.add(".pools: unknown pool '" + raw + "' (mining, foraging, farming, combat, fishing, stacks)");
            } else {
                pools.add(p.get());
            }
        }
        if (rarity == Rarity.MYTHIC && !pools.isEmpty()) {
            errors.add(".pools: Mythics never drop from activity pools (special sources only)");
        }
        if (rarity != null && rarity != Rarity.MYTHIC && pools.isEmpty()) {
            errors.add(".pools: needs at least one pool");
        }
        int minTier = s.getInt("min-tier", 1);
        if (minTier < 1 || minTier > 20) {
            errors.add(".min-tier: must be 1-20");
        }
        BonusFamily family = null;
        String familyRaw = s.getString("family", "");
        if (!familyRaw.isBlank()) {
            family = BonusFamily.byId(familyRaw).orElse(null);
            if (family == null) {
                errors.add(".family: unknown family '" + familyRaw + "'");
            }
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        ConfigurationSection vs = s.getConfigurationSection("values");
        if (vs != null) {
            for (String key : vs.getKeys(false)) {
                List<Double> list = numbers(vs.getList(key));
                if (list == null) {
                    errors.add(".values." + key + ": must be a list of numbers");
                } else if (max >= 1 && list.size() != max) {
                    errors.add(".values." + key + ": needs one number per level (" + max + "), has " + list.size());
                } else {
                    values.put(key, list);
                }
            }
        }
        Map<String, Double> params = new LinkedHashMap<>();
        ConfigurationSection ps = s.getConfigurationSection("params");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                Object raw = ps.get(key);
                if (raw instanceof Number n && Double.isFinite(n.doubleValue())) {
                    params.put(key, n.doubleValue());
                } else {
                    errors.add(".params." + key + ": must be a number");
                }
                if (values.containsKey(key)) {
                    errors.add(".params." + key + ": also in values (use one)");
                }
            }
        }
        String passive = s.getString("passive", "");
        if (passive.isBlank()) {
            passive = null;
        } else if (!values.containsKey(passive)) {
            errors.add(".passive: '" + passive + "' is not a key in values");
        } else if (family == null) {
            errors.add(".passive: needs a family");
        }
        double pvp = s.getDouble("pvp-percent", 0);
        if (!Double.isFinite(pvp) || pvp < 0 || pvp > 100) {
            errors.add(".pvp-percent: must be 0-100");
        }
        double weight = s.getDouble("weight", 1);
        if (!Double.isFinite(weight) || weight <= 0) {
            errors.add(".weight: must be above 0");
        }
        boolean enabled = s.getBoolean("enabled", true);
        if (!errors.isEmpty()) {
            return null;
        }
        return new EnchantDef(id, rarity, max, targets, slotCost, conflicts, pools, minTier, family, passive, values, params,
                pvp, weight, enabled);
    }

    private static List<Double> numbers(List<?> raw) {
        if (raw == null) {
            return null;
        }
        List<Double> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (!(o instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                return null;
            }
            out.add(n.doubleValue());
        }
        return out;
    }
}
