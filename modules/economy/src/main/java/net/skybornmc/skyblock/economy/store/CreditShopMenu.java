package net.skybornmc.skyblock.economy.store;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.store.CreditShop;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /credit shop} (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 1; inspiration: category tabs, one card per thing with
 * a few bullets and the price, a confirm screen), 6 rows:
 * <pre>
 * Row 0      1 Ranks · 2 Boosters · 4 your credits · 6 Cosmetics · 7 Extras
 * Rows 1-4   the offers of the tab (ranks: one row of 7 in the middle)
 * Row 5      45 Back · 46 Gift to a friend · 47 History · 49 Close · 50 Help · 51 Get credits
 * </pre>
 * In gift mode ({@code receiver} set) prices and tiles are the receiver's, only giftable offers can be bought and slot 4
 * shows who it is for.
 */
final class CreditShopMenu extends Menu {

    static final int[] TAB_SLOTS = {1, 2, 6, 7};
    static final int SLOT_GIFT = 46;
    static final int SLOT_HISTORY = 47;
    static final int SLOT_GET = 51;

    private final CreditShopImpl shop;
    private CreditShop.Tab tab;
    private final Player receiver;

    CreditShopMenu(CreditShopImpl shop, CreditShop.Tab tab, Player receiver) {
        this.shop = shop;
        this.tab = tab;
        this.receiver = receiver;
    }

