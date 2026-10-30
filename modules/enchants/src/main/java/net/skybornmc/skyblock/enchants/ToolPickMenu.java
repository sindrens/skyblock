package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;

import java.util.ArrayList;
import java.util.List;

/** Step 1 of apply / upgrade / remove: every custom tool in your inventory and hotbar as a button (no dragging). */
final class ToolPickMenu extends PagedMenu<EnchantOps.HeldTool> {

    private final EnchantsModule module;
    private final SlotMenu.Mode mode;

    ToolPickMenu(EnchantsModule module, SlotMenu.Mode mode) {
        this.module = module;
        this.mode = mode;
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.tools.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    protected String emptyKey() {
        return "menu.tools.empty";
    }

    @Override
    protected List<EnchantOps.HeldTool> entries() {
        return module.ops().tools(viewer);
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.title"), module.menus().text(viewer, "menu.mode." + mode.key()));
    }

    @Override
    protected MenuButton entry(EnchantOps.HeldTool tool) {
        List<Component> extra = new ArrayList<>(module.menus().lines(viewer, "menu.tools.entry",
                Msg.p("level", tool.info().level()), Msg.p("used", tool.info().usedSlots()), Msg.p("slots", tool.info().slots())));
        extra.addAll(module.menus().lines(viewer, "menu.tools.click." + mode.key()));
        return MenuButton.of(EnchantMenus.withExtraLore(EnchantMenus.toolCopy(tool.stack()), extra),
                click -> openChild(new SlotMenu(module, mode, tool.info().id())));
    }
}
