package net.skybornmc.skyblock.islands;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Per-island settings of {@code /is settings} (ISL2, owner round 3; pure). Toggles are {@code on}/{@code off};
 * {@link #WEATHER} and {@link #TIME} pick one of their values and are shown client-side (player weather/time) to
 * everyone on the island. Defaults live in {@code islands.yml → flags.defaults.*}; islands only store what they changed
 * ({@code island_settings}). The stored key never changes; the enum order may. Mob stacking is deliberately not a
 * setting (owner).
 */
public enum IslandFlag {
    /** Hostile mobs spawn naturally (spawners always work). Default off. */
    MOB_SPAWNING("mob-spawning"),
    /** Animals spawn naturally. Default on. */
    ANIMAL_SPAWNING("animal-spawning"),
    /** Fire spreads and burns blocks. Default off. */
    FIRE_SPREAD("fire-spread"),
    /** Explosions (TNT, creepers, crystals, beds …) break blocks. Default off. */
    EXPLOSIONS("explosions"),
    /** Leaves decay when no log is near. Default on. */
    LEAF_DECAY("leaf-decay"),
    /** Players and mobs trample farmland. Default off. */
    CROP_TRAMPLING("crop-trampling"),
    /** Visitors pick up items: the visitor row's "pickup" permission of the matrix (one value, two places). Default off. */
    VISITOR_PICKUP("visitor-pickup"),
    /** Members hear about visitors arriving, leaving and knocking on a locked island (each member can still mute). Default on. */
    NOTIFICATIONS("notifications"),
    /** Visitors may use island fly here (they still need fly: rank permission or the Wings mastery). Default on. */
    VISITOR_FLY("visitor-fly"),
    /** Weather everyone on the island sees. Default sunny. */
    WEATHER("weather", "sunny", "rain", "storm"),
    /** Time of day everyone on the island sees ({@code real} = the server's local clock). Default day. */
    TIME("time", "day", "noon", "sunset", "night", "real");

    public static final String ON = "on";
    public static final String OFF = "off";
    private static final IslandFlag[] VALUES = values();

    private final String key;
    private final List<String> choices;
    private final boolean toggle;

    IslandFlag(String key) {
        this.key = key;
        this.choices = List.of("on", "off");
        this.toggle = true;
    }

    IslandFlag(String key, String... choices) {
        this.key = key;
        this.choices = List.of(choices);
        this.toggle = false;
    }

    /** Stable key used in islands.yml, the database, commands and message keys. */
    public String key() {
        return key;
    }

    /** True for on/off settings. */
    public boolean toggle() {
        return toggle;
    }

    /** The values this setting accepts ({@code on}/{@code off} for toggles). */
    public List<String> choices() {
        return choices;
    }

    /** True for the setting that is really a matrix permission ({@link #VISITOR_PICKUP}); nothing is stored for it. */
    public boolean virtual() {
        return this == VISITOR_PICKUP;
    }

    /**
     * The canonical value for typed input, or empty: toggles accept on/off, true/false, yes/no, enable/disable;
     * choices accept their value (case-insensitive).
     */
    public Optional<String> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (toggle()) {
            return switch (v) {
                case "on", "true", "yes", "enable", "enabled", "allow" -> Optional.of(ON);
                case "off", "false", "no", "disable", "disabled", "deny" -> Optional.of(OFF);
                default -> Optional.empty();
            };
        }
        return choices.contains(v) ? Optional.of(v) : Optional.empty();
    }

    /** The value after {@code current} (toggles flip; choices cycle), for one-click menus and {@code /is settings <flag>}. */
    public String next(String current) {
        int i = choices.indexOf(current);
        return choices.get((i + 1) % choices.size());
    }

    public static Optional<IslandFlag> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (IslandFlag flag : VALUES) {
            if (flag.key.equals(k)) {
                return Optional.of(flag);
            }
        }
        return Optional.empty();
    }

    static IslandFlag[] cached() {
        return VALUES;
    }
}
