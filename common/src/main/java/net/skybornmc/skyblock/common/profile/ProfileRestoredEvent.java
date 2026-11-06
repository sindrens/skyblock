package net.skybornmc.skyblock.common.profile;

import net.skybornmc.skyblock.common.audit.Actor;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Staff restored a wiped profile from its snapshot ({@code /profiles restore}; main thread, after the commit). The old key
 * {@link #restored()} is live again in its slot; the profile that was in the slot ({@link #replaced()}, may be
 * {@code null}) was retired with its own snapshot. When the player was online on that slot, a
 * {@link ProfileSwitchedEvent} from {@code replaced} to {@code restored} was fired just before this event.
 *
 * <p>Modules that carried account data on {@link ProfileWipedEvent} carry it back from {@code replaced} here. The island
 * membership is not given back automatically (the island may be gone or full): staff use {@code /isadmin restore}.</p>
 */
public final class ProfileRestoredEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Profile restored;
    private final Profile replaced;
    private final WipeReason wipeReason;
    private final UUID islandId;
    private final Actor actor;
    private final long snapshotId;

    public ProfileRestoredEvent(Profile restored, Profile replaced, WipeReason wipeReason, UUID islandId, Actor actor, long snapshotId) {
        this.restored = restored;
        this.replaced = replaced;
        this.wipeReason = wipeReason;
        this.islandId = islandId;
        this.actor = actor;
        this.snapshotId = snapshotId;
    }

    public Profile restored() {
        return restored;
    }

    /** The profile that was in the slot and is retired now, or {@code null} when the slot was empty. */
    public Profile replaced() {
        return replaced;
    }

    /** Why the restored profile had been wiped. */
    public WipeReason wipeReason() {
        return wipeReason;
    }

    /** The island the profile was on when it was wiped, or {@code null}. */
    public UUID islandId() {
        return islandId;
    }

    public Actor actor() {
        return actor;
    }

    public long snapshotId() {
        return snapshotId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
