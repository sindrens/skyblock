package net.skybornmc.skyblock.common.nextstep;

import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * One source of next steps (the tutorial, the start flow, later contracts). Called on the main thread when a menu,
 * the scoreboard or a command needs the step, so it must be cheap and read cached state only (never the database).
 */
@FunctionalInterface
public interface NextStepProvider {

    /** The step this source has for {@code player}, or empty when it has nothing to say. */
    Optional<NextStep> next(Player player);
}
