package net.skybornmc.skyblock.menus;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The help content from {@code help.yml}: categories → topics → pages. Texts are MiniMessage with the style tags and
 * are rendered per viewer. Topic ids are what the "?" and Help buttons of every menu open ({@code /help <topic>}).
 */
public final class HelpBook {

    /** {@code active} = clickable; {@code coming-soon} = locked (feature not built yet). */
    public enum Status { ACTIVE, COMING_SOON }

    public record Page(String title, List<String> lines) {
    }

    /**
     * @param commandCategories help-index categories listed under "Commands" in this topic (e.g. {@code Teleport})
     * @param takeMeThere       command run by "Take me there" (without /), or empty
     */
    public record Topic(String id, Material icon, String name, List<String> summary, List<Page> pages,
                        List<String> commandCategories, String takeMeThere, List<String> aliases) {
    }

    /** @param allCommands true for the special "Commands" category (lists every command the viewer may use) */
    public record Category(String id, Material icon, Status status, String name, List<String> lore, List<String> topics,
                           boolean allCommands, int slot) {
    }

    /** The command of a goal button that runs the shared next step ({@code NextStepService}) instead of a command. */
    public static final String NEXT_STEP = "next-step";
    /** Most goals the "What do you want to do?" screen shows (three rows of two). */
    public static final int MAX_GOALS = 6;
    /** Most action buttons next to one goal card. */
    public static final int MAX_GOAL_BUTTONS = 2;

    /**
     * One action next to a goal card (plan 4 Nov C §1).
     *
     * @param icon    a vanilla item id (resolved when drawn)
     * @param command run as the player (without /), or {@link #NEXT_STEP}
     * @param action  what a click does, for {@code » CLICK to <action>} ("go to the farm")
     */
    public record GoalButton(String icon, String name, String action, String command) {
        /** True for the shared next step button. */
        public boolean nextStep() {
            return NEXT_STEP.equalsIgnoreCase(command.strip());
        }
    }

    /**
     * One card of {@code /help} "What do you want to do?" (plan 4 Nov C §1): a bold name, a muted subtitle, at most three
     * lore lines ({@code <next>} = the shared next step), and 1-2 buttons that do something.
     *
     * @param fallback the command the next-step button runs while there is no step (e.g. {@code menu}); empty = none
     */
    public record Goal(String id, String icon, String name, String subtitle, List<String> lore, List<GoalButton> buttons,
                       String fallback) {
    }

    private final Map<String, Category> categories;
    private final Map<String, Topic> topics;
    private final Map<String, String> aliases;
    private final List<Goal> goals;

    private HelpBook(Map<String, Category> categories, Map<String, Topic> topics, Map<String, String> aliases, List<Goal> goals) {
        this.categories = categories;
        this.topics = topics;
        this.aliases = aliases;
        this.goals = goals;
    }

    static HelpBook empty() {
        return new HelpBook(Map.of(), Map.of(), Map.of(), List.of());
    }

