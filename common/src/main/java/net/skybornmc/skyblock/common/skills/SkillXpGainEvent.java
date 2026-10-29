package net.skybornmc.skyblock.common.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player is about to get skill XP (main thread). Change {@link #setAmount} for boosters, perks and the island XP
 * Boost upgrade; cancel to refuse. Listen at MONITOR (not cancelled) to feed other systems, e.g. island pillars (Phase 5).
 */
public final class SkillXpGainEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SkillType skill;
    private final String source;
    private double amount;
    private boolean cancelled;

    public SkillXpGainEvent(Player player, SkillType skill, double amount, String source) {
        super(player);
        this.skill = skill;
        this.amount = amount;
        this.source = source;
    }

    public SkillType getSkill() {
        return skill;
    }

    /** Why the XP is given, e.g. {@code depths_block}, {@code mob_kill}. */
    public String getSource() {
        return source;
    }

    public double getAmount() {
        return amount;
    }

    /** Negative or NaN values count as 0 (nothing is added). */
    public void setAmount(double amount) {
        this.amount = Double.isNaN(amount) ? 0 : Math.max(0, amount);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
