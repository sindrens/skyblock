package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.skills.SkillType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * The per-action XP and unlock levels from the {@code xp} and {@code unlocks} sections of {@code skills.yml}:
 * <pre>
 * xp:
 *   mining:   { coal_ore: 1, copper_ore: 1.5 }     # block id → XP per block broken in The Depths
 *   foraging: { oak_log: 1 }                       # log id → XP per log
 *   combat:   { zombie: 5 }                        # entity id → XP per kill
 * unlocks:
 *   mining: [5, 10, 15]                            # levels with an unlock; text: messages/skills.yml → unlock.mining.&lt;level&gt;
 * </pre>
 * Defaults follow WORLD.md §4 (XP ~×1.45 per tier) and ECONOMY_BALANCE.md §3.2 (combat thresholds and kill XP priced
 * for the same XP per hour as the mine). Broken entries are reported and skipped.
 */
public final class SkillTables {

    static final String XP = "xp";
    static final String UNLOCKS = "unlocks";

    /** Default XP per action, per skill (written to the file when the section is missing). */
    static final Map<SkillType, Map<String, Double>> DEFAULT_XP = defaultXp();

    /** Default unlock levels (tier thresholds from WORLD.md §4 and the M3.0 combat proposal). */
    static final Map<SkillType, List<Integer>> DEFAULT_UNLOCKS = Map.of(
            SkillType.MINING, List.of(5, 10, 15, 20, 25, 30, 35, 42, 50, 58, 66),
            SkillType.FORAGING, List.of(5, 10, 15, 20, 25, 30, 35, 42, 50, 58),
            SkillType.COMBAT, List.of(6, 12, 18, 24, 30, 38, 46, 55, 66));

    private final Map<SkillType, Map<String, Double>> xp;
    private final Map<SkillType, List<Integer>> unlocks;

    SkillTables(Map<SkillType, Map<String, Double>> xp, Map<SkillType, List<Integer>> unlocks) {
        Map<SkillType, Map<String, Double>> x = new EnumMap<>(SkillType.class);
        xp.forEach((k, v) -> x.put(k, Collections.unmodifiableMap(new LinkedHashMap<>(v))));
        Map<SkillType, List<Integer>> u = new EnumMap<>(SkillType.class);
        unlocks.forEach((k, v) -> u.put(k, List.copyOf(new TreeSet<>(v))));
        this.xp = Collections.unmodifiableMap(x);
        this.unlocks = Collections.unmodifiableMap(u);
    }

    static SkillTables defaults() {
        return new SkillTables(DEFAULT_XP, DEFAULT_UNLOCKS);
    }

    /** XP for an action id in a skill (0 if not listed). Ids are lowercase without {@code minecraft:}. */
    public double xp(SkillType skill, String id) {
        Map<String, Double> table = xp.get(skill);
        if (table == null || id == null) {
            return 0;
        }
        Double value = table.get(normalize(id));
        return value == null ? 0 : value;
    }

    /** The listed ids of a skill with their XP (for the menu and staff). */
    public Map<String, Double> table(SkillType skill) {
        return xp.getOrDefault(skill, Map.of());
    }

    /** The first unlock level above {@code level}, or -1 if none is left. */
    public int nextUnlock(SkillType skill, int level) {
        for (int unlock : unlocks.getOrDefault(skill, List.of())) {
            if (unlock > level) {
                return unlock;
            }
        }
        return -1;
    }

    public List<Integer> unlocks(SkillType skill) {
        return unlocks.getOrDefault(skill, List.of());
    }

    static String normalize(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }

    // ------------------------------------------------------------------ loading

