package net.skybornmc.skyblock.common.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/** Tab-completion helpers. Every argument needs one (owner rule: tab completion everywhere). */
public final class Suggest {

    /** Max suggestions sent to the client (keeps packets small with 300 players / many names). */
    static final int LIMIT = 100;

    private Suggest() {
    }

    /** Values starting with {@code typed} (case-insensitive), in the given order, capped at {@link #LIMIT}. */
    public static List<String> filter(Iterable<String> values, String typed) {
        String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(value);
                if (out.size() >= LIMIT) {
                    break;
                }
            }
        }
        return out;
    }

    /** Suggests a changing list of values. */
    public static SuggestionProvider<CommandSourceStack> of(Supplier<? extends Collection<String>> values) {
        return bySender(sender -> values.get());
    }

    /** Suggests fixed values. */
    public static SuggestionProvider<CommandSourceStack> of(String... values) {
        List<String> list = List.of(values);
        return bySender(sender -> list);
    }

    /** Suggests values that depend on who is typing (e.g. only players they can see). */
    public static SuggestionProvider<CommandSourceStack> bySender(Function<CommandSender, ? extends Iterable<String>> values) {
        return (ctx, builder) -> {
            for (String value : filter(values.apply(ctx.getSource().getSender()), builder.getRemaining())) {
                builder.suggest(value);
            }
            return builder.buildFuture();
        };
    }

    /** Number-with-unit suggestions: typing {@code 2} offers {@code 2m 2h 2d}; otherwise the filtered defaults. */
    public static List<String> withUnits(String typed, List<String> units, List<String> defaults) {
        if (typed != null && !typed.isEmpty() && typed.chars().allMatch(c -> Character.isDigit(c) || c == '.')) {
            List<String> out = new ArrayList<>();
            for (String unit : units) {
                out.add(typed + unit);
            }
            return out;
        }
        return filter(defaults, typed);
    }
}
