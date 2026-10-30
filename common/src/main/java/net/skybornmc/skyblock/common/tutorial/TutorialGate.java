package net.skybornmc.skyblock.common.tutorial;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Per-step feature locks of the join tutorial (TUTORIAL.md §4, owner answer 131), published by the tutorial module:
 * {@code core.services().optional(TutorialGate.class)}. Replaces the removed 30-minute new-account limit.
 *
 * <p><b>Fail-open:</b> when the tutorial module is off the service is absent and every feature is open. Staff with
 * {@code skyblock.tutorial.bypass}, players who finished (or were marked finished because they played before the
 * tutorial existed) and players whose step has passed the gate's step see no lock. Gate ids: {@link Gates}.</p>
 *
 * <p>Modules call {@link #denied(ServiceRegistry, Player, String)} at their entry point (it tells the player which step
 * opens the feature). Main thread; never touches the database (an in-memory copy of the progress).</p>
 */
public interface TutorialGate {

    /** True if {@code gate} is open for this player right now. */
    boolean open(Player player, String gate);

    /**
     * Tells {@code player} that {@code gate} is still locked and which step opens it (chat at most once per few
     * seconds, action bar every time, error sound). Main thread.
     */
    void tellLocked(Player player, String gate);

    /** The published service, if the tutorial module runs. */
    static Optional<TutorialGate> of(ServiceRegistry services) {
        return services == null ? Optional.empty() : services.optional(TutorialGate.class);
    }

    /**
     * The one-line hook for locked features: true (and the player told why) if the tutorial still locks {@code gate} for
     * {@code player}. False when it is open or the tutorial module is off. Main thread.
     */
    static boolean denied(ServiceRegistry services, Player player, String gate) {
        if (player == null) {
            return false;
        }
        Optional<TutorialGate> service = of(services);
        if (service.isEmpty() || service.get().open(player, gate)) {
            return false;
        }
        service.get().tellLocked(player, gate);
        return true;
    }

    /** Quiet check for menus and lore ({@code true} = open, also when the tutorial module is off). */
    static boolean allows(ServiceRegistry services, Player player, String gate) {
        return player == null || of(services).map(g -> g.open(player, gate)).orElse(true);
    }
}
