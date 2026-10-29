package net.skybornmc.skyblock.common.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/**
 * One placeholder value for a player context. {@code player} is {@code null} for global placeholders asked without a
 * viewer (console, server list). Implementations must be cheap (they run for every scoreboard/tab refresh) and must
 * never block: read from caches, never from the database.
 */
@FunctionalInterface
public interface Placeholder {

    /** The rich value (colors allowed). Return {@link Component#empty()} if there is nothing to show. */
    Component component(Player player);

    /** The plain-text value; defaults to the component without formatting. */
    default String text(Player player) {
        return PlainTextComponentSerializer.plainText().serialize(component(player));
    }

    /** A placeholder that is plain text only. */
    static Placeholder text(java.util.function.Function<Player, String> value) {
        return new Placeholder() {
            @Override
            public Component component(Player player) {
                String text = value.apply(player);
                return text == null ? Component.empty() : Component.text(text);
            }

            @Override
            public String text(Player player) {
                String text = value.apply(player);
                return text == null ? "" : text;
            }
        };
    }
}
