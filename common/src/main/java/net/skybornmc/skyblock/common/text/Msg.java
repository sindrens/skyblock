package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.util.Format;

/** Short placeholder helpers: {@code msg.send(p, "paid", Msg.p("player", name), Msg.money("amount", 1500))}. */
public final class Msg {

    private Msg() {
    }

    /** Plain text placeholder (never parsed, so players can't inject tags). */
    public static TagResolver p(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Component placeholder. */
    public static TagResolver c(String name, ComponentLike value) {
        return Placeholder.component(name, value);
    }

    /** Placeholder trusted MiniMessage (only for text from our own config files). */
    public static TagResolver mm(String name, String miniMessage) {
        return Placeholder.parsed(name, miniMessage);
    }

    public static TagResolver money(String name, long amount) {
        return p(name, Format.money(amount));
    }

    public static TagResolver number(String name, long amount) {
        return p(name, Format.number(amount));
    }
}
