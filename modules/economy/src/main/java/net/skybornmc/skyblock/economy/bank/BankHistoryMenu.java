package net.skybornmc.skyblock.economy.bank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * A bank's history (M3.3): every deposit, withdrawal, fee and interest payout, newest first, with who did it (island
 * banks: "visible to the leader in Island → Bank → History"; the bank-history role permission). One database page at a
 * time.
 */
final class BankHistoryMenu extends Menu {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
            .withZone(Format.zone());

    private final BankFeature bank;
    private final AccountRef account;
    private final Component crumb;
    private int page;
    private List<BankStore.Entry> rows;
    private long total = -1;
    private boolean failed;
    private int generation;
    private int loading = -1;

    BankHistoryMenu(BankFeature bank, AccountRef account, Component crumb) {
        this.bank = bank;
        this.account = account;
        this.crumb = crumb;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        Component suffix = pages() > 1 ? service.text(viewer, "title.page", Msg.p("page", page + 1), Msg.p("pages", pages())) : null;
        return service.title(viewer, List.of(service.text(viewer, EconomyModule.ID, "bank.menu.title"), crumb), suffix);
    }

    @Override
    public String messageModule() {
        return EconomyModule.ID;
    }

    @Override
    public String infoKey() {
        return "bank.history.info";
    }

    @Override
    public String helpTopic() {
        return "bank";
    }

    private int pageSize() {
        return Math.min(PagedMenu.CONTENT_SLOTS.length, bank.config.historyPageSize);
    }

    private int pages() {
        return total <= 0 ? 1 : (int) ((total + pageSize() - 1) / pageSize());
    }

    @Override
    protected void build() {
        frame();
        if (rows == null) {
            set(PagedMenu.SLOT_EMPTY, failed ? service.item(Material.RED_STAINED_GLASS, viewer, "error-item")
                    : service.item(Material.CLOCK, viewer, "loading"));
            if (!failed) {
                load();
            }
            return;
        }
        if (rows.isEmpty()) {
            set(PagedMenu.SLOT_EMPTY, service.item(Material.COBWEB, viewer, EconomyModule.ID, "bank.history.empty"));
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

    private org.bukkit.inventory.ItemStack entry(BankStore.Entry e, long now) {
        String reasonKey = "reasons." + e.reason();
        Component reason = bank.msg.has(reasonKey) ? service.text(viewer, EconomyModule.ID, reasonKey)
                : Component.text(e.reason().replace('_', ' '));
        TagResolver[] p = {bank.amountTag(viewer, "amount", e.amount()), Msg.c("reason", reason),
                Msg.p("who", e.actorName() == null ? "-" : e.actorName()),
                e.balanceAfter() == null ? Msg.p("balance", "-") : bank.amountTag(viewer, "balance", e.balanceAfter()),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(0, now - e.time())))),
                Msg.p("date", DATE.format(Instant.ofEpochMilli(e.time()))), Msg.p("id", e.id())};
        String key = e.incoming() ? "bank.history.in" : "bank.history.out";
        return service.item(e.incoming() ? Material.LIME_DYE : Material.RED_DYE, service.text(viewer, EconomyModule.ID, key + ".name", p),
                service.lines(viewer, EconomyModule.ID, key + ".lore", p));
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
        bank.ops().store().history(account, page * size, size).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(bank.core.plugin(), () -> {
                    if (loading == requested) {
                        loading = -1;
                    }
                    if (requested != generation) {
                        return;
                    }
                    if (error != null) {
                        bank.core.plugin().getLogger().warning("Bank: history failed: " + error.getMessage());
                        failed = true;
                    } else {
                        rows = result.rows();
                        int before = pages();
                        total = result.total();
                        if (before != pages() && isOpen()) {
                            service.open(viewer, this);
                            return;
                        }
                    }
                    if (isOpen()) {
                        refresh();
                    }
                }));
    }
}
