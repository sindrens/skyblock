package net.skybornmc.skyblock.economy.admin;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Top earners (M3.8): the profiles that earned the most in the last 24 hours or 7 days, from the hourly per-profile sums
 * (bank moves and staff changes are not earnings). Shows the player's name; a click opens that profile's full history.
 */
final class TopAccountsMenu extends WatchMenu {

    static final int SLOT_RANGE = 46;

    private final Currency currency;
    private boolean week;
    private List<WatchStore.TopAccount> rows;
    private Map<UUID, String> names = Map.of();
    private int page;

    TopAccountsMenu(EconomyWatch watch, Currency currency) {
        super(watch);
        this.currency = currency;
    }

    @Override
    public Component title() {
        return pagedTitle(service.text(viewer, EconomyModule.ID, "watch.top-menu.title"), page, rows == null ? 1 : pages(rows.size()));
    }

    @Override
    public String infoKey() {
        return "watch.top-menu.info";
    }

    @Override
    protected void content() {
        set(SLOT_RANGE, service.item(Material.CLOCK, viewer, EconomyModule.ID, "watch.top-menu.range",
                Msg.c("range", service.text(viewer, EconomyModule.ID, week ? "watch.range.week" : "watch.range.day"))), click -> {
            click();
            week = !week;
            rows = null;
            page = 0;
            invalidate();
            refresh();
        });
        if (rows == null) {
            showLoading();
            if (!failed) {
                long fromHour = Buckets.hour(System.currentTimeMillis()) - (week ? 7 * 24 : 23);
                int limit = watch.config.topSize; // GUI-4c: more than one page (52/53)
                load(watch.store().async(c -> WatchStore.topEarners(c, currency, fromHour, limit))
                        .thenCompose(list -> watch.names(list.stream().map(WatchStore.TopAccount::profile).toList())
                                .thenApply(n -> {
                                    names = n;
                                    return list;
                                })), list -> rows = list);
            }
            return;
        }
        if (rows.isEmpty()) {
            set(PagedMenu.SLOT_EMPTY, service.emptyItem(viewer, EconomyModule.ID, "watch.top-menu.empty"));
        }
        int pages = pages(rows.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * PagedMenu.CONTENT_SLOTS.length;
        for (int i = from; i < rows.size() && i - from < PagedMenu.CONTENT_SLOTS.length; i++) {
            WatchStore.TopAccount a = rows.get(i);
            set(PagedMenu.CONTENT_SLOTS[i - from], service.item(Material.PLAYER_HEAD, viewer, EconomyModule.ID, "watch.top-menu.entry",
                    Msg.p("rank", i + 1), Msg.p("player", watch.name(a.profile(), names)),
                    watch.amount("earned", viewer, currency, a.earned()), watch.amount("spent", viewer, currency, a.spent()),
                    Msg.p("count", Format.number(a.count())), Msg.p("profile", a.profile().toString().substring(0, 8))),
                    click -> watch.history(this, a.profile()));
        }
        pager(page, pages, delta -> {
            page += delta;
            service.sound(viewer, "page");
            redraw();
        });
    }
}
