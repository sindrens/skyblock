package net.skybornmc.skyblock.common.command;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Thrown by argument parsers and command code to stop with a player-facing message from
 * {@code messages/<module>.yml}. No stack trace is logged; the sender gets the message and the error sound.
 */
public final class CommandFailure extends RuntimeException {

    private final String module;
    private final String key;
    private final transient TagResolver[] placeholders;

    public CommandFailure(String module, String key, TagResolver... placeholders) {
        super(module + ":" + key, null, false, false);
        this.module = module;
        this.key = key;
        this.placeholders = placeholders;
    }

    /** A failure using a key from {@code messages/commands.yml}. */
    public static CommandFailure of(String key, TagResolver... placeholders) {
        return new CommandFailure(CommandService.MESSAGES, key, placeholders);
    }

    public String module() {
        return module;
    }

    public String key() {
        return key;
    }

    public TagResolver[] placeholders() {
        return placeholders;
    }
}
