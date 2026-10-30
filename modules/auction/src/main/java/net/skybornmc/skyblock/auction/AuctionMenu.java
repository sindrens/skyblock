package net.skybornmc.skyblock.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /ah}: active listings in the 28 content slots. Top row: sell held item, my listings, summary, collection bin,
 * refresh. Bottom row: category and sort (cycle on click; right-click goes back on Java), search (chat prompt) and
 * clear search. Every action works with a plain tap (Bedrock).
 */
final class AuctionMenu extends PagedMenu<Listing> {

    static final int SLOT_SELL = 2;
    static final int SLOT_MINE = 3;
    static final int SLOT_SUMMARY = 4;
    static final int SLOT_COLLECTION = 5;
    static final int SLOT_REFRESH = 6;
    static final int SLOT_CATEGORY = 46;
    static final int SLOT_SORT = 47;
    static final int SLOT_SEARCH = 51;
    static final int SLOT_CLEAR = 52;

    /** Sort orders, cycled in this order. */
    enum Sort {
        NEWEST, ENDING_SOON, PRICE_LOW, PRICE_HIGH;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        Comparator<Listing> comparator() {
            return switch (this) {
                case NEWEST -> Comparator.comparingLong(Listing::createdAt).reversed().thenComparingLong(Listing::id);
                case ENDING_SOON -> Comparator.comparingLong(Listing::expiresAt).thenComparingLong(Listing::id);
                case PRICE_LOW -> Comparator.comparingLong(Listing::price).thenComparingLong(Listing::id);
                case PRICE_HIGH -> Comparator.comparingLong(Listing::price).reversed().thenComparingLong(Listing::id);
            };
        }
    }

    private final AuctionModule module;
    private Category category; // null = all
    private Sort sort = Sort.NEWEST;
    private String search;
    /** Items in the viewer's collection bin, loaded once per open (null = not loaded yet). */
    private Integer binCount;

    AuctionMenu(AuctionModule module) {
        this.module = module;
    }

    @Override
    public String messageModule() {
        return AuctionModule.ID;
    }

    @Override
    public String infoKey() {
        return "browse.info";
    }

    @Override
    public String helpTopic() {
        return "auction";
    }

