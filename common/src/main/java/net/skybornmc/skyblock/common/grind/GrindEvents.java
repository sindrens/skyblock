package net.skybornmc.skyblock.common.grind;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;

/**
 * Owner 6 Nov 2026: short random events in the grind areas ({@code /farm}, {@code /mine}, later {@code /fish}). A round
 * runs 2 to 4 events at the same time for about 30 seconds, then a 30 to 60 second break. Published by the essentials
 * module; the island tools read the multipliers ({@code core.services().optional(GrindEvents.class)}).
 */
public interface GrindEvents {

    /** The six events. */
    enum Kind {
        DOUBLE_XP,
        DOUBLE_DROPS,
        DOUBLE_SHARDS,
        INSTANT_REGROW,
        PROC_FRENZY,
        GOLDEN_BLOCKS;

        /** Lowercase id for configs and messages ({@code double_xp}). */
        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The events running right now (empty during a break). */
    Set<Kind> active();

    /** True when {@code kind} runs and the player is in a grind area (its world). */
    boolean active(Player player, Kind kind);

    /** The multiplier of {@code kind} for this player: its configured value while active for them, else 1. */
    double multiplier(Player player, Kind kind);

    static Optional<GrindEvents> of(ServiceRegistry services) {
        return services == null ? Optional.empty() : services.optional(GrindEvents.class);
    }

    /** The multiplier, or 1 when the service is missing. */
    static double multiplier(ServiceRegistry services, Player player, Kind kind) {
        return of(services).map(e -> e.multiplier(player, kind)).orElse(1.0);
    }
}
