package net.skybornmc.skyblock.social;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R3-32: until an account finishes the tutorial, only staff and players it messaged first can send it private messages.
 * Remembers, per protected account, who it messaged this session (kept until it logs out). Pure, unit-tested.
 */
final class NewPlayerDms {

    private final Map<UUID, Set<UUID>> contacted = new ConcurrentHashMap<>();

    /**
     * May {@code sender} message {@code recipient}?
     *
     * @param enabled            {@code dm.new-player-protection}
     * @param recipientProtected the recipient has not finished the tutorial
     * @param senderStaff        the sender is staff (or the console)
     */
    boolean allows(boolean enabled, boolean recipientProtected, boolean senderStaff, UUID recipient, UUID sender) {
        if (!enabled || !recipientProtected || senderStaff) {
            return true;
        }
        Set<UUID> partners = contacted.get(recipient);
        return partners != null && partners.contains(sender);
    }

    /** {@code sender} (a protected account) messaged {@code recipient}: the recipient may answer from now on. */
    void messaged(UUID sender, UUID recipient) {
        contacted.computeIfAbsent(sender, k -> ConcurrentHashMap.newKeySet()).add(recipient);
    }

    /** Logout: the account's list is cleared. */
    void forget(UUID player) {
        contacted.remove(player);
    }

    int tracked() {
        return contacted.size();
    }
}
