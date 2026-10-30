package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.progression.TaskStorage;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Hand-in screen of a deliver task (PHASE-5 M5.3): two big buttons, from the inventory or from the island storage
 * (STORE2; "coming soon" until the storage registers {@link TaskStorage}). One plain click each (Bedrock-safe); a button
 * is locked while its hand-in is saving, so a double click can't start two.
 * <pre>
 *  R O B B ◆ B B O R      4 the task
 *  B . . . . . . . B
 *  B . ⇩ . . . ▣ . B      20 from inventory, 24 from island storage
 *  B . . . . . . . B
 *  ← O B ? ✘ H B O R
 * </pre>
 */
final class TaskDeliverMenu extends Menu {

    static final int SLOT_TASK = 4;
    static final int SLOT_INVENTORY = 20;
    static final int SLOT_STORAGE = 24;

    private final TaskMenus menus;
    private final UUID islandId;
    private final String taskId;
    private boolean busy;

    TaskDeliverMenu(TaskMenus menus, UUID islandId, String taskId) {
        this.menus = menus;
        this.islandId = islandId;
        this.taskId = taskId;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("tasks.book.crumb"), text("tasks.deliver.crumb"));
    }

    @Override
    public String messageModule() {
        return ProgressionModule.ID;
    }

    @Override
    public String infoKey() {
        return "tasks.deliver.info";
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
        IslandService islands = menus.tasks().core().services().optional(IslandService.class).orElse(null);
        if (islands == null || islands.island(islandId).isEmpty()) {
            viewer.closeInventory();
            return;
        }
        refresh();
    }

    @Override
    protected void build() {
        frame();
        TaskDef def = menus.tasks().catalog().get(taskId).orElse(null);
        if (def == null) {
            return;
        }
        set(SLOT_TASK, MenuButton.display(menus.taskItem(service, viewer, islandId, def, false)));
        TaskProgress state = menus.tasks().state();
        long room = state.room(islandId, def);
        boolean done = state.done(islandId, def.id());
        Component item = menus.tasks().objectName(viewer, def.info());
        long have = menus.tasks().countItems(viewer, def.info().target());
        TagResolver[] p = {Msg.c("item", item), Msg.number("have", have), Msg.number("room", room)};
        if (done || room <= 0) {
            set(SLOT_INVENTORY, MenuButton.display(service.item(new ItemStack(Material.LIME_DYE), text("tasks.deliver.full.name"),
                    lines("tasks.deliver.full.lore"))));
            return;
        }
        if (busy) {
            set(SLOT_INVENTORY, MenuButton.display(service.item(new ItemStack(Material.CLOCK), text("tasks.deliver.busy.name"),
                    lines("tasks.deliver.busy.lore"))));
        } else if (have <= 0) {
            set(SLOT_INVENTORY, MenuButton.display(service.item(new ItemStack(Material.GRAY_DYE), text("tasks.deliver.inventory.name", p),
                    lines("tasks.deliver.inventory.lore-none", p))));
        } else {
            set(SLOT_INVENTORY, MenuButton.of(service.item(new ItemStack(Material.HOPPER), text("tasks.deliver.inventory.name", p),
                    lines("tasks.deliver.inventory.lore", p)), click -> {
                busy = true;
                refresh();
                menus.tasks().deliverFromInventory(viewer, taskId, this::done);
            }));
        }
        TaskStorage storage = menus.tasks().storageSource();
        if (storage == null) {
            set(SLOT_STORAGE, MenuButton.display(service.item(new ItemStack(Material.GRAY_DYE), text("tasks.deliver.storage.name", p),
                    lines("tasks.deliver.storage.lore-soon", p))));
            return;
        }
        long stored = storage.count(islandId, def.info().target());
        TagResolver[] s = {Msg.c("item", item), Msg.number("have", stored), Msg.number("room", room)};
        if (busy || stored <= 0) {
            set(SLOT_STORAGE, MenuButton.display(service.item(new ItemStack(Material.GRAY_DYE), text("tasks.deliver.storage.name", s),
                    lines(busy ? "tasks.deliver.busy.lore" : "tasks.deliver.storage.lore-none", s))));
        } else {
            set(SLOT_STORAGE, MenuButton.of(service.item(new ItemStack(Material.BARREL), text("tasks.deliver.storage.name", s),
                    lines("tasks.deliver.storage.lore", s)), click -> {
                busy = true;
                refresh();
                menus.tasks().deliverFromStorage(viewer, taskId, this::done);
            }));
        }
    }

    private void done() {
        busy = false;
        refresh();
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ProgressionModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ProgressionModule.ID, key, placeholders);
    }
}
