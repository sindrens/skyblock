package net.skybornmc.skyblock.logs;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
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
    static final int SLOT_EXPORT = 6; // GUI-1: 52 is Previous page now

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
        // GUI-4c: the page like every paged menu (UI_STYLE §5).
        Component suffix = pages() > 1 ? service.text(viewer, "title.page", Msg.p("page", page + 1), Msg.p("pages", pages())) : null;
        return service.title(viewer, List.of(module.render.text(viewer, "title.logs"), module.render.subject(viewer, filter)), suffix);
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
            if (failed) {
                setError(PagedMenu.SLOT_EMPTY, () -> {
                    failed = false;
                    refresh();
                });
            } else {
                setLoading(PagedMenu.SLOT_EMPTY);
                load();
            }
            return;
        }
        if (rows.isEmpty()) {
            setEmpty(LogsModule.ID, "results.empty");
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows.size() && i < PagedMenu.CONTENT_SLOTS.length; i++) {
            AuditEntry entry = rows.get(i);
            // GUI-4c: a plain click (left or right, Bedrock tap) opens the entry: full details and the actions behind a
            // second, named click. Right-click still prints the line in chat (Java shortcut, in the ? guide).
            set(PagedMenu.CONTENT_SLOTS[i], MenuButton.of(module.render.item(viewer, entry, now), click -> openChild(new LogEntryMenu(module, entry)))
                    .withRight(click -> {
                        viewer.sendMessage(module.render.chatLine(viewer, entry, System.currentTimeMillis()));
                        module.sound(viewer, "click");
                    }));
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
        redraw();
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
                        redraw(); // the title's page count follows the total
                    }
                }));
    }
}
