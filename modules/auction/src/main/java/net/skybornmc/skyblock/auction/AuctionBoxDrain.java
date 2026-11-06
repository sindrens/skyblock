package net.skybornmc.skyblock.auction;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.db.Database;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ECON-SELL-SHOP-AH C3 (owner C6-Q2): items that come back from the auction house (expired, cancelled, removed by staff,
 * fee not paid, bought while offline) go to the owner's {@code /box} (source "From auction"); the collection bin is gone.
 *
 * <p>Crash safety (may lose at worst, never duplicates): the state change still writes the item into {@code ah_collection}
 * in the same transaction (a durable outbox). The drain then deletes each row exactly once ({@link AuctionStore#takeAll})
 * and hands the items to the box, which stores them in one transaction (or spools them). If the box is missing or the
 * delivery fails, the rows are put back and retried by the periodic drain.</p>
 */
final class AuctionBoxDrain {

    private final AuctionEngine engine;
    private final Supplier<Optional<ItemBox>> box;
    private final AuditLog audit;
    private final Executor main;
    private final Function<byte[], ItemStack> decode;
    private final Logger log;
    /** Owners with a drain running in this process (a second trigger waits for the next round). */
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    AuctionBoxDrain(AuctionEngine engine, Supplier<Optional<ItemBox>> box, AuditLog audit, Executor main,
                    Function<byte[], ItemStack> decode, Logger log) {
        this.engine = engine;
        this.box = box;
        this.audit = audit;
        this.main = main;
        this.decode = decode;
        this.log = log;
    }

    /**
     * Moves every waiting item of one profile to its /box (works offline). Completes with the number of rows moved;
     * never exceptionally.
     */
    CompletableFuture<Integer> drain(UUID owner) {
        if (owner == null || box.get().isEmpty() || !running.add(owner)) {
            return CompletableFuture.completedFuture(0);
        }
        return engine.takeAll(owner).thenComposeAsync(rows -> deliver(owner, rows), main).handle((n, e) -> {
            running.remove(owner);
            if (e != null) {
                log.log(Level.WARNING, "Auction: moving items of " + owner + " to /box failed; retried later", Database.cause(e));
                return 0;
            }
            return n;
        });
    }

    private CompletableFuture<Integer> deliver(UUID owner, List<CollectionEntry> rows) {
        if (rows.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        Optional<ItemBox> target = box.get();
        List<ItemStack> items = new ArrayList<>();
        List<CollectionEntry> unreadable = new ArrayList<>();
        for (CollectionEntry e : rows) {
            try {
                items.add(decode.apply(e.bytes()));
            } catch (RuntimeException ex) {
                unreadable.add(e);
            }
        }
        if (target.isEmpty()) {
            return putBack(owner, rows).thenApply(v -> 0);
        }
        CompletableFuture<Void> back = unreadable.isEmpty() ? CompletableFuture.completedFuture(null) : putBack(owner, unreadable);
        if (!unreadable.isEmpty()) {
            log.warning("Auction: " + unreadable.size() + " item(s) of " + owner + " could not be read; they stay in ah_collection for staff");
        }
        if (items.isEmpty()) {
            return back.thenApply(v -> 0);
        }
        List<CollectionEntry> moved = rows.stream().filter(r -> !unreadable.contains(r)).toList();
        return target.get().deliver(owner, items, BoxSource.AUCTION, Actor.system("auction")).handle((d, e) -> {
            if (e != null) {
                log.log(Level.WARNING, "Auction: /box refused " + items.size() + " item(s) of " + owner + "; they stay waiting", Database.cause(e));
                return putBack(owner, moved).thenApply(v -> 0);
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("profile", owner.toString());
            details.put("rows", moved.size());
            List<String> list = new ArrayList<>();
            for (CollectionEntry r : moved) {
                list.add(r.amount() + "x " + r.name() + " (" + r.source() + ", listing " + r.listingId() + ")");
            }
            details.put("items", list);
            audit.logPlayer(AuditCategory.AUCTION, "ah_collection_drain", Actor.system("auction"), owner, null, (long) moved.size(), null, details);
            return CompletableFuture.completedFuture(moved.size());
        }).thenCompose(f -> f).thenCombine(back, (n, v) -> n);
    }

    /** Puts rows back into ah_collection (box missing or refused); the item data is logged if even that fails. */
    private CompletableFuture<Void> putBack(UUID owner, List<CollectionEntry> rows) {
        List<CompletableFuture<?>> all = new ArrayList<>();
        for (CollectionEntry r : rows) {
            all.add(engine.putBack(owner, r.bytes(), r.material(), r.amount(), r.name()).exceptionally(e -> {
                log.log(Level.SEVERE, "Auction: could not keep an item of " + owner + ". Restore it from this data: "
                        + java.util.Base64.getEncoder().encodeToString(r.bytes()), Database.cause(e));
                return null;
            }));
        }
        return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new));
    }

    /** Drains every owner with waiting rows, 50 owners per round (startup and the periodic retry). */
    CompletableFuture<Integer> drainAll() {
        if (box.get().isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return engine.store().collectionOwners(50).thenCompose(owners -> {
            CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
            for (UUID owner : owners) {
                chain = chain.thenCompose(sum -> drain(owner).thenApply(n -> sum + n));
            }
            return chain;
        }).exceptionally(e -> {
            log.log(Level.WARNING, "Auction: /box drain failed; retried later", Database.cause(e));
            return 0;
        });
    }
}
