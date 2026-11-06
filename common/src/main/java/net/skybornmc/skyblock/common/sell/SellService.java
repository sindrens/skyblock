package net.skybornmc.skyblock.common.sell;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Selling (M3.4) for every module: price lookups at today's dynamic prices and one safe way to sell items. Published by
 * the economy module: {@code core.services().optional(SellService.class)}. Later users: the satchel (M4.4), the AFK pool,
 * auto-sell chests and collectors.
 *
 * <p><b>How to sell safely</b> (the way {@code /sell} does it):</p>
 * <ol>
 *   <li>On the main thread, check {@link #sellable} and take the stacks out of the inventory/container (escrow: from
 *       now on they only exist in your request).</li>
 *   <li>Call {@link #sell} with the removed stacks, the profile key and {@link #bonusPercent}.</li>
 *   <li>When the future completes, give back {@link SellResult#giveBack()} (unsellable stacks; every stack if the sale
 *       failed). Nothing is paid unless the result is {@link SellResult.Status#SOLD}.</li>
 * </ol>
 * Every sale is <b>one</b> ledger transaction (reason {@code sell}, idempotency key {@code sell:<sale id>}) with one
 * sale record and one audit row holding every item, so items can never be paid twice and logs are not flooded.
 *
 * <p>Unique items (anything with a unique item id: custom tools, enchant books, money notes, set pieces) are never
 * sellable, whatever the price table says.</p>
 */
public interface SellService {

    /** Whether this stack can be sold right now (in the price table, enabled, not unique, not flagged). Main thread. */
    boolean sellable(ItemStack stack);

    /** Why this stack can't be sold ({@code sell.reason.<key>} in messages/economy.yml), or empty if it can. Main thread. */
    Optional<String> unsellableReason(ItemStack stack);

    /** Today's price of this stack's item, or empty if it can't be sold. Main thread. */
    Optional<SellPrice> price(ItemStack stack);

    /** Today's price by price table key ({@code coal}, {@code custom:depths_coal}), or empty. Any thread. */
    Optional<SellPrice> price(String itemKey);

    /**
     * Dynamic prices v2: the price of the <b>next</b> item for this seller, with what their island already sold today
     * (falls after the first 3,000 of an item per day). Main thread. Default: {@link #price(String)}.
     */
    default Optional<SellPrice> price(String itemKey, java.util.UUID profileKey) {
        return price(itemKey);
    }

    /** Dynamic prices v2: items of this kind the seller's island sold today (empty when not tracked). Main thread. */
    default java.util.OptionalLong soldTodayCount(java.util.UUID profileKey, String itemKey) {
        return java.util.OptionalLong.empty();
    }

    /** Dynamic prices v2: items left at the full price today (empty when the day price is off). Main thread. */
    default java.util.OptionalLong fullPriceLeft(java.util.UUID profileKey, String itemKey) {
        return java.util.OptionalLong.empty();
    }

    /** Dynamic prices v2: this week's price change of an item in percent ({@code -10 .. +10}). Any thread. */
    default double weekPercent(String itemKey) {
        return 0;
    }

    /** Every sellable price table key, in file order. Any thread. */
    List<String> itemKeys();

    /**
     * The most one item of this key could ever pay right now: base x the dynamic price ceiling x the Hot Item factor x
     * (1 + the sell bonus cap). Empty if the key can't be sold. Shops use it so nothing can be bought for less than it
     * sells for (an endless money loop). Any thread.
     */
    default java.util.OptionalDouble maxUnitPrice(String itemKey) {
        return java.util.OptionalDouble.empty();
    }

    /** The summed sell bonus of a player (all {@code BonusFamily.SELL} sources, before the cap), in percent. Main thread. */
    double bonusPercent(Player player);

    /** The economy's cap on the sell bonus in percent (+300%). */
    double bonusCapPercent();

    /** What these stacks would pay with this bonus, without selling anything. Main thread. */
    SellQuote quote(List<ItemStack> stacks, double bonusPercent);

    /**
     * Dynamic prices v2: what these stacks would pay <b>this seller</b> (the island's day count lowers big sales).
     * {@code profileKey} null = as if nothing was sold today. Main thread.
     */
    default SellQuote quote(List<ItemStack> stacks, double bonusPercent, java.util.UUID profileKey) {
        return quote(stacks, bonusPercent);
    }

    /**
     * Sells stacks the caller already removed (see the class comment). Call on the main thread; the future completes
     * off the main thread. Never completes exceptionally: errors become {@link SellResult.Status#ERROR} with every
     * stack in {@link SellResult#giveBack()}.
     */
    CompletableFuture<SellResult> sell(SellRequest request);

    /**
     * Like {@link #sell(SellRequest)}, but {@code guard} runs in the <b>same</b> database transaction as the payment and the
     * sale record: both commit or both roll back. The satchel (M4.4) uses it to remove the sold items from its table in
     * that transaction, so items held in the database can never be paid twice or vanish without payment. A guard that
     * returns false refuses the sale ({@link SellResult.Status#DUPLICATE}, nothing paid, every stack in giveBack).
     * Implementations without guard support refuse with ERROR, never sell without the guard.
     */
    default CompletableFuture<SellResult> sell(SellRequest request, net.skybornmc.skyblock.common.economy.TxGuard guard) {
        if (guard == null) {
            return sell(request);
        }
        return CompletableFuture.completedFuture(new SellResult(SellResult.Status.ERROR, null, request.items(), null, null));
    }

    /**
     * Opens the sell chest ({@code /sell}, SELL2) for this player, e.g. from the shop or the satchel. {@code parent} is the
     * menu its Back button returns to ({@code null} = none). When selling is not possible right now the player gets the
     * reason and false is returned. Main thread.
     */
    default boolean openSellChest(Player player, net.skybornmc.skyblock.common.menu.Menu parent) {
        return false;
    }

    /**
     * Sellable items of this kind (same item, unique/flagged stacks excluded) in the storage slots 0-35 (hotbar
     * included, never armor/offhand). Main thread. 0 when the economy does not implement it.
     */
    default long carried(Player player, ItemStack template) {
        return 0;
    }

    /**
     * Sells up to {@code amount} (0 = all) of {@code template} from the player's storage slots 0-35 through the same
     * escrow, one ledger transaction (reason sell, key sell:&lt;sale id&gt;), give-back and /box rules as /sell. {@code done}
     * runs on the main thread: the {@link SellResult}, or {@code null} when nothing was attempted (loading, frozen,
     * switching, busy, not sellable, none carried). Main thread.
     *
     * @param source where the sale came from (sale record data, e.g. {@code shop}); not a ledger reason
     */
    default void sellItem(Player player, ItemStack template, int amount, String source, java.util.function.Consumer<SellResult> done) {
        done.accept(null);
    }
}
