package net.skybornmc.skyblock.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.command.Durations;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.menu.PickListMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /transactions}: the player's own ledger, newest first, one database page at a time (a loading item shows while
 * it is fetched). Filters in the bottom row each open a small pick list (GUI-4/G9, one click picks): currency, reason
 * group and time range.
 */
final class TransactionsMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    static final int SLOT_CURRENCY = 46;
    static final int SLOT_REASON = 47;
    static final int SLOT_TIME = 51;
    /** Top row, right of the summary: 52 is Previous page (GUI-1 handoff). */
    static final int SLOT_REFRESH = 6;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
            .withZone(net.skybornmc.skyblock.common.util.Format.zone());

    private final EconomyModule module;
    private final AccountRef account;
    /** The player account (gems live there, owner PR2); equal to {@link #account} for profile 1. */
    private final AccountRef gemsAccount;
    private LedgerFilter filter = LedgerFilter.ALL;
    private int rangeIndex;
    private int page;
    private List<LedgerEntry> rows;
    private long total = -1;
    private boolean failed;
    private int loading = -1;
    private int generation;

    TransactionsMenu(EconomyModule module, AccountRef account, AccountRef gemsAccount) {
        this.module = module;
        this.account = account;
        this.gemsAccount = gemsAccount == null ? account : gemsAccount;
    }

    /** The account a row is seen from: the player for gem rows, the profile for everything else. */
    private AccountRef view(LedgerEntry e) {
        return e.currency() == net.skybornmc.skyblock.common.economy.Currency.GEMS ? gemsAccount : account;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        Component base = service.text(viewer, EconomyModule.ID, "menu.title");
        Component suffix = pages() > 1 ? service.text(viewer, "title.page", Msg.p("page", page + 1), Msg.p("pages", pages())) : null;
        return service.title(viewer, List.of(base), suffix);
    }

    @Override
    public String messageModule() {
        return EconomyModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "money";
    }

    private int pageSize() {
        return Math.min(PagedMenu.CONTENT_SLOTS.length, module.config.transactionPageSize);
    }

    private int pages() {
        return total <= 0 ? 1 : (int) ((total + pageSize() - 1) / pageSize());
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_SUMMARY, summary());
        set(SLOT_CURRENCY, filterItem(Material.SUNFLOWER, "menu.filter-currency", currencyLabel()), click -> pickCurrency());
        set(SLOT_REASON, filterItem(Material.NAME_TAG, "menu.filter-reason", reasonLabel()), click -> pickReason());
        set(SLOT_TIME, filterItem(Material.CLOCK, "menu.filter-time", rangeLabel()), click -> pickRange());
        set(SLOT_REFRESH, service.item(Material.SPYGLASS, viewer, EconomyModule.ID, "menu.refresh"), click -> reload());

        if (rows == null) {
            set(PagedMenu.SLOT_EMPTY, failed ? service.errorItem(viewer, this::reload) : service.loadingItem(viewer));
            if (!failed) {
                load();
            }
            return;
        }
        if (rows.isEmpty()) {
            set(PagedMenu.SLOT_EMPTY, service.emptyItem(viewer, EconomyModule.ID, "menu.empty"));
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows.size() && i < PagedMenu.CONTENT_SLOTS.length; i++) {
            set(PagedMenu.CONTENT_SLOTS[i], entry(rows.get(i), now));
        }
        if (page > 0) {
            set(Menu.SLOT_PREVIOUS, service.navItem("previous", Material.ARROW, viewer, "frame.previous",
                    Msg.p("page", page), Msg.p("pages", pages())), click -> turn(-1));
        }
        if (page + 1 < pages()) {
            set(Menu.SLOT_NEXT, service.navItem("next", Material.ARROW, viewer, "frame.next",
                    Msg.p("page", page + 2), Msg.p("pages", pages())), click -> turn(1));
        }
    }

    // ------------------------------------------------------------------------------------------------ items

    private org.bukkit.inventory.ItemStack summary() {
        List<Component> lore = new ArrayList<>(service.lines(viewer, EconomyModule.ID, "menu.summary.lore",
                Msg.p("total", total < 0 ? "..." : Format.number(total))));
        for (Currency currency : Currency.values()) {
            var cached = module.service().cached(viewer.getUniqueId(), currency);
            lore.add(service.text(viewer, EconomyModule.ID, "menu.summary.line", Msg.c("currency", module.currencyName(viewer, currency)),
                    cached.isPresent() ? module.amountTag("amount", viewer, currency, cached.getAsLong())
                            : Msg.p("amount", module.core.messages().raw(EconomyModule.ID, "placeholder.loading"))));
        }
        return service.item(Material.BOOK, service.text(viewer, EconomyModule.ID, "menu.summary.name"), lore);
    }

    private org.bukkit.inventory.ItemStack filterItem(Material icon, String key, Component value) {
        return service.item(icon, viewer, EconomyModule.ID, key, Msg.c("value", value));
    }

    private org.bukkit.inventory.ItemStack entry(LedgerEntry e, long now) {
        AccountRef seen = view(e);
        boolean in = e.incoming(seen);
        String other = e.counterpartyName(seen);
        AccountRef otherRef = e.counterparty(seen);
        Component counterparty;
        if (other != null) {
            counterparty = Component.text(other);
        } else if (otherRef != null && otherRef.isBank()) {
            counterparty = service.text(viewer, EconomyModule.ID, "menu.entry.bank");
        } else if (otherRef != null) {
            counterparty = service.text(viewer, EconomyModule.ID, "menu.entry.island");
        } else {
            counterparty = service.text(viewer, EconomyModule.ID, "menu.entry.server");
        }
        Long after = e.balanceAfter(seen);
        TagResolver[] p = {
                module.exactAmountTag("amount", viewer, e.currency(), e.amount()),
                Msg.p("exact", Format.number(e.amount())),
                Msg.c("other", counterparty),
                Msg.c("reason", reasonName(e.reason())),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(0, now - e.time())))),
                Msg.p("date", DATE.format(Instant.ofEpochMilli(e.time()))),
                after == null ? Msg.p("balance", "-") : module.exactAmountTag("balance", viewer, e.currency(), after),
                Msg.p("reference", e.reference() == null ? "-" : e.reference()),
                Msg.p("id", e.id()),
                Msg.c("currency", module.currencyName(viewer, e.currency()))};
        String key = in ? "menu.entry.in" : "menu.entry.out";
        List<Component> lore = new ArrayList<>(service.lines(viewer, EconomyModule.ID, key + ".lore", p));
        if (e.reference() != null) {
            lore.addAll(service.lines(viewer, EconomyModule.ID, "menu.entry.reference", p));
        }
        lore.addAll(service.lines(viewer, EconomyModule.ID, "menu.entry.footer", p));
        return service.item(module.config.icon(e.currency(), Material.GOLD_NUGGET), service.text(viewer, EconomyModule.ID, key + ".name", p),
                lore);
    }

    /** {@code reasons.<key>} from the messages file; unknown reasons show with spaces instead of underscores. */
    private Component reasonName(String reason) {
        String key = "reasons." + reason;
        if (module.msg.has(key)) {
            return service.text(viewer, EconomyModule.ID, key);
        }
        return Component.text(reason.replace('_', ' '));
    }

    private Component currencyLabel() {
        return filter.currency() == null ? service.text(viewer, EconomyModule.ID, "menu.any")
                : module.currencyName(viewer, filter.currency());
    }

    private Component reasonLabel() {
        return filter.reason() == null ? service.text(viewer, EconomyModule.ID, "menu.any") : reasonName(filter.reason());
    }

    private Component rangeLabel() {
        Duration since = filter.since();
        return since == null ? service.text(viewer, EconomyModule.ID, "menu.any-time")
                : service.text(viewer, EconomyModule.ID, "menu.last", Msg.p("time", Format.duration(since)));
    }

    // ------------------------------------------------------------------------------------------------ filters

    private void pickCurrency() {
        List<PickListMenu.Option<Currency>> options = new ArrayList<>();
        options.add(new PickListMenu.Option<>(null, new ItemStack(Material.SUNFLOWER), service.text(viewer, EconomyModule.ID, "menu.any"),
                List.of(), filter.currency() == null));
        for (Currency c : Currency.values()) {
            options.add(new PickListMenu.Option<>(c, new ItemStack(module.config.icon(c, Material.GOLD_NUGGET)), module.currencyName(viewer, c),
                    List.of(), c == filter.currency()));
        }
        pick("menu.filter-currency.crumb", options, c -> filter = filter.withCurrency(c));
    }

    private void pickReason() {
        List<PickListMenu.Option<String>> options = new ArrayList<>();
        options.add(new PickListMenu.Option<>(null, new ItemStack(Material.NAME_TAG), service.text(viewer, EconomyModule.ID, "menu.any"),
                List.of(), filter.reason() == null));
        for (String raw : module.config.transactionReasons) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String reason = raw.toLowerCase(Locale.ROOT);
            options.add(new PickListMenu.Option<>(reason, new ItemStack(Material.PAPER), reasonName(reason), List.of(),
                    reason.equals(filter.reason())));
        }
        pick("menu.filter-reason.crumb", options, r -> filter = filter.withReason(r));
    }

    private void pickRange() {
        List<String> ranges = module.config.transactionRanges;
        List<PickListMenu.Option<Integer>> options = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            Duration since = parseRange(ranges.get(i));
            Component name = since == null ? service.text(viewer, EconomyModule.ID, "menu.any-time")
                    : service.text(viewer, EconomyModule.ID, "menu.last", Msg.p("time", Format.duration(since)));
            options.add(new PickListMenu.Option<>(i, new ItemStack(Material.CLOCK), name, List.of(), i == rangeIndex));
        }
        pick("menu.filter-time.crumb", options, i -> {
            rangeIndex = i;
            filter = filter.withSince(parseRange(ranges.get(i)));
        });
    }

    /** Opens a pick list (GUI-4/G9); the picked filter reloads the list from page 1. */
    private <T> void pick(String crumbKey, List<PickListMenu.Option<T>> options, java.util.function.Consumer<T> apply) {
        openChild(new PickListMenu<>(List.of(service.text(viewer, EconomyModule.ID, "menu.title"),
                service.text(viewer, EconomyModule.ID, crumbKey)), options, value -> {
            apply.accept(value);
            page = 0;
            rows = null;
            total = -1;
            failed = false;
            generation++;
        }));
    }

    /** {@code all} (or anything invalid) = no limit. */
    static Duration parseRange(String text) {
        if (text == null || text.equalsIgnoreCase("all")) {
            return null;
        }
        try {
            return Durations.parse(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void reload() {
        page = 0;
        rows = null;
        total = -1;
        failed = false;
        generation++;
        service.sound(viewer, "click");
        refresh();
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages() - 1, page + delta));
        rows = null;
        generation++;
        service.sound(viewer, "page");
        service.open(viewer, this);
    }

    private void load() {
        int requested = generation;
        if (loading == requested) {
            return;
        }
        loading = requested;
        int size = pageSize();
        module.service().store().history(account, gemsAccount, filter, page * size, size).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(module.core.plugin(), () -> {
                    if (loading == requested) {
                        loading = -1;
                    }
                    if (requested != generation) {
                        return;
                    }
                    if (error != null) {
                        module.core.plugin().getLogger().warning("Economy: /transactions failed: " + error.getMessage());
                        failed = true;
                    } else {
                        rows = result.rows();
                        int pagesBefore = pages();
                        total = result.total();
                        boolean pagesChanged = pagesBefore != pages();
                        failed = false;
                        if (pagesChanged && isOpen()) {
                            service.open(viewer, this); // title shows the page count
                            return;
                        }
                    }
                    if (isOpen()) {
                        refresh();
                    }
                }));
    }
}
