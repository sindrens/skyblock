package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusPart;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellQuote;
import net.skybornmc.skyblock.common.sell.SellRequest;
import net.skybornmc.skyblock.common.sell.SellResult;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.economy.EconomyConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link SellService} of the economy module (M3.4): price table + dynamic prices + one atomic, idempotent ledger
 * transaction per sale with the sale record in the same database transaction.
 *
 * <p><b>Why escrow (items out first) and not "pay, then remove":</b> removing after the payment leaves a window in which
 * the player could still move, drop or trade the items while the money is already on its way (paid twice). Removing
 * first on the main thread makes the items exist only in the request; the payment can then fail safely (every stack is
 * handed back) and can never be paid twice (idempotency key {@code sell:<sale id>}). {@code /sell} also saves the
 * player's data right after removing the items ({@code sell.save-player-data}), so a crash after the payment cannot roll
 * the inventory back to a state that still holds them.</p>
 */
public final class SellServiceImpl implements SellService {

    private final Supplier<SellCatalog> catalog;
    private final PriceBook book;
    private final SellStore store;
    private final EconomyService economy;
    private final Supplier<EconomyConfig> config;
    private final ItemRegistry items;
    private final Supplier<Optional<BonusService>> bonus;
    private final LongSupplier clock;
    private final Supplier<ZoneId> zone;
    private final Logger log;
    /** Called (off the main thread) after every successful sale: player, profile key, items sold, money paid. Set by {@link SellFeature}. */
    private volatile SoldListener soldListener;

    /** Hook for features that react to sales (wardrobe set XP); never affects the sale itself. */
    interface SoldListener {
        void sold(UUID player, UUID profileKey, long items, long money, String source);
    }

    void onSold(SoldListener listener) {
        this.soldListener = listener;
    }

    /** Opens the sell chest (set by {@link SellFeature}); returns false when selling is not possible right now. */
    private volatile java.util.function.BiPredicate<org.bukkit.entity.Player, net.skybornmc.skyblock.common.menu.Menu> chestOpener;

    void chestOpener(java.util.function.BiPredicate<org.bukkit.entity.Player, net.skybornmc.skyblock.common.menu.Menu> opener) {
        this.chestOpener = opener;
    }

    @Override
    public boolean openSellChest(org.bukkit.entity.Player player, net.skybornmc.skyblock.common.menu.Menu parent) {
        var opener = chestOpener;
        return opener != null && opener.test(player, parent);
    }

    /** Sells N of one item from the inventory (set by {@link SellFeature}, GUI-P3). */
    interface ItemSeller {
        void sell(org.bukkit.entity.Player player, ItemStack template, int amount, String source,
                  java.util.function.Consumer<net.skybornmc.skyblock.common.sell.SellResult> done);
    }

    private volatile ItemSeller itemSeller;
    private volatile java.util.function.ToLongBiFunction<org.bukkit.entity.Player, ItemStack> carriedCounter;

    void itemSeller(ItemSeller seller) {
        this.itemSeller = seller;
    }

    void carriedCounter(java.util.function.ToLongBiFunction<org.bukkit.entity.Player, ItemStack> counter) {
        this.carriedCounter = counter;
    }

    @Override
    public long carried(org.bukkit.entity.Player player, ItemStack template) {
        var counter = carriedCounter;
        return counter == null || player == null || template == null ? 0 : counter.applyAsLong(player, template);
    }

    @Override
    public void sellItem(org.bukkit.entity.Player player, ItemStack template, int amount, String source,
                         java.util.function.Consumer<net.skybornmc.skyblock.common.sell.SellResult> done) {
        var seller = itemSeller;
        if (seller == null) {
            done.accept(null);
            return;
        }
        seller.sell(player, template, amount, source, done);
    }
    private volatile LocalDate hotDay;
    private volatile String hotKey;
    private volatile SellCatalog hotCatalog;

    public SellServiceImpl(Supplier<SellCatalog> catalog, PriceBook book, SellStore store, EconomyService economy,
                           Supplier<EconomyConfig> config, ItemRegistry items, Supplier<Optional<BonusService>> bonus,
                           LongSupplier clock, Supplier<ZoneId> zone, Logger log) {
        this.catalog = catalog;
        this.book = book;
        this.store = store;
        this.economy = economy;
        this.config = config;
        this.items = items;
        this.bonus = bonus;
        this.clock = clock;
        this.zone = zone;
        this.log = log;
    }

    private DynamicSettings settings() {
        return config.get().dynamicSettings();
    }

