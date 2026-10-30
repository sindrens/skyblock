package net.skybornmc.skyblock.perks;

import java.util.Locale;
import java.util.Optional;

/**
 * Owner answer 377: {@code /ec} and {@code /craft} don't work in the warzone, in a boss arena or for a while after combat
 * (combat tag, {@code perks.yml → blocked.combat-seconds}, default 10). Pure; the module feeds it the facts.
 */
final class PerkBlocks {

    /** Why a perk menu can't open; texts: {@code blocked.<key>} in messages/perks.yml. */
    enum Reason {
        WARZONE, BOSS_ARENA, COMBAT;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private PerkBlocks() {
    }

    /**
     * @param inBlockedWorld the player stands in a world of {@code blocked.worlds} (the warzone)
     * @param inBossArena    the player stands in a boss arena (and {@code blocked.boss-arena} is on)
     * @param combatLeftMs   milliseconds left of the combat tag ({@code blocked.combat-seconds}), 0 when not tagged
     */
    static Optional<Reason> check(boolean inBlockedWorld, boolean inBossArena, long combatLeftMs) {
        if (inBlockedWorld) {
            return Optional.of(Reason.WARZONE);
        }
        if (inBossArena) {
            return Optional.of(Reason.BOSS_ARENA);
        }
        if (combatLeftMs > 0) {
            return Optional.of(Reason.COMBAT);
        }
        return Optional.empty();
    }
}
