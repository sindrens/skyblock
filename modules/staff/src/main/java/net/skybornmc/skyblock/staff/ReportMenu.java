package net.skybornmc.skyblock.staff;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** One report: claim/unclaim, teleport, inspect, punish, close with an outcome. Bedrock-friendly (plain clicks only). */
final class ReportMenu extends Menu {

    private final ReportService reports;
    private final Report report;
    private final Runnable afterChange;

    ReportMenu(ReportService reports, Report report, Runnable afterChange) {
        this.reports = reports;
        this.report = report;
        this.afterChange = afterChange;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, StaffFeature.MODULE, "menu.reports.title"),
                service.text(viewer, StaffFeature.MODULE, "menu.report-view.title", Msg.p("id", report.id())));
    }

    @Override
    public String messageModule() {
        return StaffFeature.MODULE;
    }

    @Override
    public String infoKey() {
        return "menu.report-view.info";
    }

    @Override
    public String helpTopic() {
        return "staff";
    }

    @Override
    protected void build() {
        frame();
        set(13, ReportsMenu.item(service, viewer, report, reports));
        boolean closed = report.status() == Report.Status.CLOSED;
        boolean mine = viewer.getUniqueId().equals(report.claimedBy());
        Player target = Bukkit.getPlayer(report.target());
        if (!closed) {
            if (report.status() == Report.Status.CLAIMED && mine) {
                set(29, service.item(Material.ORANGE_DYE, viewer, StaffFeature.MODULE, "menu.report-view.unclaim"),
                        click -> act(() -> reports.claim(click.player(), report.id(), false)));
            } else {
                set(29, service.item(Material.LIME_DYE, viewer, StaffFeature.MODULE, "menu.report-view.claim",
                                Msg.p("claimer", report.claimedByName() == null ? "-" : report.claimedByName())),
                        click -> act(() -> reports.claim(click.player(), report.id(), true)));
            }
        }
        set(30, service.item(target != null ? Material.ENDER_PEARL : Material.GRAY_DYE, viewer, StaffFeature.MODULE,
                target != null ? "menu.report-view.teleport" : "menu.report-view.offline", Msg.p("player", report.targetName())),
                click -> {
                    if (Bukkit.getPlayer(report.target()) != null) {
                        click.player().closeInventory();
                        reports.dispatchFor(click.player(), "tp " + report.targetName());
                    }
                });
        set(31, service.item(Material.SPYGLASS, viewer, StaffFeature.MODULE, "menu.report-view.inspect", Msg.p("player", report.targetName())),
                click -> {
                    click.player().closeInventory();
                    reports.dispatchFor(click.player(), "inspect " + report.targetName());
                });
        set(32, service.item(Material.BREEZE_ROD, viewer, StaffFeature.MODULE, "menu.report-view.punish", Msg.p("player", report.targetName())),
                click -> {
                    click.player().closeInventory();
                    reports.dispatchFor(click.player(), "punish " + report.targetName());
                });
        if (!closed) {
            set(33, service.item(Material.RED_CONCRETE, viewer, StaffFeature.MODULE, "menu.report-view.close"),
                    click -> openChild(new OutcomeMenu(reports, report, afterChange)));
        }
    }

    private void act(Runnable action) {
        viewer.closeInventory();
        action.run();
    }
}
