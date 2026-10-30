package net.skybornmc.skyblock.common.moderation;

import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Economy and trade sanctions (Phase P spec 13.1), published by the moderation module:
 * {@code core.services().optional(SanctionService.class)}. Sanctions are <b>account-wide</b>: they are stored by player
 * UUID and apply to every profile of the player (a profile key is mapped to its player here).
 *
 * <ul>
 *   <li><b>Freeze balance</b> ({@link Kind#FREEZE}): the player can't spend (pay, money notes, bank, shop, auction, trade).</li>
 *   <li><b>Block trading</b> ({@link Kind#TRADE}): no auction listings or purchases, no {@code /pay}, no trades.</li>
 * </ul>
 * Which {@link Use} each kind blocks comes from {@code punishments.yml → extras.sanctions}. Modules that move value call
 * {@link #denied(ServiceRegistry, Player, Use)} at their entry point (it tells the player why); the economy also checks
 * every transaction with {@link #blocks(Transaction)} as a safety net. All methods are thread-safe and never touch the
 * database (an in-memory copy of the active sanctions).
 */
public interface SanctionService {

    /** The blocking sanction kinds (removing money and confiscating items are one-off actions, they block nothing). */
    enum Kind { FREEZE, TRADE }

    /** A place where value moves. {@link #id()} is the config spelling ({@code auction-list}). */
    enum Use {
        /** {@code /pay}. */
        PAY,
        /** Creating or redeeming money notes ({@code /withdraw}). */
        NOTE,
        /** Putting money into or taking it out of a personal or island bank. */
        BANK,
        /** Buying from the server shop. */
        SHOP,
        /** Listing items on the auction house. */
        AUCTION_LIST,
        /** Buying on the auction house. */
        AUCTION_BUY,
        /** Secure trades ({@code /trade}). */
        TRADE;

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        /** The use with this config spelling ({@code auction-list}), or empty. */
        public static Optional<Use> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String key = raw.strip().toLowerCase(Locale.ROOT);
            for (Use u : values()) {
                if (u.id().equals(key)) {
                    return Optional.of(u);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * An active sanction that blocks something.
     *
     * @param id     the sanction id (shown to staff)
     * @param until  end (epoch millis), {@code null} = until lifted
     * @param reason the reason text
     */
    record Block(long id, Kind kind, Long until, String reason) {
    }

    /** The active sanction that blocks {@code use} for this player (player UUID or one of their profile keys), if any. */
    Optional<Block> block(UUID player, Use use);

    /**
     * Safety net for the economy: true if an active sanction forbids this transaction (money leaving a frozen player's
     * accounts, or a trade-like transfer touching a trade-blocked player). The economy refuses it without changing
     * anything.
     */
    boolean blocks(Transaction transaction);

    /** Tells {@code player} why {@code use} is blocked right now (one line, error sound). Main thread. */
    void tell(Player player, Block block, Use use);

    /** Tells {@code viewer} that {@code otherName} can't do {@code use} right now (no details about the sanction). */
    void tellOther(Player viewer, String otherName, Use use);

    /**
     * SECFIX1 F-15: tells {@code player} that {@code use} is paused because the sanction check failed (fail closed). The
     * default says nothing; the moderation module sends its message. Main thread.
     */
    default void tellUnavailable(Player player, Use use) {
    }

    /** The published service, if the moderation module runs. */
    static Optional<SanctionService> of(ServiceRegistry services) {
        return services == null ? Optional.empty() : services.optional(SanctionService.class);
    }

    /**
     * The one-line hook for value paths: true (and the player told why) if a sanction blocks {@code use} for
     * {@code player}. False when nothing blocks it or the moderation module is off. Main thread.
     */
    static boolean denied(ServiceRegistry services, Player player, Use use) {
        Optional<SanctionService> service = of(services);
        if (service.isEmpty() || player == null) {
            return false;
        }
        Optional<Block> block;
        try {
            block = service.get().block(player.getUniqueId(), use);
        } catch (RuntimeException e) {
            return failClosed(service.get(), player, use, e); // SECFIX1 F-15
        }
        block.ifPresent(b -> service.get().tell(player, b, use));
        return block.isPresent();
    }

    /**
     * SECFIX1 F-15: the check threw, so the value move is refused (fail closed: "everything not allowed is impossible")
     * and {@code player} is told it is paused. Always true.
     */
    private static boolean failClosed(SanctionService service, Player player, Use use, RuntimeException error) {
        java.util.logging.Logger.getLogger("PhoenixSkyblock").log(java.util.logging.Level.SEVERE,
                "Sanction check failed for " + (player == null ? "?" : player.getName()) + " (" + use.id() + "); refused", error);
        if (player != null) {
            try {
                service.tellUnavailable(player, use);
            } catch (RuntimeException ignored) {
                // the service is broken: refusing is what matters
            }
        }
        return true;
    }

    /**
     * Same check for the other side of a two-player action (trade partner): true (and {@code viewer} told, without
     * details) if {@code other} may not do {@code use}. Main thread.
     */
    static boolean deniedOther(ServiceRegistry services, Player viewer, Player other, Use use) {
        Optional<SanctionService> service = of(services);
        if (service.isEmpty() || other == null) {
            return false;
        }
        boolean blocked;
        try {
            blocked = service.get().block(other.getUniqueId(), use).isPresent();
        } catch (RuntimeException e) {
            return failClosed(service.get(), viewer, use, e); // SECFIX1 F-15
        }
        if (blocked && viewer != null) {
            service.get().tellOther(viewer, other.getName(), use);
        }
        return blocked;
    }
}
