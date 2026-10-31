package net.skybornmc.skyblock.common.hud;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * The timer row of the sidebar scoreboard (G7-R1-01): "at most one timer line, only while it runs, the soonest first".
 * Published by the hud module; boosters, events (I3) and anything else with a countdown register a provider.
 *
 * <pre>{@code
 * core.services().optional(BoardTimers.class).ifPresent(t -> t.register(ID, this::boardTimer)); // enable
 * core.services().optional(BoardTimers.class).ifPresent(t -> t.unregister(ID));                 // disable
 * }</pre>
 *
 * <p><b>Provider contract:</b> called on the main thread, at most once per player per sidebar refresh (about once a
 * second). Map reads only: no I/O, no database, no scheduling, and no list allocation per call. Return empty when
 * nothing runs for that player (or nothing has time left). A provider that throws is skipped and logged once per
 * owner. The label is built from the caller's own messages and carries no colour: the board colours it.</p>
 */
public interface BoardTimers {

    /** One running countdown: {@code label} such as "Booster: +50% Sell", {@code remainingMillis} until it ends. */
    record Timer(Component label, long remainingMillis) {
    }

    /** Adds (or replaces) the provider of {@code owner} (a module id). */
    void register(String owner, Function<Player, Optional<Timer>> provider);

    /** Removes the provider of {@code owner}; nothing happens if there is none. */
    void unregister(String owner);

    /** The soonest-ending running timer of all providers for this player. */
    Optional<Timer> soonest(Player player);

    /** Pure: the timer with the smallest {@code remainingMillis} above 0; ties keep the first; nulls are skipped. */
    static Optional<Timer> soonest(Collection<Timer> timers) {
        Timer best = null;
        for (Timer t : timers) {
            if (t != null && t.remainingMillis() > 0 && (best == null || t.remainingMillis() < best.remainingMillis())) {
                best = t;
            }
        }
        return Optional.ofNullable(best);
    }
}
