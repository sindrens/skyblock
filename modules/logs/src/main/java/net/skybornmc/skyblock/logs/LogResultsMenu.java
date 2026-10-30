package net.skybornmc.skyblock.logs;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;

/**
 * Paged, newest-first results of a log search. Each page is fetched from the database when it is shown (a loading
 * item fills the middle meanwhile). Entries: left-click teleports to the place (staff with permission) or prints the
 * full entry in chat; right-click always prints it in chat (hover = pretty details).
 */
final class LogResultsMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    static final int SLOT_FILTERS = 46;
    static final int SLOT_CHAT = 47;
    static final int SLOT_REFRESH = 51;
    static final int SLOT_EXPORT = 52;

    private final LogsModule module;
    private final LogFilter filter;
    private int page;
    private List<AuditEntry> rows;
    private long total = -1;
    private boolean failed;
    private int loading = -1;

    LogResultsMenu(LogsModule module, LogFilter filter) {
        this.module = module;
        this.filter = filter;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.render.text(viewer, "title.logs"), module.render.subject(viewer, filter));
    }

    @Override
    public String messageModule() {
        return LogsModule.ID;
    }

    @Override
    public String infoKey() {
        return "results.info";
    }

    @Override
    public String helpTopic() {
        return "logs";
    }

    private int pages() {
        return total <= 0 ? 1 : (int) ((total + LogsModule.MENU_PAGE - 1) / LogsModule.MENU_PAGE);
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_SUMMARY, service.item(Material.KNOWLEDGE_BOOK, module.render.text(viewer, "results.summary.name"),
                service.lines(viewer, LogsModule.ID, "results.summary.lore",
                        Msg.c("subject", module.render.subject(viewer, filter)),
                        Msg.c("categories", module.render.categories(viewer, filter)),
                        Msg.c("range", module.render.range(viewer, filter.range)),
                        Msg.p("total", total < 0 ? "..." : net.skybornmc.skyblock.common.util.Format.number(total)),
                        Msg.p("page", page + 1), Msg.p("pages", pages()))));
        set(SLOT_FILTERS, service.item(Material.HOPPER, viewer, LogsModule.ID, "results.filters"), click -> {
            if (parent() instanceof LogsHubMenu) {
                back();
            } else {
                module.openHub(viewer, filter.copy());
            }
        });
        set(SLOT_CHAT, service.item(Material.WRITABLE_BOOK, viewer, LogsModule.ID, "results.chat"), click -> {
            viewer.closeInventory();
            module.chatPage(viewer, filter, page);
        });
        set(SLOT_REFRESH, service.item(Material.CLOCK, viewer, LogsModule.ID, "results.refresh"), click -> {
            rows = null;
            total = -1;
            refresh();
        });
        if (module.canExport(viewer)) {
            set(SLOT_EXPORT, service.item(Material.MAP, viewer, LogsModule.ID, "results.export"), click -> {
                viewer.closeInventory();
                module.export(viewer, filter);
            });
        }

        if (rows == null) {
            set(PagedMenu.SLOT_EMPTY, failed
                    ? service.item(Material.RED_STAINED_GLASS, viewer, "error-item")
                    : service.item(Material.CLOCK, viewer, "loading"));
            if (!failed) {
                load();
            }
            return;
        }
        if (rows.isEmpty()) {
            set(PagedMenu.SLOT_EMPTY, service.item(Material.COBWEB, viewer, LogsModule.ID, "results.empty"));
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows.size() && i < PagedMenu.CONTENT_SLOTS.length; i++) {
            AuditEntry entry = rows.get(i);
            set(PagedMenu.CONTENT_SLOTS[i], module.render.item(viewer, entry, now), click -> {
                // spec 13.11: a chat line opens the punish menu with that line as evidence (plain click: Bedrock too)
                if (click.isLeft() && LogPunish.punishable(entry) && LogPunish.allowed(viewer)) {
                    viewer.closeInventory();
                    module.punishFrom(viewer, entry);
                    return;
                }
                if (click.isLeft() && entry.hasLocation() && module.canTeleport(viewer)) {
                    World world = Bukkit.getWorld(entry.world());
                    if (world == null) {
                        module.msg.send(viewer, "errors.world-unloaded", Msg.p("world", entry.world()));
                        module.sound(viewer, "error");
                        return;
                    }
                    viewer.closeInventory();
                    module.teleport(viewer, new Location(world, entry.x() + 0.5, entry.y(), entry.z() + 0.5));
                } else {
                    viewer.sendMessage(module.render.chatLine(viewer, entry, System.currentTimeMillis()));
                    module.sound(viewer, "click");
                }
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
        rows = null;
        service.sound(viewer, "page");
        refresh();
    }

    private void load() {
        int requested = page;
        if (loading == requested) {
            return;
        }
        loading = requested;
        module.fetch(viewer, filter, requested, LogsModule.MENU_PAGE).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(module.core.plugin(), () -> {
                    if (loading == requested) {
                        loading = -1;
                    }
                    if (requested != page) {
                        return;
                    }
                    if (error != null) {
                        module.core.plugin().getLogger().warning("Log search failed: " + error.getMessage());
                        failed = true;
                    } else {
                        rows = result.rows();
                        total = result.total();
                        failed = false;
                    }
                    if (isOpen()) {
                        refresh();
                    }
                }));
    }
}
