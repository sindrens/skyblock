package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.progression.TaskInfo;
import net.skybornmc.skyblock.common.progression.TaskType;
import net.skybornmc.skyblock.common.skills.SkillType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Every task of {@code tasks.yml} (PHASE-5 M5.3), validated on load: a broken entry is skipped with a readable problem,
 * the rest still load. The file has one section, {@code tasks}; every key inside it is one task (no plain settings in
 * this file: those live in {@code tasks-settings.yml}). Pure (no server), unit-tested against the bundled file.
 */
final class TaskCatalog {

    static final String FILE = "tasks.yml";
    static final String SECTION = "tasks";
    static final int MAX_TIER = 28;
    static final int MAX_NAME = 32;

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Pattern UNLOCK = Pattern.compile("[a-z0-9_.:-]{1,64}");
    private static final Pattern OBJECT = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern CUSTOM = Pattern.compile("custom:[a-z0-9_]{1,64}");

    /** One task: the shared info plus how the book shows it. {@code name} = optional MiniMessage override. */
    record TaskDef(TaskInfo info, String icon, String name) {
        String id() {
            return info.id();
        }
    }

    /** Lookups the server provides (tests pass {@code id -> true}). */
    record Checks(Predicate<String> block, Predicate<String> entity, Predicate<String> item) {
        static Checks all() {
            return new Checks(id -> true, id -> true, id -> true);
        }
    }

    private final Map<String, TaskDef> tasks;
    private final List<TaskDef> ordered;
    private final Map<String, List<TaskDef>> byCounter;
    private final Map<String, List<TaskDef>> byUnlock;
    private final Map<Integer, List<TaskDef>> byTier;
    private final List<TaskDef> reach;

    private TaskCatalog(Map<String, TaskDef> tasks) {
        List<TaskDef> list = new ArrayList<>(tasks.values());
        list.sort(Comparator.comparingInt(t -> t.info().tier())); // stable: file order inside a tier
        this.ordered = List.copyOf(list);
        this.tasks = Collections.unmodifiableMap(tasks);
        Map<String, List<TaskDef>> counter = new HashMap<>();
        Map<String, List<TaskDef>> unlock = new HashMap<>();
        Map<Integer, List<TaskDef>> tier = new LinkedHashMap<>();
        List<TaskDef> reachList = new ArrayList<>();
        for (TaskDef def : ordered) {
            String key = def.info().counterKey();
            if (key != null) {
                counter.computeIfAbsent(key, k -> new ArrayList<>()).add(def);
            } else {
                reachList.add(def);
            }
            for (String u : def.info().unlocks()) {
                unlock.computeIfAbsent(u, k -> new ArrayList<>()).add(def);
            }
            tier.computeIfAbsent(def.info().tier(), k -> new ArrayList<>()).add(def);
        }
        counter.replaceAll((k, v) -> List.copyOf(v));
        unlock.replaceAll((k, v) -> List.copyOf(v));
        tier.replaceAll((k, v) -> List.copyOf(v));
        this.byCounter = Collections.unmodifiableMap(counter);
        this.byUnlock = Collections.unmodifiableMap(unlock);
        this.byTier = Collections.unmodifiableMap(tier);
        this.reach = List.copyOf(reachList);
    }

    static TaskCatalog empty() {
        return new TaskCatalog(new LinkedHashMap<>());
    }

    List<TaskDef> all() {
        return ordered;
    }

    int size() {
        return ordered.size();
    }

