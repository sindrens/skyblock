package net.skybornmc.skyblock.common.skills;

import net.skybornmc.skyblock.common.audit.Actor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Player skill XP and levels (M4.5), published by the skills module: {@code core.services().optional(SkillService.class)}.
 *
 * <p>XP is a total per skill and season; the level follows from the configurable curve ({@code skills.yml}). Values of
 * online players come from a cache (cheap, never blocking); offline players are loaded with {@link #loadXp}.
 * {@link #addXp} and {@link #setXp} are main-thread only.</p>
 *
 * <p>Events: {@link SkillXpGainEvent} before XP is added (multipliers, cancel), {@link SkillLevelUpEvent} after a level
 * changed. Modules that pay combat XP themselves (custom mobs) add the scoreboard tag {@link #NO_XP_TAG} to the entity so
 * the vanilla kill XP is skipped.</p>
 */
public interface SkillService {

    /** Entity scoreboard tag: killing this entity gives no automatic Combat XP. */
    String NO_XP_TAG = "skyblock_no_skill_xp";

    /** True if the skill gives XP now (Farming and Fishing arrive in Phase 7). */
    boolean available(SkillType skill);

    /**
     * Adds XP for an action (fires {@link SkillXpGainEvent}, may level up). Shows the action bar gain and the level-up.
     *
     * @param source short reason for logs and listeners, e.g. {@code "depths_block"}, {@code "mob_kill"}
     * @return the XP actually added (0 if the skill is not available, the amount was not positive or a listener cancelled)
     */
    double addXp(Player player, SkillType skill, double amount, String source);

    /** Current level of an online player (0 for offline or still loading players). */
    int level(UUID player, SkillType skill);

    default int level(Player player, SkillType skill) {
        return level(player.getUniqueId(), skill);
    }

    /** Total XP of an online player in this skill (0 for offline players). */
    double xp(UUID player, SkillType skill);

    /** Progress to the next level, 0.0 to 1.0 (1.0 at the max level). */
    double progress(UUID player, SkillType skill);

    /** Highest reachable level. */
    int maxLevel();

    /** Total XP needed to reach {@code level} from 0 (for "Mining 15 needed" texts). */
    double xpForLevel(int level);

    /** The level a total XP amount gives. */
    int levelFor(double totalXp);

    /** Total XP per skill of any player (online: from the cache; offline: from the database). */
    CompletableFuture<Map<SkillType, Double>> loadXp(UUID player);

    /**
     * Staff tool: sets an online player's total XP in a skill (level follows). Audited (category staff).
     *
     * @return false if the player has no loaded data yet or the value is negative
     */
    boolean setXp(Player player, SkillType skill, double totalXp, Actor by, String reason);

    /**
     * Highest total XP in a skill this season, one line per player from their best live profile (owner decision P7);
     * {@link net.skybornmc.skyblock.common.leaderboard.TopLine#value()} is the whole XP (the level follows from
     * {@link #levelFor}). Reads the database off the main thread (XP not saved yet is missing until the next save).
     * Implementations without it return an empty list.
     */
    default CompletableFuture<java.util.List<net.skybornmc.skyblock.common.leaderboard.TopLine>> top(SkillType skill, int limit) {
        return CompletableFuture.completedFuture(java.util.List.of());
    }
}
