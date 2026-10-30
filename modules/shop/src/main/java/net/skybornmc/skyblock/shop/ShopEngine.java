package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The money side of a purchase (M3.5), without Bukkit: <b>one</b> ledger transaction (reason {@code shop_buy}, idempotency
 * key {@code shop_buy:<purchase id>}) that takes the money from the <b>profile</b> account and writes the purchase row in
 * the same database transaction. Items are only handed out after it succeeded (by the module).
 */
final class ShopEngine {

    /** What the player wants to buy; {@code profile} is the profile key that pays and receives. */
    record Request(UUID player, UUID profile, String name, ShopCatalog.Item item, int quantity, Actor actor) {
    }

    enum Status {
        /** Paid; the purchase row is {@code delivering}. */
        OK,
        NOT_ENOUGH,
        /** Total too large, quantity below 1 … nothing happened. */
        INVALID,
        /** The economy refused or failed; nothing changed. */
        ERROR
    }

    record Result(Status status, ShopStore.Purchase purchase, Long balance) {
    }

    private final Supplier<EconomyService> economy;
    private final LongSupplier clock;
    private final IntSupplier season;

    ShopEngine(Supplier<EconomyService> economy, LongSupplier clock, IntSupplier season) {
        this.economy = economy;
        this.clock = clock;
        this.season = season;
    }

    /** Builds the ledger transaction for a purchase (also used by tests to check what is written). */
    static Transaction transaction(ShopStore.Purchase p, Actor actor) {
        return Transaction.withdraw(AccountRef.player(p.profile()), Currency.MONEY, p.totalPrice(), TxReason.SHOP_BUY)
                .actor(actor)
                .reference("shop:" + p.id())
                .idempotencyKey("shop_buy:" + p.id())
                .detail("target_name", p.playerName())
                .detail("purchase", p.id().toString())
                .detail("item", p.itemId())
                .detail("category", p.category())
                .detail("quantity", p.quantity())
                .detail("unit_price", p.unitPrice())
                .detail("profile", p.profile().toString());
    }

    /** Pays for the request. Never completes exceptionally. */
    CompletableFuture<Result> buy(Request r) {
        OptionalLong total = ShopMath.total(r.item().price(), r.quantity());
        if (total.isEmpty()) {
            return CompletableFuture.completedFuture(new Result(Status.INVALID, null, null));
        }
        long now = clock.getAsLong();
        ShopStore.Purchase purchase = new ShopStore.Purchase(UUID.randomUUID(), season.getAsInt(), r.player(), r.profile(), r.name(),
                r.item().id(), r.item().category(), r.quantity(), r.item().price(), total.getAsLong(), Currency.MONEY.key(),
                ShopStore.DELIVERING, now);
        EconomyService eco;
        try {
            eco = economy.get();
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(new Result(Status.ERROR, null, null));
        }
        return eco.executeGuarded(transaction(purchase, r.actor()), c -> ShopStore.insert(c, purchase, now))
                .handle((tx, error) -> {
                    if (error != null || tx == null) {
                        return new Result(Status.ERROR, null, null);
                    }
                    return switch (tx.status()) {
                        case SUCCESS -> new Result(Status.OK, purchase, tx.fromBalance());
                        case INSUFFICIENT_FUNDS -> new Result(Status.NOT_ENOUGH, null, null);
                        case INVALID -> new Result(Status.INVALID, null, null);
                        default -> new Result(Status.ERROR, null, null);
                    };
                });
    }

}
