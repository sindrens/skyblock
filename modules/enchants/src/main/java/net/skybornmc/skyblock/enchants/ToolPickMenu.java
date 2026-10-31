package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 1 of apply / upgrade / remove: every custom tool in your inventory and hotbar as a button (no dragging). GUI-4b:
 * skipped when you carry exactly one tool, and a tool with only one slot for the mode skips the slot screen
 * ({@link SlotMenu#next}).
 */
final class ToolPickMenu extends PagedMenu<EnchantOps.HeldTool> {

    private final EnchantsModule module;
    private final SlotMenu.Mode mode;

    ToolPickMenu(EnchantsModule module, SlotMenu.Mode mode) {
        this.module = module;
        this.mode = mode;
    }

    /** Opens the tool picker from {@code from}, or the next step right away when the player carries exactly one tool. */
    static void open(Menu from, EnchantsModule module, SlotMenu.Mode mode) {
        List<EnchantOps.HeldTool> tools = module.ops().tools(from.viewer());
        from.openChild(tools.size() == 1 ? SlotMenu.next(module, mode, tools.getFirst().info()) : new ToolPickMenu(module, mode));
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
        boolean direct = SlotMenu.onlyChoice(SlotMenu.states(module, tool.info()), mode).isPresent();
        extra.addAll(module.menus().lines(viewer, (direct ? "menu.tools.click-direct." : "menu.tools.click.") + mode.key()));
        return MenuButton.of(EnchantMenus.withExtraLore(EnchantMenus.toolCopy(tool.stack()), extra),
                click -> openChild(SlotMenu.next(module, mode, tool.info())));
    }
}
