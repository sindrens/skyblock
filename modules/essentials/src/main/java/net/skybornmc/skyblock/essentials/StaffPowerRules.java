package net.skybornmc.skyblock.essentials;

import java.util.Collection;
import java.util.Locale;

/**
 * Staff powers (owner answers 331, 333, 334), pure and unit-tested:
 * <ul>
 *   <li>331: creative mode only for builders and admins (the creative game mode permission; mods use spectator) and only
 *       in the creative worlds ({@code staff-powers.creative-worlds}: spawn, the Depths, builder areas).</li>
 *   <li>333: god mode and fly only for a player in staff mode or build mode, heal only by staff in staff or build mode
 *       (the console always may).</li>
 *   <li>334: staff play normally on the same account: outside staff and build mode their powers are off, and god mode or
 *       fly they had are taken away as soon as both modes are off.</li>
 * </ul>
 */
final class StaffPowerRules {

    /** The powers that need staff or build mode. */
    enum Power {
        GOD, FLY, HEAL;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Result of a creative mode check; texts {@code creative.<key>}. */
    enum Creative {
        OK, NOT_ALLOWED, WRONG_PLACE;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** Result of a power check; texts {@code powers.<key>}. */
    enum Check {
        OK, NEED_MODE, TARGET_NEEDS_MODE;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private StaffPowerRules() {
    }

    /** Case-insensitive: is {@code world} one of the creative worlds? */
    static boolean creativeWorld(String world, Collection<String> creativeWorlds) {
        if (world == null || creativeWorlds == null) {
            return false;
        }
        for (String w : creativeWorlds) {
            if (w != null && w.strip().equalsIgnoreCase(world)) {
                return true;
            }
        }
        return false;
    }

    /**
     * May a player switch to (or stay in) creative?
     *
     * @param enforced          {@code staff-powers.enabled}; off = only the permission counts (old behaviour)
     * @param mayUseCreative    the player has the creative game mode permission (builders and admins)
     * @param world             the world the player is in
     * @param creativeWorlds    {@code staff-powers.creative-worlds}
     */
    static Creative creative(boolean enforced, boolean mayUseCreative, String world, Collection<String> creativeWorlds) {
        if (!mayUseCreative) {
            return Creative.NOT_ALLOWED;
        }
        if (enforced && !creativeWorld(world, creativeWorlds)) {
            return Creative.WRONG_PLACE;
        }
        return Creative.OK;
    }

    /**
     * May {@code power} be used now?
     *
     * @param enforced     {@code staff-powers.enabled}
     * @param console      the console (or a command block) runs it: no mode needed for the sender
     * @param senderInMode the staff member is in staff mode or build mode
     * @param self         the staff member targets themselves
     * @param targetInMode the target is in staff mode or build mode (same as the sender for self)
     */
    static Check power(Power power, boolean enforced, boolean console, boolean senderInMode, boolean self, boolean targetInMode) {
        if (!enforced) {
            return Check.OK;
        }
        if (!console && !senderInMode) {
            return Check.NEED_MODE;
        }
        if (power != Power.HEAL && !self && !targetInMode) {
            // God mode and fly are staff powers: a normal player never gets them.
            return Check.TARGET_NEEDS_MODE;
        }
        return Check.OK;
    }

    /** A god mode or /fly that was granted is kept only while the player is in staff mode or build mode. */
    static boolean keep(boolean enforced, boolean inMode) {
        return !enforced || inMode;
    }

    /** What {@code /fly} does (FIXES1). */
    enum FlyAction {
        /** Creative or spectator: they always fly; nothing changes. */
        GAME_MODE,
        /** Give {@code /fly} (checked against the staff/build mode rule first). */
        TURN_ON,
        /** Take {@code /fly} away. */
        TURN_OFF
    }

    /**
     * The {@code /fly} toggle reads only its own grant, never {@code allowFlight}: island fly, staff mode or freeze can
     * make {@code allowFlight} true too, and reading it made {@code /fly} "turn off" a flight it never gave (FIXES1 root
     * cause: on an island with island fly, every {@code /fly} printed "Flying off." and island fly gave flight back).
     *
     * @param ownGrant           {@code /fly} is on for the player
     * @param creativeOrSpectator the player is in creative or spectator
     */
    static FlyAction fly(boolean ownGrant, boolean creativeOrSpectator) {
        if (creativeOrSpectator) {
            return FlyAction.GAME_MODE;
        }
        return ownGrant ? FlyAction.TURN_OFF : FlyAction.TURN_ON;
    }

    /** Result text of {@code /fly}, chosen from the real state after the change ({@code fly.<key>}). */
    enum FlyText {
        ENABLED, DISABLED, DISABLED_STILL_FLYING;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * Picks the text from what the player really has now: {@code /fly} on, or off with flight still given by another
     * source (island fly, staff mode), or off with no flight at all.
     */
    static FlyText flyText(boolean ownGrantNow, boolean canFlyNow) {
        if (ownGrantNow) {
            return FlyText.ENABLED;
        }
        return canFlyNow ? FlyText.DISABLED_STILL_FLYING : FlyText.DISABLED;
    }
}
