package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.progression.TaskType;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;

import java.util.List;
import java.util.UUID;

/**
 * One chapter of the challenge book (PHASE-5 M5.3): every task of a tier with its shared island progress and rewards.
 * Deliver tasks open the hand-in screen with one click; the rest are display only. Refreshes every 2 s.
 */
final class TaskTierMenu extends PagedMenu<TaskDef> {

    private final TaskMenus menus;
    private final UUID islandId;
    private final int tier;

    TaskTierMenu(TaskMenus menus, UUID islandId, int tier) {
        this.menus = menus;
        this.islandId = islandId;
        this.tier = tier;
    }

    @Override
    public String messageModule() {
        return ProgressionModule.ID;
    }

    @Override
    public String infoKey() {
        return "tasks.tier.info";
    }

    @Override
    public String helpTopic() {
        return "island-level";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected String emptyKey() {
        return "tasks.tier.empty";
    }

    @Override
    protected List<TaskDef> entries() {
        return menus.tasks().catalog().inTier(tier);
    }

    @Override
    protected MenuButton entry(TaskDef def) {
        boolean deliver = def.info().type() == TaskType.DELIVER && !menus.tasks().state().done(islandId, def.id());
        var item = menus.taskItem(service, viewer, islandId, def, true);
        if (!deliver) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> openChild(new TaskDeliverMenu(menus, islandId, def.id())));
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, ProgressionModule.ID, "tasks.book.crumb"),
                service.text(viewer, ProgressionModule.ID, "tasks.tier.crumb", Msg.p("tier", tier)));
    }
}
