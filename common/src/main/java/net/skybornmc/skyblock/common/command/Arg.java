package net.skybornmc.skyblock.common.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.Objects;

/**
 * One command argument: its name (shown in usage as {@code <name>}), how it is parsed, and its tab completion.
 * Create them with {@link Args}. <b>Every argument must have suggestions</b> (owner rule); the constructor refuses
 * one without, so a command without tab completion can't even be registered.
 *
 * @param <T> parsed value type
 */
public final class Arg<T> {

    /** Turns the raw text into a value or throws {@link CommandFailure} with a clear message. */
    @FunctionalInterface
    public interface Parser<T> {
        T parse(CommandSender sender, String raw);
    }

    private final String name;
    private final boolean greedy;
    private final Parser<T> parser;
    private final SuggestionProvider<CommandSourceStack> suggestions;

    private Arg(String name, boolean greedy, Parser<T> parser, SuggestionProvider<CommandSourceStack> suggestions) {
        if (name == null || !name.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid argument name '" + name + "' (use lowercase a-z, 0-9, - or _)");
        }
        this.name = name;
        this.greedy = greedy;
        this.parser = Objects.requireNonNull(parser, "parser");
        this.suggestions = Objects.requireNonNull(suggestions,
                () -> "Argument '" + name + "' has no suggestions. Every argument needs tab completion (owner rule).");
    }

    /** A single-word argument. */
    public static <T> Arg<T> word(String name, Parser<T> parser, SuggestionProvider<CommandSourceStack> suggestions) {
        return new Arg<>(name, false, parser, suggestions);
    }

    /** Everything until the end of the line (reasons, messages). Must be the last argument. */
    public static <T> Arg<T> greedy(String name, Parser<T> parser, SuggestionProvider<CommandSourceStack> suggestions) {
        return new Arg<>(name, true, parser, suggestions);
    }

    public String name() {
        return name;
    }

    public boolean greedy() {
        return greedy;
    }

    public SuggestionProvider<CommandSourceStack> suggestions() {
        return suggestions;
    }

    ArgumentType<String> brigadierType() {
        return greedy ? StringArgumentType.greedyString() : StringArgumentType.word();
    }

    T parse(CommandSender sender, String raw) {
        return parser.parse(sender, raw);
    }

    @Override
    public String toString() {
        return "<" + name + ">";
    }
}
