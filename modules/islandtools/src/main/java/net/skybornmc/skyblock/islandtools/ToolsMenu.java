package net.skybornmc.skyblock.islandtools;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /is tools}: the four island tools (tier and upgrades done) and the hoe storage. One click opens a tool.
 * <pre>
 *  . . . . I . . . .      4 info
 *  . . P . A . H . S      11 pickaxe, 13 axe, 15 hoe, 17... (centred: 10 12 14 16)
 *  . . . . B . . . .      22 hoe storage
 *  nav bar
 * </pre>
 */
final class ToolsMenu extends Menu {

    static final int[] TOOL_SLOTS = {10, 12, 14, 16};
    static final int SLOT_STORAGE = 22;

    private final IslandToolsModule m;

    ToolsMenu(IslandToolsModule m) {
        this.m = m;
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("menu.crumb-island"), text("menu.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandToolsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "tools";
    }

    @Override
    protected void build() {
        navBar();
        Kind[] kinds = Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            Kind kind = kinds[i];
            ToolStore.Rec r = m.rec(viewer, kind);
            if (r == null) {
                continue;
            }
            ItemStack icon = m.items().create(r);
            int done = r.levels().values().stream().mapToInt(Integer::intValue).sum();
            int max = m.upgrades().of(kind).stream().mapToInt(Upgrades.Def::max).sum();
            List<Component> lore = new ArrayList<>(service.lines(viewer, IslandToolsModule.ID, "menu.tool-lore",
                    Msg.p("tier", m.raw("tier." + ToolRules.TIERS.get(r.tier()))), Msg.number("done", done), Msg.number("max", max)));
            if (r.stored()) {
                lore.addAll(service.lines(viewer, IslandToolsModule.ID, "menu.tool-stored"));
            }
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "menu.click-open"));
            set(TOOL_SLOTS[i], service.item(icon, text("item.name." + kind.id()), lore), click -> openChild(new ToolMenu(m, kind)));
        }
        UUID profile = m.profile(viewer);
        long cap = m.storage().capacity(viewer);
        long used = m.storage().used(profile);
        set(SLOT_STORAGE, service.item(Material.BARREL, viewer, IslandToolsModule.ID, "menu.storage",
                Msg.p("used", Format.number(used)), Msg.p("cap", Format.number(cap))), click -> openChild(new StorageMenu(m)));
    }

    private Component text(String key) {
        return service.text(viewer, IslandToolsModule.ID, key);
    }
}
