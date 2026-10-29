package net.skybornmc.skyblock.common.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Internal placeholders ({@code balance}, {@code rank}, {@code island_level} …) used by scoreboard, tab, chat, menus and
 * messages ({@code <ph:balance>}), and exported to PlaceholderAPI as {@code %phoenix_<key>%} when it is installed.
 *
 * <p>Keys are lowercase {@code [a-z0-9_]}. Each key records its owner (module id) so it disappears when the module
 * disables. A failing placeholder never breaks the line it is in: it shows an empty value and is logged once.</p>
 */
public final class PlaceholderRegistry {

    private record Entry(String owner, Placeholder placeholder) {
    }

    /** MiniMessage tag name: {@code <ph:key>}. */
    public static final String TAG = "ph";

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Set<String> reportedErrors = ConcurrentHashMap.newKeySet();
    private final Logger log;

    public PlaceholderRegistry(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    public void register(String owner, String key, Placeholder placeholder) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(placeholder, "placeholder");
        if (key == null || !key.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid placeholder key '" + key + "' (use a-z, 0-9, _)");
        }
        Entry previous = entries.putIfAbsent(key, new Entry(owner, placeholder));
        if (previous != null) {
            throw new IllegalStateException("Placeholder '" + key + "' is already registered by '" + previous.owner() + "'");
        }
    }

    /** Plain-text placeholder shortcut. */
    public void registerText(String owner, String key, Function<Player, String> value) {
        register(owner, key, Placeholder.text(value));
    }

    public void unregisterAll(String owner) {
        entries.values().removeIf(e -> e.owner().equals(owner));
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    /** All keys with their owner, sorted (for docs and a staff list command). */
    public Map<String, String> keys() {
        Map<String, String> out = new TreeMap<>();
        entries.forEach((k, e) -> out.put(k, e.owner()));
        return out;
    }

    /** Plain value, or empty if the key is unknown. */
    public Optional<String> text(Player player, String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(entry.placeholder().text(player));
        } catch (RuntimeException e) {
            report(key, e);
            return Optional.of("");
        }
    }

    /** Rich value, or empty if the key is unknown. */
    public Optional<Component> component(Player player, String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(entry.placeholder().component(player));
        } catch (RuntimeException e) {
            report(key, e);
            return Optional.of(Component.empty());
        }
    }

    /**
     * MiniMessage resolver for {@code <ph:key>} in the context of {@code player} (may be null). Unknown keys render as
     * {@code [?key]} so typos in messages files are visible.
     */
    public TagResolver resolver(Player player) {
        return TagResolver.resolver(TAG, (args, ctx) -> {
            String key = args.popOr("<ph:key> needs a key").value();
            return Tag.selfClosingInserting(component(player, key).orElse(Component.text("[?" + key + "]")));
        });
    }

    private void report(String key, RuntimeException e) {
        if (reportedErrors.add(key)) {
            log.warning("Placeholder '" + key + "' failed: " + e);
        }
    }
}