    /** Parses {@code help.yml}; problems (unknown icons, missing topics) are reported and skipped. */
    static HelpBook parse(YamlConfiguration yaml, Consumer<String> problem) {
        Map<String, Topic> topics = new LinkedHashMap<>();
        Map<String, String> aliases = new LinkedHashMap<>();
        ConfigurationSection ts = yaml.getConfigurationSection("topics");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                ConfigurationSection t = ts.getConfigurationSection(id);
                if (t == null) {
                    continue;
                }
                List<Page> pages = new ArrayList<>();
                for (Map<?, ?> raw : t.getMapList("pages")) {
                    Object title = raw.get("title");
                    Object lines = raw.get("lines");
                    List<String> list = new ArrayList<>();
                    if (lines instanceof List<?> l) {
                        l.forEach(o -> list.add(String.valueOf(o)));
                    }
                    pages.add(new Page(title == null ? "" : String.valueOf(title), List.copyOf(list)));
                }
                Topic topic = new Topic(id, material(t.getString("icon"), Material.BOOK, "topics." + id, problem),
                        t.getString("name", id), t.getStringList("summary"), List.copyOf(pages),
                        t.getStringList("commands"), t.getString("take-me-there", ""), t.getStringList("aliases"));
                topics.put(id, topic);
                for (String alias : topic.aliases()) {
                    aliases.put(alias.toLowerCase(Locale.ROOT), id);
                }
            }
        }
        Map<String, Category> categories = new LinkedHashMap<>();
        ConfigurationSection cs = yaml.getConfigurationSection("categories");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection c = cs.getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                List<String> list = new ArrayList<>();
                for (String topic : c.getStringList("topics")) {
                    if (topics.containsKey(topic)) {
                        list.add(topic);
                    } else {
                        problem.accept("help.yml → categories." + id + ": unknown topic '" + topic + "' (skipped)");
                    }
                }
                Status status = "coming-soon".equalsIgnoreCase(c.getString("status", "active")) ? Status.COMING_SOON : Status.ACTIVE;
                categories.put(id, new Category(id, material(c.getString("icon"), Material.BOOK, "categories." + id, problem), status,
                        c.getString("name", id), c.getStringList("lore"), List.copyOf(list), c.getBoolean("all-commands", false),
                        c.getInt("slot", -1)));
            }
        }
        return new HelpBook(java.util.Collections.unmodifiableMap(categories), java.util.Collections.unmodifiableMap(topics),
                java.util.Collections.unmodifiableMap(aliases), goals(yaml.getConfigurationSection("goals"), problem));
    }

    /** {@code goals:} in file order; {@code show: false} hides one; more than {@link #MAX_GOALS} are reported and cut. */
    static List<Goal> goals(ConfigurationSection gs, Consumer<String> problem) {
        List<Goal> out = new ArrayList<>();
        if (gs == null) {
            return List.of();
        }
        for (String id : gs.getKeys(false)) {
            ConfigurationSection g = gs.getConfigurationSection(id);
            if (g == null || !g.getBoolean("show", true)) {
                continue;
            }
            if (out.size() == MAX_GOALS) {
                problem.accept("help.yml → goals: at most " + MAX_GOALS + " are shown, '" + id + "' and later ones are skipped");
                break;
            }
            List<GoalButton> buttons = new ArrayList<>();
            for (Map<?, ?> raw : g.getMapList("buttons")) {
                Object command = raw.get("command");
                if (command == null || String.valueOf(command).isBlank()) {
                    problem.accept("help.yml → goals." + id + ".buttons: a button without a command (skipped)");
                    continue;
                }
                if (buttons.size() == MAX_GOAL_BUTTONS) {
                    problem.accept("help.yml → goals." + id + ".buttons: at most " + MAX_GOAL_BUTTONS + " (the rest are skipped)");
                    break;
                }
                buttons.add(new GoalButton(text(raw.get("icon"), "compass"), text(raw.get("name"), id),
                        text(raw.get("action"), "open"), String.valueOf(command).strip().replaceFirst("^/+", "")));
            }
            List<String> lore = g.getStringList("lore");
            if (lore.size() > 3) {
                problem.accept("help.yml → goals." + id + ".lore: at most 3 lines (the rest are cut)");
                lore = lore.subList(0, 3);
            }
            out.add(new Goal(id, g.getString("icon", "book"), g.getString("name", id), g.getString("subtitle", ""),
                    List.copyOf(lore), List.copyOf(buttons), g.getString("fallback-command", "").strip()));
        }
        return List.copyOf(out);
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    /** The goals of the {@code /help} start screen, in file order (at most {@link #MAX_GOALS}). */
    public List<Goal> goals() {
        return goals;
    }

    private static Material material(String name, Material fallback, String where, Consumer<String> problem) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isItem()) {
            problem.accept("help.yml → " + where + ".icon: unknown item '" + name + "' (using " + fallback.getKey().getKey() + ")");
            return fallback;
        }
        return m;
    }

    /** Categories in file order. */
    public List<Category> categories() {
        return List.copyOf(categories.values());
    }

    public Optional<Category> category(String id) {
        return Optional.ofNullable(categories.get(id.toLowerCase(Locale.ROOT)));
    }

    /** Topic by id or alias (case-insensitive). */
    public Optional<Topic> topic(String idOrAlias) {
        String key = idOrAlias.toLowerCase(Locale.ROOT);
        Topic topic = topics.get(key);
        if (topic == null && aliases.containsKey(key)) {
            topic = topics.get(aliases.get(key));
        }
        return Optional.ofNullable(topic);
    }

    /** The category a topic belongs to (first match), for the Back button and breadcrumbs. */
    public Optional<Category> categoryOf(Topic topic) {
        return categories.values().stream().filter(c -> c.topics().contains(topic.id())).findFirst();
    }

    /** Every id usable after {@code /help}: topics, aliases and categories (for tab completion). */
    public List<String> ids() {
        List<String> ids = new ArrayList<>(topics.keySet());
        ids.addAll(aliases.keySet());
        ids.addAll(categories.keySet());
        return ids.stream().distinct().sorted().toList();
    }
}