    // ------------------------------------------------------------------ dynamic prices v2

    /** Today's count per island / profile and item (dynamic prices v2). */
    private final DayCounts days = new DayCounts(this::dayPrice);
    /** The scopes of a seller (set by {@link SellFeature}: the profile, plus its island when it has one). */
    private volatile java.util.function.Function<UUID, List<String>> scopes = p -> List.of("p:" + p);

    void dayScopes(java.util.function.Function<UUID, List<String>> scopes) {
        this.scopes = scopes;
    }

    DayCounts days() {
        return days;
    }

    DayPrice dayPrice() {
        return config.get().dayPrice();
    }

    /** The sales day now (resets at sell.daily.reset-hour, server zone). */
    long today() {
        return dayPrice().day(clock.getAsLong(), zone.get());
    }

    /** The seller's scopes; never throws (an island lookup that fails leaves only the profile). */
    List<String> scopesOf(UUID profileKey) {
        if (profileKey == null) {
            return List.of();
        }
        try {
            List<String> out = scopes.apply(profileKey);
            return out == null || out.isEmpty() ? List.of("p:" + profileKey) : out;
        } catch (RuntimeException e) {
            return List.of("p:" + profileKey);
        }
    }

    /** How many of this item the seller's island (or the seller, whichever is higher) sold today. */
    public long soldToday(UUID profileKey, String itemKey) {
        List<String> sc = scopesOf(profileKey);
        return sc.isEmpty() || !dayPrice().enabled() ? 0 : days.count(sc, itemKey, today());
    }

    /** True when the item's category never gets the day price ({@code sell.daily.exempt-categories}, e.g. mob-loot). */
    boolean dayExempt(String itemKey) {
        List<String> exempt = config.get().sellDailyExempt;
        return !exempt.isEmpty() && catalog.get().get(itemKey).map(e -> exempt.contains(e.category())).orElse(false);
    }

    /** This week's drift factor of an item (1.0 when the weekly drift is off). */
    double weekFactor(String itemKey) {
        return config.get().weeklyDrift().factor(WeeklyDrift.week(clock.getAsLong(), zone.get()), itemKey);
    }

    // ------------------------------------------------------------------ lookups

    SellRules.ItemFacts facts(ItemStack stack) {
        if (net.skybornmc.skyblock.common.sell.MineDrop.isMineEmerald(stack)) { // owner 7 Nov 2026: /mine emeralds sell
            return new SellRules.ItemFacts("emerald", net.skybornmc.skyblock.common.sell.MineDrop.EMERALD_ID, "material", false, false, false);
        }
        return SellItems.facts(stack, items, config.get().sellBlockedDataKeys);
    }

    Optional<SellRules.Reason> reason(ItemStack stack) {
        stack = probe(stack); // a plain hay bale sells as 9 wheat (Auto-Compactor, owner 7 Nov 2026)
        EconomyConfig c = config.get();
        return SellRules.check(facts(stack), catalog.get(), c.sellBlockedIds, c.sellCustomTypes);
    }

    @Override
    public boolean sellable(ItemStack stack) {
        return reason(stack).isEmpty();
    }

    @Override
    public Optional<String> unsellableReason(ItemStack stack) {
        return reason(stack).map(SellRules.Reason::key);
    }

    /** A plain compact block stands for one of its item (the amount is x9 in quotes). */
    private static ItemStack probe(ItemStack stack) {
        return net.skybornmc.skyblock.common.sell.Compact.itemOf(stack).map(ItemStack::new).orElse(stack);
    }

    /** The price table key of a sellable stack, or null. */
    String keyOf(ItemStack stack) {
        SellRules.ItemFacts f = facts(probe(stack));
        EconomyConfig c = config.get();
        return SellRules.check(f, catalog.get(), c.sellBlockedIds, c.sellCustomTypes).isEmpty() ? f.key() : null;
    }

    @Override
    public Optional<SellPrice> price(ItemStack stack) {
        String key = keyOf(stack);
        return key == null ? Optional.empty() : price(key);
    }

    @Override
    public Optional<SellPrice> price(String itemKey) {
        return catalog.get().get(itemKey).filter(SellCatalog.Entry::sellable)
                .map(e -> SaleCalculator.price(e, book.get(e.key()), hotKey(), settings(), weekFactor(e.key())));
    }

    @Override
    public Optional<SellPrice> price(String itemKey, UUID profileKey) {
        Optional<SellPrice> p = price(itemKey);
        if (p.isEmpty() || profileKey == null || settings().enabled() || dayExempt(itemKey)) {
            return p;
        }
        return Optional.of(SaleCalculator.forSeller(p.get(), dayPrice(), soldToday(profileKey, itemKey)));
    }

