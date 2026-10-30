package net.skybornmc.skyblock.crates;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The summary screen of Open 10 / Open all (no animation, PHASE-10): equal rewards merged into one item with how often
 * they came, top-band rewards first. Back returns to where the open was clicked.
 */
final class ResultMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39,
            40, 41, 42, 43};

    private final CratesModule m;
    private final OpenPlan.Plan plan;

    ResultMenu(CratesModule module, OpenPlan.Plan plan) {
        this.m = module;
        this.plan = plan;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, CratesModule.ID, "menu.title"),
                service.text(viewer, CratesModule.ID, "result.title", Msg.number("count", plan.grants().size())));
    }

    @Override
    public String messageModule() {
        return CratesModule.ID;
    }

    @Override
    public String infoKey() {
        return "result.info";
    }

    @Override
    public String helpTopic() {
        return "crates";
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_SUMMARY, service.item(m.icon(plan.crate()), m.mini(viewer, m.crateMini(plan.crate())),
                service.lines(viewer, CratesModule.ID, "result.summary", Msg.number("count", plan.grants().size()),
                        Msg.number("top", plan.topCount()), Msg.number("pity", plan.pityAfter()),
                        Msg.number("max", plan.crate().pity()))));
        List<CrateOpener.Line> lines = CrateOpener.lines(plan);
        for (int i = 0; i < lines.size() && i < SLOTS.length; i++) {
            CrateOpener.Line line = lines.get(i);
            Component name = line.name(viewer, m.opener(), plan.crate());
            Material icon = line.first().fallback() ? Material.PRISMARINE_CRYSTALS : m.icon(line.first().reward());
            set(SLOTS[i], service.item(new ItemStack(icon), name, service.lines(viewer, CratesModule.ID,
                    line.top() ? "result.line-top" : "result.line", Msg.number("times", line.times()))));
        }
        if (lines.size() > SLOTS.length) {
            set(44, service.item(Material.PAPER, viewer, CratesModule.ID, "result.more", Msg.number("more", lines.size() - SLOTS.length)));
        }
    }
}
