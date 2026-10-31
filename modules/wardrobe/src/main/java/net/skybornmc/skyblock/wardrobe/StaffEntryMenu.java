package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Material;

import java.util.List;

/**
 * Admin actions on one entry of the inspected player's wardrobe (WD25, no new command): set its level (buttons for the
 * usual test levels) or remove it. Every action confirms first and is audited.
 * <pre>
 *  4  the entry     10-16 set level 0 · 1 · 10 · 25 · 40 · 49 · max     26 remove (22 is Close)
 * </pre>
 */
final class StaffEntryMenu extends Menu {

    private final WardrobeModule module;
    private final StaffMenu staffMenu;
    private final WardrobeStore.Entry entry;

    StaffEntryMenu(WardrobeModule module, StaffMenu staffMenu, WardrobeStore.Entry entry) {
        this.module = module;
        this.staffMenu = staffMenu;
        this.entry = entry;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.menus().text(viewer, "menu.staff.title", Msg.p("player", staffMenu.targetName)),
                Component.text(module.text().entryName(module.registry(), entry)));
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    protected void build() {
        frame();
        WardrobeMenus m = module.menus();
        String name = module.text().entryName(module.registry(), entry);
        var look = module.registry().get(entry.look()).or(() -> module.registry().get(entry.setId()));
        set(4, service.item(look.map(d -> module.items().icon(d, ArmorPiece.CHEST)).orElse(new org.bukkit.inventory.ItemStack(Material.LEATHER_CHESTPLATE)),
                module.core.messages().parse(viewer, module.text().entryColored(module.registry(), entry)),
                m.lines(viewer, "menu.staff.entry-head", Msg.p("level", entry.level()))));
        int max = module.config().maxLevel;
        List<Integer> levels = levelChoices(max);
        int slot = 10;
        for (int level : levels) {
            int target = level;
            set(slot++, service.item(level == entry.level() ? Material.LIME_DYE : Material.EXPERIENCE_BOTTLE, viewer, WardrobeModule.ID,
                    "menu.staff.set-level", Msg.p("level", level)), click -> openChild(new ConfirmMenu(v -> m.text(v, "confirm.set-level.crumb"), null,
                    v -> m.lines(v, "confirm.set-level.details", Msg.p("set", name), Msg.p("player", staffMenu.targetName), Msg.p("level", target)),
                    p -> module.staff().setLevel(p, staffMenu, entry, target), p -> {
            })));
        }
        set(26, service.item(Material.LAVA_BUCKET, viewer, WardrobeModule.ID, "menu.staff.remove"), click -> openChild(new ConfirmMenu(
                v -> m.text(v, "confirm.remove.crumb"), null,
                v -> m.lines(v, "confirm.remove.details", Msg.p("set", name), Msg.p("player", staffMenu.targetName)),
                p -> module.staff().remove(p, staffMenu, entry), p -> {
        })));
    }

    /**
     * The "Set to Lv n" buttons: 0, 1, 10, 25, 40, max-1 and max, each once and in order (max 40 used to show
     * "Set to Lv 40" twice, out of order). Pure, unit-tested.
     */
    static List<Integer> levelChoices(int max) {
        int top = Math.max(0, max);
        java.util.TreeSet<Integer> out = new java.util.TreeSet<>();
        for (int level : new int[]{0, 1, 10, 25, 40, top - 1, top}) {
            out.add(Math.max(0, Math.min(top, level)));
        }
        return List.copyOf(out);
    }
}
