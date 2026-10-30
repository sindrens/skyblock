package net.skybornmc.skyblock.economy.admin;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Drill-down per ledger reason (M3.8): created, removed and moved money and the number of transactions for today / 7 days
 * / 14 days, sortable. Transit groups (auction, notes) show their net plus what passed through. A click opens the biggest
 * transactions of that reason (or group) of the last 24 hours.
 */
final class ReasonsMenu extends WatchMenu {

    static final int SLOT_RANGE = 46;
    static final int SLOT_SORT = 47;

    enum Sort {
        VOLUME, CREATED, REMOVED;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final Currency currency;
    private DashboardData.Range range;
    private Sort sort;
    private DashboardData data;
    private int page;

    ReasonsMenu(EconomyWatch watch, Currency currency, DashboardData.Range range, Sort sort) {
        super(watch);
        this.currency = currency;
        this.range = range;
        this.sort = sort;
    }

    @Override
    public Component title() {
        return titleWith("watch.reasons-menu.title");
    }

    @Override
    public String infoKey() {
        return "watch.reasons-menu.info";
    }

    private List<FlowSummary.ReasonTotals> sorted() {
        List<FlowSummary.ReasonTotals> list = new ArrayList<>(data.period(range).reasons());
        Comparator<FlowSummary.ReasonTotals> order = switch (sort) {
            case VOLUME -> Comparator.comparingLong(FlowSummary.ReasonTotals::volume);
            case CREATED -> Comparator.comparingLong(FlowSummary.ReasonTotals::created);
            case REMOVED -> Comparator.comparingLong(FlowSummary.ReasonTotals::removed);
        };
        list.sort(order.reversed().thenComparing(FlowSummary.ReasonTotals::key));
        if (sort != Sort.VOLUME) {
            list.removeIf(r -> (sort == Sort.CREATED ? r.created() : r.removed()) <= 0);
        }
        return list;
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_RANGE, service.item(Material.CLOCK, viewer, EconomyModule.ID, "watch.reasons-menu.range",
                Msg.c("range", service.text(viewer, EconomyModule.ID, "watch.range." + range.key()))), click -> {
            click();
            range = range.next();
            page = 0;
            refresh();
        });
        set(SLOT_SORT, service.item(Material.HOPPER, viewer, EconomyModule.ID, "watch.reasons-menu.sort",
                Msg.c("sort", service.text(viewer, EconomyModule.ID, "watch.reasons-menu.sorts." + sort.key()))), click -> {
            click();
            sort = sort.next();
            page = 0;
            refresh();
        });
        if (data == null) {
            showLoading();
            if (!failed) {
                load(watch.dashboard(currency, false), d -> data = d);
            }
            return;
        }
        List<FlowSummary.ReasonTotals> list = sorted();
        if (list.isEmpty()) {
            set(PagedMenu.SLOT_EMPTY, service.item(Material.COBWEB, viewer, EconomyModule.ID, "watch.reasons-menu.empty"));
        }
        int size = PagedMenu.CONTENT_SLOTS.length;
        int pages = Math.max(1, (list.size() + size - 1) / size);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < size && page * size + i < list.size(); i++) {
            FlowSummary.ReasonTotals r = list.get(page * size + i);
            set(PagedMenu.CONTENT_SLOTS[i], entry(r), click -> openChild(new TransactionListMenu(watch, currency, members(r),
                    watch.reasonLabel(viewer, r.key(), r.group()))));
        }
        if (page > 0) {
            set(Menu.SLOT_PREVIOUS, service.navItem("previous", Material.ARROW, viewer, "frame.previous",
                    Msg.p("page", page), Msg.p("pages", pages)), click -> turn(-1));
        }
        if (page + 1 < pages) {
            set(Menu.SLOT_NEXT, service.navItem("next", Material.ARROW, viewer, "frame.next",
                    Msg.p("page", page + 2), Msg.p("pages", pages)), click -> turn(1));
        }
    }

    /** The ledger reasons behind an entry (a group's members, or the reason itself). */
    private List<String> members(FlowSummary.ReasonTotals r) {
        if (!r.group()) {
            return List.of(r.key());
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : FlowSummary.transitGroups(watch.config.transitGroups).entrySet()) {
            if (e.getValue().equals(r.key())) {
                out.add(e.getKey());
            }
        }
        return out.isEmpty() ? List.of(r.key()) : out;
    }

    private void turn(int delta) {
        page = Math.max(0, page + delta);
        service.sound(viewer, "page");
        refresh();
    }

    private ItemStack entry(FlowSummary.ReasonTotals r) {
        var tags = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                Msg.c("reason", watch.reasonLabel(viewer, r.key(), r.group())),
                watch.amount("created", viewer, currency, r.created()),
                watch.amount("removed", viewer, currency, r.removed()),
                watch.amount("moved", viewer, currency, r.moved()),
                Msg.p("count", Format.number(r.count())),
                watch.amount("in", viewer, currency, r.transitIn()),
                watch.amount("out", viewer, currency, r.transitOut()),
                Msg.p("key", r.key())};
        List<Component> lore = new ArrayList<>(service.lines(viewer, EconomyModule.ID, "watch.reasons-menu.entry.lore", tags));
        if (r.group()) {
            lore.addAll(service.lines(viewer, EconomyModule.ID, "watch.reasons-menu.transit", tags));
        }
        lore.addAll(service.lines(viewer, EconomyModule.ID, "watch.reasons-menu.footer", tags));
        Material icon = r.created() > r.removed() ? Material.LIME_DYE : r.removed() > 0 ? Material.RED_DYE : Material.LIGHT_GRAY_DYE;
        return service.item(icon, service.text(viewer, EconomyModule.ID, "watch.reasons-menu.entry.name", tags), lore);
    }
}
