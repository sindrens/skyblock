package net.skybornmc.skyblock.common.pack;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The icon pack (I7 pack v2), published by the pack module ({@code core.services().optional(PackService.class)}).
 * Absent when the module is off: then nobody gets pack models and {@link PackWait} runs actions at once.
 * Statuses are memory only.
 */
public interface PackService {

    /** Where a player stands with the pack. */
    enum Status {
        /** Sent, no final answer yet (still downloading or the prompt is open). */
        PENDING,
        /** Loaded: the only status that gets pack models. */
        LOADED,
        DECLINED,
        FAILED,
        /** Floodgate player: never sent. */
        BEDROCK,
        /** {@code pack.yml enabled: false}. */
        DISABLED,
        /** Not sent: the pack host was down at join time. */
        SKIPPED
    }

    Status status(UUID player);

    /** True only when {@code viewer}'s client has loaded the pack. */
    default boolean hasPack(Player viewer) {
        return viewer != null && status(viewer.getUniqueId()) == Status.LOADED;
    }

    /**
     * Runs {@code action} on the main thread once, as soon as the player's status is not {@link Status#PENDING}, or
     * after {@code join.wait-seconds}, whichever comes first; at once when not pending; never when the player left or
     * was kicked. Several calls run in order.
     */
    void afterAnswer(Player player, Runnable action);

    /** The pack host passed its last self-check (unhealthy = the pack is not sent and nobody is kicked). */
    boolean healthy();

    /** {@code pack.yml required}. */
    boolean required();
}
