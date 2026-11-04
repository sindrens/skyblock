package net.skybornmc.skyblock.common.player;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Owner 4 Nov 2026 (PLAN_4NOV phase 1, item 3): player-to-player money and item paths ({@code /pay}, {@code /trade}, buying
 * and listing on {@code /ah}) stay locked until the player has talked to the guide (Rowan) at spawn once. The flag is the
 * start flow's own account setting {@link #MET_KEY}; the essentials module (start guide) publishes this gate, so with the
 * start flow off nothing is locked.
 *
 * <pre>{@code
 * if (StartGate.locked(core.services(), player)) {
 *     return; // the player was told: "Talk to Rowan at spawn first (/start)."
 * }
 * }</pre>
 */
public interface StartGate {

    /** Account setting that remembers that the player talked to the guide (any value but empty = met). */
    String MET_KEY = "start.met-guide";

    /** Staff bypass of the gate (declared in the essentials permissions file, default op). */
    String BYPASS = "skyblock.guide.bypass";

    /** True when the player has met the guide (or the gate is off, or they have {@link #BYPASS}). Main thread. */
    boolean isOpen(Player player);

    /**
     * True (= refuse) when the gate is closed for {@code player}; the player was then told how to open it (clickable
     * {@code /start}) and heard the error sound. Main thread.
     */
    boolean refuse(Player player);

    /** The published gate, if the essentials module runs. */
    static Optional<StartGate> of(ServiceRegistry services) {
        return services == null ? Optional.empty() : services.optional(StartGate.class);
    }

    /** {@link #refuse} of the published gate; false (not locked) when no gate is published. */
    static boolean locked(ServiceRegistry services, Player player) {
        return player != null && of(services).map(gate -> gate.refuse(player)).orElse(false);
    }

    /** True when an account setting value means "met" (pure). */
    static boolean met(String value) {
        return value != null && !value.isBlank();
    }
}