    /** The content slots of rows 1-4 (7 a row). Pure. */
    static int[] contentSlots() {
        int[] out = new int[28];
        int i = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                out[i++] = row * 9 + col;
            }
        }
        return out;
    }

    private Player receiver() {
        return receiver == null ? viewer : receiver;
    }

    private boolean gift() {
        return receiver != null && !receiver.equals(viewer);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        Component base = service.text(viewer, CreditShopImpl.MODULE, "credit.shop.menu.title");
        Component tabName = service.text(viewer, CreditShopImpl.MODULE, "credit.shop.menu.tab." + tab.name().toLowerCase(Locale.ROOT) + ".name");
        return service.title(viewer, base, tabName);
    }

    @Override
    public String messageModule() {
        return CreditShopImpl.MODULE;
    }

    @Override
    protected void build() {
        navBar();
        helpButton();
        if (gift() && !receiver.isOnline()) {
            viewer.closeInventory();
            shop.msg().send(viewer, "credit.gift.offline", Msg.p("player", receiver.getName()));
            return;
        }
        CreditShop.Tab[] tabs = CreditShop.Tab.values();
        for (int i = 0; i < tabs.length && i < TAB_SLOTS.length; i++) {
            CreditShop.Tab t = tabs[i];
            String key = "credit.shop.menu.tab." + t.name().toLowerCase(Locale.ROOT);
            set(TAB_SLOTS[i], service.tab(viewer, new ItemStack(tabIcon(t)), service.text(viewer, CreditShopImpl.MODULE, key + ".name"),
                    service.lines(viewer, CreditShopImpl.MODULE, key + ".lore"), t == tab, () -> tab = t));
        }
        long[] wallet = shop.wallet(viewer);
        if (gift()) {
            set(SLOT_SUMMARY, service.item(Material.PLAYER_HEAD, viewer, CreditShopImpl.MODULE, "credit.shop.menu.gift-for",
                    Msg.p("player", receiver.getName()), Msg.p("bought", Format.number(wallet[0]))));
        } else {
            set(SLOT_SUMMARY, service.item(Material.EMERALD, viewer, CreditShopImpl.MODULE, "credit.shop.menu.wallet",
                    Msg.p("bought", Format.number(wallet[0])), Msg.p("earned", Format.number(wallet[1])),
                    Msg.p("total", Format.number(wallet[0] + wallet[1]))));
        }
        List<CreditShop.Offer> offers = shop.offers(tab);
        if (gift()) {
            offers = offers.stream().filter(CreditShop.Offer::giftable).toList();
        }
        int[] slots = slotsFor(tab, offers.size());
        if (offers.isEmpty()) {
            setEmpty(CreditShopImpl.MODULE, "credit.shop.menu.empty");
        }
        for (int i = 0; i < offers.size() && i < slots.length; i++) {
            CreditShop.Offer o = offers.get(i);
            set(slots[i], tile(o), click -> clicked(click.player(), o));
        }
        if (!gift()) {
            set(SLOT_GIFT, service.item(Material.CAKE, viewer, CreditShopImpl.MODULE, "credit.shop.menu.gift"), click -> askGift(click.player()));
            set(SLOT_HISTORY, service.item(Material.WRITABLE_BOOK, viewer, CreditShopImpl.MODULE, "credit.shop.menu.history"),
                    click -> service.navigate(click.player(), this, () -> click.player().performCommand("credit history")));
        }
        set(SLOT_GET, service.item(Material.GOLD_INGOT, viewer, CreditShopImpl.MODULE, "credit.shop.menu.get"), click -> {
            click.player().closeInventory();
            shop.msg().send(click.player(), "credit.shop.get-credits");
        });
    }

    /** Ranks (7 or fewer): one centred row (19-25); everything else fills rows 1-4. Pure. */
    static int[] slotsFor(CreditShop.Tab tab, int count) {
        if (tab == CreditShop.Tab.RANKS && count <= 7) {
            int[] out = new int[count];
            int first = 19 + (7 - count) / 2;
            for (int i = 0; i < count; i++) {
                out[i] = first + i;
            }
            return out;
        }
        return contentSlots();
    }

    private static Material tabIcon(CreditShop.Tab t) {
        return switch (t) {
            case RANKS -> Material.NETHER_STAR;
            case BOOSTERS -> Material.GOLD_INGOT;
            case COSMETICS -> Material.AMETHYST_SHARD;
            case EXTRAS -> Material.CHEST;
        };
    }

    /** The offer's own tile plus the price lines (or why it can't be bought). */
    private ItemStack tile(CreditShop.Offer o) {
        ItemStack item = o.icon().apply(viewer, receiver());
        if (o.open() != null) {
            return withLines(item, service.lines(viewer, CreditShopImpl.MODULE, "credit.shop.menu.link"));
        }
        long price = o.price().apply(viewer, receiver());
        if (price < 0) {
            return item;
        }
        boolean boughtOnly = o.boughtOnly() || gift();
        return withLines(item, service.lines(viewer, CreditShopImpl.MODULE, boughtOnly ? "credit.shop.menu.price-bought" : "credit.shop.menu.price",
                Msg.p("price", Format.number(price))));
    }

    private static ItemStack withLines(ItemStack item, List<Component> lines) {
        List<Component> lore = new ArrayList<>(item.lore() == null ? List.of() : item.lore());
        lore.addAll(lines);
        item.lore(lore);
        return item;
    }

    private void clicked(Player p, CreditShop.Offer o) {
        if (o.open() != null) {
            service.navigate(p, this, () -> o.open().accept(p));
            return;
        }
        long price = o.price().apply(p, receiver());
        if (price < 0) {
            shop.core().sounds().play(p, "error");
            return;
        }
        boolean boughtOnly = o.boughtOnly() || gift();
        long[] wallet = shop.wallet(p);
        long have = boughtOnly ? wallet[0] : wallet[0] + wallet[1];
        String key = gift() ? "credit.shop.confirm.gift" : boughtOnly ? "credit.shop.confirm.bought" : "credit.shop.confirm.any";
        Player to = receiver();
        ConfirmMenu confirm = new ConfirmMenu(v -> service.text(v, CreditShopImpl.MODULE, "credit.shop.confirm.crumb"),
                o.icon().apply(p, to),
                v -> service.lines(v, CreditShopImpl.MODULE, key, Msg.p("price", Format.number(price)), Msg.p("have", Format.number(have)),
                        Msg.p("after", Format.number(Math.max(0, have - price))), Msg.p("player", to.getName())),
                pl -> shop.buy(pl, o, to, () -> {
                    if (pl.isOnline()) {
                        shop.core().menus().open(pl, new CreditShopMenu(shop, tab, receiver));
                    }
                }), pl -> { });
        confirm.parent(this);
        service.open(p, confirm);
    }

    private void askGift(Player p) {
        service.input(p, CreditShopImpl.MODULE, "credit.gift.ask", 16, "", name -> {
            Player target = Bukkit.getPlayerExact(name.trim());
            if (target == null) {
                shop.msg().send(p, "credit.gift.offline", Msg.p("player", name.trim()));
                shop.core().sounds().play(p, "error");
                return;
            }
            if (target.equals(p)) {
                shop.msg().send(p, "credit.gift.self");
                shop.core().sounds().play(p, "error");
                return;
            }
            shop.openGift(p, target);
        }, () -> shop.open(p, tab));
    }
}
