package net.skybornmc.skyblock.common.social;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The shared {@code /ignore} lists (owner answers 316/317), published by the social module:
 * {@code core.services().optional(IgnoreService.class)}. Ignoring blocks private messages, {@code /tpa} and
 * {@code /tpahere}, trade requests and island invites from that player, and (an option in {@code /settings}) hides their
 * public chat lines for the ignoring player only. Staff ({@link #UNIGNORABLE}) can never be ignored.
 *
 * <p>All reads are in-memory (lists of online players are loaded on join) and thread-safe, so the async chat event can
 * use them too.</p>
 */
public interface IgnoreService {

    /** Staff with this permission can't be ignored (owned by the social module). */
    String UNIGNORABLE = "skyblock.staff.unignorable";

    /** True if {@code viewer} has {@code sender} on their ignore list (no staff check). Any thread. */
    boolean isIgnoring(UUID viewer, UUID sender);

    /**
     * True if {@code viewer} hides {@code sender}'s public chat: ignoring them and the {@code /settings} option is on.
     * No staff check (callers use {@link #hidesChat(Player, Player)}). Any thread.
     */
    boolean hidesChat(UUID viewer, UUID sender);

    /** {@link #hidesChat(UUID, UUID)} with the staff rule: staff lines are always shown. Any thread. */
    default boolean hidesChat(Player viewer, Player sender) {
        return viewer != null && sender != null && blocks(viewer.equals(sender), sender.hasPermission(UNIGNORABLE),
                hidesChat(viewer.getUniqueId(), sender.getUniqueId()));
    }

    /**
     * The one rule every module uses: a request (tpa, trade, island invite) or message from {@code sender} is blocked for
     * {@code viewer} when the viewer ignores the sender, it is not the same player and the sender is not staff. Pure.
     */
    static boolean blocks(boolean samePlayer, boolean senderUnignorable, boolean viewerIgnoresSender) {
        return !samePlayer && !senderUnignorable && viewerIgnoresSender;
    }

    /**
     * True if {@code viewer} ignores {@code sender} (and the sender is not staff). False when the social module is off.
     * Any thread.
     */
    static boolean ignoring(ServiceRegistry services, Player viewer, Player sender) {
        if (services == null || viewer == null || sender == null) {
            return false;
        }
        IgnoreService service = services.optional(IgnoreService.class).orElse(null);
        return service != null && blocks(viewer.equals(sender), sender.hasPermission(UNIGNORABLE),
                service.isIgnoring(viewer.getUniqueId(), sender.getUniqueId()));
    }
}
