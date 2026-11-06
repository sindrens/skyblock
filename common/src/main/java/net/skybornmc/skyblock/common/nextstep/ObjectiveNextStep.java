package net.skybornmc.skyblock.common.nextstep;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The default next step (plan 4 Nov C §2): the start flow's scoreboard objective ("Talk to Rowan at spawn", the
 * essentials {@code objective} placeholder) with its command ({@code /start}). Both names come from
 * {@code core.yml → next-step.*}; while the objective is empty (Rowan met) there is no default step.
 */
public final class ObjectiveNextStep implements NextStepProvider {

    private final Function<Player, Optional<String>> text;
    private final Supplier<String> command;

    /**
     * @param text    the objective text of a player (e.g. {@code placeholders.text(player, key)}); blank = no step
     * @param command the command of the objective step, without {@code /}
     */
    public ObjectiveNextStep(Function<Player, Optional<String>> text, Supplier<String> command) {
        this.text = text;
        this.command = command;
    }

    @Override
    public Optional<NextStep> next(Player player) {
        return step(text.apply(player).orElse(null), command.get());
    }

    /** Pure: a step for a non-blank objective text. */
    static Optional<NextStep> step(String objective, String command) {
        if (objective == null || objective.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new NextStep(objective, command));
    }
}