    @Override
    public java.util.OptionalLong soldTodayCount(UUID profileKey, String itemKey) {
        return profileKey == null || itemKey == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(soldToday(profileKey, itemKey));
    }

    @Override
    public java.util.OptionalLong fullPriceLeft(UUID profileKey, String itemKey) {
        if (profileKey == null || itemKey == null || settings().enabled() || !dayPrice().enabled() || dayExempt(itemKey)) {
            return java.util.OptionalLong.empty();
        }
        return java.util.OptionalLong.of(dayPrice().fullLeft(soldToday(profileKey, itemKey)));
    }

    @Override
    public double weekPercent(String itemKey) {
        return settings().enabled() ? 0 : config.get().weeklyDrift().percent(WeeklyDrift.week(clock.getAsLong(), zone.get()), itemKey);
    }

    @Override
    public double priceMultiplier() {
        double m = config.get().sellPriceMultiplier;
        return m > 0 && Double.isFinite(m) ? m : 1.0;
    }

    @Override
    public List<String> itemKeys() {
        return catalog.get().sellableKeys();
    }

    @Override
    public java.util.OptionalDouble maxUnitPrice(String itemKey) {
        return catalog.get().get(itemKey).filter(SellCatalog.Entry::sellable)
                .map(e -> java.util.OptionalDouble.of(SaleCalculator.maxUnit(e.base(), settings(), config.get().weeklyDrift(),
                        bonusCapPercent())))
                .orElse(java.util.OptionalDouble.empty());
    }

