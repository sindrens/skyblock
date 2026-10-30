package net.skybornmc.skyblock.common.command;

import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Ready-made argument types. All of them come with tab completion and clear error messages. */
public final class Args {

    private static final List<String> DURATION_UNITS = List.of("m", "h", "d", "w");
    private static final List<String> DURATION_DEFAULTS = List.of("30m", "1h", "12h", "1d", "3d", "7d", "14d", "30d");

    private Args() {
    }

    /** An online player the sender can see. Suggests visible online players. */
    public static Arg<Player> onlinePlayer(String name) {
        return Arg.word(name, (sender, raw) -> {
            Player target = Bukkit.getPlayerExact(raw);
            if (target == null || (sender instanceof Player viewer && !viewer.canSee(target))) {
                throw CommandFailure.of("player-offline", Msg.p("input", raw));
            }
            return target;
        }, Suggest.bySender(Args::visibleNames));
    }

    /**
     * Any player who has joined before (online or offline); value = the correctly-cased name.
     * Names come from {@link OfflineNames} (DB-backed once player data exists).
     */
    public static Arg<String> offlinePlayer(String name) {
        return Arg.word(name, (sender, raw) -> {
            for (String known : OfflineNames.names()) {
                if (known.equalsIgnoreCase(raw)) {
                    return known;
                }
            }
            throw CommandFailure.of("player-unknown", Msg.p("input", raw));
        }, Suggest.bySender(sender -> {
            List<String> names = new ArrayList<>(visibleNames(sender));
            for (String known : OfflineNames.names()) {
                if (!names.contains(known)) {
                    names.add(known);
                }
            }
            return names;
        }));
    }

    /** A loaded world. */
    public static Arg<World> world(String name) {
        return Arg.word(name, (sender, raw) -> {
            World world = Bukkit.getWorld(raw);
            if (world == null) {
                throw CommandFailure.of("world-unknown", Msg.p("input", raw));
            }
            return world;
        }, Suggest.of(() -> Bukkit.getWorlds().stream().map(World::getName).toList()));
    }

    /** {@code 30m}, {@code 2h30m}, {@code 7d}, {@code 1w}. */
    public static Arg<Duration> duration(String name) {
        return Arg.word(name, (sender, raw) -> {
            try {
                return Durations.parse(raw);
            } catch (IllegalArgumentException e) {
                throw CommandFailure.of("invalid-duration", Msg.p("input", raw));
            }
        }, (ctx, builder) -> {
            Suggest.withUnits(builder.getRemaining(), DURATION_UNITS, DURATION_DEFAULTS).forEach(builder::suggest);
            return builder.buildFuture();
        });
    }

    /**
     * A positive whole amount with optional suffix: {@code 1500}, {@code 1.5k}, {@code 2M}, {@code 3B}.
     * Tab completion shows only the hint {@code <name>} (owner #48: no number suggestions like 10k/10M/10B;
     * players still type suffixes themselves).
     */
    public static Arg<Long> amount(String name) {
        return Arg.word(name, (sender, raw) -> {
            try {
                return Format.parseAmount(raw);
            } catch (IllegalArgumentException e) {
                throw CommandFailure.of("invalid-amount", Msg.p("input", raw));
            }
        }, Suggest.hint(name));
    }

    /** A whole number between {@code min} and {@code max}; suggests {@code examples}. */
    public static Arg<Integer> integer(String name, int min, int max, String... examples) {
        return Arg.word(name, (sender, raw) -> {
            try {
                int value = Integer.parseInt(raw);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // falls through to the error
            }
            throw CommandFailure.of("invalid-number", Msg.p("input", raw), Msg.p("min", min), Msg.p("max", max));
        }, Suggest.of(examples.length > 0 ? examples : new String[]{String.valueOf(min), String.valueOf(max)}));
    }

    /** One of an enum's values, typed in lower kebab-case ({@code DARK_RED} → {@code dark-red}). */
    public static <E extends Enum<E>> Arg<E> choice(String name, Class<E> type) {
        List<String> names = Arrays.stream(type.getEnumConstants()).map(Args::kebab).toList();
        return Arg.word(name, (sender, raw) -> {
            for (E constant : type.getEnumConstants()) {
                if (kebab(constant).equalsIgnoreCase(raw)) {
                    return constant;
                }
            }
            throw CommandFailure.of("invalid-choice", Msg.p("input", raw), Msg.p("choices", String.join(", ", names)));
        }, Suggest.of(() -> names));
    }

    /** One word from a (possibly changing) list of valid values. */
    public static Arg<String> oneOf(String name, Supplier<? extends Collection<String>> values) {
        return Arg.word(name, (sender, raw) -> {
            Collection<String> valid = values.get();
            for (String value : valid) {
                if (value.equalsIgnoreCase(raw)) {
                    return value;
                }
            }
            throw CommandFailure.of("invalid-choice", Msg.p("input", raw), Msg.p("choices", String.join(", ", valid)));
        }, Suggest.of(values));
    }

    /** Any single word; the command validates it. {@code suggestions} still required (e.g. hints or known values). */
    public static Arg<String> word(String name, com.mojang.brigadier.suggestion.SuggestionProvider<
            io.papermc.paper.command.brigadier.CommandSourceStack> suggestions) {
        return Arg.word(name, (sender, raw) -> raw, suggestions);
    }

    /** Free text until the end of the line (reasons, messages). Suggests the given hints, e.g. common reasons. */
    public static Arg<String> text(String name, String... hints) {
        if (hints.length == 0) {
            throw new IllegalArgumentException("Text argument '" + name + "' needs at least one hint for tab completion (owner rule).");
        }
        return Arg.greedy(name, (sender, raw) -> raw.strip(), Suggest.of(hints));
    }

    static String kebab(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static List<String> visibleNames(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(sender instanceof Player viewer) || viewer.canSee(p)) {
                names.add(p.getName());
            }
        }
        return names;
    }
}
