package net.skybornmc.skyblock.common.text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A small cache of rendered values (usually MiniMessage {@code Component}s) by a text key, valid for one
 * {@link Messages#generation()} (PERF7). A texts or style reload changes the generation, so every entry built before it
 * is rebuilt on its next use. The key must name <b>every</b> input of the value (raw text, placeholder values, Bedrock or
 * Java); values that depend on a player placeholder ({@code <ph:...>}) must not be cached here.
 *
 * <p>Thread-safe (the tab list reads it on the main thread, placeholders may be read from other threads). Bounded: when
 * it holds {@code max} entries it starts over, so keys with changing parts (online count, ping) never grow it without
 * limit. Pure, unit-tested.</p>
 *
 * @param <V> the cached value
 */
public final class TextMemo<V> {

    private record Hit<V>(long generation, V value) {
    }

    private final int max;
    private final Map<String, Hit<V>> entries = new ConcurrentHashMap<>();

    public TextMemo(int max) {
        this.max = Math.max(1, max);
    }

    /**
     * The value cached under {@code key} for {@code generation}, else {@code build.apply(key)} (then cached). A build that
     * returns {@code null} is not cached.
     */
    public V get(long generation, String key, Function<String, V> build) {
        Hit<V> hit = entries.get(key);
        if (hit != null && hit.generation() == generation) {
            return hit.value();
        }
        V value = build.apply(key);
        if (value != null) {
            if (entries.size() >= max) {
                entries.clear();
            }
            entries.put(key, new Hit<>(generation, value));
        }
        return value;
    }

    /** Forgets everything (config reload of the owner). */
    public void clear() {
        entries.clear();
    }

    /** Entries currently held (tests). */
    public int size() {
        return entries.size();
    }
}
