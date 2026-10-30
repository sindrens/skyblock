package net.skybornmc.skyblock.common.chat;

import org.bukkit.entity.Player;

/**
 * Published by the chat module ({@code core.services().optional(ChatChannels.class)}): sends a one-shot message to a
 * {@link ChatChannel} with the same checks as public chat. Without the chat module, callers fall back to their own
 * minimal handling.
 */
@FunctionalInterface
public interface ChatChannels {

    /**
     * Checks {@code text} like a chat message (mute, rate limit, duplicates, filter, colors, {@code [item]}), audits it as
     * chat with the channel id and hands it to {@link ChatChannel#deliver}. Main thread.
     *
     * @return true when the channel delivered it; false when it was blocked (the sender was told why)
     */
    boolean send(Player sender, ChatChannel channel, String text);
}
