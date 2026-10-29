package net.skybornmc.skyblock.auction;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.db.Database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The auction house state machine (Bukkit-free, unit-tested against H2 with a fake economy).
 *
 * <h2>Dupe safety</h2>
 * <ul>
 *   <li><b>Listing:</b> the caller removes the item from the inventory first; {@link #create} inserts it (serialized) with
 *       the listing row in one transaction. Only {@link CreateOutcome#NOT_STORED} means the caller must give it back.</li>
 *   <li><b>Fee:</b> withdrawn with idempotency key {@code ah_fee:<id>} while the listing is PENDING, then PENDING → ACTIVE.</li>
 *   <li><b>Purchase:</b> ACTIVE → BUYING (conditional UPDATE: one buyer at most), then the buyer pays the price
 *       ({@code ah_buy:<id>}), the seller receives price minus tax ({@code ah_sale:<id>}), then BUYING → SOLD with the item
 *       moved into the buyer's collection bin in the same transaction. Cancel and expiry only work on ACTIVE, so a listing
 *       can never be sold twice, or sold and cancelled.</li>
 *   <li><b>Unknown outcomes</b> (database error mid-way, crash) leave the listing in PENDING/BUYING; {@link #recover} replays
 *       the same steps with the same idempotency keys, so money is never taken twice. A listing is only put back to ACTIVE
 *       when the economy answered INSUFFICIENT_FUNDS, which guarantees nothing was taken and the key is still unused.</li>
 * </ul>
 */
final class AuctionEngine {

    /** Ledger reason of a refunded listing fee ({@code fee.refund-on-cancel}). */
    static final String FEE_REFUND = "ah_fee_refund";

    /** What happened to a listing attempt. */
    enum CreateOutcome {
        /** Listed and visible. */
        LISTED,
        /** Refused by a rule ({@link CreateResult#rejection()}); nothing was stored, give the item back. */
        REJECTED,
        /** The fee could not be paid; the item is in the collection bin ({@link CreateResult#collectionId()}). */
        FEE_UNPAID,
        /** Stored, but the fee outcome is unknown (database problem); resolved automatically later. The item is safe. */
        PENDING,
        /** Nothing was stored (database error); give the item back. */
        NOT_STORED,
        /** Another listing by the same player is still being processed; nothing stored, give the item back. */
        BUSY
    }

    record CreateResult(CreateOutcome outcome, Listing listing, ListingRules.Rejection rejection, Long collectionId) {
        boolean giveBack() {
            return outcome == CreateOutcome.REJECTED || outcome == CreateOutcome.NOT_STORED || outcome == CreateOutcome.BUSY;
        }
    }

    enum BuyOutcome {
        /** Paid; the item is in the buyer's collection bin ({@link BuyResult#collectionId()}), ready to be delivered. */
        SOLD,
        /** Sold, cancelled or expired meanwhile. */
        GONE,
        /** The buyer is the seller. */
        OWN,
        /** The buyer can't afford it; nothing changed, the listing is still active. */
        INSUFFICIENT,
        /** Someone else is paying for it right now. */
        BUSY,
        /** Unknown outcome (database problem); resolved automatically, the buyer either gets the item or keeps the money. */
        PROCESSING
    }

    record BuyResult(BuyOutcome outcome, Listing listing, Long collectionId, Long buyerBalance) {
    }

    enum CancelOutcome { CANCELLED, GONE, BUSY, ERROR }

    record CancelResult(CancelOutcome outcome, Listing listing, Long collectionId) {
    }

    /** Callbacks for things that happen in the background (expiry, recovery, sales); called off the main thread. */
    interface Events {
        default void sold(Listing listing) {
        }

        default void expired(Listing listing) {
        }

        default void feeFailed(Listing listing, long collectionId) {
        }
    }

    private final AuctionStore store;
    private final Supplier<EconomyService> economy;
    private final AuditLog audit;
    private final Supplier<AuctionConfig> config;
    private final LongSupplier clock;
    private final IntSupplier season;
    private final Logger log;
    private volatile Events events = new Events() {
    };

    /** Listings with a transition running in this process (recovery skips them). */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    /** Sellers with a listing being created (stops two quick listings from both passing the slot check). */
    private final Set<UUID> creating = ConcurrentHashMap.newKeySet();
    /** Active listings for browsing (display only; the database decides every transition). */
    private final Map<Long, Listing> active = new ConcurrentHashMap<>();

    AuctionEngine(AuctionStore store, Supplier<EconomyService> economy, AuditLog audit, Supplier<AuctionConfig> config,
                  LongSupplier clock, IntSupplier season, Logger log) {
        this.store = store;
        this.economy = economy;
        this.audit = audit;
        this.config = config;
        this.clock = clock;
        this.season = season;
        this.log = log;
    }

    void events(Events events) {
        this.events = events;
    }

    AuctionStore store() {
        return store;
    }

    // ================================================================== cache

    /** Active listings (snapshot). */
    List<Listing> activeListings() {
        return new ArrayList<>(active.values());
    }

    Optional<Listing> cached(long id) {
        return Optional.ofNullable(active.get(id));
    }

    /** Reloads the browse cache from the database (startup and every {@code tasks.cache-resync-seconds}). */
    CompletableFuture<Void> refreshCache() {
        return store.active().thenAccept(list -> {
            Map<Long, Listing> fresh = new LinkedHashMap<>();
            for (Listing l : list) {
                if (!inFlight.contains(l.id())) {
                    fresh.put(l.id(), l);
                }
            }
            active.keySet().retainAll(fresh.keySet());
            active.putAll(fresh);
        });
    }

    // ================================================================== list

    /**
     * Creates a listing for an item the caller already removed from the player's inventory.
     *
     * @param slots the seller's listing slots ({@link ListingRules#slots})
     */
    CompletableFuture<CreateResult> create(UUID seller, String sellerName, ItemSnapshot item, long price, long durationMillis, int slots,
                                           Actor actor) {
        AuctionConfig cfg = config.get();
        Optional<ListingRules.Rejection> priceProblem = ListingRules.price(cfg, price);
        if (priceProblem.isPresent()) {
            return CompletableFuture.completedFuture(new CreateResult(CreateOutcome.REJECTED, null, priceProblem.get(), null));
        }
        if (!creating.add(seller)) {
            return CompletableFuture.completedFuture(new CreateResult(CreateOutcome.BUSY, null, null, null));
        }
        long now = clock.getAsLong();
        long fee = Pricing.fee(cfg, price);
        ListingStatus first = fee > 0 ? ListingStatus.PENDING : ListingStatus.ACTIVE;
        AuctionStore.NewListing row = new AuctionStore.NewListing(season.getAsInt(), seller, sellerName, Currency.MONEY.key(), price,
                fee, item, first, now, now + durationMillis);
        CompletableFuture<AuctionStore.Created> insert;
        try {
            insert = store.create(row, cfg, slots);
        } catch (RuntimeException e) {
            insert = CompletableFuture.failedFuture(e);
        }
        return insert.handle((created, error) -> {
            if (error != null) {
                log.log(Level.WARNING, "Auction: could not store a listing by " + sellerName + " (" + item + ")", Database.cause(error));
                return CompletableFuture.completedFuture(new CreateResult(CreateOutcome.NOT_STORED, null, null, null));
            }
            if (created.rejection() != null) {
                return CompletableFuture.completedFuture(new CreateResult(CreateOutcome.REJECTED, null, created.rejection(), null));
            }
            Listing l = created.listing();
            if (l.status() == ListingStatus.ACTIVE) {
                listed(l, actor);
                return CompletableFuture.completedFuture(new CreateResult(CreateOutcome.LISTED, l, null, null));
            }
            inFlight.add(l.id());
            return chargeFee(l, actor).whenComplete((r, e) -> inFlight.remove(l.id()));
        }).thenCompose(f -> f).whenComplete((r, e) -> creating.remove(seller));
    }

    /** PENDING: takes the fee (idempotent), then activates, or moves the item to the bin if the seller can't pay. */
    private CompletableFuture<CreateResult> chargeFee(Listing l, Actor actor) {
        Transaction tx = Transaction.withdraw(AccountRef.player(l.seller()), Currency.MONEY, l.fee(), TxReason.AH_FEE)
                .actor(actor).reference(reference(l)).idempotencyKey("ah_fee:" + l.id())
                .detail("listing", l.id()).detail("price", l.price()).detail("item", l.item().toString());
        return economy.get().execute(tx).thenCompose(result -> switch (result.status()) {
            case SUCCESS, DUPLICATE -> store.activate(l.id()).thenApply(ok -> {
                if (!ok) {
                    log.warning("Auction: listing " + l.id() + " was no longer pending after its fee was paid");
                    return new CreateResult(CreateOutcome.PENDING, l, null, null);
                }
                Listing activeListing = l.withStatus(ListingStatus.ACTIVE);
                listed(activeListing, actor);
                return new CreateResult(CreateOutcome.LISTED, activeListing, null, null);
            });
            case INSUFFICIENT_FUNDS, INVALID -> store.failPending(l.id(), clock.getAsLong()).thenApply(col -> {
                if (col.isEmpty()) {
                    return new CreateResult(CreateOutcome.PENDING, l, null, null);
                }
                audit(l.withStatus(ListingStatus.FAILED), "ah_list_failed", actor, Map.of("reason", "fee not paid"));
                events.feeFailed(l, col.get());
                return new CreateResult(CreateOutcome.FEE_UNPAID, l, null, col.get());
            });
            default -> {
                log.warning("Auction: fee of listing " + l.id() + " returned " + result.status() + "; it will be retried");
                yield CompletableFuture.completedFuture(new CreateResult(CreateOutcome.PENDING, l, null, null));
            }
        }).exceptionally(e -> {
            log.log(Level.WARNING, "Auction: fee step of listing " + l.id() + " failed; it will be retried", Database.cause(e));
            return new CreateResult(CreateOutcome.PENDING, l, null, null);
        });
    }

    private void listed(Listing l, Actor actor) {
        active.put(l.id(), l);
        audit(l, "ah_list", actor, Map.of("duration_ms", l.expiresAt() - l.createdAt()));
    }

    // ================================================================== buy

    /** Buys a listing (after the confirm dialog). Never completes exceptionally. */
    CompletableFuture<BuyResult> buy(long id, UUID buyer, String buyerName, Actor actor) {
        if (!inFlight.add(id)) {
            return CompletableFuture.completedFuture(new BuyResult(BuyOutcome.BUSY, active.get(id), null, null));
        }
        long now = clock.getAsLong();
        return store.claimForBuy(id, buyer, buyerName, now).thenCompose(claimed -> {
            if (!claimed) {
                return store.get(id).thenApply(found -> {
                    Listing l = found.orElse(null);
                    if (l != null && l.status() == ListingStatus.ACTIVE && l.seller().equals(buyer)) {
                        return new BuyResult(BuyOutcome.OWN, l, null, null);
                    }
                    if (l != null && l.status() == ListingStatus.BUYING) {
                        return new BuyResult(BuyOutcome.BUSY, l, null, null);
                    }
                    if (l != null && l.status() != ListingStatus.ACTIVE) {
                        active.remove(id);
                    }
                    return new BuyResult(BuyOutcome.GONE, l, null, null);
                });
            }
            active.remove(id);
            return store.get(id).thenCompose(found -> {
                if (found.isEmpty() || found.get().status() != ListingStatus.BUYING) {
                    return CompletableFuture.completedFuture(new BuyResult(BuyOutcome.PROCESSING, found.orElse(null), null, null));
                }
                return settle(found.get(), actor);
            });
        }).exceptionally(e -> {
            log.log(Level.WARNING, "Auction: purchase of listing " + id + " by " + buyerName + " failed", Database.cause(e));
            return new BuyResult(BuyOutcome.PROCESSING, active.get(id), null, null);
        }).whenComplete((r, e) -> inFlight.remove(id));
    }

    /**
     * BUYING: the buyer pays, the seller is paid, the listing is marked sold. Idempotent (same keys), so recovery runs the
     * exact same code for a listing a crash left in BUYING.
     */
    private CompletableFuture<BuyResult> settle(Listing l, Actor actor) {
        AuctionConfig cfg = config.get();
        long tax = Pricing.tax(cfg, l.price());
        long net = l.price() - tax;
        UUID buyer = l.buyer();
        Transaction pay = Transaction.withdraw(AccountRef.player(buyer), Currency.MONEY, l.price(), TxReason.AH_BUY)
                .actor(actor).reference(reference(l)).idempotencyKey("ah_buy:" + l.id())
                .detail("listing", l.id()).detail("target_name", l.sellerName()).detail("seller", l.seller().toString())
                .detail("item", l.item().toString());
        return economy.get().execute(pay).thenCompose(paid -> switch (paid.status()) {
            case SUCCESS, DUPLICATE -> paySeller(l, net, actor).thenCompose(sellerPaid -> {
                if (!sellerPaid) {
                    return CompletableFuture.completedFuture(new BuyResult(BuyOutcome.PROCESSING, l, null, null));
                }
                long now = clock.getAsLong();
                return store.finishSale(l.id(), buyer, tax, now).thenApply(col -> {
                    if (col.isEmpty()) {
                        log.severe("Auction: listing " + l.id() + " was paid for by " + l.buyerName()
                                + " but is no longer in 'buying'. Check /logs auction and the ledger (ah_buy:" + l.id() + ").");
                        return new BuyResult(BuyOutcome.PROCESSING, l, null, null);
                    }
                    Listing sold = l.sold(tax, now);
                    Map<String, Object> extra = new LinkedHashMap<>();
                    extra.put("buyer", buyer.toString());
                    extra.put("buyer_name", l.buyerName());
                    extra.put("tax", tax);
                    extra.put("seller_received", net);
                    extra.put("replayed", paid.status() == TxResult.Status.DUPLICATE);
                    audit(sold, "ah_buy", actor, extra);
                    events.sold(sold);
                    return new BuyResult(BuyOutcome.SOLD, sold, col.get(), paid.fromBalance());
                });
            });
            case INSUFFICIENT_FUNDS, INVALID -> store.revertBuy(l.id(), buyer).thenApply(reverted -> {
                if (reverted && l.expiresAt() > clock.getAsLong()) {
                    active.put(l.id(), l.withStatus(ListingStatus.ACTIVE));
                }
                return new BuyResult(BuyOutcome.INSUFFICIENT, l, null, null);
            });
            default -> {
                log.warning("Auction: payment for listing " + l.id() + " returned " + paid.status() + "; it will be retried");
                yield CompletableFuture.completedFuture(new BuyResult(BuyOutcome.PROCESSING, l, null, null));
            }
        });
    }

    /** Pays the seller price minus tax (idempotent). {@code false} = unknown outcome, retried by recovery. */
    private CompletableFuture<Boolean> paySeller(Listing l, long net, Actor actor) {
        if (net <= 0) {
            return CompletableFuture.completedFuture(true);
        }
        Transaction sale = Transaction.deposit(AccountRef.player(l.seller()), Currency.MONEY, net, TxReason.AH_SALE)
                .actor(actor).reference(reference(l)).idempotencyKey("ah_sale:" + l.id())
                .detail("listing", l.id()).detail("target_name", l.sellerName()).detail("buyer", String.valueOf(l.buyer()))
                .detail("buyer_name", l.buyerName()).detail("price", l.price()).detail("item", l.item().toString());
        return economy.get().execute(sale).thenApply(r -> {
            if (r.status() == TxResult.Status.SUCCESS || r.status() == TxResult.Status.DUPLICATE) {
                return true;
            }
            log.warning("Auction: paying the seller of listing " + l.id() + " returned " + r.status() + "; it will be retried");
            return false;
        });
    }

    // ================================================================== cancel / expire

    /**
     * Cancels an active listing: by its seller ({@code staff == false}, only their own) or by staff (any listing). The item
     * goes to the seller's collection bin.
     */
    CompletableFuture<CancelResult> cancel(long id, UUID seller, boolean staff, Actor actor, String reason) {
        if (inFlight.contains(id)) {
            return CompletableFuture.completedFuture(new CancelResult(CancelOutcome.BUSY, active.get(id), null));
        }
        CollectionEntry.Source source = staff ? CollectionEntry.Source.REMOVED : CollectionEntry.Source.CANCELLED;
        return store.cancel(id, staff ? null : seller, source, clock.getAsLong()).thenCompose(col -> store.get(id).thenCompose(found -> {
            if (col.isEmpty()) {
                if (found.isPresent() && found.get().status() != ListingStatus.ACTIVE) {
                    active.remove(id);
                }
                CancelOutcome outcome = found.isPresent() && found.get().status() == ListingStatus.BUYING ? CancelOutcome.BUSY
                        : CancelOutcome.GONE;
                return CompletableFuture.completedFuture(new CancelResult(outcome, found.orElse(null), null));
            }
            active.remove(id);
            Listing l = found.orElseThrow();
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("by_staff", staff);
            if (reason != null) {
                extra.put("reason", reason);
            }
            audit(l, "ah_cancel", actor, extra);
            CompletableFuture<Void> refund = !staff && config.get().feeRefundOnCancel && l.fee() > 0
                    ? economy.get().execute(Transaction.deposit(AccountRef.player(l.seller()), Currency.MONEY, l.fee(), FEE_REFUND)
                    .actor(actor).reference(reference(l)).idempotencyKey("ah_fee_refund:" + l.id()).detail("listing", l.id()))
                    .thenAccept(r -> {
                    })
                    : CompletableFuture.completedFuture(null);
            return refund.thenApply(v -> new CancelResult(CancelOutcome.CANCELLED, l, col.get()));
        })).exceptionally(e -> {
            log.log(Level.WARNING, "Auction: cancelling listing " + id + " failed", Database.cause(e));
            return new CancelResult(CancelOutcome.ERROR, active.get(id), null);
        });
    }

    /** Moves every listing whose time is up to its seller's collection bin. */
    CompletableFuture<List<Listing>> expireDue() {
        long now = clock.getAsLong();
        return store.due(now, 200).thenCompose(ids -> {
            List<Listing> done = new ArrayList<>();
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (long id : ids) {
                if (inFlight.contains(id)) {
                    continue;
                }
                chain = chain.thenCompose(v -> store.expire(id, now).thenCompose(ok -> {
                    if (!ok) {
                        return CompletableFuture.completedFuture(null);
                    }
                    active.remove(id);
                    return store.get(id).thenAccept(found -> found.ifPresent(l -> {
                        done.add(l);
                        audit(l, "ah_expire", Actor.system("auction"), Map.of());
                        events.expired(l);
                    }));
                }));
            }
            return chain.thenApply(v -> done);
        });
    }

    // ================================================================== recovery

    /**
     * Resolves listings left in PENDING or BUYING (crash, database error) for at least {@code olderThanMillis}: replays
     * the fee or the purchase with the same idempotency keys. Returns how many were looked at.
     */
    CompletableFuture<Integer> recover(long olderThanMillis) {
        return store.stuck(clock.getAsLong() - olderThanMillis).thenCompose(list -> {
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            int count = 0;
            for (Listing l : list) {
                if (!inFlight.add(l.id())) {
                    continue;
                }
                count++;
                Actor actor = Actor.system("auction-recovery");
                chain = chain.thenCompose(v -> {
                    CompletableFuture<?> step = l.status() == ListingStatus.PENDING ? chargeFee(l, actor) : settle(l, actor);
                    return step.handle((r, e) -> {
                        if (e != null) {
                            log.log(Level.WARNING, "Auction: recovery of listing " + l.id() + " failed", Database.cause(e));
                        } else {
                            log.info("Auction: recovered listing " + l.id() + " (" + l.status().key() + "): " + r);
                        }
                        inFlight.remove(l.id());
                        return null;
                    });
                });
            }
            int looked = count;
            return chain.thenApply(v -> looked);
        });
    }

    // ================================================================== collection bin

    /** Takes one item out of the bin (exactly once) and logs it with full item data. */
    CompletableFuture<Optional<CollectionEntry>> take(long entryId, UUID owner, Actor actor) {
        return store.take(entryId, owner).thenApply(entry -> {
            entry.ifPresent(e -> {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("entry", e.id());
                details.put("listing", e.listingId());
                details.put("source", e.source());
                details.put("material", e.material());
                details.put("amount", e.amount());
                details.put("name", e.name());
                ItemSnapshot.putData(details, e.bytes());
                audit.logPlayer(AuditCategory.AUCTION, "ah_collect", actor, owner, null, null, null, details);
            });
            return entry;
        });
    }

    /** Puts what did not fit back into the bin. */
    CompletableFuture<Long> putBack(UUID owner, byte[] bytes, String material, int amount, String name) {
        return store.put(owner, CollectionEntry.Source.RETURNED, bytes, material, amount, name, clock.getAsLong());
    }

    // ================================================================== audit

    private static String reference(Listing l) {
        return "ah:" + l.id();
    }

    /** One audit row (category auction) with the full item summary, so {@code /logs} shows every step of a listing. */
    private void audit(Listing l, String action, Actor actor, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("listing", l.id());
        details.put("seller", l.seller().toString());
        details.put("seller_name", l.sellerName());
        details.put("price", l.price());
        details.put("fee", l.fee());
        details.put("currency", l.currency());
        details.put("expires_at", l.expiresAt());
        details.put("item", l.item().audit());
        details.putAll(extra);
        audit.logPlayer(AuditCategory.AUCTION, action, actor, l.seller(), l.sellerName(), l.price(), null, details);
    }
}
