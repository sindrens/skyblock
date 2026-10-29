package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Every player-facing text, loaded from {@code messages/<module>.yml} (MiniMessage + named placeholders). No text in code.
 *
 * <ul>
 *   <li>Bundled defaults are copied on first start; on updates, new keys are merged in and the owner's edits are kept.</li>
 *   <li>A missing key is logged once and shown as {@code [missing: module:key]} instead of crashing.</li>
 *   <li>Every text is checked against the Bedrock-safe symbol list at load time.</li>
 *   <li>A value can be a string or a list of strings (multi-line).</li>
 * </ul>
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final File folder;
    private final Style style;
    private final Logger log;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private volatile BedrockCheck bedrock = BedrockCheck.NONE;
    private volatile net.skybornmc.skyblock.common.placeholder.PlaceholderRegistry placeholders;

    private final Map<String, Map<String, String>> texts = new ConcurrentHashMap<>();
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();
    private final Map<String, List<String>> problems = new ConcurrentHashMap<>();

    public Messages(JavaPlugin plugin, Style style) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "messages");
        this.style = style;
        this.log = plugin.getLogger();
    }

    /** Enables {@code <ph:key>} in every message. */
    public void placeholders(net.skybornmc.skyblock.common.placeholder.PlaceholderRegistry registry) {
        this.placeholders = registry;
    }

    public void bedrockCheck(BedrockCheck check) {
        this.bedrock = check;
    }

    /** Loads (or reloads) {@code messages/<module>.yml}. Returns problems (empty = all good). */
    public List<String> load(String module) {
        List<String> found = new ArrayList<>();
        File file = new File(folder, module + ".yml");
        YamlConfiguration bundled = bundled(module);
        YamlConfiguration yaml = new YamlConfiguration();
        boolean changed = false;
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (Exception e) {
                String p = "messages/" + module + ".yml: not valid YAML, the previous texts stay active. "
                        + String.valueOf(e.getMessage()).lines().findFirst().orElse("");
                log.severe(p);
                problems.put(module, List.of(p));
                return List.of(p);
            }
        } else if (bundled == null) {
            log.warning("messages/" + module + ".yml does not exist and has no bundled default.");
        }
        if (bundled != null) {
            for (String key : bundled.getKeys(true)) {
                if (!bundled.isConfigurationSection(key) && !yaml.contains(key)) {
                    yaml.set(key, bundled.get(key));
                    List<String> comments = bundled.getComments(key);
                    if (!comments.isEmpty()) {
                        yaml.setComments(key, comments);
                    }
                    changed = true;
                }
            }
            if (yaml.options().getHeader().isEmpty()) {
                yaml.options().setHeader(bundled.options().getHeader());
            }
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isList(key)) {
                map.put(key, String.join("<newline>", yaml.getStringList(key)));
            } else if (!yaml.isConfigurationSection(key)) {
                map.put(key, yaml.getString(key, ""));
            }
        }
        map.forEach((key, text) -> {
            Set<String> unsafe = style.unsafeGlyphs(text);
            if (!unsafe.isEmpty()) {
                found.add("messages/" + module + ".yml → " + key + ": symbol(s) " + String.join(" ", unsafe)
                        + " are not in the Bedrock-safe list (style.yml → bedrock-safe-symbols)");
            }
        });
        texts.put(module, Map.copyOf(map));
        reportedMissing.removeIf(k -> k.startsWith(module + ":"));
        if (changed) {
            try {
                folder.mkdirs();
                yaml.save(file);
            } catch (IOException e) {
                found.add("messages/" + module + ".yml: could not save merged keys: " + e.getMessage());
            }
        }
        found.forEach(log::warning);
        problems.put(module, List.copyOf(found));
        return found;
    }

    /** Reloads every module loaded so far. */
    public void reloadAll() {
        for (String module : List.copyOf(texts.keySet())) {
            load(module);
        }
    }

    public Set<String> modules() {
        return texts.keySet();
    }

    public Map<String, List<String>> problems() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        problems.forEach((m, list) -> {
            if (!list.isEmpty()) {
                out.put("messages/" + m + ".yml", list);
            }
        });
        return out;
    }

    private YamlConfiguration bundled(String module) {
        InputStream in = plugin.getResource("messages/" + module + ".yml");
        if (in == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        } catch (Exception e) {
            log.severe("Bundled messages/" + module + ".yml is broken: " + e.getMessage());
            return null;
        }
    }

    /** The raw MiniMessage text, or {@code null} if the key does not exist. */
    public String raw(String module, String key) {
        Map<String, String> map = texts.get(module);
        return map == null ? null : map.get(key);
    }

    public boolean has(String module, String key) {
        return raw(module, key) != null;
    }

    /** Parses a message for {@code viewer} (Bedrock viewers get legacy colors). */
    public Component component(Audience viewer, String module, String key, TagResolver... placeholders) {
        String text = raw(module, key);
        if (text == null) {
            if (reportedMissing.add(module + ":" + key)) {
                log.warning("Missing message '" + key + "' in messages/" + module + ".yml");
            }
            return Component.text("[missing: " + module + ":" + key + "]", NamedTextColor.RED);
        }
        return parse(viewer, text, placeholders);
    }

    /** Parses any MiniMessage text with the style tags for {@code viewer}. */
    public Component parse(Audience viewer, String text, TagResolver... placeholders) {
        boolean be = isBedrock(viewer);
        var registry = this.placeholders;
        TagResolver ph = registry == null ? TagResolver.empty() : registry.resolver(viewer instanceof Player p ? p : null);
        TagResolver resolver = TagResolver.resolver(TagResolver.standard(), style.fullResolver(be, mm), ph, TagResolver.resolver(placeholders));
        try {
            return mm.deserialize(style.preprocess(text, be), resolver);
        } catch (RuntimeException e) {
            log.warning("Could not parse message '" + text + "': " + e.getMessage());
            return Component.text(text);
        }
    }

    public void send(Audience viewer, String module, String key, TagResolver... placeholders) {
        viewer.sendMessage(component(viewer, module, key, placeholders));
    }

    public void actionBar(Audience viewer, String module, String key, TagResolver... placeholders) {
        viewer.sendActionBar(component(viewer, module, key, placeholders));
    }

    /**
     * Shows a title. The key is a section with {@code title}, {@code subtitle} and optional
     * {@code fade-in}/{@code stay}/{@code fade-out} in ticks (defaults 10/60/20).
     */
    public void title(Audience viewer, String module, String key, TagResolver... placeholders) {
        Component title = has(module, key + ".title") ? component(viewer, module, key + ".title", placeholders) : Component.empty();
        Component subtitle = has(module, key + ".subtitle") ? component(viewer, module, key + ".subtitle", placeholders) : Component.empty();
        if (!has(module, key + ".title") && !has(module, key + ".subtitle")) {
            component(viewer, module, key + ".title"); // reports the missing key once
            return;
        }
        viewer.showTitle(Title.title(title, subtitle, Title.Times.times(
                ticks(module, key + ".fade-in", 10), ticks(module, key + ".stay", 60), ticks(module, key + ".fade-out", 20))));
    }

    private Duration ticks(String module, String key, int fallback) {
        String raw = raw(module, key);
        int value = fallback;
        if (raw != null) {
            try {
                value = Math.max(0, Integer.parseInt(raw.trim()));
            } catch (NumberFormatException ignored) {
                // keep the default
            }
        }
        return Duration.ofMillis(value * 50L);
    }

    private boolean isBedrock(Audience viewer) {
        return viewer instanceof Player p && bedrock.isBedrock(p.getUniqueId());
    }

    /** A view bound to one module, so callers write {@code msg.send(player, "key")}. */
    public ModuleMessages of(String module) {
        return new ModuleMessages(this, module);
    }
}
