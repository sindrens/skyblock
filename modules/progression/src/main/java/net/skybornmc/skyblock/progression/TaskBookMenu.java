package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The challenge book (PHASE-5 M5.3, O6): one chapter per task tier with its done count; click a chapter to see its tasks.
 * One plain click each (Bedrock-safe). Opened from {@code /is level}.
 * <pre>
 *  R O B B 📖 B B O R      4 header: tasks done
 *  B 1 2 3 4 5 6 7 B      10-16, 19-25, 28-34, 37-43: chapters (tiers)
 *  ...
 *  ← O B ? ✘ H B O R      45 back, 48 info, 49 close, 50 help
 * </pre>
 */
final class TaskBookMenu extends Menu {

    static final int SLOT_HEADER = 4;

    private final TaskMenus menus;
    private final UUID islandId;

    TaskBookMenu(TaskMenus menus, UUID islandId) {
        this.menus = menus;
        this.islandId = islandId;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("level-menu.crumb-island"), text("tasks.book.crumb"));
    }

    @Override
    public String messageModule() {
        return ProgressionModule.ID;
    }

    @Override
    public String infoKey() {
        return "tasks.book.info";
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
        TaskCatalog catalog = menus.tasks().catalog();
        TaskProgress state = menus.tasks().state();
        TagResolver[] head = {Msg.number("done", state.doneCount(islandId)), Msg.number("total", catalog.size())};
        set(SLOT_HEADER, MenuButton.display(service.item(new ItemStack(Material.WRITABLE_BOOK), text("tasks.book.header.name", head),
                lines(catalog.size() == 0 ? "tasks.book.header.lore-empty" : "tasks.book.header.lore", head))));
        List<Integer> tiers = catalog.tiers();
        for (int i = 0; i < tiers.size() && i < PagedMenu.CONTENT_SLOTS.length; i++) {
            set(PagedMenu.CONTENT_SLOTS[i], chapter(tiers.get(i)));
        }
    }

    private MenuButton chapter(int tier) {
        TaskProgress state = menus.tasks().state();
        List<TaskDef> list = menus.tasks().catalog().inTier(tier);
        int done = 0;
        TaskDef next = null;
        for (TaskDef def : list) {
            if (state.done(islandId, def.id())) {
                done++;
            } else if (next == null) {
                next = def;
            }
        }
        boolean all = done == list.size();
        TagResolver[] p = {Msg.p("tier", tier), Msg.number("done", done), Msg.number("total", list.size())};
        List<Component> lore = new ArrayList<>(lines(all ? "tasks.book.chapter.lore-done" : "tasks.book.chapter.lore", p));
        if (next != null) {
            lore.add(menus.tasks().taskLine(viewer, islandId, next));
        }
        lore.add(Component.empty());
        lore.addAll(lines("tasks.book.chapter.click"));
        Material icon = all ? Material.ENCHANTED_BOOK : done > 0 ? Material.BOOK : Material.PAPER;
        ItemStack item = service.item(new ItemStack(icon), text(all ? "tasks.book.chapter.name-done" : "tasks.book.chapter.name", p), lore);
        return MenuButton.of(item, click -> menus.openTier(viewer, islandId, tier, this));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ProgressionModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ProgressionModule.ID, key, placeholders);
    }
}