    Optional<TaskDef> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(tasks.get(id.toLowerCase(Locale.ROOT)));
    }

    /** Tasks reading this counter key (O(1) for event handlers). */
    List<TaskDef> byCounter(String counterKey) {
        return byCounter.getOrDefault(counterKey, List.of());
    }

    /** True if some task reads this counter (others are not tracked at all). */
    boolean tracks(String counterKey) {
        return byCounter.containsKey(counterKey);
    }

    List<TaskDef> byUnlock(String unlockKey) {
        return unlockKey == null ? List.of() : byUnlock.getOrDefault(unlockKey, List.of());
    }

    Set<String> unlockKeys() {
        return Collections.unmodifiableSet(new TreeSet<>(byUnlock.keySet()));
    }

    /** Tiers that have tasks, lowest first. */
    List<Integer> tiers() {
        return List.copyOf(new TreeSet<>(byTier.keySet()));
    }

    List<TaskDef> inTier(int tier) {
        return byTier.getOrDefault(tier, List.of());
    }

    /** Reach tasks (skill, pillar, island level). */
    List<TaskDef> reach() {
        return reach;
    }

    // ------------------------------------------------------------------ loading

    static TaskCatalog load(ConfigurationSection yaml, Checks checks, Consumer<String> problems) {
        Map<String, TaskDef> out = new LinkedHashMap<>();
        ConfigurationSection list = yaml == null ? null : yaml.getConfigurationSection(SECTION);
        if (list == null) {
            problems.accept(FILE + ": no 'tasks' section, no tasks loaded");
            return new TaskCatalog(out);
        }
        Set<String> seen = new HashSet<>();
        for (String id : list.getKeys(false)) {
            List<String> errors = new ArrayList<>();
            ConfigurationSection s = list.getConfigurationSection(id);
            TaskDef def = null;
            if (s == null) {
                errors.add("is not a section");
            } else {
                def = parse(id, s, checks, errors);
            }
            if (!seen.add(id.toLowerCase(Locale.ROOT))) {
                errors.add("id is used twice");
            }
            if (def == null || !errors.isEmpty()) {
                errors.forEach(e -> problems.accept(FILE + " → tasks." + id + ": " + e + " (skipped)"));
                continue;
            }
            if (s.getBoolean("enabled", true)) {
                out.put(def.id(), def);
            }
        }
        return new TaskCatalog(out);
    }

    static TaskDef parse(String id, ConfigurationSection s, Checks checks, List<String> errors) {
        if (!ID.matcher(id).matches()) {
            errors.add("id must be a-z, 0-9 and _ (max 48)");
        }
        int tier = s.getInt("tier", 0);
        if (tier < 1 || tier > MAX_TIER) {
            errors.add("tier must be 1-" + MAX_TIER);
        }
        TaskType type = TaskType.byId(s.getString("type")).orElse(null);
        if (type == null) {
            errors.add("type must be one of mine, chop, farm, kill, craft, sell, earn, deposit, deliver, skill-level, pillar-level, island-level");
            return null;
        }
        String target = s.getString("target", TaskType.ANY).strip().toLowerCase(Locale.ROOT);
        if (target.startsWith("minecraft:")) {
            target = target.substring("minecraft:".length());
        }
        String targetError = checkTarget(type, target, checks);
        if (targetError != null) {
            errors.add(targetError);
        }
        long amount = s.getLong("amount", 0);
        if (amount < 1) {
            errors.add("amount must be at least 1");
        }
        List<String> unlocks = new ArrayList<>();
        for (String u : s.getStringList("unlocks")) {
            String key = u.strip().toLowerCase(Locale.ROOT);
            if (!UNLOCK.matcher(key).matches()) {
                errors.add("unlock key '" + u + "' must be a-z, 0-9, _ . : - (max 64)");
            } else if (!unlocks.contains(key)) {
                unlocks.add(key);
            }
        }
        long crystals = s.getLong("rewards.crystals", 0);
        if (crystals < 0) {
            errors.add("rewards.crystals must be 0 or more");
        }
        double pillarXp = s.getDouble("rewards.pillar-xp", 0);
        Pillar pillar = null;
        if (pillarXp < 0 || !Double.isFinite(pillarXp)) {
            errors.add("rewards.pillar-xp must be 0 or more");
        } else if (pillarXp > 0) {
            pillar = Pillar.byId(s.getString("rewards.pillar", "")).orElse(null);
            if (pillar == null) {
                errors.add("rewards.pillar must be mining, foraging, farming, combat or conquest when rewards.pillar-xp is set");
            }
        }
        String icon = s.getString("icon", "").strip().toLowerCase(Locale.ROOT);
        if (!icon.isEmpty() && !checks.item().test(icon)) {
            errors.add("icon '" + icon + "' is not an item");
            icon = "";
        }
        String name = s.getString("name", "").strip();
        if (visible(name).length() > MAX_NAME) {
            errors.add("name must be at most " + MAX_NAME + " visible characters");
        }
        if (!errors.isEmpty()) {
            return null;
        }
        TaskInfo info = new TaskInfo(id.toLowerCase(Locale.ROOT), type, target, amount, tier, unlocks, crystals, pillar, pillarXp);
        return new TaskDef(info, icon.isEmpty() ? null : icon, name.isEmpty() ? null : name);
    }

    /** Null when the target fits the type, else the problem. */
    static String checkTarget(TaskType type, String target, Checks checks) {
        if (type.anyOnly()) {
            return TaskType.ANY.equals(target) ? null : "target of " + type.id() + " must be 'any' (or left out)";
        }
        boolean any = TaskType.ANY.equals(target);
        return switch (type) {
            case MINE, CHOP, FARM -> any || (OBJECT.matcher(target).matches() && checks.block().test(target)) ? null
                    : "target '" + target + "' is not a block";
            case KILL -> any || (OBJECT.matcher(target).matches() && checks.entity().test(target)) ? null
                    : "target '" + target + "' is not an entity";
            case CRAFT -> any || (OBJECT.matcher(target).matches() && checks.item().test(target)) ? null
                    : "target '" + target + "' is not an item";
            case DELIVER -> !any && (CUSTOM.matcher(target).matches() || (OBJECT.matcher(target).matches() && checks.item().test(target))) ? null
                    : "target '" + target + "' must be an item id or custom:<id>";
            case SKILL_LEVEL -> SkillType.byId(target).isPresent() ? null : "target '" + target + "' is not a skill";
            case PILLAR_LEVEL -> Pillar.byId(target).isPresent() ? null : "target '" + target + "' is not a pillar";
            default -> null;
        };
    }

    static String visible(String miniMessage) {
        return miniMessage == null ? "" : miniMessage.replaceAll("<[^<>]*>", "");
    }
}
