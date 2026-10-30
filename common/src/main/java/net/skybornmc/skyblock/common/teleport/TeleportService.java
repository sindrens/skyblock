package net.skybornmc.skyblock.common.teleport;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Every player teleport of the plugin goes through here (F4): guards (other modules can forbid a destination), the
 * warmup per {@link TeleportReason} ({@code teleport.yml}) with cancel on move / damage / combat tag / freeze / world
 * change, cooldowns, bypass permissions, the action bar countdown, async chunk preload, an optional safe-destination
 * hook and the staff {@code /back} location.
 *
 * <p>Registered by the core, so it is always present: {@code core.services().get(TeleportService.class)}. Call it on
 * the main thread.</p>
 */
public interface TeleportService {

    /** Can forbid a teleport. Return a message (MiniMessage, trusted config text) to show why, or empty to allow. */
    @FunctionalInterface
    interface Guard {
        Optional<String> deny(Player player, Location destination);
    }

    void addGuard(Guard guard);

    /**
     * A combat zone owned by a module (the Depths boss arena and combat tiers, events). Inside one, and while
     * combat-tagged, every player teleport waits {@code teleport.yml → danger.warmup-seconds} (owner answers 285/345).
     */
    @FunctionalInterface
    interface DangerZone {
        boolean contains(Player player);
    }

    void addDangerZone(DangerZone zone);

    void removeDangerZone(DangerZone zone);

    /** True while the player is combat-tagged or inside a combat zone (danger worlds or a module's {@link DangerZone}). */
    boolean inDanger(Player player);

    void removeGuard(Guard guard);

    /** The first guard's denial reason, if any guard forbids this destination. */
    Optional<String> denied(Player player, Location destination);

    /**
     * A player-requested teleport: guards and {@link TeleportOptions#check}, freeze, cooldown and (optionally) combat
     * block, then the reason's warmup (instant for 0 seconds or the reason's bypass permission). A running warmup of the
     * same player is replaced. Every refusal is explained to the player.
     *
     * @return true if the teleport started (warmup or instant), false if it was refused
     */
    boolean request(Player player, Location destination, TeleportReason reason, TeleportOptions options);

    /** Instant teleport (staff, system): no warmup or cooldown; guards apply unless the reason's bypass permission. */
    void now(Player player, Location destination, TeleportReason reason, TeleportOptions options);

    /** True while the player is in a warmup. */
    boolean hasWarmup(UUID player);

    /** Stops a running warmup without a message. True if there was one. */
    boolean cancel(UUID player);

    /** Cooldown left for this reason, {@link Duration#ZERO} when ready. */
    Duration cooldownLeft(UUID player, TeleportReason reason);

    /** Configured warmup of a reason in seconds. */
    int warmupSeconds(TeleportReason reason);

    /** Last location before a teleport (or death), for staff {@code /back}. */
    Optional<Location> back(UUID player);
}
