package net.skybornmc.skyblock.common.tutorial;

import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * The join tutorial as seen by other modules (TUTORIAL.md §9.1), published by the tutorial module:
 * {@code core.services().optional(TutorialService.class)}. Progress is per <b>account</b> (player UUID), never per
 * profile (TQ3). Main thread.
 */
public interface TutorialService {

    /** Where an account stands. */
    enum State {
        /** Joined after the tutorial existed and has not walked into the island portal yet. */
        NEW,
        /** Running: {@link #currentStep} is the step to do. */
        ACTIVE,
        /** Done (reward paid). */
        FINISHED,
        /** Played before the tutorial existed: everything open, no reward (TQ6). */
        LEGACY,
        /** Finished by staff without a reward. */
        STAFF_SKIPPED;

        /** True when every lock is open. */
        public boolean complete() {
            return this == FINISHED || this == LEGACY || this == STAFF_SKIPPED;
        }
    }

    /** The account's state, empty while it is not loaded (offline players). */
    Optional<State> state(UUID player);

    /** The step id the player has to do now ({@code chest}, {@code mine}...), empty when not running. */
    Optional<String> currentStep(UUID player);

    /** True while the tutorial runs for this online player. */
    default boolean isActive(Player player) {
        return player != null && state(player.getUniqueId()).orElse(null) == State.ACTIVE;
    }

    /** Starts the tutorial at its first step (nothing happens when it is running or complete). */
    void start(Player player);

    /** Opens the {@code /tutorial} menu. */
    void openMenu(Player player);

    /** Staff view for {@code /inspect}: status, skip, reset. {@code back} is the inspect menu. */
    void openStaffView(Player staff, UUID player, String name, Menu back);
}
