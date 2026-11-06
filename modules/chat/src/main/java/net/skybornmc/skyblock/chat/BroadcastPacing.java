package net.skybornmc.skyblock.chat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * When the next broadcast card may go out (owner answer 3, 4 Nov 2026: "every 7 minutes, but only when chat is
 * active"). A card is due when, since the last card,
 * <ul>
 *   <li>at least {@code interval} has passed,</li>
 *   <li>players sent at least {@code minMessages} public chat messages,</li>
 *   <li>from at least {@code minChatters} different players,</li>
 *   <li>and at least {@code minOnline} players are online.</li>
 * </ul>
 * In a quiet chat nothing is sent, and since {@link #sent} starts a new window, cards never stack up (at most one per
 * window). Thread-safe: chat messages are counted from the async chat thread. Pure, unit-tested.
 */
final class BroadcastPacing {

    /** The numbers of the rule (from chat.yml {@code broadcasts.*}). */
    record Rules(long intervalMillis, int minMessages, int minChatters, int minOnline) {
    }

    private long windowStart;
    private int messages;
    private final Set<UUID> chatters = new HashSet<>();

    /** A new pacing whose first window starts at {@code now} (the first card comes one interval after start). */
    BroadcastPacing(long now) {
        this.windowStart = now;
    }

    /** Counts one public chat message by {@code player}. */
    synchronized void chat(UUID player) {
        if (messages < Integer.MAX_VALUE) {
            messages++;
        }
        if (player != null && chatters.size() < 10_000) {
            chatters.add(player);
        }
    }

    /** True when a card may go out now. */
    synchronized boolean due(long now, int online, Rules rules) {
        return now - windowStart >= rules.intervalMillis()
                && messages >= rules.minMessages()
                && chatters.size() >= rules.minChatters()
                && online >= rules.minOnline();
    }

    /** A card went out: the next window starts now, the chat count starts over. */
    synchronized void sent(long now) {
        windowStart = now;
        messages = 0;
        chatters.clear();
    }

    /**
     * Whether one player gets the card (pure part of the per-player rules): "Tips" setting on, past the join grace
     * (they just got the welcome), not AFK and not at the card's place. {@code joinedAgoMillis < 0} = unknown (online
     * since before a reload): counts as long ago.
     */
    static boolean receives(boolean tipsOn, long joinedAgoMillis, long graceMillis, boolean afk, boolean atPlace) {
        if (!tipsOn || afk || atPlace) {
            return false;
        }
        return joinedAgoMillis < 0 || joinedAgoMillis >= graceMillis;
    }

    synchronized int messages() {
        return messages;
    }

    synchronized int chatters() {
        return chatters.size();
    }

    synchronized long windowStart() {
        return windowStart;
    }
}
