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

    private final Map<String, Category> categories;
    private final Map<String, Topic> topics;
    private final Map<String, String> aliases;

    private HelpBook(Map<String, Category> categories, Map<String, Topic> topics, Map<String, String> aliases) {
        this.categories = categories;
        this.topics = topics;
        this.aliases = aliases;
    }

    static HelpBook empty() {
        return new HelpBook(Map.of(), Map.of(), Map.of());
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
                java.util.Collections.unmodifiableMap(aliases));
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