    /**
     * Reads both sections from {@code yaml}. Missing sections are filled with the defaults (returns true: the caller
     * saves the file). {@code validId} checks an id per skill (block/entity exists); unknown ids are reported and skipped.
     */
    static Loaded load(YamlConfiguration yaml, String fileName, BiPredicate<SkillType, String> validId, Consumer<String> problem) {
        boolean changed = false;
        if (!yaml.contains(XP)) {
            writeXpDefaults(yaml);
            changed = true;
        }
        if (!yaml.contains(UNLOCKS)) {
            writeUnlockDefaults(yaml);
            changed = true;
        }
        Map<SkillType, Map<String, Double>> xp = new EnumMap<>(SkillType.class);
        ConfigurationSection xpSection = yaml.getConfigurationSection(XP);
        if (xpSection == null) {
            problem.accept(fileName + " → xp: must be a section (skill → { id: xp }), using the defaults");
            xp.putAll(DEFAULT_XP);
        }
        for (String key : xpSection == null ? List.<String>of() : xpSection.getKeys(false)) {
            String where = fileName + " → xp." + key;
            SkillType skill = SkillType.byId(key).orElse(null);
            if (skill == null) {
                problem.accept(where + ": unknown skill (use mining, foraging, combat, farming, fishing), skipped");
                continue;
            }
            ConfigurationSection table = xpSection.getConfigurationSection(key);
            if (table == null) {
                problem.accept(where + ": must be a section of id: xp, skipped");
                continue;
            }
            Map<String, Double> values = new LinkedHashMap<>();
            for (String id : table.getKeys(false)) {
                Object raw = table.get(id);
                if (!(raw instanceof Number number) || !(number.doubleValue() >= 0) || Double.isInfinite(number.doubleValue())) {
                    problem.accept(where + "." + id + ": '" + raw + "' is not a number >= 0, skipped");
                    continue;
                }
                String norm = normalize(id);
                if (!validId.test(skill, norm)) {
                    problem.accept(where + "." + id + ": unknown " + (skill == SkillType.COMBAT ? "entity" : "block") + " id, skipped");
                    continue;
                }
                values.put(norm, number.doubleValue());
            }
            xp.put(skill, values);
        }
        Map<SkillType, List<Integer>> unlocks = new EnumMap<>(SkillType.class);
        ConfigurationSection unlockSection = yaml.getConfigurationSection(UNLOCKS);
        if (unlockSection == null) {
            problem.accept(fileName + " → unlocks: must be a section (skill → [levels]), using the defaults");
            unlocks.putAll(DEFAULT_UNLOCKS);
        } else {
            for (String key : unlockSection.getKeys(false)) {
                String where = fileName + " → unlocks." + key;
                SkillType skill = SkillType.byId(key).orElse(null);
                if (skill == null) {
                    problem.accept(where + ": unknown skill, skipped");
                    continue;
                }
                List<Integer> levels = new ArrayList<>();
                for (Object raw : unlockSection.getList(key, List.of())) {
                    if (raw instanceof Integer i && i > 0) {
                        levels.add(i);
                    } else {
                        problem.accept(where + ": '" + raw + "' is not a whole number above 0, skipped");
                    }
                }
                unlocks.put(skill, levels);
            }
        }
        return new Loaded(new SkillTables(xp, unlocks), changed);
    }

    record Loaded(SkillTables tables, boolean changed) {
    }

    private static void writeXpDefaults(YamlConfiguration yaml) {
        for (SkillType skill : SkillType.values()) {
            Map<String, Double> table = DEFAULT_XP.get(skill);
            String path = XP + "." + skill.id();
            yaml.createSection(path);
            if (table != null) {
                table.forEach((id, value) -> yaml.set(path + "." + id, value));
            }
        }
        yaml.setComments(XP, List.of(
                "XP per action: mining/foraging = per resource block broken in The Depths (block id),",
                "combat = per kill by a player (entity id). Farming and fishing come in Phase 7."));
    }

    private static void writeUnlockDefaults(YamlConfiguration yaml) {
        for (SkillType skill : SkillType.values()) {
            yaml.set(UNLOCKS + "." + skill.id(), DEFAULT_UNLOCKS.getOrDefault(skill, List.of()));
        }
        yaml.setComments(UNLOCKS, List.of(
                "Levels that unlock something (shown as 'Next unlock' in /skills).",
                "Text per level: messages/skills.yml → unlock.<skill>.<level>."));
    }

    private static Map<SkillType, Map<String, Double>> defaultXp() {
        Map<SkillType, Map<String, Double>> map = new EnumMap<>(SkillType.class);
        Map<String, Double> mining = new LinkedHashMap<>();
        mining.put("coal_ore", 1.0);
        mining.put("copper_ore", 1.5);
        mining.put("iron_ore", 2.5);
        mining.put("gold_ore", 4.0);
        mining.put("redstone_ore", 6.0);
        mining.put("lapis_ore", 9.0);
        mining.put("deepslate_diamond_ore", 13.0);
        mining.put("deepslate_emerald_ore", 19.0);
        mining.put("amethyst_block", 28.0);
        mining.put("nether_quartz_ore", 40.0);
        mining.put("ancient_debris", 58.0);
        mining.put("crying_obsidian", 85.0);
        map.put(SkillType.MINING, mining);
        Map<String, Double> foraging = new LinkedHashMap<>();
        foraging.put("oak_log", 1.0);
        foraging.put("birch_log", 1.5);
        foraging.put("spruce_log", 2.5);
        foraging.put("jungle_log", 4.0);
        foraging.put("acacia_log", 6.0);
        foraging.put("dark_oak_log", 9.0);
        foraging.put("mangrove_log", 13.0);
        foraging.put("cherry_log", 19.0);
        foraging.put("pale_oak_log", 28.0);
        foraging.put("crimson_stem", 40.0);
        foraging.put("warped_stem", 58.0);
        map.put(SkillType.FORAGING, foraging);
        // Kill XP = XP per hour of the mine at the same unlock level / kills per hour (ECONOMY_BALANCE §3.2).
        Map<String, Double> combat = new LinkedHashMap<>();
        combat.put("zombie", 5.0);
        combat.put("skeleton", 10.0);
        combat.put("spider", 22.0);
        combat.put("cave_spider", 22.0);
        combat.put("slime", 40.0);
        combat.put("drowned", 72.0);
        combat.put("witch", 135.0);
        combat.put("pillager", 250.0);
        combat.put("vindicator", 250.0);
        combat.put("blaze", 420.0);
        combat.put("wither_skeleton", 700.0);
        combat.put("ravager", 1200.0);
        combat.put("evoker", 1200.0);
        map.put(SkillType.COMBAT, combat);
        return Collections.unmodifiableMap(map);
    }
}
