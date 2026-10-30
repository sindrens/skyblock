package net.skybornmc.skyblock.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Auction house (M3.6): {@code /ah} browse menu (categories, search, sort, own listings, collection bin), {@code /ah sell}
 * for the held item, buy confirm dialog. The dupe-safe state machine lives in {@link AuctionEngine}; this class is the
 * Bukkit side: commands, menus, inventories, notifications and background tasks.
 * Settings: {@code auction.yml}; texts: {@code messages/auction.yml}; data: {@code db/auction}.
 */
public final class AuctionModule implements SkyblockModule, Listener {

    static final String ID = "auction";

    static final String PERM_USE = "skyblock.auction.use";
    static final String PERM_SELL = "skyblock.auction.sell";
    static final String PERM_STAFF_REMOVE = "skyblock.staff.auction.remove";

    final Core core;
    final ModuleMessages msg;
    final AuctionConfig config = new AuctionConfig();
    private AuctionEngine engine;
    private ItemCodec codec;
    private Executor main;
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    /** Decoded listing items for the menus (bytes of a listing never change). */
    private final Map<Long, ItemStack> decoded = new java.util.concurrent.ConcurrentHashMap<>();

    public AuctionModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public java.util.Set<String> dependencies() {
        return java.util.Set.of("economy");
    }

    @Override
    public void enable() throws Exception {
        if (core.services().optional(EconomyService.class).isEmpty()) {
            throw new IllegalStateException("EconomyService is not available (economy module disabled?)");
        }
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        main = new MainThread(core.plugin());
        codec = new ItemCodec(core.items());
        engine = new AuctionEngine(new AuctionStore(core.database()),
                () -> core.services().optional(EconomyService.class).orElseThrow(() -> new IllegalStateException("economy is gone")),
                core.audit(), () -> config, System::currentTimeMillis, () -> 0, core.plugin().getLogger());
        // P4: never buy from another of your own profiles (audited as profile_transfer_blocked).
        engine.ownProfileCheck((seller, buyer, actor) -> profiles().blockOwnTransfer(AuditCategory.AUCTION, "ah_buy", seller, buyer, actor));
        engine.events(new AuctionEngine.Events() {
            @Override
            public void sold(Listing listing) {
                main.execute(() -> notifySold(listing));
            }

            @Override
            public void expired(Listing listing) {
                main.execute(() -> notifyExpired(listing));
            }
        });
        registerCommands();
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());
        // P7 (spec 13.1 "Block trading"): staff can cancel all listings of a player (items to the collection bin)
        core.services().register(ID, net.skybornmc.skyblock.common.moderation.ListingSanctions.class, this::cancelAllOf);

