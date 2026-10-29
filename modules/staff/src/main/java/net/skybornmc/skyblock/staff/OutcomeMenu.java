package net.skybornmc.skyblock.staff;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

/** Close a report: choose the outcome (the reporter is told the result). */
final class OutcomeMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15};
    private static final Material[] ICONS = {Material.NETHERITE_AXE, Material.GOLD_INGOT, Material.GLASS_BOTTLE,
            Material.BARRIER, Material.PAPER};

    private final ReportService reports;
    private final Report report;
    private final Runnable after;

    OutcomeMenu(ReportService reports, Report report, Runnable after) {
        this.reports = reports;
        this.report = report;
        this.after = after;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, StaffFeature.MODULE, "menu.outcome.title", Msg.p("id", report.id())));
    }

    @Override
    public String messageModule() {
        return StaffFeature.MODULE;
    }

    @Override
    protected void build() {
        frame();
        Report.Outcome[] outcomes = Report.Outcome.values();
        for (int i = 0; i < outcomes.length && i < SLOTS.length; i++) {
            Report.Outcome outcome = outcomes[i];
            set(SLOTS[i], service.item(ICONS[i], viewer, StaffFeature.MODULE, "outcome." + outcome.key(),
                    Msg.p("player", report.targetName()), Msg.p("reporter", report.reporterName())), click -> {
                click.player().closeInventory();
                reports.close(click.player(), report.id(), outcome, after);
            });
        }
    }
}
