package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /preport} (spec 14.8), Bedrock-safe: 6 rows, plain clicks only, every number in item lore. Header, six number
 * tiles, top reasons, reoffence and (with {@code skyblock.staff.punish.report.staff}) the staff ranking; a button prints
 * the chat summary; the arrows open the month before / after (stored months, made on demand when missing).
 */
final class ReportMenu extends Menu {

    private static final int SLOT_HEADER = 4;
    private static final int[] TILES = {19, 20, 21, 22, 23, 24};
    private static final int SLOT_REASONS = 29;
    private static final int SLOT_REOFFENCE = 31;
    private static final int SLOT_STAFF = 33;
    private static final int SLOT_CHAT = 40;

    private final ReportService reports;
    private final MonthlyReport.Report report;
    private final boolean staffSection;

    ReportMenu(ReportService reports, MonthlyReport.Report report, boolean staffSection) {
        this.reports = reports;
        this.report = report;
        this.staffSection = staffSection;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, PunishMenus.MODULE, "report.menu.title"),
                Component.text(reports.monthName(viewer, report.yearMonth())));
    }

    @Override
    public String messageModule() {
        return PunishMenus.MODULE;
    }

    @Override
    public String infoKey() {
        return "report.menu.info";
    }

    @Override
    public String helpTopic() {
        return "staff";
    }

    @Override
    protected void build() {
        frame();
        TagResolver[] parts = reports.parts(viewer, report);
        set(SLOT_HEADER, service.item(Material.WRITABLE_BOOK, viewer, PunishMenus.MODULE,
                report.partial() ? "report.menu.header-partial" : "report.menu.header", parts));
        String[][] tiles = {{"IRON_SWORD", "totals"}, {"LIME_DYE", "reversed"}, {"BOOK", "appeals"},
                {"PAPER", report.reports() == null ? "reports-none" : "reports"}, {"BELL", "requests"}, {"SHIELD", "safety"}};
        for (int i = 0; i < tiles.length; i++) {
            set(TILES[i], service.item(Material.valueOf(tiles[i][0]), viewer, PunishMenus.MODULE, "report.menu." + tiles[i][1], parts));
        }
        set(SLOT_REASONS, list(Material.NAME_TAG, "report.menu.reasons", reasonLines()));
        set(SLOT_REOFFENCE, list(Material.REPEATER, "report.menu.reoffence", reoffenceLines(parts), parts));
        if (staffSection) {
            set(SLOT_STAFF, list(Material.GOLDEN_HELMET, "report.menu.staff", staffLines()));
        } else {
            set(SLOT_STAFF, service.item(Material.GRAY_DYE, viewer, PunishMenus.MODULE, "report.menu.staff-locked"));
        }
        set(SLOT_CHAT, service.item(Material.OAK_SIGN, viewer, PunishMenus.MODULE, "report.menu.chat"), click -> {
            viewer.closeInventory();
            reports.summary(viewer, report, staffSection);
        });
        YearMonth month = report.yearMonth();
        YearMonth before = month.minusMonths(1);
        set(SLOT_PREVIOUS, service.item(Material.ARROW, viewer, PunishMenus.MODULE, "report.menu.previous",
                Msg.p("month", reports.monthName(viewer, before))), click -> reports.open(viewer, before));
        YearMonth after = month.plusMonths(1);
        if (!after.isAfter(reports.currentMonth())) {
            set(SLOT_NEXT, service.item(Material.ARROW, viewer, PunishMenus.MODULE, "report.menu.next",
                    Msg.p("month", reports.monthName(viewer, after))), click -> reports.open(viewer, after));
        }
    }

    private org.bukkit.inventory.ItemStack list(Material icon, String key, List<Component> lines, TagResolver... parts) {
        Component name = service.text(viewer, PunishMenus.MODULE, key + ".name", parts);
        List<Component> lore = new ArrayList<>(service.lines(viewer, PunishMenus.MODULE, key + ".lore", parts));
        if (lines.isEmpty()) {
            lore.add(service.text(viewer, PunishMenus.MODULE, "report.menu.empty"));
        } else {
            lore.addAll(lines);
        }
        return service.item(icon, name, lore);
    }

    private List<Component> reasonLines() {
        List<Component> out = new ArrayList<>();
        int place = 1;
        for (MonthlyReport.Count c : report.topReasons()) {
            out.add(service.text(viewer, PunishMenus.MODULE, "report.menu.reason-line", Msg.number("place", place++),
                    Msg.p("reason", reports.reasonName(viewer, c.id())), Msg.number("count", c.count())));
        }
        return out;
    }

    private List<Component> reoffenceLines(TagResolver[] parts) {
        List<Component> out = new ArrayList<>();
        List<MonthlyReport.Reoffence> list = report.reoffence();
        int max = Math.min(reports.currentSettings().reoffenceLines(), list.size());
        for (MonthlyReport.Reoffence o : list.subList(0, max)) {
            out.add(service.text(viewer, PunishMenus.MODULE, "report.menu.reoffence-line",
                    Msg.p("reason", reports.reasonName(viewer, o.reason())), Msg.number("step", o.step()),
                    Msg.number("percent", o.percent()), Msg.number("again", o.again()), Msg.number("players", o.players())));
        }
        return out;
    }

    private List<Component> staffLines() {
        List<Component> out = new ArrayList<>();
        List<MonthlyReport.StaffLine> list = report.staff();
        int max = Math.min(reports.currentSettings().staffLines(), list.size());
        int place = 1;
        for (MonthlyReport.StaffLine l : list.subList(0, max)) {
            out.add(service.text(viewer, PunishMenus.MODULE, l.trainee() ? "report.menu.staff-line-trainee" : "report.menu.staff-line",
                    reports.staffParts(place++, l)));
        }
        return out;
    }
}
