package net.skybornmc.skyblock.economy.admin;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The anomaly alerts (M3.8), newest first, open ones or all; a click opens the evidence. One database page at a time. */
final class AlertsMenu extends WatchMenu {

    static final int SLOT_FILTER = 46;

    private boolean all;
    private int page;
    private WatchStore.AlertPage data;
    private Map<UUID, String> names = Map.of();

    AlertsMenu(EconomyWatch watch) {
        super(watch);
    }

    @Override
    public Component title() {
        return titleWith("watch.alerts-menu.title");
    }

    @Override
    public String infoKey() {
        return "watch.alerts-menu.info";
    }

    private int pages() {
        int size = PagedMenu.CONTENT_SLOTS.length;
        return data == null || data.total() <= 0 ? 1 : (int) ((data.total() + size - 1) / size);
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_FILTER, service.item(Material.HOPPER, viewer, EconomyModule.ID, "watch.alerts-menu.filter",
                Msg.c("filter", service.text(viewer, EconomyModule.ID, "watch.alerts-menu.filters." + (all ? "all" : "open")))),
                click -> {
                    click();
                    all = !all;
                    page = 0;
                    reload();
                });
        if (data == null) {
            showLoading();
            if (!failed) {
                int size = PagedMenu.CONTENT_SLOTS.length;
                boolean openOnly = !all;
                int offset = page * size;
                load(watch.store().async(c -> WatchStore.alerts(c, openOnly, offset, size)).thenCompose(p -> {
                    List<UUID> keys = new ArrayList<>();
                    p.alerts().forEach(a -> {
                        keys.add(a.subject());
                        keys.add(a.other());
                    });
                    return watch.names(keys).thenApply(n -> {
                        names = n;
                        return p;
                    });
                }), p -> data = p);
            }
            return;
        }
        if (data.alerts().isEmpty()) {
            set(PagedMenu.SLOT_EMPTY, service.item(Material.LIME_DYE, viewer, EconomyModule.ID, "watch.alerts-menu.empty"));
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < data.alerts().size() && i < PagedMenu.CONTENT_SLOTS.length; i++) {
            WatchAlert alert = data.alerts().get(i);
            set(PagedMenu.CONTENT_SLOTS[i], entry(alert, now), click -> {
                click();
                openChild(new AlertMenu(watch, alert));
            });
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

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages() - 1, page + delta));
        service.sound(viewer, "page");
        reload();
    }

    private void reload() {
        data = null;
        invalidate();
        refresh();
    }

    static Material icon(WatchAlert alert) {
        return switch (alert.state()) {
            case REVIEWED -> Material.LIME_DYE;
            case DISMISSED -> Material.GRAY_DYE;
            case OPEN -> alert.severity() == AlertDraft.Severity.HIGH ? Material.RED_DYE : Material.ORANGE_DYE;
        };
    }

    private ItemStack entry(WatchAlert alert, long now) {
        return service.item(icon(alert), viewer, EconomyModule.ID, "watch.alerts-menu.entry",
                Msg.c("severity", watch.severity(viewer, alert)), Msg.c("rule", watch.ruleName(viewer, alert)),
                Msg.p("id", alert.id()), Msg.c("details", watch.details(viewer, alert, names)),
                Msg.c("state", service.text(viewer, EconomyModule.ID, "watch.state." + alert.state().key())),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(0, now - alert.createdAt())))),
                Msg.p("date", EconomyWatch.date(alert.createdAt())));
    }
}
