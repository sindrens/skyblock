package net.skybornmc.skyblock.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.MenuService;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /reports}: open / claimed / closed tabs, or all reports about one player.
 *
 * <p>GUI-4c: the tabs sit in row 0 (3, 4, 5; GUI_DESIGN §1.5) and switch in place (the list loads, then the same window
 * redraws, so Back keeps working); Refresh (51) reloads the shown list in place, and so does coming back after a
 * report changed.</p>
 */
final class ReportsMenu extends PagedMenu<Report> {

    static final int TAB_OPEN = 3;
    static final int TAB_CLAIMED = 4;
    static final int TAB_CLOSED = 5;
    static final int SLOT_REFRESH = 51;

    private final ReportService reports;
    private Report.Status status;
    private List<Report> list;
    private final String about;
    private boolean loading;

    /**
     * @param status shown tab, or {@code null} when listing reports about one player ({@code about})
     */
    ReportsMenu(ReportService reports, Report.Status status, List<Report> list, String about) {
        this.reports = reports;
        this.status = status;
        this.list = list;
        this.about = about;
    }

    @Override
    public String messageModule() {
        return StaffFeature.MODULE;
    }

    @Override
    public String infoKey() {
        return "menu.reports.info";
    }

    @Override
    public String helpTopic() {
        return "staff";
    }

    @Override
    protected String emptyKey() {
        return "menu.reports.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        Component base = service.text(viewer, StaffFeature.MODULE, "menu.reports.title");
        Component sub = about != null
                ? service.text(viewer, StaffFeature.MODULE, "menu.reports.about", Msg.p("player", about))
                : service.text(viewer, StaffFeature.MODULE, "menu.reports.tab." + status.db() + ".name");
        return List.of(base, sub);
    }

    @Override
    protected List<Report> entries() {
        return list;
    }

    @Override
    protected MenuButton entry(Report r) {
        return MenuButton.of(item(service, viewer, r, reports), click ->
                reports.openReport(click.player(), r.id(), () -> reload(click.player())));
    }

    @Override
    protected void decorate() {
        set(SLOT_REFRESH, service.item(Material.CLOCK, viewer, StaffFeature.MODULE, "menu.reports.refresh"), click -> reload(click.player()));
        if (about != null) {
            return;
        }
        tab(TAB_OPEN, Report.Status.OPEN, Material.PAPER);
        tab(TAB_CLAIMED, Report.Status.CLAIMED, Material.WRITABLE_BOOK);
        tab(TAB_CLOSED, Report.Status.CLOSED, Material.BOOK);
    }

    private void tab(int slot, Report.Status tab, Material icon) {
        String key = "menu.reports.tab." + tab.db();
        set(slot, service.tab(viewer, new ItemStack(icon), service.text(viewer, StaffFeature.MODULE, key + ".name"),
                service.lines(viewer, StaffFeature.MODULE, key + ".desc", Msg.p("max", reports.closedShown())), tab == status,
                () -> show(viewer, tab)));
    }

    /** Loads {@code tab}'s list and redraws this window in place (no new menu, Back stays). */
    private void show(Player staff, Report.Status tab) {
        if (loading) {
            return;
        }
        loading = true;
        reports.loadList(tab).whenComplete((loaded, error) -> reports.onMain(() -> {
            loading = false;
            if (error != null) {
                service.sound(staff, "error");
                return;
            }
            status = tab;
            list = loaded;
            page(0);
            if (isOpen()) {
                redraw();
            }
        }));
    }

    /** Refresh / back after a change: the shown list again, in place when this screen is open. */
    private void reload(Player staff) {
        if (about != null) {
            reports.loadAbout(about).whenComplete((loaded, error) -> reports.onMain(() -> {
                if (error == null) {
                    list = loaded;
                }
                if (isOpen()) {
                    redraw();
                } else {
                    service.open(staff, this);
                }
            }));
            return;
        }
        if (isOpen()) {
            loadingSame(staff, status);
        } else {
            reports.loadList(status).whenComplete((loaded, error) -> reports.onMain(() -> {
                if (error == null) {
                    list = loaded;
                }
                service.open(staff, this);
            }));
        }
    }

    private void loadingSame(Player staff, Report.Status shown) {
        reports.loadList(shown).whenComplete((loaded, error) -> reports.onMain(() -> {
            status = shown;
            if (error == null) {
                list = loaded;
            } else {
                service.sound(staff, "error");
            }
            if (isOpen()) {
                redraw();
            }
        }));
    }

    /** The list/detail item of one report (shared with {@link ReportMenu}). */
    static ItemStack item(MenuService service, Player viewer, Report r, ReportService reports) {
        Material icon = switch (r.status()) {
            case OPEN -> Material.PAPER;
            case CLAIMED -> Material.WRITABLE_BOOK;
            case CLOSED -> Material.BOOK;
        };
        String where = r.world() == null ? "-" : r.world() + " " + r.x() + " " + r.y() + " " + r.z();
        TagResolver[] p = {
                Msg.p("id", r.id()), Msg.p("player", r.targetName()), Msg.p("reporter", r.reporterName()),
                Msg.p("reason", r.reason()), Msg.p("details", r.details().isEmpty() ? "-" : r.details()),
                Msg.p("ago", ReportService.ago(r.createdAt())), Msg.p("where", where),
                Msg.p("claimer", r.claimedByName() == null ? "-" : r.claimedByName()),
                Msg.p("closer", r.closedByName() == null ? "-" : r.closedByName()),
                Msg.p("outcome", r.outcome() == null ? "-" : outcome(viewer, r, reports))};
        return service.item(icon, viewer, StaffFeature.MODULE, "menu.reports.entry." + r.status().db(), p);
    }

    private static String outcome(Player viewer, Report r, ReportService reports) {
        for (Report.Outcome o : Report.Outcome.values()) {
            if (o.name().equals(r.outcome())) {
                return reports.outcomeName(viewer, o);
            }
        }
        return r.outcome();
    }
}
