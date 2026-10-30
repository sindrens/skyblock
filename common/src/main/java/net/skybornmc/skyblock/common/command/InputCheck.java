package net.skybornmc.skyblock.common.command;

import java.util.List;
import java.util.function.Predicate;

/**
 * Walks a typed command line through our {@link CommandSpec} tree the way Brigadier will (FIXES1), to catch the input
 * Brigadier would answer with its own grey "Incorrect argument for command ... &lt;--[HERE]" text: an unknown
 * sub-command or words after the last argument (e.g. {@code /is create Steve}). The command service then answers with
 * our own usage line instead. Pure (no Bukkit), unit-tested.
 *
 * <p>Not handled here (left to the framework or Brigadier): missing arguments (our usage text already), bad values
 * (each argument's parser gives our own error), commands of other plugins and vanilla, and commands the sender may not
 * use at all (Brigadier hides them, so we never reveal their usage).</p>
 */
final class InputCheck {

    /** Where the input stopped fitting: the node whose usage to show and the first word that does not fit. */
    record Problem(CommandSpec spec, String path, String input) {
    }

    private InputCheck() {
    }

    /**
     * @param top           the top-level command
     * @param tokens        the words after the command label (already split on single spaces, no empty words)
     * @param hasPermission the sender's permission check
     * @return the problem, or null when the words fit (or a problem the framework reports itself)
     */
    static Problem check(CommandSpec top, List<String> tokens, Predicate<String> hasPermission) {
        CommandSpec node = top;
        String path = top.name();
        int i = 0;
        while (i < tokens.size()) {
            String word = tokens.get(i);
            CommandSpec child = literal(node, word, hasPermission);
            if (child != null) {
                node = child;
                path = path + " " + child.name();
                i++;
                continue;
            }
            List<Arg<?>> args = node.args();
            if (args.isEmpty() && node.executor() != null && node.confirmSeconds() > 0 && word.equals("confirm")) {
                return i == tokens.size() - 1 ? null : new Problem(node, path, tokens.get(i + 1));
            }
            if (args.isEmpty() || node.executor() == null) {
                return new Problem(node, path, word); // unknown sub-command, or a word after a command without arguments
            }
            for (Arg<?> arg : args) {
                if (i >= tokens.size()) {
                    return null;
                }
                if (arg.greedy()) {
                    return null; // the rest of the line is this argument
                }
                i++;
            }
            if (i < tokens.size() && node.confirmSeconds() > 0 && tokens.get(i).equals("confirm")) {
                i++;
            }
            return i < tokens.size() ? new Problem(node, path, tokens.get(i)) : null;
        }
        return null;
    }

    /** Brigadier matches literals exactly (case-sensitive) and only those the sender may use. */
    private static CommandSpec literal(CommandSpec node, String word, Predicate<String> hasPermission) {
        for (CommandSpec child : node.children()) {
            if (child.name().equals(word) && (child.permission() == null || child.permission().isBlank()
                    || hasPermission.test(child.permission()))) {
                return child;
            }
        }
        return null;
    }

    /** Splits a typed line ({@code /is create x}) into label + words; the client already collapses spaces. */
    static List<String> words(String line) {
        String s = line.strip();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            return List.of();
        }
        return List.of(s.split(" +"));
    }
}
