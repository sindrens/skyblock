package net.skybornmc.skyblock.common.crate;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * A crate open (or a bulk open) was committed: keys taken, rewards decided and saved (M10.1). Main thread, once per
 * batch, after the hand-outs started. Read-only: nothing here changes the rewards. Used by the collection log (M10.10);
 * later by quests and the battle pass.
 */
public final class CrateOpenedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * One reward of the batch.
     *
     * @param reward   reward id in the crate ({@code cinder_key})
     * @param top      a star (top) reward
     * @param fallback crystals were paid instead (the reward is not live, or its system was missing)
     */
    public record Won(String reward, boolean top, boolean fallback) {
    }

    private final UUID player;
    private final Player online;
    private final UUID profile;
    private final String crate;
    private final List<Won> rewards;

    public CrateOpenedEvent(UUID player, Player online, UUID profile, String crate, List<Won> rewards) {
        this.player = player;
        this.online = online;
        this.profile = profile;
        this.crate = crate;
        this.rewards = List.copyOf(rewards);
    }

    /** The account that opened. */
    public UUID player() {
        return player;
    }

    /** The opener when still online, else null. */
    public Player online() {
        return online;
    }

    /** The profile the keys came from and the rewards went to. */
    public UUID profile() {
        return profile;
    }

    public String crate() {
        return crate;
    }

    public List<Won> rewards() {
        return rewards;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
