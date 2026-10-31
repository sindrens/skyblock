package net.skybornmc.skyblock.common.mob;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The fixed list of mob abilities (plan M8.1). The behaviour is code; every number is tuned in config: a mob's own
 * {@code abilities} entry first, then {@code mobs.yml → ability-defaults.<id>}, then the defaults here. Every ability is
 * telegraphed for at least one second before it hits (ring of fake blocks, sound and action bar line), so a careful
 * player can always step away.
 *
 * <p>{@code damage} params are multipliers of the mob's own damage per hit (1.5 = one and a half hits).</p>
 */
public enum AbilityId {

    /** Ground ring around the mob; damage + knockback to players inside when it lands. */
    SLAM(8, 0.6, num("radius", 4, 1, 12), num("damage", 1.5, 0, 10), num("knockback", 1.0, 0, 4)),
    /** Jumps at the farthest player in range; damage to players still in the marked spot when it lands. */
    LEAP(10, 0.5, num("range", 12, 2, 24), num("radius", 2.5, 1, 6), num("damage", 1.2, 0, 10)),
    /** Runs in a straight line toward its target; players in the marked lane take damage. */
    CHARGE(10, 0.5, num("length", 8, 2, 16), num("width", 1.5, 0.5, 4), num("damage", 1.5, 0, 10), num("speed", 1.6, 0.2, 4)),
    /** 3-8 projectiles in a fan toward its target (each hits for the mob's damage). */
    VOLLEY(8, 0.6, num("count", 5, 3, 8), num("spread", 40, 5, 120), num("speed", 1.6, 0.5, 4)),
    /** Pulls players inside the marked ring toward the mob. */
    PULL(12, 0.5, num("radius", 8, 2, 16), num("strength", 1.0, 0.1, 3)),
    /** Calls adds from its zone's mob list (capped per mob and by the zone caps). */
    SUMMON(20, 0.5, num("count", 2, 1, 5), num("max-alive", 3, 1, 10)),
    /** Takes {@code reduction} less damage for a few seconds (shown on its name). */
    SHIELD(15, 0.4, num("seconds", 4, 1, 15), num("reduction", 0.8, 0.1, 0.95)),
    /** Fire or soul ring on the ground for a few seconds: damage each second to players inside. */
    RING(12, 0.5, num("radius", 3, 1, 8), num("seconds", 3, 1, 10), num("damage", 0.5, 0, 10), text("kind", "fire", "fire", "soul")),
    /** Short slowness, weakness or wither on players inside the marked ring. */
    CURSE(10, 0.5, num("radius", 5, 1, 12), num("seconds", 3, 1, 15), num("level", 1, 1, 3),
            text("effect", "slowness", "slowness", "weakness", "wither")),
    /** Teleports behind its target (the spot is marked first). */
    TELEPORT(10, 0.4, num("range", 12, 2, 24), num("distance", 2, 1, 4)),
    /** Once, below {@code below} of its health: faster attacks and more damage until it dies. */
    ENRAGE(0, 1.0, num("below", 0.3, 0.05, 0.95), num("damage-bonus", 0.5, 0, 3), num("speed-bonus", 0.25, 0, 2),
            num("attack-factor", 0.6, 0.2, 1));

    /**
     * One tunable value of an ability.
     *
     * @param key     config key
     * @param def     default number (numbers only)
     * @param min     lowest allowed number
     * @param max     highest allowed number
     * @param choices allowed words (text params only, first = default); empty for numbers
     */
    public record Param(String key, double def, double min, double max, List<String> choices) {

        public boolean text() {
            return !choices.isEmpty();
        }

        public String textDefault() {
            return choices.isEmpty() ? "" : choices.getFirst();
        }
    }

    /** Lowest and highest cooldown in seconds a config may set. */
    public static final double MIN_COOLDOWN = 1, MAX_COOLDOWN = 600;

    private final double cooldownSeconds;
    private final double chance;
    private final Map<String, Param> params;

    AbilityId(double cooldownSeconds, double chance, Param... params) {
        this.cooldownSeconds = cooldownSeconds;
        this.chance = chance;
        Map<String, Param> map = new LinkedHashMap<>();
        for (Param p : params) {
            map.put(p.key(), p);
        }
        this.params = Map.copyOf(map);
    }

    private static Param num(String key, double def, double min, double max) {
        return new Param(key, def, min, max, List.of());
    }

    private static Param text(String key, String def, String... choices) {
        List<String> list = Arrays.asList(choices);
        if (!list.isEmpty() && !list.getFirst().equals(def)) {
            throw new IllegalArgumentException("text default must be the first choice");
        }
        return new Param(key, 0, 0, 0, List.copyOf(list));
    }

    /** Config id ({@code slam}, {@code volley} ...). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Code default cooldown in seconds (0 for {@link #ENRAGE}, which fires once on a health threshold). */
    public double defaultCooldownSeconds() {
        return cooldownSeconds;
    }

    /** Code default chance per try once off cooldown. */
    public double defaultChance() {
        return chance;
    }

    /** True for abilities started by a health threshold instead of a cooldown roll. */
    public boolean healthTriggered() {
        return this == ENRAGE;
    }

    /** The tunable values of this ability. */
    public Map<String, Param> params() {
        return params;
    }

    public Param param(String key) {
        return params.get(key);
    }

    /** The ability with this config id, or {@code null}. */
    public static AbilityId of(String id) {
        if (id == null) {
            return null;
        }
        String clean = id.trim().toLowerCase(Locale.ROOT);
        for (AbilityId a : values()) {
            if (a.id().equals(clean)) {
                return a;
            }
        }
        return null;
    }

    /** Every config id, in order (tab completion, problem messages). */
    public static List<String> ids() {
        return Arrays.stream(values()).map(AbilityId::id).toList();
    }
}
