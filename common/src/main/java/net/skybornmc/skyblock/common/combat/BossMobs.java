package net.skybornmc.skyblock.common.combat;

import org.bukkit.entity.Entity;

/**
 * Marks boss mobs (Depths mini-bosses now, world bosses later) with one scoreboard tag, so other modules can tell a boss
 * from a normal mob at hit time without depending on the module that spawned it. Enchants use it: Giant Slayer only
 * hurts bosses more, Executioner never kills a boss (ENCHANTS §6.5).
 */
public final class BossMobs {

    /** Scoreboard tag on every boss mob. Modules that spawn bosses add it in the spawn callback. */
    public static final String TAG = "skyblock_boss";

    private BossMobs() {
    }

    public static boolean isBoss(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }
}
