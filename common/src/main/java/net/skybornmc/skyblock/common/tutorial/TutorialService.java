package net.skybornmc.skyblock.common.tutorial;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * The guided tour {@code /tutorial} (tutorial module, docs/planning/PLAN_TUTORIAL_CHAT.md section 3) as seen by other
 * modules: {@code TutorialService.of(core.services())}. Owner 4 Nov 2026: a new player always knows what to do, so the
 * boss bar, the scoreboard "Next" line, Kindle, /help and /start all show the same step from here. Main thread.
 */
public interface TutorialService {

    static Optional<TutorialService> of(ServiceRegistry services) {
        return services.optional(TutorialService.class);
    }

    /**
     * The player's next step.
     *
     * @param chapter chapter name, plain text ({@code The Farm})
     * @param text    the step, plain text and short ({@code Meet Bramble at /farm}); fits the scoreboard
     * @param hint    one short plain sentence on where to go or what to do (may be empty)
     * @param command command without slash that takes the player there or opens the right screen (may be empty)
     */
    record NextStep(String chapter, String text, String hint, String command) {
    }

    /** The player's next tutorial step; empty when the tutorial is finished, skipped, off or still loading. */
    Optional<NextStep> next(Player player);

    /** Opens the pages of Rowan's {@code /start} menu: the current chapter and what's next, plus Island, Farm and Mine. */
    void openStart(Player player);

    /** Opens {@code /tutorial} (the quest book; a chest menu on Bedrock). */
    void open(Player player);

    /**
     * True while the tutorial shows its own boss bar for this player's start, so the start flow's "Talk to Rowan" bar
     * (essentials) stays hidden.
     */
    default boolean ownsStartBar() {
        return true;
    }
}
