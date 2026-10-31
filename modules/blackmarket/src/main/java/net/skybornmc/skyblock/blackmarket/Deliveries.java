package net.skybornmc.skyblock.blackmarket;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.enchant.EnchantService;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.common.stacking.StackingService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Hand-outs of buys from {@code black_market_buys}: {@code pending} → {@code sending} (one atomic update, so a buy is
 * never handed out twice) → {@code delivered}. Same pattern as the collection log and the daily rewards:
 * <ul>
 *   <li>A module that is missing right now (crates, wardrobe ...) leaves the row {@code pending}; it is tried again every
 *       {@code delivery.retry-seconds} and at the next start.</li>
 *   <li>A hand-out that can never happen (unknown set, island gone) gives the price back once (idempotency key) and the
 *       row becomes {@code refunded}; if even that fails it stays {@code failed} for staff.</li>
 *   <li>Rows still {@code sending} at start were cut off by a crash: tags and charters (safe to repeat) are tried again,
 *       items and keys go to {@code review} for staff ({@code buy_review} in /logs). Lost, never doubled.</li>
 * </ul>
 */
final class Deliveries {

    enum Result { DELIVERED, RETRY, FAILED }

    private static final int BATCH = 200;

    private final BlackMarketModule m;
    private volatile boolean recovered;

    Deliveries(BlackMarketModule module) {
        this.m = module;
    }

    /** Hands out a buy that just committed. Main thread. */
    void handOut(BlackMarketStore.BuyRow row) {
        take(row);
    }

    /** Periodic: recover interrupted rows once, then hand out waiting ones. Main thread. */
    void retry() {
        if (!recovered) {
            recovered = true;
            recover().whenCompleteAsync((v, e) -> retryPending(), m.main());
            return;
        }
        retryPending();
    }

