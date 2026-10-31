package net.skybornmc.skyblock.common.mob;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One ability of a mob as configured ({@code {id, cooldown, chance, params}}). Values that were not set are
 * {@code NaN} (cooldown, chance) or missing from the maps; {@link #withDefaults} fills them from a defaults entry and
 * {@link #num}/{@link #text} fall back to the code defaults of {@link AbilityId}.
 *
 * @param id              which ability
 * @param cooldownSeconds seconds before it can be used again ({@code NaN} = default)
 * @param chance          chance per try once off cooldown, 0..1 ({@code NaN} = default)
 * @param numbers         number params that were set (validated against {@link AbilityId#params()})
 * @param texts           text params that were set (lower case, one of the param's choices)
 */
public record AbilitySpec(AbilityId id, double cooldownSeconds, double chance, Map<String, Double> numbers,
                          Map<String, String> texts) {

    public AbilitySpec {
        Objects.requireNonNull(id, "id");
        numbers = numbers == null ? Map.of() : Map.copyOf(numbers);
        texts = texts == null ? Map.of() : Map.copyOf(texts);
    }

    /** An ability with every value at its default. */
    public static AbilitySpec of(AbilityId id) {
        return new AbilitySpec(id, Double.NaN, Double.NaN, Map.of(), Map.of());
    }

    /** This spec with every unset value taken from {@code defaults} (same ability; {@code null} = unchanged). */
    public AbilitySpec withDefaults(AbilitySpec defaults) {
        if (defaults == null || defaults.id != id) {
            return this;
        }
        Map<String, Double> n = new LinkedHashMap<>(defaults.numbers);
        n.putAll(numbers);
        Map<String, String> t = new LinkedHashMap<>(defaults.texts);
        t.putAll(texts);
        return new AbilitySpec(id, Double.isNaN(cooldownSeconds) ? defaults.cooldownSeconds : cooldownSeconds,
                Double.isNaN(chance) ? defaults.chance : chance, n, t);
    }

    /** Cooldown in seconds (set value or code default). */
    public double cooldown() {
        return Double.isNaN(cooldownSeconds) ? id.defaultCooldownSeconds() : cooldownSeconds;
    }

    /** Cooldown in server ticks (at least 1 for cooldown abilities). */
    public int cooldownTicks() {
        return id.healthTriggered() ? 0 : (int) Math.max(1, Math.round(cooldown() * 20));
    }

    /** Chance per try (set value or code default). */
    public double rollChance() {
        return Double.isNaN(chance) ? id.defaultChance() : chance;
    }

    /** A number param (set value or code default; 0 for unknown keys). */
    public double num(String key) {
        Double v = numbers.get(key);
        if (v != null) {
            return v;
        }
        AbilityId.Param p = id.param(key);
        return p == null || p.text() ? 0 : p.def();
    }

    /** A text param (set value or code default; empty for unknown keys). */
    public String text(String key) {
        String v = texts.get(key);
        if (v != null) {
            return v;
        }
        AbilityId.Param p = id.param(key);
        return p == null ? "" : p.textDefault();
    }
}
