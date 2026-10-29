package net.skybornmc.skyblock.common.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/** What a command executor gets: the sender, parsed arguments and the typed input. */
public final class CmdContext {

    private final CommandSender sender;
    private final Map<String, Object> values;
    private final String input;

    CmdContext(CommandSender sender, Map<String, Object> values, String input) {
        this.sender = sender;
        this.values = values;
        this.input = input;
    }

    public CommandSender sender() {
        return sender;
    }

    /** The sender as a player. Only call on {@code playerOnly()} commands (checked before the executor runs). */
    public Player player() {
        if (sender instanceof Player p) {
            return p;
        }
        throw CommandFailure.of("players-only");
    }

    /** A required (or present optional) argument's parsed value. */
    @SuppressWarnings("unchecked")
    public <T> T get(Arg<T> arg) {
        Object value = values.get(arg.name());
        if (value == null) {
            throw new IllegalStateException("Argument '" + arg.name() + "' was not given; use getOr for optional arguments");
        }
        return (T) value;
    }

    /** An optional argument's value, or {@code fallback} when it was left out. */
    @SuppressWarnings("unchecked")
    public <T> T getOr(Arg<T> arg, T fallback) {
        Object value = values.get(arg.name());
        return value == null ? fallback : (T) value;
    }

    public boolean has(Arg<?> arg) {
        return values.containsKey(arg.name());
    }

    /** The full command line without the leading slash, e.g. {@code "unban Steve"}. */
    public String input() {
        return input;
    }
}
