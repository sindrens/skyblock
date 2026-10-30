package net.skybornmc.skyblock.common.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * A chat channel owned by a feature module (M2.5: island chat), published as a service:
 * {@code core.services().register(id, ChatChannel.class, channel)}. The chat module looks it up for every chat message,
 * after mute, freeze and menu prompts had their turn and before its own checks: when {@link #captures} says the player
 * talks in this channel (toggle mode), the message runs through the normal chat checks (rate limit, duplicates, word and
 * link filter, {@code &} colors by permission, {@code [item]}), is audited like public chat (with {@code channel} in the
 * details) and is handed to {@link #deliver} instead of being broadcast. One-shot messages (a command such as
 * {@code /is chat <text>}) go through {@link ChatChannels#send}.
 */
public interface ChatChannel {

    /** Short lowercase id, stored in the audit details ({@code island}). */
    String id();

    /** True when {@code player}'s normal chat goes to this channel right now. Called on the async chat thread: thread-safe and fast. */
    boolean captures(Player player);

    /**
     * Delivers a message that passed the chat checks (main thread).
     *
     * @param sender  who wrote it
     * @param message the checked message (filtered, colored by the sender's chat color permissions, {@code [item]} shown)
     * @param plain   the filtered plain text
     * @return extra audit details (e.g. the island), or {@code null} when nothing was delivered (the sender was told why)
     */
    Map<String, Object> deliver(Player sender, Component message, String plain);
}
