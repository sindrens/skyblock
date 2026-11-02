package net.skybornmc.skyblock.milestones;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A milestone tier was reached (Complete Developer Spec §7.11 step 3), main thread, after it was saved. {@code island} true
 * = the scope id is an island (shared tier), false = a profile key. Listeners only read it (achievements, the rewards hub).
 */
public final class MilestoneTierCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean island;
    private final UUID scopeId;
    private final String chainId;
    private final int tier;

    public MilestoneTierCompletedEvent(boolean island, UUID scopeId, String chainId, int tier) {
        this.island = island;
        this.scopeId = scopeId;
        this.chainId = chainId;
        this.tier = tier;
    }

    public boolean island() {
        return island;
    }

    public UUID scopeId() {
        return scopeId;
    }

    public String chainId() {
        return chainId;
    }

    public int tier() {
        return tier;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
