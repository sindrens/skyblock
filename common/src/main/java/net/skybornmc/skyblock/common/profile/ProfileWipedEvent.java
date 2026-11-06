package net.skybornmc.skyblock.common.profile;

import net.skybornmc.skyblock.common.audit.Actor;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A profile was wiped (main thread, after the database commit; owner 4 Nov 2026, "island = profile"). The old key
 * {@link #gone()} is retired (its rows stay as the 14-day snapshot, nobody plays on it) and {@link #fresh()} took its slot
 * with no data at all. When the player was online on that profile, a {@link ProfileSwitchedEvent} from {@code gone} to
 * {@code fresh} was fired just before this event, so per-profile caches already follow the fresh key.
 *
 * <p>Modules do <b>not</b> delete anything here (that happens at the purge, through {@link ProfileDeletedEvent}). Only
 * modules that keep account data under the profile key (see {@link WipeScope}, e.g. achievements) carry it from
 * {@code gone} to {@code fresh}; modules with references to the old key that must follow the person (pending island
 * invites) move them.</p>
 */
public final class ProfileWipedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Profile gone;
    private final Profile fresh;
    private final WipeReason reason;
    private final Actor actor;
    private final long snapshotId;
    private final boolean online;

    public ProfileWipedEvent(Profile gone, Profile fresh, WipeReason reason, Actor actor, long snapshotId, boolean online) {
        this.gone = gone;
        this.fresh = fresh;
        this.reason = reason;
        this.actor = actor;
        this.snapshotId = snapshotId;
        this.online = online;
    }

    /** The retired profile (its key is no longer live). */
    public Profile gone() {
        return gone;
    }

    /** The new empty profile in the same slot. */
    public Profile fresh() {
        return fresh;
    }

    public WipeReason reason() {
        return reason;
    }

    public Actor actor() {
        return actor;
    }

    public long snapshotId() {
        return snapshotId;
    }

    /** True when the player was online on the wiped profile and now plays on the fresh one. */
    public boolean online() {
        return online;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
