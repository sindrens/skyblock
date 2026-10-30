package net.skybornmc.skyblock.common.npc;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player clicked an NPC from the builds (npcs module), before its action runs. Cancel it to replace the action
 * (e.g. the tutorial shows its own step text). Main thread.
 */
public final class NpcClickEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String npcId;
    private final String role;
    private final boolean help;
    private boolean cancelled;

    /**
     * @param help true when the player asked for the help text (sneak-click) instead of the action
     */
    public NpcClickEvent(@NotNull Player who, String npcId, String role, boolean help) {
        super(who);
        this.npcId = npcId;
        this.role = role;
        this.help = help;
    }

    public String npcId() {
        return npcId;
    }

    public String role() {
        return role;
    }

    public boolean help() {
        return help;
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
