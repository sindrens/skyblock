package net.skybornmc.skyblock.common.island;

import net.skybornmc.skyblock.common.audit.Actor;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * An island was removed (main thread, after the database marked it deleted): {@code /is disband}, a staff delete or a
 * reset (M2.7). Modules clean up what belongs to the island, e.g. the island bank (M3.3).
 *
 * @param islandId the removed island
 * @param owner    the leader's member key (profile key) at removal
 * @param reason   {@code disband}, {@code delete} or {@code reset}
 */
public final class IslandRemovedEvent extends Event {

    public static final String DISBAND = "disband";
    public static final String DELETE = "delete";
    public static final String RESET = "reset";

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID owner;
    private final String reason;
    private final Actor actor;

    public IslandRemovedEvent(UUID islandId, UUID owner, String reason, Actor actor) {
        this.islandId = islandId;
        this.owner = owner;
        this.reason = reason;
        this.actor = actor == null ? Actor.SYSTEM : actor;
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID owner() {
        return owner;
    }

    public String reason() {
        return reason;
    }

    public Actor actor() {
        return actor;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
