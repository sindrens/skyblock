package net.skybornmc.skyblock.islandtools;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /is tools}: the island's two tools (owner 4 Nov 2026); each tool's menu holds its storage.
 * <pre>
 *  . . . . . . . . .
 *  . . . H . P . . .      12 hoe, 14 pickaxe
 *  . . . . . . . . .
 *  nav bar
 * </pre>
 */
final class ToolsMenu extends Menu {

    static final int SLOT_HOE = 10;
    static final int SLOT_PICKAXE = 12;
    static final int SLOT_ROD = 14; // owner 6 Nov 2026: /fish
    static final int SLOT_SWORD = 16; // owner 6 Nov 2026: /pve

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
        UUID island = m.island(viewer);
        if (island == null) {
            return;
        }
        for (Kind kind : Kind.values()) {
            ToolStore.Prog prog = m.view(viewer, kind);
            if (prog == null) {
                continue;
            }
            ToolRules.Level lv = m.level(prog);
            String stars = ToolRules.stars(prog.prestige(), m.config().maxStars);
            List<Component> lore = new ArrayList<>(service.lines(viewer, IslandToolsModule.ID, "menu.tool-lore",
                    Msg.number("level", lv.level()), Msg.number("max", m.config().maxLevel),
                    Msg.number("percent", (long) Math.floor(lv.progress() * 100)), Msg.p("stars", stars.isEmpty() ? "-" : stars),
                    Msg.p("shards", m.raw("shards.name." + kind.id()))));
            set(kind == Kind.HOE ? SLOT_HOE : kind == Kind.ROD ? SLOT_ROD : kind == Kind.SWORD ? SLOT_SWORD : SLOT_PICKAXE, service.item(m.items().create(kind, island, m.profile(viewer)),
                    m.msg().component(viewer, "menu.tool-name", Msg.p("tool", m.raw("item.name." + kind.id())), Msg.p("stars", stars)), lore),
                    click -> openChild(new ToolMenu(m, kind)));
        }
    }

    private Component text(String key) {
        return service.text(viewer, IslandToolsModule.ID, key);
    }
}
