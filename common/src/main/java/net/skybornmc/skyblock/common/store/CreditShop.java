package net.skybornmc.skyblock.common.store;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The credit shop ({@code /credit shop}, {@code /store}; owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 1), published by the
 * economy module. Tebex sells only credit packs; everything else is bought here. Modules add what they sell with the
 * static {@link #register} (on enable) and remove it with {@link #unregisterAll} (on disable); the shop shows the tiles, takes
 * the credits and then calls the offer's {@link Deliver}. A delivery that reports {@code false} (or fails) is refunded.
 *
 * <p><b>Credits:</b> earned credits pay first, then bought ones, except for {@link Offer#boughtOnly()} offers (ranks,
 * gifts), which take bought credits only.</p>
 *
 * <p><b>Account things</b> ({@link #owned}, {@link #consume}): small counters the shop keeps per account, such as an
 * island rename token or an extra {@code /pv} vault, so a module needs no table of its own for them.</p>
 */
public interface CreditShop {

    /** The menu tabs, left to right. */
    enum Tab { RANKS, BOOSTERS, COSMETICS, EXTRAS }

    /**
     * Gives what was paid for. Main thread. {@code buyer} paid; {@code receiver} gets it (the same player unless it is a
     * gift; always online). {@code purchaseId} is unique per purchase (use it as an idempotency key).
     */
    @FunctionalInterface
    interface Deliver {
        CompletableFuture<Boolean> deliver(Player buyer, Player receiver, String purchaseId);
    }

    /**
     * One thing for sale.
     *
     * @param id        unique id, lower case ({@code pass_premium}); also the audit and ledger reference
     * @param tab       where it is shown
     * @param order     position in the tab (lower first)
     * @param icon      the tile for (viewer, receiver): name and lore without the price lines (the shop adds them)
     * @param price     credits for the receiver; below 0 = can't be bought now (the icon says why: owned, locked ...)
     * @param boughtOnly only bought credits pay
     * @param giftable  may be bought for another player
     * @param deliver   what happens after the payment; null for a link tile
     * @param open      a link tile: a click opens the module's own menu instead of buying (null for a normal offer)
     */
    record Offer(String id, Tab tab, int order, BiFunction<Player, Player, ItemStack> icon, BiFunction<Player, Player, Long> price,
                 boolean boughtOnly, boolean giftable, Deliver deliver, Consumer<Player> open) {

        /** A normal offer. */
        public static Offer of(String id, Tab tab, int order, BiFunction<Player, Player, ItemStack> icon,
                               BiFunction<Player, Player, Long> price, boolean boughtOnly, boolean giftable, Deliver deliver) {
            return new Offer(id, tab, order, icon, price, boughtOnly, giftable, deliver, null);
        }

        /** A link tile (no price): opens another menu. */
        public static Offer link(String id, Tab tab, int order, BiFunction<Player, Player, ItemStack> icon, Consumer<Player> open) {
            return new Offer(id, tab, order, icon, (a, b) -> -1L, false, false, null, open);
        }
    }

    /** The published shop, if the economy module runs. Cheap: look it up per call. */
    static Optional<CreditShop> of(ServiceRegistry services) {
        return services.optional(CreditShop.class);
    }

    /**
     * Adds (or replaces, same id) an offer of {@code module}. Static so a module can register in its own enable whatever
     * the module order is (the economy module, which shows them, may start later). Main thread.
     */
    static void register(String module, Offer offer) {
        Offers.ALL.computeIfAbsent(module, k -> new java.util.concurrent.ConcurrentHashMap<>()).put(offer.id(), offer);
    }

    /** Removes every offer of {@code module} (its disable). Main thread. */
    static void unregisterAll(String module) {
        Offers.ALL.remove(module);
    }

    /** Every registered offer, by module (read by the shop). */
    static java.util.Map<String, java.util.Map<String, Offer>> offers() {
        return java.util.Collections.unmodifiableMap(Offers.ALL);
    }

    /** The registry behind {@link #register}. */
    final class Offers {
        static final java.util.Map<String, java.util.Map<String, Offer>> ALL = new java.util.concurrent.ConcurrentHashMap<>();

        private Offers() {
        }
    }

    /** Opens the shop for a player (first tab, or {@code tab}). Main thread. */
    void open(Player player, Tab tab);

    /** How many of an account thing ({@code key}) the account has; cached for online players, 0 when unknown. */
    int owned(UUID account, String key);

    /** Uses one of an account thing; completes with false when there was none. */
    CompletableFuture<Boolean> consume(UUID account, String key);
}
