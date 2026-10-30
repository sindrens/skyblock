package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server defaults of the {@code /is settings} values (ISL2, pure), from {@code islands.yml → flags.defaults.*}. The
 * owner's defaults: mob spawning off, animal spawning on, fire spread off, explosions off, leaf decay on, crop
 * trampling off, notifications on, visitor fly on, weather sunny, time day. Invalid values fall back to these and are
 * reported.
 */
public final class FlagDefaults {

    /** Parse result with the console problems. */
    public record Result(FlagDefaults defaults, List<String> problems) {
    }

    private final String[] values = new String[IslandFlag.cached().length];

    private FlagDefaults() {
        for (IslandFlag flag : IslandFlag.cached()) {
            values[flag.ordinal()] = owner(flag);
        }
    }

    /** The owner's defaults (round 3), also the fallback for invalid config values. */
    static String owner(IslandFlag flag) {
        return switch (flag) {
            case MOB_SPAWNING, FIRE_SPREAD, EXPLOSIONS, CROP_TRAMPLING, VISITOR_PICKUP -> IslandFlag.OFF;
            case ANIMAL_SPAWNING, LEAF_DECAY, NOTIFICATIONS, VISITOR_FLY -> IslandFlag.ON;
            case WEATHER -> "sunny";
            case TIME -> "day";
        };
    }

    /** The owner's defaults without any config. */
    public static FlagDefaults owner() {
        return new FlagDefaults();
    }

    /** Builds the defaults from flag key → typed value ({@code true}/{@code false} or a choice). Unknown values are reported. */
    public static Result parse(Map<IslandFlag, String> configured) {
        FlagDefaults defaults = new FlagDefaults();
        List<String> problems = new ArrayList<>();
        if (configured != null) {
            configured.forEach((flag, raw) -> {
                if (flag == null || flag.virtual()) {
                    return;
                }
                var value = flag.parse(raw);
                if (value.isPresent()) {
                    defaults.values[flag.ordinal()] = value.get();
                } else {
                    problems.add("flags.defaults." + flag.key() + ": '" + raw + "' is not one of " + flag.choices() + " (using "
                            + owner(flag) + ")");
                }
            });
        }
        return new Result(defaults, List.copyOf(problems));
    }

    public String value(IslandFlag flag) {
        return values[flag.ordinal()];
    }
}
