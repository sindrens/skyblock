package net.skybornmc.skyblock.common.flight;

import org.bukkit.entity.Player;

/**
 * Feedback 4 Nov 2026 (E17): {@code /fly} for players with the rank perk {@code skyblock.fly} (not staff). It only works
 * on islands: where the player is a member, and on visited islands whose visitor-fly setting is on. Provided by the
 * islands module (island fly); essentials' {@code /fly} calls it for players without the staff fly permission.
 */
public interface PlayerFlight {

    /** What a toggle did. */
    enum Toggle {
        /** Flight is on here now. */
        ON,
        /** Flight was on and is off now (soft landing). */
        OFF,
        /** Turned on, but this spot doesn't allow it: it starts once the player is on an island that does. */
        NOT_HERE
    }

    /** Toggles the player's island fly. */
    Toggle toggle(Player player);
}
