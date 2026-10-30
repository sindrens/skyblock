package net.skybornmc.skyblock.common.combat;

import java.util.UUID;

/**
 * Combat tag (WARDROBE §7.3, LD3): a player is "in combat" for a while after they dealt or took damage from another
 * player or a hostile mob. Used by {@code /loadout} and wardrobe equips; profile switches and island storage can use
 * the same service. Registered by the core ({@code core.yml → combat.*}); read with
 * {@code core.services().optional(CombatTagService.class)}. Main thread or any thread.
 */
public interface CombatTagService {

    /** The default tag length ({@code core.yml → combat.tag-seconds}). */
    int tagSeconds();

    /** Last combat moment (epoch millis), or 0 if the player was not in combat since they joined. */
    long lastCombat(UUID player);

    /** Milliseconds left of a tag that lasts {@code seconds} (a module's own length), 0 if not tagged. */
    long remainingMillis(UUID player, int seconds);

    /** Milliseconds left of the default tag. */
    default long remainingMillis(UUID player) {
        return remainingMillis(player, tagSeconds());
    }

    default boolean inCombat(UUID player, int seconds) {
        return remainingMillis(player, seconds) > 0;
    }

    default boolean inCombat(UUID player) {
        return inCombat(player, tagSeconds());
    }

    /** Tags a player now (e.g. a module's own combat action that is not a damage event). */
    void tag(UUID player);

    /** No combat tracking (tests, or the service missing): nobody is ever in combat. */
    CombatTagService NONE = new CombatTagService() {
        @Override
        public int tagSeconds() {
            return 0;
        }

        @Override
        public long lastCombat(UUID player) {
            return 0;
        }

        @Override
        public long remainingMillis(UUID player, int seconds) {
            return 0;
        }

        @Override
        public void tag(UUID player) {
        }
    };
}
