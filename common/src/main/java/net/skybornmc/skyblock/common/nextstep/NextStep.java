package net.skybornmc.skyblock.common.nextstep;

import java.util.Locale;

/**
 * What a player should do next (plan 4 Nov C, sections 1-2): one short plain text ("Talk to Rowan at spawn") and the
 * command that does it ({@code start}, without the slash; empty = nothing to run). Kindle's Next step button, /help
 * "Find my way" and the scoreboard {@code Next:} line all show the same step ({@link NextStepService}).
 *
 * @param text    plain text (no MiniMessage), never blank
 * @param command the command the action runs as the player, without {@code /}; empty when there is none
 */
public record NextStep(String text, String command) {

    public NextStep {
        text = text == null ? "" : text.strip();
        command = clean(command);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A next step needs a text");
        }
    }

    /** True when clicking the step runs something. */
    public boolean hasAction() {
        return !command.isEmpty();
    }

    /** The first word of the command in lower case ({@code is home} → {@code is}), for a "does it exist" check. */
    public String label() {
        return command.isEmpty() ? "" : command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    }

    /** {@code " /start "} → {@code start}; null → empty. Pure. */
    static String clean(String command) {
        if (command == null) {
            return "";
        }
        String c = command.strip();
        while (c.startsWith("/")) {
            c = c.substring(1).strip();
        }
        return c;
    }
}