    private void retryPending() {
        m.core.database().async(c -> BlackMarketStore.buys(c, BlackMarketStore.PENDING, BATCH)).whenCompleteAsync((rows, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Black Market: could not read waiting hand-outs", Database.cause(e));
                return;
            }
            rows.forEach(this::take);
        }, m.main());
    }

    /** True for hand-outs that are safe to run twice (a tag owned is not granted again, a size is only raised). */
    static boolean repeatable(String kind) {
        return OfferCatalog.Kind.TAG.key().equals(kind) || Purchases.CHARTER_KIND.equals(kind);
    }

    private CompletableFuture<Void> recover() {
        long now = System.currentTimeMillis();
        return m.core.database().async(c -> {
            List<BlackMarketStore.BuyRow> reviewed = new ArrayList<>();
            for (BlackMarketStore.BuyRow row : BlackMarketStore.buys(c, BlackMarketStore.SENDING, 10_000)) {
                String to = repeatable(row.kind()) ? BlackMarketStore.PENDING : BlackMarketStore.REVIEW;
                if (BlackMarketStore.move(c, row.id(), BlackMarketStore.SENDING, to, now) && to.equals(BlackMarketStore.REVIEW)) {
                    reviewed.add(row);
                }
            }
            return reviewed;
        }).thenAcceptAsync(reviewed -> {
            for (BlackMarketStore.BuyRow row : reviewed) {
                m.audit("buy_review", Actor.SYSTEM, row.account(), null, row.price(), details(row));
            }
            if (!reviewed.isEmpty()) {
                m.core.plugin().getLogger().warning("Black Market: " + reviewed.size() + " hand-out(s) were cut off by a stop or crash; "
                        + "they are marked 'review' (buy_review in /logs, category blackmarket). Check the player's inventory and /box "
                        + "and give what is missing by hand.");
            }
        }, m.main()).exceptionally(e -> {
            m.core.plugin().getLogger().log(Level.WARNING, "Black Market: could not check interrupted hand-outs", Database.cause(e));
            return null;
        });
    }

    private boolean available(BlackMarketStore.BuyRow row) {
        OfferCatalog.Kind kind = OfferCatalog.Kind.of(row.kind()).orElse(null);
        if (Purchases.CHARTER_KIND.equals(row.kind())) {
            return m.islands().isPresent();
        }
        if (kind == null) {
            return true; // unknown kind: perform() fails it (refund)
        }
        return switch (kind) {
            case SET_TOKEN -> m.core.services().has(WardrobeService.class);
            case ENCHANT_BOOK, SCROLL -> m.core.services().has(EnchantService.class);
            case KEY -> m.core.services().has(CrateKeys.class);
            case SPAWNER -> m.core.services().has(StackingService.class);
            case TAG -> m.cosmetics().isPresent();
            default -> true;
        };
    }

    private void take(BlackMarketStore.BuyRow row) {
        if (!available(row)) {
            return; // stays pending; tried again later
        }
        long now = System.currentTimeMillis();
        m.core.database().async(c -> BlackMarketStore.move(c, row.id(), BlackMarketStore.PENDING, BlackMarketStore.SENDING, now))
                .whenCompleteAsync((taken, e) -> {
                    if (e != null) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Black Market: could not take hand-out " + row.id(), Database.cause(e));
                        return;
                    }
                    if (!Boolean.TRUE.equals(taken)) {
                        return; // someone else has it
                    }
                    CompletableFuture<Result> done;
                    try {
                        done = perform(row);
                    } catch (RuntimeException ex) {
                        done = CompletableFuture.failedFuture(ex);
                    }
                    done.whenCompleteAsync((result, error) -> finish(row, error != null ? Result.FAILED : result, error), m.main());
                }, m.main());
    }

    private void finish(BlackMarketStore.BuyRow row, Result result, Throwable error) {
        Result r = result == null ? Result.FAILED : result;
        String to = switch (r) {
            case DELIVERED -> BlackMarketStore.DELIVERED;
            case RETRY -> BlackMarketStore.PENDING;
            case FAILED -> BlackMarketStore.FAILED;
        };
        long now = System.currentTimeMillis();
        m.core.database().async(c -> BlackMarketStore.move(c, row.id(), BlackMarketStore.SENDING, to, now)).whenCompleteAsync((moved, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Black Market: could not mark hand-out " + row.id() + " as " + to
                        + " (it is reviewed at the next start)", Database.cause(e));
                return;
            }
            if (r == Result.FAILED && Boolean.TRUE.equals(moved)) {
                refund(row);
            }
        }, m.main());
        if (r == Result.FAILED) {
            m.core.plugin().getLogger().log(Level.WARNING, "Black Market: hand-out " + row.kind() + " " + row.ref() + " x" + row.amount()
                    + " of buy " + row.id() + " failed; the price is given back", error == null ? null : Database.cause(error));
            Map<String, Object> d = details(row);
            d.put("error", error == null ? "" : String.valueOf(Database.cause(error).getMessage()));
            m.audit("buy_failed", Actor.SYSTEM, row.account(), null, row.price(), d);
        } else if (r == Result.DELIVERED) {
            m.audit("buy_delivered", Actor.SYSTEM, row.account(), null, row.price(), details(row));
        }
    }

    /** Gives the price of a failed hand-out back once ({@code failed} → {@code refunded}); staff gives cost nothing. */
    private void refund(BlackMarketStore.BuyRow row) {
        Optional<Currency> currency = Currency.byKey(row.currency());
        EconomyService economy = m.economy().orElse(null);
        if (row.price() <= 0 || currency.isEmpty() || economy == null) {
            return;
        }
        // Credits come back as the halves the payment took (ledger key bm:<buy id>); never as more bought credits (C1).
        net.skybornmc.skyblock.common.economy.CreditSpend.refund(economy,
                Transaction.deposit(AccountRef.player(row.profile()), currency.get(), row.price(), TxReason.BLACK_MARKET_REFUND)
                        .actor(Actor.system(BlackMarketModule.ID)).reference("bm:" + row.offerId()).idempotencyKey("bm_refund:" + row.id())
                        .detail("buy", row.id().toString()), "bm:" + row.id()).whenCompleteAsync((result, error) -> {
            if (error != null || result == null || !(result.ok() || result.status() == net.skybornmc.skyblock.common.economy.TxResult.Status.DUPLICATE)) {
                m.core.plugin().getLogger().warning("Black Market: the refund of buy " + row.id() + " failed; it stays 'failed' for staff");
                return;
            }
            long now = System.currentTimeMillis();
            m.core.database().async(c -> BlackMarketStore.move(c, row.id(), BlackMarketStore.FAILED, BlackMarketStore.REFUNDED, now));
            m.audit("buy_refunded", Actor.system(BlackMarketModule.ID), row.account(), null, row.price(), details(row));
            Player p = m.receiver(row.profile());
            if (p != null) {
                m.fail(p, "delivery.refunded", Msg.p("price", m.price(currency.get(), row.price())));
            }
        }, m.main());
    }

    static Map<String, Object> details(BlackMarketStore.BuyRow row) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("buy", row.id().toString());
        d.put("profile", row.profile().toString());
        d.put("offer", row.offerId());
        d.put("reward", row.kind() + ":" + row.ref());
        d.put("amount", row.amount());
        d.put("currency", row.currency());
        if (row.island() != null) {
            d.put("island", row.island().toString());
        }
        return d;
    }

    // ================================================================== the hand-outs

    private CompletableFuture<Result> perform(BlackMarketStore.BuyRow row) {
        Actor actor = Actor.system(BlackMarketModule.ID);
        String origin = "blackmarket:" + row.offerId();
        if (Purchases.CHARTER_KIND.equals(row.kind())) {
            return charter(row, actor);
        }
        OfferCatalog.Kind kind = OfferCatalog.Kind.of(row.kind()).orElse(OfferCatalog.Kind.COMING_SOON);
        int amount = Math.max(1, Math.min(OfferCatalog.MAX_AMOUNT, row.amount()));
        return switch (kind) {
            case KEY -> {
                Optional<CrateKeys> keys = m.core.services().optional(CrateKeys.class);
                if (keys.isEmpty()) {
                    yield CompletableFuture.completedFuture(Result.RETRY);
                }
                if (!keys.get().exists(row.ref())) {
                    yield CompletableFuture.completedFuture(Result.FAILED);
                }
                yield keys.get().giveKeys(row.profile(), row.ref(), amount, BlackMarketModule.KEY_SOURCE, actor)
                        .thenApply(ok -> Boolean.TRUE.equals(ok) ? Result.DELIVERED : Result.RETRY);
            }
            case TAG -> {
                Optional<Cosmetics> cosmetics = m.cosmetics();
                if (cosmetics.isEmpty()) {
                    yield CompletableFuture.completedFuture(Result.RETRY);
                }
                yield cosmetics.get().grant(row.account(), Cosmetics.Kind.TAG, row.ref(), origin).thenApply(isNew -> Result.DELIVERED);
            }
            case COMING_SOON -> CompletableFuture.completedFuture(Result.FAILED);
            default -> items(row, kind, amount, origin, actor).thenComposeAsync(items -> {
                if (items == null || items.isEmpty()) {
                    return CompletableFuture.completedFuture(Result.FAILED);
                }
                if (m.settings.bound) {
                    BoundItems.markAll(items, row.profile());
                }
                return deliver(row, items, actor);
            }, m.main());
        };
    }

    /** New items of a buy (books, scrolls and set tokens get their database rows from their module). Main thread. */
    private CompletableFuture<List<ItemStack>> items(BlackMarketStore.BuyRow row, OfferCatalog.Kind kind, int amount, String origin, Actor actor) {
        switch (kind) {
            case SET_TOKEN -> {
                WardrobeService w = m.core.services().optional(WardrobeService.class).orElseThrow();
                List<CompletableFuture<ItemStack>> tokens = new ArrayList<>();
                for (int i = 0; i < amount; i++) {
                    tokens.add(w.createToken(row.ref(), origin, row.account(), actor));
                }
                return all(tokens);
            }
            case ENCHANT_BOOK -> {
                EnchantService e = m.core.services().optional(EnchantService.class).orElseThrow();
                int colon = row.ref().lastIndexOf(':');
                String id = colon > 0 ? row.ref().substring(0, colon) : row.ref();
                int level = colon > 0 ? Integer.parseInt(row.ref().substring(colon + 1)) : 1;
                level = Math.max(1, Math.min(level, Math.max(1, e.maxLevel(id))));
                List<CompletableFuture<ItemStack>> books = new ArrayList<>();
                for (int i = 0; i < amount; i++) {
                    books.add(e.createBook(id, level, origin, row.account()));
                }
                return all(books);
            }
            case SCROLL -> {
                EnchantService e = m.core.services().optional(EnchantService.class).orElseThrow();
                List<CompletableFuture<ItemStack>> scrolls = new ArrayList<>();
                for (int i = 0; i < amount; i++) {
                    scrolls.add(e.createScroll(origin, row.account()));
                }
                return all(scrolls);
            }
            case SPAWNER -> {
                StackingService s = m.core.services().optional(StackingService.class).orElseThrow();
                return CompletableFuture.completedFuture(s.createSpawner(row.ref(), amount, origin).map(List::of).orElse(List.of()));
            }
            case ITEM -> {
                Material mat = BlackMarketModule.material(row.ref());
                List<ItemStack> stacks = new ArrayList<>();
                if (mat != null) {
                    int max = Math.max(1, new ItemStack(mat).getMaxStackSize());
                    for (int left = amount; left > 0; left -= max) {
                        stacks.add(new ItemStack(mat, Math.min(max, left)));
                    }
                }
                return CompletableFuture.completedFuture(stacks);
            }
            default -> {
                return CompletableFuture.completedFuture(List.of());
            }
        }
    }

    private static CompletableFuture<List<ItemStack>> all(List<CompletableFuture<ItemStack>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(v -> {
            List<ItemStack> out = new ArrayList<>();
            for (CompletableFuture<ItemStack> f : futures) {
                ItemStack s = f.join();
                if (s != null && !s.isEmpty()) {
                    out.add(s);
                }
            }
            return out;
        });
    }

    /** Inventory first, the rest to /box if the buyer is online on that profile; else straight into that profile's box. */
    private CompletableFuture<Result> deliver(BlackMarketStore.BuyRow row, List<ItemStack> items, Actor actor) {
        Player receiver = m.receiver(row.profile());
        if (receiver != null) {
            List<ItemStack> rest = ItemBox.give(m.core.services(), receiver, BoxSource.BLACK_MARKET, items);
            if (m.settings.savePlayerData) {
                PlayerSaves.save(receiver, "a Black Market buy");
            }
            m.msg.send(receiver, rest.isEmpty() ? "delivery.inventory" : "delivery.box");
            return CompletableFuture.completedFuture(Result.DELIVERED);
        }
        return ItemBox.of(m.core.services()).map(b -> b.deliver(row.profile(), items, BoxSource.BLACK_MARKET, actor)
                .thenApply(d -> Result.DELIVERED)).orElse(CompletableFuture.completedFuture(Result.RETRY));
    }

    /** Raises the island to the bought size (only ever up; already that big = done). */
    private CompletableFuture<Result> charter(BlackMarketStore.BuyRow row, Actor actor) {
        IslandService islands = m.islands().orElse(null);
        if (islands == null) {
            return CompletableFuture.completedFuture(Result.RETRY);
        }
        int target;
        try {
            target = Integer.parseInt(row.ref());
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(Result.FAILED);
        }
        Optional<IslandService.IslandInfo> island = row.island() == null ? Optional.empty() : islands.island(row.island());
        if (island.isEmpty() || target > island.get().maxSizeChunks()) {
            return CompletableFuture.completedFuture(Result.FAILED); // island gone (disbanded) or the size is no longer allowed
        }
        if (island.get().sizeChunks() >= target) {
            return CompletableFuture.completedFuture(Result.DELIVERED);
        }
        String name = island.get().name();
        return islands.setSize(row.island(), target, actor).thenApplyAsync(ok -> {
            if (!Boolean.TRUE.equals(ok)) {
                return Result.RETRY;
            }
            for (UUID member : islands.members(row.island()).keySet()) {
                m.profiles().onlinePlayer(member).ifPresent(p -> m.msg.send(p, "charter.grown",
                        Msg.p("size", Purchases.sizeText(target)), Msg.p("island", name)));
            }
            return Result.DELIVERED;
        }, m.main());
    }
}