        // Crash recovery first (everything left in pending/buying), then the browse cache.
        engine.recover(0).exceptionally(e -> {
            core.plugin().getLogger().log(Level.SEVERE, "Auction: startup recovery failed", Database.cause(e));
            return 0;
        }).thenCompose(n -> engine.refreshCache()).exceptionally(e -> {
            core.plugin().getLogger().log(Level.SEVERE, "Auction: could not load listings", Database.cause(e));
            return null;
        });
        long expire = 20L * config.expireCheckSeconds;
        tasks.add(core.scheduler().asyncRepeat(expire, expire, () -> engine.expireDue().exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Auction: expiry check failed", Database.cause(e));
            return List.of();
        })));
        long resync = 20L * config.cacheResyncSeconds;
        tasks.add(core.scheduler().asyncRepeat(resync, resync, () -> engine.refreshCache()
                .thenCompose(v -> engine.recover(config.recoverAfterSeconds * 1000L)).exceptionally(e -> {
                    core.plugin().getLogger().log(Level.WARNING, "Auction: cache refresh/recovery failed", Database.cause(e));
                    return 0;
                })));
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
    }

    /**
     * P7: cancels every active listing of every profile of {@code player} as a staff removal (items to the seller's
     * collection bin, never deleted). Listings being bought right now finish. Never completes exceptionally.
     */
    private java.util.concurrent.CompletableFuture<Integer> cancelAllOf(UUID player, String reason, Actor actor) {
        return engine.refreshCache().thenCompose(v -> {
            List<java.util.concurrent.CompletableFuture<Boolean>> runs = new ArrayList<>();
            for (Listing l : engine.activeListings()) {
                if (player.equals(profiles().ownerOf(l.seller()))) {
                    runs.add(engine.cancel(l.id(), l.seller(), true, actor, reason)
                            .thenApply(r -> r.outcome() == AuctionEngine.CancelOutcome.CANCELLED)
                            .exceptionally(e -> false));
                }
            }
            return java.util.concurrent.CompletableFuture.allOf(runs.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .thenApply(x -> (int) runs.stream().filter(java.util.concurrent.CompletableFuture::join).count());
        }).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Auction: cancelling listings for a sanction failed", Database.cause(e));
            return 0;
        });
    }

    @Override
    public void disable() {
        core.services().unregister(ID, net.skybornmc.skyblock.common.moderation.ListingSanctions.class);
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        HandlerList.unregisterAll(this);
    }

    AuctionEngine engine() {
        return engine;
    }

    // ================================================================== commands

    private void registerCommands() {
        Arg<Long> price = Args.amount("price");
        Arg<String> duration = Args.oneOf("duration", () -> Pricing.durations(config));
        core.commands().register(CommandSpec.command("ah")
                .description("Auction house: buy and sell items with other players")
                .category("Economy")
                .permission(PERM_USE)
                .playerOnly()
                .aliases("auction", "auctionhouse")
                .example("ah")
                .executes(ctx -> openBrowse(ctx.player()))
                .sub(CommandSpec.sub("sell")
                        .description("List the item in your hand for a price")
                        .permission(PERM_SELL)
                        .playerOnly()
                        .arg(price).optional(duration)
                        .example("ah sell 1.5k 24h")
                        .executes(ctx -> sellCommand(ctx.player(), ctx.get(price), ctx.getOr(duration, null)))));
    }

    void openBrowse(Player player) {
        if (net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(core.services(), player, net.skybornmc.skyblock.common.tutorial.Gates.AUCTION)) {
            return; // TUTORIAL §4: the auction house opens after the sell step
        }
        core.menus().open(player, new AuctionMenu(this));
    }

    private void sellCommand(Player player, long price, String durationKey) {
        if (!player.hasPermission(PERM_SELL)) {
            throw fail("no-permission-sell");
        }
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), player, net.skybornmc.skyblock.common.moderation.SanctionService.Use.AUCTION_LIST)
                || net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(core.services(), player, net.skybornmc.skyblock.common.tutorial.Gates.AUCTION)) {
            return; // P7: blocked trading or frozen balance; TUTORIAL §4: locked until the sell step
        }
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack hand = player.getInventory().getItem(slot);
        Optional<String> problem = sellProblem(player, hand, price);
        if (problem.isPresent()) {
            throw fail(problem.get(), sellPlaceholders(player, price));
        }
        if (durationKey == null) {
            core.menus().open(player, new SellMenu(this, slot, hand.clone(), price));
            return;
        }
        startListing(player, slot, hand.clone(), price, durationKey);
    }

    /** Problems known before anything is touched (message key), or empty. */
    Optional<String> sellProblem(Player player, ItemStack hand, long price) {
        if (frozen(player)) {
            return Optional.of("frozen");
        }
        // Owner answers 131/203: no playtime limit; the join tutorial locks the auction house (TutorialGate, checked at
        // every entry point) until its sell step.
        Optional<String> item = codec.problem(hand, config);
        if (item.isPresent()) {
            return Optional.of("items." + item.get());
        }
        Optional<ListingRules.Rejection> range = ListingRules.price(config, price);
        if (range.isPresent()) {
            return Optional.of("rules." + range.get().problem().key());
        }
        long fee = Pricing.fee(config, price);
        if (fee > 0 && economy().cached(player.getUniqueId(), Currency.MONEY).orElse(Long.MAX_VALUE) < fee) {
            return Optional.of("sell.cannot-afford-fee");
        }
        return Optional.empty();
    }

    TagResolver[] sellPlaceholders(Player player, long price) {
        long fee = Pricing.fee(config, price);
        return new TagResolver[]{
                moneyTag("price", player, price), moneyTag("fee", player, fee), moneyTag("tax", player, Pricing.tax(config, price)),
                moneyTag("net", player, Pricing.net(config, price)), moneyTag("min", player, config.priceMin),
                moneyTag("max", player, config.priceMax),
                moneyTag("balance", player, economy().cached(player.getUniqueId(), Currency.MONEY).orElse(0))};
    }

    // ================================================================== listing

    /**
     * Lists the item in {@code slot}, which must still be exactly {@code expected}. The item leaves the inventory
     * <b>before</b> the database call; it comes back only if nothing was stored.
     */
    void startListing(Player player, int slot, ItemStack expected, long price, String durationKey) {
        Duration duration = Pricing.offered(config, durationKey);
        if (duration == null) {
            send(player, "sell.bad-duration", Msg.p("choices", String.join(", ", Pricing.durations(config))));
            return;
        }
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), player, net.skybornmc.skyblock.common.moderation.SanctionService.Use.AUCTION_LIST)
                || net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(core.services(), player, net.skybornmc.skyblock.common.tutorial.Gates.AUCTION)) {
            return; // P7: blocked trading or frozen balance; TUTORIAL §4: locked until the sell step
        }
        PlayerInventory inv = player.getInventory();
        ItemStack now = inv.getItem(slot);
        if (now == null || !now.isSimilar(expected) || now.getAmount() != expected.getAmount()) {
            send(player, "sell.item-changed");
            return;
        }
        Optional<String> problem = sellProblem(player, now, price);
        if (problem.isPresent()) {
            send(player, problem.get(), sellPlaceholders(player, price));
            return;
        }
        ItemSnapshot snapshot;
        try {
            snapshot = codec.snapshot(now);
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Auction: could not serialize " + now.getType() + " of " + player.getName(), e);
            send(player, "error");
            return;
        }
        inv.setItem(slot, null); // the item now only exists in this snapshot until the database has it
        UUID uuid = key(player);
        String name = player.getName();
        // SECFIX1 F-01: the player file is saved without the item BEFORE the listing row is written, so a crash can lose
        // the item at worst, never keep it in both places. A failed save skips the database and gives the item back.
        net.skybornmc.skyblock.common.player.PlayerSaves.commitAfterSave(player, "an auction listing",
                        () -> engine.create(uuid, name, snapshot, price, duration.toMillis(), slots(player), Actor.player(player)))
                .whenCompleteAsync((result, error) -> {
                    Player online = online(uuid);
                    if (error != null || result == null) {
                        core.plugin().getLogger().log(Level.SEVERE, "Auction: listing by " + name + " failed unexpectedly", error);
                        giveBack(uuid, snapshot);
                        sendIfOnline(online, "error");
                        return;
                    }
                    listingResult(online, uuid, snapshot, result, price);
                }, main);
    }

    private void listingResult(Player player, UUID uuid, ItemSnapshot snapshot, AuctionEngine.CreateResult result, long price) {
        if (result.giveBack()) {
            giveBack(uuid, snapshot);
        }
        if (player == null) {
            return;
        }
        switch (result.outcome()) {
            case LISTED -> {
                Listing l = result.listing();
                send(player, "sell.listed", Msg.p("item", snapshot.name()), Msg.number("amount", snapshot.amount()),
                        moneyTag("price", player, price), moneyTag("fee", player, l.fee()),
                        Msg.p("time", Format.duration(Duration.ofMillis(l.expiresAt() - l.createdAt()))));
                sound(player, "success");
                refreshMenus();
            }
            case REJECTED -> {
                ListingRules.Rejection r = result.rejection();
                send(player, "rules." + r.problem().key(), rejectionPlaceholders(player, r));
                sound(player, "error");
            }
            case FEE_UNPAID -> {
                send(player, "sell.fee-unpaid", moneyTag("fee", player, Pricing.fee(config, price)));
                sound(player, "error");
                if (result.collectionId() != null) {
                    deliver(player, result.collectionId(), snapshot.bytes(), false);
                }
            }
            case PENDING -> send(player, "sell.pending");
            case BUSY -> send(player, "sell.busy");
            case NOT_STORED -> {
                send(player, "error");
                sound(player, "error");
            }
        }
    }

    TagResolver[] rejectionPlaceholders(Player player, ListingRules.Rejection r) {
        long v = r.value();
        return new TagResolver[]{moneyTag("min", player, v), moneyTag("max", player, v), Msg.number("slots", v),
                Msg.p("time", Format.duration(Duration.ofMillis(Math.max(1000, v)))), Msg.number("limit", config.relistMaxSameItem),
                Msg.p("window", Format.duration(Duration.ofMinutes(v)))};
    }

    /**
     * Returns an item that was taken from a player but never stored: into the inventory if they are online, the rest
     * (or everything, if offline) into the collection bin. The full item is logged if even that fails.
     */
    private void giveBack(UUID uuid, ItemSnapshot snapshot) {
        Player player = online(uuid);
        if (player != null) {
            ItemStack item = ItemCodec.decode(snapshot.bytes());
            Map<Integer, ItemStack> left = player.getInventory().addItem(item);
            net.skybornmc.skyblock.common.player.PlayerSaves.save(player, "an auction give-back"); // SECFIX1 F-01
            if (left.isEmpty()) {
                return;
            }
            overflow(uuid, left.values());
            return;
        }
        engine.putBack(uuid, snapshot.bytes(), snapshot.material(), snapshot.amount(), snapshot.name()).exceptionally(e -> {
            lost(uuid, snapshot.bytes(), e);
            return null;
        });
    }

    /** Owner answer 7: what does not fit goes to /box; without the box module back into the collection bin as before. */
    private void overflow(UUID owner, java.util.Collection<ItemStack> rest) {
        if (rest.isEmpty()) {
            return;
        }
        Optional<net.skybornmc.skyblock.common.box.ItemBox> box = net.skybornmc.skyblock.common.box.ItemBox.of(core.services());
        if (box.isPresent()) {
            box.get().deliver(owner, rest, net.skybornmc.skyblock.common.box.BoxSource.AUCTION, Actor.system("auction"));
        } else {
            rest.forEach(r -> putBack(owner, r));
        }
    }

    private void putBack(UUID owner, ItemStack stack) {
        byte[] bytes = stack.serializeAsBytes();
        engine.putBack(owner, bytes, stack.getType().name(), stack.getAmount(), ItemCodec.plainName(stack)).exceptionally(e -> {
            main.execute(() -> {
                Player p = online(owner);
                Optional<net.skybornmc.skyblock.common.box.ItemBox> box = net.skybornmc.skyblock.common.box.ItemBox.of(core.services());
                if (box.isPresent()) {
                    // Owner answer 7: the collection bin refused it, the /box keeps it (never dropped).
                    box.get().deliver(owner, List.of(stack), net.skybornmc.skyblock.common.box.BoxSource.AUCTION, Actor.system("auction"));
                } else if (p != null) {
                    p.getWorld().dropItemNaturally(p.getLocation(), stack); // last resort: never lose it
                } else {
                    lost(owner, bytes, e);
                }
            });
            return null;
        });
    }

    private void lost(UUID owner, byte[] bytes, Throwable e) {
        core.plugin().getLogger().log(Level.SEVERE, "Auction: could not return an item to " + owner + ". Restore it from this data: "
                + Base64.getEncoder().encodeToString(bytes), Database.cause(e));
    }

    // ================================================================== buying

    /** After the buy confirm dialog. */
    void startBuy(Player player, Listing listing) {
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), player, net.skybornmc.skyblock.common.moderation.SanctionService.Use.AUCTION_BUY)
                || net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(core.services(), player, net.skybornmc.skyblock.common.tutorial.Gates.AUCTION)) {
            return; // P7: blocked trading or frozen balance; TUTORIAL §4: locked until the sell step
        }
        if (frozen(player)) {
            send(player, "frozen");
            sound(player, "error");
            return;
        }
        if (listing.seller().equals(key(player))) {
            send(player, "buy.own");
            sound(player, "error");
            return;
        }
        if (profiles().ownOtherProfile(listing.seller(), key(player))) {
            profiles().blockOwnTransfer(AuditCategory.AUCTION, "ah_buy", listing.seller(), key(player), Actor.player(player));
            send(player, "buy.own-profile");
            sound(player, "error");
            return;
        }
        UUID uuid = key(player);
        engine.buy(listing.id(), uuid, player.getName(), Actor.player(player)).whenCompleteAsync((r, error) -> {
            Player online = online(uuid);
            if (online == null) {
                return; // bought items wait in the collection bin
            }
            if (error != null || r == null) {
                send(online, "error");
                sound(online, "error");
                return;
            }
            String item = listing.item().name();
            switch (r.outcome()) {
                case SOLD -> {
                    send(online, "buy.bought", Msg.p("item", item), Msg.number("amount", listing.item().amount()),
                            moneyTag("price", online, listing.price()), Msg.p("seller", listing.sellerName()),
                            moneyTag("balance", online, r.buyerBalance() == null ? 0 : r.buyerBalance()));
                    sound(online, "money");
                    deliver(online, r.collectionId(), r.listing().item().bytes(), true);
                }
                case INSUFFICIENT -> {
                    send(online, "buy.insufficient", moneyTag("price", online, listing.price()),
                            moneyTag("balance", online, economy().cached(uuid, Currency.MONEY).orElse(0)));
                    sound(online, "error");
                }
                case GONE -> {
                    send(online, "buy.gone", Msg.p("item", item));
                    sound(online, "error");
                }
                case OWN -> {
                    send(online, "buy.own");
                    sound(online, "error");
                }
                case OWN_PROFILE -> {
                    send(online, "buy.own-profile");
                    sound(online, "error");
                }
                case BUSY -> {
                    send(online, "buy.busy", Msg.p("item", item));
                    sound(online, "error");
                }
                case PROCESSING -> send(online, "buy.processing", Msg.p("item", item));
            }
            refreshMenus();
        }, main);
    }

    // ================================================================== cancel

    /** The seller cancels their listing (after the confirm dialog), or staff remove it ({@code staff}). */
    void startCancel(Player player, Listing listing, boolean staff, String reason) {
        UUID uuid = key(player);
        engine.cancel(listing.id(), uuid, staff, Actor.player(player), reason).whenCompleteAsync((r, error) -> {
            Player online = online(uuid);
            if (online == null) {
                return;
            }
            if (error != null || r == null || r.outcome() == AuctionEngine.CancelOutcome.ERROR) {
                send(online, "error");
                sound(online, "error");
                return;
            }
            switch (r.outcome()) {
                case CANCELLED -> {
                    if (staff) {
                        send(online, "staff.removed", Msg.p("item", listing.item().name()), Msg.p("seller", listing.sellerName()));
                        Player seller = online(listing.seller());
                        if (seller != null && !seller.equals(online)) {
                            send(seller, "staff.removed-seller", Msg.p("item", listing.item().name()));
                        }
                    } else {
                        send(online, "cancel.done", Msg.p("item", listing.item().name()));
                        sound(online, "success");
                        if (r.collectionId() != null) {
                            deliver(online, r.collectionId(), listing.item().bytes(), true);
                        }
                    }
                }
                case BUSY -> send(online, "cancel.busy");
                default -> send(online, "cancel.gone", Msg.p("item", listing.item().name()));
            }
            refreshMenus();
        }, main);
    }

    // ================================================================== delivery from the collection bin

    /**
     * Moves one collection bin entry into the player's inventory if it fits (taken from the database exactly once). If it
     * doesn't fit it stays in the bin and the player is told.
     *
     * @param bytes    the item as listed (only used to check the space; the database copy is what is given)
     * @param announce tell the player where the item went
     */
    void deliver(Player player, long entryId, byte[] bytes, boolean announce) {
        ItemStack preview = ItemCodec.decode(bytes);
        if (!ItemCodec.fits(player.getInventory(), preview)) {
            if (announce) {
                send(player, "collection.full", Msg.p("item", ItemCodec.plainName(preview)));
            }
            return;
        }
        claim(player, entryId, ok -> {
            if (ok && announce) {
                send(player, "collection.delivered", Msg.p("item", ItemCodec.plainName(preview)));
            }
        });
    }

    /** Takes an entry out of the bin into the inventory; {@code done} gets whether the player received it (main thread). */
    void claim(Player player, long entryId, Consumer<Boolean> done) {
        UUID uuid = key(player);
        engine.take(entryId, uuid, Actor.player(player)).whenCompleteAsync((entry, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Auction: taking collection entry " + entryId + " failed", Database.cause(error));
                Player p = online(uuid);
                if (p != null) {
                    send(p, "error");
                }
                done.accept(false);
                return;
            }
            if (entry == null || entry.isEmpty()) {
                done.accept(false);
                return;
            }
            ItemStack item = ItemCodec.decode(entry.get().bytes());
            Player p = online(uuid);
            if (p == null) {
                putBack(uuid, item);
                done.accept(false);
                return;
            }
            Map<Integer, ItemStack> left = p.getInventory().addItem(item);
            // SECFIX1 F-01c: the row is already gone; saving now keeps a crash from losing the item.
            net.skybornmc.skyblock.common.player.PlayerSaves.save(p, "an auction collection take");
            overflow(uuid, left.values());
            sound(p, "success");
            done.accept(true);
        }, main);
    }

    // ================================================================== notifications

    private void notifySold(Listing l) {
        Player seller = online(l.seller());
        if (seller == null) {
            return; // told on the next join (seller_notice)
        }
        send(seller, "notify.sold", Msg.p("item", l.item().name()), Msg.number("amount", l.item().amount()),
                Msg.p("buyer", l.buyerName() == null ? "?" : l.buyerName()), moneyTag("price", seller, l.price()),
                moneyTag("net", seller, l.price() - l.tax()), moneyTag("tax", seller, l.tax()));
        sound(seller, "money");
        engine.store().clearNotice(l.id());
        refreshMenus();
    }

    private void notifyExpired(Listing l) {
        Player seller = online(l.seller());
        if (seller != null) {
            send(seller, "notify.expired", Msg.p("item", l.item().name()), Msg.number("amount", l.item().amount()));
            sound(seller, "notify");
            engine.store().clearNotice(l.id());
        }
        refreshMenus();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = key(event.getPlayer());
        core.scheduler().later("auction:join", config.joinNoticeDelayTicks, () -> {
            if (online(uuid) == null) {
                return;
            }
            engine.store().takeNotices(uuid).thenCombine(engine.store().collectionCount(uuid), (notices, bin) -> {
                main.execute(() -> joinNotices(uuid, notices, bin));
                return null;
            }).exceptionally(e -> {
                core.plugin().getLogger().log(Level.WARNING, "Auction: join notices failed", Database.cause(e));
                return null;
            });
        });
    }

    private void joinNotices(UUID uuid, List<Listing> notices, int bin) {
        Player p = online(uuid);
        if (p == null) {
            return;
        }
        long earned = 0;
        int sold = 0;
        int expired = 0;
        for (Listing l : notices) {
            if (l.status() == ListingStatus.SOLD) {
                sold++;
                earned += l.price() - l.tax();
                if (sold <= 5) {
                    send(p, "notify.sold-offline", Msg.p("item", l.item().name()), Msg.number("amount", l.item().amount()),
                            Msg.p("buyer", l.buyerName() == null ? "?" : l.buyerName()), moneyTag("net", p, l.price() - l.tax()));
                }
            } else if (l.status() == ListingStatus.EXPIRED) {
                expired++;
            }
        }
        if (sold > 5) {
            send(p, "notify.sold-more", Msg.number("count", sold - 5));
        }
        if (sold > 0) {
            send(p, "notify.sold-total", Msg.number("count", sold), moneyTag("net", p, earned));
            sound(p, "money");
        }
        if (expired > 0) {
            send(p, "notify.expired-offline", Msg.number("count", expired));
        }
        if (bin > 0) {
            send(p, "notify.collection", Msg.number("count", bin));
        }
    }

    // ================================================================== island profiles (PROF)

    /** Island profiles: listings, the collection bin and notices belong to a profile (the seller/buyer/owner keys). */
    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    /** The profile key of an online player (stored as seller, buyer and collection owner). */
    UUID key(Player player) {
        return profiles().profileKey(player.getUniqueId());
    }

    /**
     * The online player whose ACTIVE profile is {@code key} and who is not switching right now, or null. Items and
     * notices for another profile wait in that profile's collection bin instead of landing in the wrong inventory.
     */
    Player online(UUID key) {
        return profiles().onlinePlayer(key).filter(p -> !profiles().isSwitching(p.getUniqueId())).orElse(null);
    }

    /**
     * Owner answer 118 (round 1, replaces P9's refusal): deleting a profile deletes everything on it, auction listings
     * included: its active listings are cancelled and their items deleted with the profile (never returned), and its
     * collection bin is emptied. Run again after {@code tasks.recover-after-seconds} for listings that were still
     * pending or being bought at that moment (a purchase that fails puts the listing back to active).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        UUID key = event.profile().id();
        Actor actor = Actor.system("profiles");
        wipeProfile(key, actor);
        long delay = 20L * Math.max(5, config.recoverAfterSeconds + 5);
        core.scheduler().later("auction:profile-delete", delay, () -> wipeProfile(key, actor));
    }

    private void wipeProfile(UUID key, Actor actor) {
        engine.deleteProfile(key, actor).whenCompleteAsync((wipe, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Auction: deleting the listings of deleted profile " + key + " failed",
                        Database.cause(error));
                return;
            }
            if (!wipe.empty()) {
                refreshMenus();
            }
        }, main);
    }

    /** Rebuilds open auction menus (after a sale, cancel or expiry). */
    void refreshMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof AuctionMenu menu) {
                menu.refresh();
            }
        }
    }

    // ================================================================== helpers

    EconomyService economy() {
        return core.services().optional(EconomyService.class).orElseThrow(() -> new IllegalStateException("economy is gone"));
    }

    ItemCodec codec() {
        return codec;
    }

    /** The listing's item for menus (decoded once, display copy without the unique id). */
    ItemStack displayItem(Listing l) {
        return decoded.computeIfAbsent(l.id(), id -> {
            if (decoded.size() > 5_000) {
                decoded.clear();
            }
            try {
                return ItemCodec.display(ItemCodec.decode(l.item().bytes()));
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Auction: listing " + id + " has an unreadable item", e);
                return new ItemStack(Material.BARRIER);
            }
        }).clone();
    }

    int slots(Player player) {
        List<String> granted = new ArrayList<>();
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (info.getValue() && info.getPermission().startsWith(ListingRules.SLOT_PERMISSION)) {
                granted.add(info.getPermission());
            }
        }
        return ListingRules.slots(config, granted);
    }

    /**
     * Owner answer 118 / P4: true when {@code listing} was made by another profile of the viewer's own player (refused and
     * audited as profile_transfer_blocked). The engine checks again inside the purchase.
     */
    boolean ownOtherProfile(Player player, Listing listing) {
        return profiles().ownOtherProfile(listing.seller(), key(player));
    }

    boolean frozen(Player player) {
        return core.placeholders().has("frozen")
                && core.placeholders().text(player, "frozen").map("true"::equalsIgnoreCase).orElse(false);
    }

    /** A money amount with every digit in the money color ({@code format.money}). */
    Component money(CommandSender viewer, long amount) {
        return msg.component(viewer, "format.money", Msg.p("value", Format.number(amount)));
    }

    /** {@code 1.0} → {@code 1}, {@code 2.5} → {@code 2.5}. */
    static String percent(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    TagResolver moneyTag(String name, CommandSender viewer, long amount) {
        return Msg.c(name, money(viewer, amount));
    }

    void send(CommandSender to, String key, TagResolver... placeholders) {
        msg.send(to, key, placeholders);
    }

    private void sendIfOnline(Player p, String key) {
        if (p != null) {
            send(p, key);
            sound(p, "error");
        }
    }

    void sound(Player player, String key) {
        core.sounds().play(player, key);
    }

    private CommandFailure fail(String key, TagResolver... placeholders) {
        return new CommandFailure(ID, key, placeholders);
    }

}
