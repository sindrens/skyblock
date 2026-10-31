package net.skybornmc.skyblock.blackmarket;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * {@code /blackmarket} (PHASE-10 mockup, GUI-1 framework), 6 rows:
 * <pre>
 * Row 0  frame · 4 "Restocks in 4h 12m" · frame
 * Row 1  13 the headline ("Stock 18/25")
 * Row 2  the rare offers · Row 3 the common offers
 * Row 4  fixed row: Island Charter (shards, credits) and the Disenchant Scroll
 * Row 5  46 your shards and credits · standard navigation
 * </pre>
 * Every action is one tap and a confirm screen with the price on its middle item (Bedrock). Updates every second.
 */
final class BlackMarketMenu extends Menu {

    static final int SLOT_CLOCK = 4;
    static final int SLOT_WALLET = 46;
    static final int ROW_HEADLINE = 10;
    static final int ROW_RARE = 19;
    static final int ROW_COMMON = 28;
    static final int ROW_FIXED = 37;

    private final BlackMarketModule m;

    BlackMarketMenu(BlackMarketModule module) {
        this.m = module;
    }

    /** Slots of {@code count} items in the inner row starting at {@code start} (10, 19, 28, 37): spaced out up to 4. Pure. */
    static int[] rowSlots(int count, int start) {
        int n = Math.max(0, Math.min(7, count));
        return switch (n) {
            case 0 -> new int[0];
            case 1 -> new int[]{start + 3};
            case 2 -> new int[]{start + 2, start + 4};
            case 3 -> new int[]{start + 1, start + 3, start + 5};
            case 4 -> new int[]{start, start + 2, start + 4, start + 6};
            default -> {
                int[] out = new int[n];
                int first = start + (7 - n) / 2;
                for (int i = 0; i < n; i++) {
                    out[i] = first + i;
                }
                yield out;
            }
        };
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, BlackMarketModule.ID, "menu.title"));
    }

    @Override
    public String messageModule() {
        return BlackMarketModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "blackmarket";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    protected void tick() {
        m.market().tick();
        refresh();
    }

    @Override
    protected void build() {
        frame();
        long left = m.rotation().untilRestock(System.currentTimeMillis());
        set(SLOT_CLOCK, service.item(Material.CLOCK, viewer, BlackMarketModule.ID, "menu.clock",
                Msg.p("time", Format.duration(Duration.ofMillis(left)))));
        set(SLOT_WALLET, wallet());
        if (!m.settings.enabled) {
            set(22, service.item(Material.GRAY_DYE, viewer, BlackMarketModule.ID, "menu.disabled"));
            return;
        }
        if (!m.market().ready()) {
            setLoading(22);
        } else {
            List<OfferCatalog.Offer> headline = new ArrayList<>();
            List<OfferCatalog.Offer> rare = new ArrayList<>();
            List<OfferCatalog.Offer> common = new ArrayList<>();
            for (BlackMarketStore.RotationRow row : m.market().rows()) {
                m.catalog().get(row.offerId()).ifPresent(o -> (switch (o.slot()) {
                    case HEADLINE -> headline;
                    case RARE -> rare;
                    default -> common;
                }).add(o));
            }
            if (headline.isEmpty() && rare.isEmpty() && common.isEmpty()) {
                set(22, service.emptyItem(viewer, BlackMarketModule.ID, "menu.empty"));
            }
            place(headline, ROW_HEADLINE);
            place(rare, ROW_RARE);
            place(common, ROW_COMMON);
        }
        fixedRow();
    }

    private void place(List<OfferCatalog.Offer> offers, int row) {
        int[] slots = rowSlots(offers.size(), row);
        for (int i = 0; i < slots.length; i++) {
            OfferCatalog.Offer o = offers.get(i);
            set(slots[i], offerItem(o), click -> clicked(click.player(), o));
        }
    }

    private void fixedRow() {
        List<ItemStack> stacks = new ArrayList<>();
        List<java.util.function.Consumer<Player>> actions = new ArrayList<>();
        if (m.settings.charterEnabled && !m.charterSteps().isEmpty() && m.islands().isPresent()) {
            Purchases.CharterView view = m.purchases().charter(viewer);
            stacks.add(charterItem(view, Currency.SHARDS));
            actions.add(p -> charterClicked(p, Currency.SHARDS));
            boolean credits = m.charterSteps().stream().anyMatch(s -> s.credits() > 0);
            if (credits) {
                stacks.add(charterItem(view, Currency.CREDITS));
                actions.add(p -> charterClicked(p, Currency.CREDITS));
            }
        }
        for (OfferCatalog.Offer o : m.catalog().of(OfferCatalog.Slot.FIXED)) {
            if (o.kind() == OfferCatalog.Kind.COMING_SOON) {
                continue;
            }
            stacks.add(offerItem(o));
            actions.add(p -> clicked(p, o));
        }
        int[] slots = rowSlots(stacks.size(), ROW_FIXED);
        for (int i = 0; i < slots.length; i++) {
            java.util.function.Consumer<Player> action = actions.get(i);
            set(slots[i], stacks.get(i), click -> action.accept(click.player()));
        }
    }

    // ================================================================== items

    private ItemStack wallet() {
        OptionalLong shards = m.economy().map(e -> e.cached(viewer.getUniqueId(), Currency.SHARDS)).orElse(OptionalLong.empty());
        OptionalLong credits = m.economy().map(e -> net.skybornmc.skyblock.common.economy.CreditView.cachedShown(e, viewer.getUniqueId(),
                Currency.CREDITS)).orElse(OptionalLong.empty());
        return service.item(Material.AMETHYST_SHARD, viewer, BlackMarketModule.ID, "menu.wallet",
                Msg.p("shards", shards.isPresent() ? m.price(Currency.SHARDS, shards.getAsLong()) : "..."),
                Msg.p("credits", credits.isPresent() ? m.price(Currency.CREDITS, credits.getAsLong()) : "..."));
    }

    private ItemStack offerItem(OfferCatalog.Offer o) {
        List<Component> lore = new ArrayList<>(m.offerLore(viewer, o));
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.addAll(service.lines(viewer, BlackMarketModule.ID, "menu.offer.price", Msg.p("price", m.shards(o.price()))));
        if (o.amount() > 1) {
            lore.addAll(service.lines(viewer, BlackMarketModule.ID, "menu.offer.amount", Msg.number("amount", o.amount())));
        }
        Optional<BlackMarketStore.RotationRow> row = o.slot() == OfferCatalog.Slot.FIXED ? Optional.empty() : m.market().row(o.id());
        if (row.isPresent() && row.get().total() > 0) {
            lore.addAll(service.lines(viewer, BlackMarketModule.ID, "menu.offer.stock", Msg.number("left", Math.max(0, row.get().left())),
                    Msg.number("total", row.get().total())));
        }
        if (o.limit() > 0) {
            lore.addAll(service.lines(viewer, BlackMarketModule.ID, "menu.offer.limit",
                    Msg.number("bought", Math.min(o.limit(), m.counts().bought(viewer.getUniqueId(), o))), Msg.number("limit", o.limit()),
                    Msg.c("period", service.text(viewer, BlackMarketModule.ID, "periods." + o.period().id()))));
        }
        if (o.kind() == OfferCatalog.Kind.TAG) {
            lore.addAll(service.lines(viewer, BlackMarketModule.ID, "menu.offer.account-wide"));
        } else if (m.settings.bound || o.kind() == OfferCatalog.Kind.KEY) {
            lore.addAll(service.lines(viewer, BlackMarketModule.ID, "menu.offer.bound"));
        }
        lore.add(Component.empty());
        Optional<String> problem = m.purchases().problem(viewer, o);
        lore.addAll(service.lines(viewer, BlackMarketModule.ID, problem.map(p -> "menu.state." + stateKey(p)).orElse("menu.offer.click")));
        Component name = service.text(viewer, BlackMarketModule.ID, o.slot() == OfferCatalog.Slot.HEADLINE ? "menu.offer.headline-name"
                : "menu.offer.name", Msg.c("offer", m.offerName(viewer, o)));
        ItemStack item = service.item(m.icon(o), name, lore);
        if (o.slot() == OfferCatalog.Slot.HEADLINE && problem.isEmpty()) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    /** Menu state key of a problem message key ({@code buy.sold-out} → {@code sold-out}). */
    static String stateKey(String problem) {
        int dot = problem.lastIndexOf('.');
        return dot < 0 ? problem : problem.substring(dot + 1);
    }

    private ItemStack charterItem(Purchases.CharterView view, Currency currency) {
        List<Component> lore = new ArrayList<>(service.lines(viewer, BlackMarketModule.ID, "charter.lore"));
        lore.add(Component.empty());
        Optional<String> problem = m.purchases().charterProblem(viewer, view, currency);
        int size = view.step() == null ? 0 : view.step().size();
        if (view.island() != null) {
            lore.addAll(service.lines(viewer, BlackMarketModule.ID, "charter.now", Msg.p("size", Purchases.sizeText(view.island().sizeChunks()))));
        }
        if (view.step() != null) {
            long price = Purchases.priceOf(view.step(), currency);
            lore.addAll(service.lines(viewer, BlackMarketModule.ID, "menu.offer.price", Msg.p("price", price > 0 ? m.price(currency, price) : "-")));
        }
        lore.add(Component.empty());
        lore.addAll(service.lines(viewer, BlackMarketModule.ID, problem.map(p -> "menu.state." + stateKey(p)).orElse("menu.offer.click"),
                Msg.p("size", Purchases.sizeText(m.settings.fromSize))));
        Component name = service.text(viewer, BlackMarketModule.ID, size > 0 ? "charter.item-name" : "charter.item-name-plain",
                Msg.p("size", Purchases.sizeText(size)), Msg.c("currency", service.text(viewer, BlackMarketModule.ID, "currency." + currency.key())));
        return service.item(currency.isCredits() ? Material.EMERALD : Material.MAP, name, lore);
    }

    // ================================================================== clicks

    private void clicked(Player player, OfferCatalog.Offer o) {
        Optional<Purchases.Plan> plan = m.purchases().plan(player, o);
        if (plan.isEmpty()) {
            return;
        }
        ItemStack shown = service.item(m.icon(o), m.offerName(player, o), List.of());
        openChild(new ConfirmMenu(v -> service.text(v, BlackMarketModule.ID, "confirm.crumb"), shown,
                v -> {
                    List<Component> lines = new ArrayList<>(service.lines(v, BlackMarketModule.ID, "confirm.offer",
                            Msg.c("offer", m.offerName(v, o)), Msg.number("amount", o.amount()), Msg.p("price", m.shards(o.price())),
                            Msg.p("balance", balance(v, Currency.SHARDS))));
                    if (o.kind() != OfferCatalog.Kind.TAG && (m.settings.bound || o.kind() == OfferCatalog.Kind.KEY)) {
                        lines.addAll(service.lines(v, BlackMarketModule.ID, "confirm.bound"));
                    }
                    return lines;
                },
                p -> m.purchases().buy(p, plan.get(), () -> reopen(p)),
                p -> { }));
    }

    private void charterClicked(Player player, Currency currency) {
        Optional<Purchases.Plan> plan = m.purchases().charterPlan(player, currency);
        if (plan.isEmpty()) {
            return;
        }
        Purchases.Plan pl = plan.get();
        ItemStack shown = service.item(currency.isCredits() ? Material.EMERALD : Material.MAP,
                service.text(player, BlackMarketModule.ID, "charter.name", Msg.p("size", Purchases.sizeText(pl.charterSize()))), List.of());
        openChild(new ConfirmMenu(v -> service.text(v, BlackMarketModule.ID, "confirm.charter-crumb"), shown,
                v -> service.lines(v, BlackMarketModule.ID, "confirm.charter", Msg.p("size", Purchases.sizeText(pl.charterSize())),
                        Msg.p("price", m.price(currency, pl.price())), Msg.p("balance", balance(v, currency))),
                p -> m.purchases().buy(p, pl, () -> reopen(p)),
                p -> { }));
    }

    private String balance(Player v, Currency currency) {
        OptionalLong b = m.economy().map(e -> net.skybornmc.skyblock.common.economy.CreditView.cachedShown(e, v.getUniqueId(), currency))
                .orElse(OptionalLong.empty());
        return b.isPresent() ? m.price(currency, b.getAsLong()) : "...";
    }

    private void reopen(Player p) {
        if (p.isOnline()) {
            m.open(p, null);
        }
    }
}