    @Override
    public int tickInterval() {
        return 200; // remaining times and sold/expired listings stay current while open
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected void onClose() {
        binCount = null; // reload when it is opened again (e.g. back from the collection bin)
    }

    @Override
    protected String emptyKey() {
        return search != null || category != null ? "browse.empty-filtered" : "browse.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        List<Component> crumbs = new ArrayList<>();
        crumbs.add(service.text(viewer, AuctionModule.ID, "browse.title"));
        if (category != null) {
            crumbs.add(service.text(viewer, AuctionModule.ID, "categories." + category.key()));
        }
        return crumbs;
    }

    /** Listings matching the category and search, in the chosen order. */
    @Override
    protected List<Listing> entries() {
        long now = System.currentTimeMillis();
        String needle = search == null ? null : search.toLowerCase(Locale.ROOT);
        List<Listing> out = new ArrayList<>();
        for (Listing l : module.engine().activeListings()) {
            if (l.expiresAt() <= now || (category != null && l.item().category() != category)) {
                continue;
            }
            if (needle != null && !matches(l, needle)) {
                continue;
            }
            out.add(l);
        }
        out.sort(sort.comparator());
        return out;
    }

    static boolean matches(Listing l, String needle) {
        return l.item().name().toLowerCase(Locale.ROOT).contains(needle)
                || ItemCodec.words(l.item().material()).toLowerCase(Locale.ROOT).contains(needle)
                || l.item().itemKey().toLowerCase(Locale.ROOT).replace('_', ' ').contains(needle)
                || l.sellerName().toLowerCase(Locale.ROOT).equals(needle);
    }

    // ------------------------------------------------------------------ header and filters

    @Override
    protected void decorate() {
        AuctionConfig cfg = module.config;
        set(SLOT_SELL, service.item(cfg.icon("sell", Material.EMERALD), viewer, AuctionModule.ID, "browse.sell",
                Msg.p("command", "/ah sell"), module.moneyTag("min", viewer, cfg.priceMin)), click -> sellHeld());
        set(SLOT_MINE, service.item(cfg.icon("mine", Material.GOLD_BLOCK), viewer, AuctionModule.ID, "browse.mine",
                Msg.number("used", ownCount()), Msg.number("slots", module.slots(viewer))), click -> openMine());
        set(SLOT_SUMMARY, summary());
        if (binCount == null) {
            setAsync(SLOT_COLLECTION, module.engine().store().collectionCount(module.key(viewer)).thenApply(count -> {
                binCount = count;
                return collectionButton(count);
            }));
        } else {
            set(SLOT_COLLECTION, collectionButton(binCount));
        }
        set(SLOT_REFRESH, service.item(cfg.icon("refresh", Material.SPYGLASS), viewer, AuctionModule.ID, "browse.refresh"),
                click -> module.engine().refreshCache().thenRun(() -> module.core.scheduler().main("auction:refresh", this::refresh)));

        set(SLOT_CATEGORY, cycler(cfg.icon(category == null ? "all" : category.key(), Material.CHEST), "browse.category",
                categoryName(category), categoryLines()), click -> {
            cycleCategory(click.isRight() ? -1 : 1);
            page(0);
            service.open(viewer, this);
        });
        set(SLOT_SORT, cycler(cfg.icon("sort", Material.HOPPER), "browse.sort",
                service.text(viewer, AuctionModule.ID, "sort." + sort.key()), sortLines()), click -> {
            Sort[] all = Sort.values();
            sort = all[Math.floorMod(sort.ordinal() + (click.isRight() ? -1 : 1), all.length)];
            page(0);
            refresh();
        });
        set(SLOT_SEARCH, service.item(cfg.icon("search", Material.OAK_SIGN), viewer, AuctionModule.ID,
                search == null ? "browse.search" : "browse.search-active", Msg.p("search", search == null ? "" : search)),
                click -> askSearch());
        if (search != null) {
            set(SLOT_CLEAR, service.item(cfg.icon("clear-search", Material.BARRIER), viewer, AuctionModule.ID, "browse.clear-search",
                    Msg.p("search", search)), click -> {
                search = null;
                page(0);
                service.open(viewer, this);
            });
        }
    }

    private MenuButton collectionButton(int count) {
        return MenuButton.of(glow(service.item(module.config.icon("collection", Material.ENDER_CHEST), viewer, AuctionModule.ID,
                count > 0 ? "browse.collection-full" : "browse.collection", Msg.number("count", count)), count > 0),
                click -> CollectionMenu.open(module, viewer, this));
    }

    private ItemStack summary() {
        long balance = module.economy().cached(viewer.getUniqueId(), Currency.MONEY).orElse(0);
        return service.item(Material.BOOK, viewer, AuctionModule.ID, "browse.summary",
                Msg.number("listings", module.engine().activeListings().size()), module.moneyTag("balance", viewer, balance),
                Msg.p("fee", AuctionModule.percent(module.config.feePercent)), Msg.p("tax", AuctionModule.percent(module.config.taxPercent)));
    }

    private static ItemStack glow(ItemStack item, boolean glow) {
        if (glow) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private int ownCount() {
        UUID me = module.key(viewer);
        return (int) module.engine().activeListings().stream().filter(l -> l.seller().equals(me)).count();
    }

    private void cycleCategory(int delta) {
        Category[] all = Category.values();
        int index = category == null ? -1 : category.ordinal(); // -1 = all
        index = Math.floorMod(index + 1 + delta, all.length + 1) - 1;
        category = index < 0 ? null : all[index];
    }

    private Component categoryName(Category c) {
        return service.text(viewer, AuctionModule.ID, c == null ? "categories.all" : "categories." + c.key());
    }

    /** A filter button: {@code <key>.name} with the current value, {@code <key>.lore-top}, one line per choice, {@code <key>.lore}. */
    private ItemStack cycler(Material icon, String key, Component value, List<Component> choices) {
        List<Component> lore = new ArrayList<>(service.lines(viewer, AuctionModule.ID, key + ".lore-top"));
        lore.addAll(choices);
        lore.addAll(service.lines(viewer, AuctionModule.ID, key + ".lore"));
        return service.item(icon, service.text(viewer, AuctionModule.ID, key + ".name", Msg.c("value", value)), lore);
    }

    private List<Component> categoryLines() {
        List<Component> out = new ArrayList<>();
        List<Category> all = new ArrayList<>();
        all.add(null);
        all.addAll(List.of(Category.values()));
        for (Category c : all) {
            String key = c == category ? "browse.list-selected" : "browse.list-other";
            out.add(service.text(viewer, AuctionModule.ID, key, Msg.c("name", categoryName(c))));
        }
        return out;
    }

    private List<Component> sortLines() {
        List<Component> out = new ArrayList<>();
        for (Sort s : Sort.values()) {
            String key = s == sort ? "browse.list-selected" : "browse.list-other";
            out.add(service.text(viewer, AuctionModule.ID, key, Msg.c("name", service.text(viewer, AuctionModule.ID, "sort." + s.key()))));
        }
        return out;
    }

    private void askSearch() {
        service.prompt(viewer, AuctionModule.ID, "search.ask", module.config.searchMaxLength, text -> {
            search = text.strip();
            page(0);
            service.open(viewer, this);
        }, () -> service.open(viewer, this));
    }

    private void sellHeld() {
        if (!viewer.hasPermission(AuctionModule.PERM_SELL)) {
            module.send(viewer, "no-permission-sell");
            service.sound(viewer, "error");
            return;
        }
        ItemStack hand = viewer.getInventory().getItemInMainHand();
        if (hand.isEmpty()) {
            module.send(viewer, "items.no-item");
            service.sound(viewer, "error");
            return;
        }
        Optional<String> itemProblem = module.codec().problem(hand, module.config);
        if (itemProblem.isPresent()) {
            module.send(viewer, "items." + itemProblem.get());
            service.sound(viewer, "error");
            return;
        }
        int slot = viewer.getInventory().getHeldItemSlot();
        ItemStack expected = hand.clone();
        service.prompt(viewer, AuctionModule.ID, "sell.ask-price", 24, text -> {
            long price;
            try {
                price = Format.parseAmount(text);
            } catch (IllegalArgumentException e) {
                module.send(viewer, "sell.bad-price", Msg.p("input", text));
                service.sound(viewer, "error");
                return;
            }
            if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(module.core.services(), viewer, net.skybornmc.skyblock.common.moderation.SanctionService.Use.AUCTION_LIST)
                    || net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(module.core.services(), viewer, net.skybornmc.skyblock.common.tutorial.Gates.AUCTION)) {
                return; // P7: blocked trading or frozen balance; TUTORIAL §4: locked until the sell step
            }
            Optional<String> problem = module.sellProblem(viewer, viewer.getInventory().getItem(slot), price);
            if (problem.isPresent()) {
                module.send(viewer, problem.get(), module.sellPlaceholders(viewer, price));
                service.sound(viewer, "error");
                return;
            }
            SellMenu menu = new SellMenu(module, slot, expected, price);
            menu.parent(this);
            service.open(viewer, menu);
        }, () -> service.open(viewer, this), Msg.p("item", ItemCodec.plainName(hand)), module.moneyTag("min", viewer, module.config.priceMin));
    }

    private void openMine() {
        MyListingsMenu.open(module, viewer, this);
    }

    // ------------------------------------------------------------------ entries

    @Override
    protected MenuButton entry(Listing l) {
        UUID me = module.key(viewer);
        boolean own = l.seller().equals(me);
        boolean staff = !own && viewer.hasPermission(AuctionModule.PERM_STAFF_REMOVE);
        String action = own ? "browse.entry.own" : staff ? "browse.entry.staff" : "browse.entry.buy";
        ItemStack item = listingItem(module, viewer, l, action);
        return MenuButton.of(item, click -> {
            if (module.engine().cached(l.id()).isEmpty()) {
                module.send(viewer, "buy.gone", Msg.p("item", l.item().name()));
                service.sound(viewer, "error");
                refresh();
                return;
            }
            if (own) {
                openChild(MyListingsMenu.cancelConfirm(module, viewer, l));
            } else if (staff) {
                openChild(new StaffListingMenu(module, l));
            } else {
                openBuyConfirm(l);
            }
        });
    }

    void openBuyConfirm(Listing l) {
        if (module.ownOtherProfile(viewer, l)) {
            module.send(viewer, "buy.own-profile"); // owner answer 118: never buy from your own other profile
            service.sound(viewer, "error");
            return;
        }
        openChild(buyConfirm(module, viewer, l));
    }

    static ConfirmMenu buyConfirm(AuctionModule module, org.bukkit.entity.Player viewer, Listing l) {
        TagResolver[] p = {Msg.p("item", l.item().name()), Msg.number("amount", l.item().amount()), Msg.p("seller", l.sellerName()),
                module.moneyTag("price", viewer, l.price()),
                module.moneyTag("balance", viewer, module.economy().cached(viewer.getUniqueId(), Currency.MONEY).orElse(0))};
        return new ConfirmMenu(v -> module.core.menus().text(v, AuctionModule.ID, "buy.crumb"),
                module.displayItem(l),
                v -> module.core.menus().lines(v, AuctionModule.ID, "buy.confirm-lines", p),
                player -> module.startBuy(player, l), null);
    }

    /** The listed item with the auction lines added under its own lore. */
    static ItemStack listingItem(AuctionModule module, org.bukkit.entity.Player viewer, Listing l, String actionKey) {
        ItemStack item = module.displayItem(l);
        long left = l.remaining(System.currentTimeMillis());
        TagResolver[] p = {Msg.p("seller", l.sellerName()), module.moneyTag("price", viewer, l.price()),
                Msg.p("time", Format.duration(Duration.ofMillis(left))), Msg.number("amount", l.item().amount()),
                Msg.p("id", l.id())};
        List<Component> lore = new ArrayList<>();
        List<Component> existing = item.lore();
        if (existing != null) {
            lore.addAll(existing);
        }
        lore.addAll(module.core.menus().lines(viewer, AuctionModule.ID, "browse.entry.lore", p));
        lore.addAll(module.core.menus().lines(viewer, AuctionModule.ID, actionKey, p));
        item.lore(lore);
        return item;
    }
}
