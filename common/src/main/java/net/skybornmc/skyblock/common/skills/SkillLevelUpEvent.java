package net.skybornmc.skyblock.common.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player's skill level went up (main thread, after the XP was added). One event per gain, even when it jumps several
 * levels: check {@link #getOldLevel()} and {@link #getNewLevel()}. Tier unlocks (M4.6) and perks (Phase 6) listen here.
 */
public final class SkillLevelUpEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SkillType skill;
    private final int oldLevel;
    private final int newLevel;

    public SkillLevelUpEvent(Player player, SkillType skill, int oldLevel, int newLevel) {
        super(player);
        this.skill = skill;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public SkillType getSkill() {
        return skill;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    /** True if {@code level} was reached with this level-up (oldLevel &lt; level &lt;= newLevel). */
    public boolean reached(int level) {
        return oldLevel < level && level <= newLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
