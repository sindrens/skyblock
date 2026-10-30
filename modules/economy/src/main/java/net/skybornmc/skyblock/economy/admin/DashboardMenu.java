package net.skybornmc.skyblock.economy.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /eco admin} (M3.8): the economy overview for staff. Row 2: money supply and trend, inflation risk badge, created vs
 * removed today / 7 days / 14 days, sell price index, velocity and highlights. Row 3: biggest sources and sinks, every
 * reason, top earners, biggest transactions. Row 4: alerts, currency switch, refresh.
 */
final class DashboardMenu extends WatchMenu {

    static final int SLOT_SUPPLY = 10;
    static final int SLOT_RISK = 11;
    static final int SLOT_TODAY = 12;
    static final int SLOT_WEEK = 13;
    static final int SLOT_FORTNIGHT = 14;
    static final int SLOT_SELL = 15;
    static final int SLOT_HIGHLIGHTS = 16;
    static final int SLOT_SOURCES = 20;
    static final int SLOT_SINKS = 21;
    static final int SLOT_REASONS = 22;
    static final int SLOT_TOP = 23;
    static final int SLOT_BIGGEST = 24;
    static final int SLOT_ALERTS = 30;
    static final int SLOT_CURRENCY = 31;
    static final int SLOT_REFRESH = 32;
    /** Lines in the sources/sinks items. */
    static final int TOP_LINES = 5;

    private Currency currency;
    private DashboardData data;
    private boolean forceFresh;

    DashboardMenu(EconomyWatch watch, Currency currency) {
        super(watch);
        this.currency = currency;
    }

    @Override
    public net.kyori.adventure.text.Component title() {
        return titleWith(null);
    }

