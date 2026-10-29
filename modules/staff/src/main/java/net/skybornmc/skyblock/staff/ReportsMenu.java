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

/** {@code /reports}: open / claimed / closed tabs (bottom row), or all reports about one player. */
final class ReportsMenu extends PagedMenu<Report> {

    private static final int TAB_OPEN = 46;
    private static final int TAB_CLAIMED = 47;
    private static final int TAB_CLOSED = 51;

    private final ReportService reports;
    private final Report.Status status;
    private final List<Report> list;
    private final String about;

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
        if (about != null) {
            return;
        }
        tab(TAB_OPEN, Report.Status.OPEN, Material.PAPER);
        tab(TAB_CLAIMED, Report.Status.CLAIMED, Material.WRITABLE_BOOK);
        tab(TAB_CLOSED, Report.Status.CLOSED, Material.BOOK);
    }

    private void tab(int slot, Report.Status tab, Material icon) {
        String key = "menu.reports.tab." + tab.db() + (tab == status ? "-selected" : "");
        ItemStack item = service.item(icon, viewer, StaffFeature.MODULE, key);
        if (tab == status) {
            item.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        set(slot, item, click -> {
            if (tab != status) {
                reports.openList(click.player(), tab);
            }
        });
    }

    private void reload(Player staff) {
        if (about == null) {
            reports.openList(staff, status);
        }
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
