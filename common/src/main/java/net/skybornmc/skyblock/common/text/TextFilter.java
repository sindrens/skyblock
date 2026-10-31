package net.skybornmc.skyblock.common.text;

import org.bukkit.entity.Player;

/**
 * The chat content filter (links, IP addresses, phone numbers, blocked and replaced words, caps) for text players write
 * outside the chat, e.g. island notes (owner N8: "same filter as chat"). Published as a service by the chat module:
 * {@code core.services().optional(TextFilter.class)}. Without the chat module text passes unchanged.
 */
@FunctionalInterface
public interface TextFilter {

    /**
     * Outcome of one check.
     *
     * @param text    the text to use (blocked words replaced, shouting lowered); the input when blocked
     * @param blocked why it is refused ({@code link}, {@code ip}, {@code phone}, {@code word}), or {@code null} when allowed
     */
    record Result(String text, String blocked) {
        public boolean isBlocked() {
            return blocked != null;
        }
    }

    /** Checks {@code text} written by {@code author} (their link bypass permission applies). Main thread or async. */
    Result check(Player author, String text);

    /** The contact reasons of {@link #checkContact}: links, IP addresses, phone numbers. */
    java.util.Set<String> CONTACT_REASONS = java.util.Set.of("link", "ip", "phone");

    /**
     * R3-32 / owner answer 305: only the contact checks (links, IP addresses, phone numbers), never the word list or the
     * caps rule; the text itself is never changed. Used for private messages. The chat filter checks contact details
     * before words, so a message with both is still refused for the contact detail.
     */
    default Result checkContact(Player author, String text) {
        return contactOnly(check(author, text), text);
    }

    /** Pure part of {@link #checkContact}: keeps a contact refusal, drops everything else (and any text change). */
    static Result contactOnly(Result full, String original) {
        if (full != null && full.isBlocked() && CONTACT_REASONS.contains(full.blocked())) {
            return new Result(original, full.blocked());
        }
        return new Result(original, null);
    }
}