    @Override
    public String infoKey() {
        return "watch.menu.info";
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_CURRENCY, service.item(Material.SUNFLOWER, viewer, EconomyModule.ID, "watch.menu.currency",
                Msg.c("currency", currencyName())), click -> {
            click();
            currency = Currency.values()[(currency.ordinal() + 1) % Currency.values().length];
            data = null;
            invalidate();
            refresh();
        });
        set(SLOT_REFRESH, service.item(Material.SPYGLASS, viewer, EconomyModule.ID, "watch.menu.refresh",
                Msg.p("ago", watch.job().lastRun() == 0 ? "-" : Format.duration(Duration.ofMillis(
                        Math.max(0, System.currentTimeMillis() - watch.job().lastRun())))),
                Msg.p("cursor", watch.job().cursor() < 0 ? "-" : Format.number(watch.job().cursor()))), click -> refreshNow());
        if (data == null) {
            showLoading();
            if (!failed) {
                boolean fresh = forceFresh;
                forceFresh = false;
                load(watch.dashboard(currency, fresh), d -> data = d);
            }
            return;
        }
        set(SLOT_SUPPLY, supply());
        set(SLOT_RISK, risk());
        set(SLOT_TODAY, period(Material.CLOCK, DashboardData.Range.TODAY));
        set(SLOT_WEEK, period(Material.COMPASS, DashboardData.Range.WEEK));
        set(SLOT_FORTNIGHT, period(Material.RECOVERY_COMPASS, DashboardData.Range.FORTNIGHT));
        set(SLOT_SELL, sellIndex());
        set(SLOT_HIGHLIGHTS, highlights());
        set(SLOT_SOURCES, topList(Material.LIME_DYE, "watch.menu.sources", data.week().sources(), true),
                click -> openChild(new ReasonsMenu(watch, currency, DashboardData.Range.WEEK, ReasonsMenu.Sort.CREATED)));
        set(SLOT_SINKS, topList(Material.RED_DYE, "watch.menu.sinks", data.week().sinks(), false),
                click -> openChild(new ReasonsMenu(watch, currency, DashboardData.Range.WEEK, ReasonsMenu.Sort.REMOVED)));
        set(SLOT_REASONS, service.item(Material.BOOK, viewer, EconomyModule.ID, "watch.menu.reasons"),
                click -> openChild(new ReasonsMenu(watch, currency, DashboardData.Range.TODAY, ReasonsMenu.Sort.VOLUME)));
        set(SLOT_TOP, service.item(Material.PLAYER_HEAD, viewer, EconomyModule.ID, "watch.menu.top"),
                click -> openChild(new TopAccountsMenu(watch, currency)));
        set(SLOT_BIGGEST, service.item(Material.DIAMOND, viewer, EconomyModule.ID, "watch.menu.biggest"),
                click -> openChild(new TransactionListMenu(watch, currency, List.of(), null)));
        set(SLOT_ALERTS, service.item(data.openAlerts() > 0 ? Material.BELL : Material.FEATHER, viewer, EconomyModule.ID,
                "watch.menu.alerts", Msg.p("open", Format.number(data.openAlerts()))), click -> openChild(new AlertsMenu(watch)));
    }

    private Component currencyName() {
        return service.text(viewer, EconomyModule.ID, "names." + currency.key());
    }

    private void refreshNow() {
        click();
        watch.then(viewer, watch.runJob(), result -> {
            watch.msg.send(viewer, "watch.refreshed", Msg.p("rows", Format.number(result.rows())));
            data = null;
            forceFresh = true;
            invalidate();
            if (isOpen()) {
                refresh();
            }
        });
    }

    private ItemStack supply() {
        return service.item(Material.GOLD_BLOCK, viewer, EconomyModule.ID, "watch.menu.supply",
                Msg.c("currency", currencyName()),
                watch.amount("amount", viewer, currency, data.supplyNow()),
                watch.amount("notes", viewer, currency, data.notesNow()),
                data.supplyDayAgo() < 0 ? Msg.p("day", "-") : watch.amount("day", viewer, currency, data.supplyDayAgo()),
                data.supplyWeekAgo() < 0 ? Msg.p("week", "-") : watch.amount("week", viewer, currency, data.supplyWeekAgo()),
                Msg.p("day-change", watch.signedPercent(FlowSummary.percentChange(data.supplyDayAgo(), data.supplyNow()))),
                Msg.p("week-change", watch.signedPercent(FlowSummary.percentChange(data.supplyWeekAgo(), data.supplyNow()))));
    }

    private ItemStack risk() {
        InflationRisk.Risk risk = data.risk();
        Material icon = switch (risk.level()) {
            case NO_DATA -> Material.GRAY_CONCRETE;
            case OK -> Material.LIME_CONCRETE;
            case WATCH -> Material.YELLOW_CONCRETE;
            case ACT -> Material.RED_CONCRETE;
        };
        WatchConfig c = watch.config;
        return service.item(icon, viewer, EconomyModule.ID, "watch.menu.risk",
                Msg.c("level", service.text(viewer, EconomyModule.ID, "watch.risk." + risk.level().key())),
                Msg.p("ratio", watch.percent(risk.ratio())),
                Msg.p("below", risk.daysBelowAct()), Msg.p("days", risk.daysWithData()),
                Msg.p("watch", Format.number(Math.round(c.watchBelowPercent))),
                Msg.p("act", Format.number(Math.round(c.actBelowPercent))), Msg.p("act-days", c.actDays));
    }

    private ItemStack period(Material icon, DashboardData.Range range) {
        FlowSummary.Totals t = data.period(range).totals();
        return service.item(icon, viewer, EconomyModule.ID, "watch.menu.period",
                Msg.c("range", service.text(viewer, EconomyModule.ID, "watch.range." + range.key())),
                watch.amount("created", viewer, currency, t.created()),
                watch.amount("removed", viewer, currency, t.removed()),
                watch.amount("net", viewer, currency, t.net()),
                Msg.p("ratio", watch.percent(t.sinkPercent())),
                watch.amount("moved", viewer, currency, t.moved()));
    }

    private ItemStack sellIndex() {
        SellIndex index = watch.sellIndex();
        if (index == null || index.items() == 0) {
            return service.item(Material.EMERALD, viewer, EconomyModule.ID, "watch.menu.sell-index-none");
        }
        return service.item(Material.EMERALD, viewer, EconomyModule.ID, "watch.menu.sell-index",
                Msg.p("index", watch.percent(index.index())),
                Msg.p("items", index.items()), Msg.p("below", index.below()), Msg.p("above", index.above()),
                Msg.p("lowest", index.lowestKey() == null ? "-" : index.lowestKey()),
                Msg.p("lowest-percent", index.lowestKey() == null ? "-" : watch.signedPercent(index.lowestPercent())),
                Msg.p("hot", index.hotKey() == null ? "-" : index.hotKey()));
    }

    private ItemStack highlights() {
        FlowSummary.Period week = data.week();
        long interest = createdOf(week, TxReason.INTEREST);
        FlowSummary.ReasonTotals auction = week.reason("auction");
        long ahVolume = auction == null ? 0 : auction.transitOut();
        long ahTax = (auction == null ? 0 : auction.removed()) + removedOf(week, TxReason.AH_FEE);
        long shop = removedOf(week, TxReason.SHOP_BUY);
        String velocity = Double.isNaN(FlowSummary.velocity(week.totals().moved(), data.supplyNow())) ? watch.raw("watch.percent-none")
                : String.format(java.util.Locale.ROOT, "%.2fx", FlowSummary.velocity(week.totals().moved(), data.supplyNow()));
        return service.item(Material.HOPPER, viewer, EconomyModule.ID, "watch.menu.highlights",
                Msg.p("velocity", velocity),
                watch.amount("interest", viewer, currency, interest),
                watch.amount("ah-volume", viewer, currency, ahVolume),
                watch.amount("ah-tax", viewer, currency, ahTax),
                watch.amount("shop", viewer, currency, shop));
    }

    private static long createdOf(FlowSummary.Period p, String reason) {
        FlowSummary.ReasonTotals r = p.reason(reason);
        return r == null ? 0 : r.created();
    }

    private static long removedOf(FlowSummary.Period p, String reason) {
        FlowSummary.ReasonTotals r = p.reason(reason);
        return r == null ? 0 : r.removed();
    }

    private ItemStack topList(Material icon, String key, List<FlowSummary.ReasonTotals> list, boolean created) {
        List<Component> lore = new ArrayList<>();
        FlowSummary.Totals totals = data.week().totals();
        long all = created ? totals.created() : totals.removed();
        if (list.isEmpty()) {
            lore.add(service.text(viewer, EconomyModule.ID, key + ".empty"));
        }
        for (int i = 0; i < list.size() && i < TOP_LINES; i++) {
            FlowSummary.ReasonTotals r = list.get(i);
            long value = created ? r.created() : r.removed();
            TagResolver[] tags = {Msg.c("reason", watch.reasonLabel(viewer, r.key(), r.group())),
                    watch.amount("amount", viewer, currency, value),
                    Msg.p("share", watch.percent(all <= 0 ? Double.NaN : value * 100.0 / all))};
            lore.add(service.text(viewer, EconomyModule.ID, key + ".line", tags));
        }
        lore.addAll(service.lines(viewer, EconomyModule.ID, key + ".footer"));
        return service.item(icon, service.text(viewer, EconomyModule.ID, key + ".name"), lore);
    }
}
