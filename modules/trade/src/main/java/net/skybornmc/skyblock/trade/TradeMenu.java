package net.skybornmc.skyblock.trade;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * The trade window (6 rows, one per viewer). Taps only, so it works the same on Bedrock: tap an item in your own
 * inventory to offer it, tap your offered item to take it back, tap the gold ingot for money, tap Confirm.
 *
 * <pre>
 *  R O [you] B [status] B [them] O R
 *  B  y  y  y  |  t  t  t  B          your items: 10-12, 19-21, 28-30, 37-39
 *  B  y  y  y  |  t  t  t  B          their items: 14-16, 23-25, 32-34, 41-43
 *  B  y  y  y  |  t  t  t  B          divider: 13, 22, 31, 40
 *  B  y  y  y  |  t  t  t  B
 *  [back] [your money] [confirm] ?  X  [main menu] [their ready] [their money] [cancel]
 * </pre>
 * The framework's bottom bar stays as it is (45 Back, 48 ?, 49 Close, 50 Main menu): none of them cancel the trade, it
 * stays open while the window is closed ({@code session.away-seconds}, {@code /trade} goes back). Only Cancel trade (53,
 * in the corner, away from Confirm) ends it.
 */
final class TradeMenu extends Menu {

    static final int[] MINE = {10, 11, 12, 19, 20, 21, 28, 29, 30, 37, 38, 39};
    static final int[] THEIRS = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};
    static final int[] DIVIDER = {13, 22, 31, 40};
    static final int SLOT_ME = 2;
    static final int SLOT_STATUS = 4;
    static final int SLOT_THEM = 6;
    static final int SLOT_CANCEL = 53;
    static final int SLOT_MY_MONEY = 46;
    static final int SLOT_MY_READY = 47;
    static final int SLOT_THEIR_READY = 51;
    static final int SLOT_THEIR_MONEY = 52;

    private final TradeModule module;
    private final TradeSession session;
    private int shownSeconds = -1;

    TradeMenu(TradeModule module, TradeSession session) {
        this.module = module;
        this.session = session;
    }

    TradeSession session() {
        return session;
    }

    /** Countdown seconds shown at the last build (the tick refreshes when it changes). */
    int shownSeconds() {
        return shownSeconds;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        TradeSession.Side other = session.other(viewer.getUniqueId());
        return service.title(viewer, module.msg.component(viewer, "menu.title", Msg.p("player", other.name)));
    }

    @Override
    public String messageModule() {
        return TradeModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "trade";
    }

    @Override
    protected void build() {
        frame();
        TradeConfig cfg = module.config;
        TradeSession.Side me = session.side(viewer.getUniqueId());
        TradeSession.Side them = session.other(viewer.getUniqueId());
        long now = TradeModule.now();
        shownSeconds = session.secondsLeft(now);

        ItemStack empty = service.filler(cfg.icon("empty", Material.LIGHT_GRAY_STAINED_GLASS_PANE));
        ItemStack divider = service.filler(cfg.icon("divider", Material.IRON_BARS));
        for (int slot : DIVIDER) {
            set(slot, divider);
        }
        fillItems(MINE, me, true, empty);
        fillItems(THEIRS, them, false, empty);

        set(SLOT_ME, head(me, "menu.you"));
        set(SLOT_THEM, head(them, "menu.them"));
        set(SLOT_STATUS, status(now));

        set(SLOT_CANCEL, service.item(cfg.icon("cancel", Material.BARRIER), viewer, TradeModule.ID, "menu.cancel"),
                click -> module.cancelBy(click.player(), "cancelled"));
        set(SLOT_MY_MONEY, service.item(cfg.icon("money", Material.GOLD_INGOT), viewer, TradeModule.ID,
                session.editable() ? "menu.my-money" : "menu.my-money-locked", Msg.c("money", moneyLines(me))),
                click -> module.openMoney(click.player(), this));
        boolean ready = me.ready();
        set(SLOT_MY_READY, service.item(cfg.icon(ready ? "ready" : "not-ready", ready ? Material.LIME_CONCRETE : Material.RED_CONCRETE),
                viewer, TradeModule.ID, ready ? "menu.my-ready" : "menu.my-not-ready"), click -> {
            if (me.ready()) {
                module.unconfirm(click.player());
            } else {
                module.confirm(click.player());
            }
        });
        boolean theyReady = them.ready();
        set(SLOT_THEIR_READY, service.item(cfg.icon(theyReady ? "ready" : "not-ready",
                        theyReady ? Material.LIME_CONCRETE : Material.RED_CONCRETE), viewer, TradeModule.ID,
                theyReady ? "menu.their-ready" : "menu.their-not-ready", Msg.p("player", them.name)));
        set(SLOT_THEIR_MONEY, service.item(cfg.icon("money", Material.GOLD_INGOT), viewer, TradeModule.ID, "menu.their-money",
                Msg.p("player", them.name), Msg.c("money", moneyLines(them))));
    }

    private void fillItems(int[] slots, TradeSession.Side side, boolean mine, ItemStack empty) {
        List<Offer> offers = side.items();
        int max = Math.min(slots.length, module.config.maxItems);
        for (int i = 0; i < slots.length; i++) {
            if (i < offers.size()) {
                Offer o = offers.get(i);
                ItemStack shown = display(o, mine);
                if (mine) {
                    long row = o.rowId();
                    set(slots[i], shown, click -> module.takeBack(click.player(), row));
                } else {
                    set(slots[i], shown);
                }
            } else if (i < offers.size() + side.inFlight()) {
                set(slots[i], service.item(module.config.icon("storing", Material.HOPPER), viewer, TradeModule.ID, "menu.storing"));
            } else if (i < max) {
                set(slots[i], mine ? service.item(empty.getType(), viewer, TradeModule.ID,
                        session.editable() ? "menu.empty-mine" : "menu.empty-locked") : empty);
            } else {
                set(slots[i], service.filler(module.config.icon("locked-slot", Material.BLACK_STAINED_GLASS_PANE)));
            }
        }
    }

    private ItemStack display(Offer o, boolean mine) {
        ItemStack item;
        try {
            item = TradeItems.display(TradeItems.decode(o.bytes()));
        } catch (RuntimeException e) {
            module.core.plugin().getLogger().log(Level.WARNING, "Trade: offered item " + o + " is unreadable", e);
            return service.item(Material.BARRIER, viewer, TradeModule.ID, "menu.unreadable");
        }
        List<Component> value = module.valueLines(viewer, item, session); // R3-38 value hints
        boolean takeBack = mine && session.editable();
        if (takeBack || !value.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            List<Component> existing = item.lore();
            if (existing != null) {
                lore.addAll(existing);
            }
            lore.addAll(value);
            if (takeBack) {
                lore.addAll(service.lines(viewer, TradeModule.ID, "menu.take-back"));
            }
            item.lore(lore);
        }
        return item;
    }

    private ItemStack head(TradeSession.Side side, String key) {
        ItemStack head = service.item(Material.PLAYER_HEAD, viewer, TradeModule.ID, key, Msg.p("player", side.name),
                Msg.number("items", side.items().size()), Msg.c("money", moneyLines(side)),
                Msg.c("ready", module.msg.component(viewer, side.ready() ? "menu.state-ready" : "menu.state-not-ready")));
        Player online = Bukkit.getPlayer(side.player);
        if (online != null) {
            head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(online));
        }
        return head;
    }

    private ItemStack status(long now) {
        Material icon = module.config.icon("status", Material.CLOCK);
        return switch (session.state()) {
            case COUNTDOWN -> {
                ItemStack item = service.item(icon, viewer, TradeModule.ID, "menu.status.countdown",
                        Msg.p("seconds", session.secondsLeft(now)));
                item.setAmount(Math.max(1, Math.min(64, session.secondsLeft(now))));
                yield item;
            }
            case COMPLETING -> service.item(icon, viewer, TradeModule.ID, "menu.status.completing");
            case ENDED -> service.item(icon, viewer, TradeModule.ID, "menu.status.ended");
            case OPEN -> service.item(icon, viewer, TradeModule.ID, "menu.status.open",
                    Msg.p("seconds", module.config.countdownSeconds));
        };
    }

    /** "$1,500, ✧ 20" or "nothing" as one component. */
    private Component moneyLines(TradeSession.Side side) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Currency, Long> e : side.money().entrySet()) {
            parts.add(module.economy().format(e.getKey(), e.getValue()));
        }
        if (parts.isEmpty()) {
            return module.msg.component(viewer, "menu.no-money");
        }
        return module.msg.component(viewer, "menu.money-value", Msg.p("value", String.join(", ", parts)));
    }
}
