package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;

import java.util.List;

/** Admin: pick a set to give to the inspected player, as a wardrobe entry or as a Set Token (confirm first). */
final class StaffPickMenu extends PagedMenu<SetDef> {

    private final WardrobeModule module;
    private final StaffMenu staffMenu;
    private final boolean token;

    StaffPickMenu(WardrobeModule module, StaffMenu staffMenu, boolean token) {
        this.module = module;
        this.staffMenu = staffMenu;
        this.token = token;
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    public String helpTopic() {
        return "staff";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.staff.title", Msg.p("player", staffMenu.targetName)),
                module.menus().text(viewer, token ? "menu.staff.pick-token" : "menu.staff.pick-set"));
    }

    @Override
    protected List<SetDef> entries() {
        return List.copyOf(module.registry().all());
    }

    @Override
    protected MenuButton entry(SetDef def) {
        String key = token ? "confirm.give-token" : "confirm.give-set";
        return MenuButton.of(service.item(module.items().icon(def, ArmorPiece.CHEST), module.core.messages().parse(viewer, WardrobeText.colored(def)),
                module.menus().lines(viewer, token ? "menu.staff.pick-token-click" : "menu.staff.pick-set-click")),
                click -> openChild(new ConfirmMenu(v -> module.menus().text(v, key + ".crumb"), null,
                        v -> module.menus().lines(v, key + ".details", Msg.p("set", def.name()), Msg.p("player", staffMenu.targetName)),
                        p -> {
                            if (token) {
                                module.staff().giveToken(p, staffMenu, def);
                            } else {
                                module.staff().giveSet(p, staffMenu, def);
                            }
                        }, p -> {
                })));
    }
}