    /** Today's Hot Item key (null if none); recomputed when the day or the price table changes. */
    public String hotKey() {
        LocalDate today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(clock.getAsLong()), zone.get());
        SellCatalog cat = catalog.get();
        if (!today.equals(hotDay) || cat != hotCatalog) {
            EconomyConfig c = config.get();
            hotKey = c.sellHotEnabled ? HotItem.pick(today, cat.all(), c.sellHotCategories, c.sellHotSalt, c.hotMinBase) : null;
            hotDay = today;
            hotCatalog = cat;
        }
        return config.get().sellHotEnabled ? hotKey : null;
    }

    @Override
    public double bonusPercent(Player player) {
        return bonus.get().map(b -> b.total(BonusFamily.SELL, new BonusContext(player, null, null, "sell"))).orElse(0.0);
    }

    /** Every source's share of the sell bonus (menus). Main thread. */
    public List<BonusPart> bonusBreakdown(Player player) {
        return bonus.get().map(b -> b.breakdown(BonusFamily.SELL, new BonusContext(player, null, null, "sell"))).orElse(List.of());
    }

    @Override
    public double bonusCapPercent() {
        return economy.sellBonusCapPercent();
    }

    @Override
    public SellQuote quote(List<ItemStack> stacks, double bonusPercent) {
        return quote(stacks, bonusPercent, null);
    }

    /** Dynamic prices v2: the quote of this seller ({@code profileKey}; null = as if nothing was sold today). */
    @Override
    public SellQuote quote(List<ItemStack> stacks, double bonusPercent, UUID profileKey) {
        List<String> keys = new ArrayList<>(stacks.size());
        List<Long> amounts = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            keys.add(stack == null ? null : keyOf(stack));
            amounts.add(stack == null ? 0L : (long) stack.getAmount() * (net.skybornmc.skyblock.common.sell.Compact.isBlock(stack)
                    ? net.skybornmc.skyblock.common.sell.Compact.SIZE : 1));
        }
        List<String> sc = scopesOf(profileKey);
        DayPrice day = dayPrice();
        long today = today();
        java.util.function.ToLongFunction<String> count = key -> dayExempt(key) ? -1L
                : sc.isEmpty() || !day.enabled() ? 0L : days.count(sc, key, today);
        return SaleCalculator.quote(keys, amounts, this::price, key -> book.get(key).impact(), count, day, settings(), bonusPercent,
                bonusCapPercent());
    }

    // ------------------------------------------------------------------ selling

    @Override
    public CompletableFuture<SellResult> sell(SellRequest request) {
        return sell(request, null);
    }

    @Override
    public CompletableFuture<SellResult> sell(SellRequest request, net.skybornmc.skyblock.common.economy.TxGuard guard) {
        List<ItemStack> stacks = request.items();
        SellQuote quote;
        try {
            quote = quote(stacks, request.bonusPercent(), request.profileKey());
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Sell: could not price a sale of " + request.playerName(), e);
            return CompletableFuture.completedFuture(new SellResult(SellResult.Status.ERROR, null, stacks, null, null));
        }
        List<ItemStack> unsold = new ArrayList<>();
        for (int index : quote.unsellable()) {
            unsold.add(stacks.get(index));
        }
        if (quote.isEmpty()) {
            return CompletableFuture.completedFuture(new SellResult(SellResult.Status.NOTHING_SELLABLE, null, stacks, null, null));
        }
        if (quote.total() <= 0) {
            return CompletableFuture.completedFuture(new SellResult(SellResult.Status.TOO_CHEAP, quote, stacks, null, null));
        }
        long now = clock.getAsLong();
        // Reserve the price drop now, so a second sale in the same moment can't get the old price; undone on failure.
        Map<String, Long> soldAmounts = SaleCalculator.amounts(quote);
        PriceBook.Reservation reservation = settings().enabled() ? book.reserve(soldAmounts, now) : null;
        // Dynamic prices v2: the island's day count goes up now too (the next sale starts where this one ended).
        DayCounts.Reservation dayReservation = dayPrice().enabled() && !settings().enabled()
                ? days.reserve(scopesOf(request.profileKey()), counted(soldAmounts), today()) : DayCounts.Reservation.NONE;
        UUID saleId = UUID.randomUUID();
        Transaction tx = Transaction.deposit(AccountRef.player(request.profileKey()), Currency.MONEY, quote.total(), TxReason.SELL)
                .actor(request.actor())
                .reference("sell:" + saleId)
                .idempotencyKey("sell:" + saleId)
                .detail("target_name", request.playerName())
                .detail("source", request.source())
                .detail("items", itemsDetail(quote))
                .detail("lines", SellStore.linesJson(quote))
                .detail("subtotal", quote.subtotal())
                .detail("bonus_percent", round2(quote.bonusPercent()))
                .detail("bonus_paid_percent", round2(quote.cappedBonusPercent()))
                .detail("multiplier", round2(1 + quote.cappedBonusPercent() / 100.0))
                .detail("sale", saleId.toString());
        SellStore.SaleRecord record = new SellStore.SaleRecord(saleId, now, request.profileKey(), request.player(),
                request.playerName(), request.source(), quote);
        CompletableFuture<TxResult> future;
        try {
            future = economy.executeGuarded(tx, c -> (guard == null || guard.holds(c)) && store.insertSale(c, record));
        } catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }
        SellQuote q = quote;
        return future.handle((result, error) -> {
            if (error != null || result == null) {
                log.log(Level.SEVERE, "Sell: sale " + saleId + " of " + request.playerName() + " failed; items are handed back", error);
                undo(reservation, dayReservation);
                return new SellResult(SellResult.Status.ERROR, q, stacks, null, null);
            }
            if (result.ok()) {
                SoldListener listener = soldListener;
                if (listener != null) {
                    try {
                        listener.sold(request.player(), request.profileKey(), q.items(), q.total(), request.source());
                    } catch (RuntimeException e) {
                        log.log(Level.WARNING, "Sell: sale listener failed for " + request.playerName(), e);
                    }
                }
                return new SellResult(SellResult.Status.SOLD, q, unsold, saleId, result.toBalance());
            }
            undo(reservation, dayReservation);
            SellResult.Status status = switch (result.status()) {
                case OVERFLOW -> SellResult.Status.OVERFLOW;
                case DUPLICATE -> SellResult.Status.DUPLICATE;
                default -> SellResult.Status.ERROR;
            };
            return new SellResult(status, q, stacks, null, null);
        });
    }

    /** The amounts that count for the day price (exempt categories left out). */
    private Map<String, Long> counted(Map<String, Long> amounts) {
        Map<String, Long> out = new LinkedHashMap<>();
        amounts.forEach((k, v) -> {
            if (!dayExempt(k)) {
                out.put(k, v);
            }
        });
        return out;
    }

    private void undo(PriceBook.Reservation reservation, DayCounts.Reservation dayReservation) {
        if (reservation != null) {
            book.undo(reservation);
        }
        days.undo(dayReservation);
    }

    /** {item: amount} for the audit row (one row per sale, every item in it). */
    static Map<String, Long> itemsDetail(SellQuote quote) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (SellQuote.Line line : quote.lines()) {
            out.merge(line.price().itemKey(), line.amount(), Long::sum);
        }
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    PriceBook book() {
        return book;
    }
}
