package net.skybornmc.skyblock.common.moderation;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A player was just banned (main thread). Fired once per banned account (the target, plus every online player on the
 * same address for an IP-wide ban) <b>before</b> online players are kicked, so quit handlers already see the result of
 * listeners. The staff module clears a stored freeze here (owner #4: a ban clears the freeze), without either module
 * depending on the other.
 */
public final class PlayerBannedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final String name;
    private final String bannedBy;
    private final long punishmentId;

    public PlayerBannedEvent(UUID player, String name, String bannedBy, long punishmentId) {
        this.player = player;
        this.name = name;
        this.bannedBy = bannedBy;
        this.punishmentId = punishmentId;
    }

    public UUID getPlayer() {
        return player;
    }

    /** Last known name of the banned player (may be null when unknown). */
    public String getName() {
        return name;
    }

    /** Name of the staff member or system that banned. */
    public String getBannedBy() {
        return bannedBy;
    }

    public long getPunishmentId() {
        return punishmentId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
