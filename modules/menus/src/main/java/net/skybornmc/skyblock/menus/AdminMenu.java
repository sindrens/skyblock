package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.config.ConfigEdits;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.SettingInfo;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /admin} (M1.12): every loaded config file. Generated from the typed config classes, so new modules and new
 * settings appear here automatically. Files with secrets (database.yml) are never shown.
 */
final class AdminMenu extends PagedMenu<ModuleConfig> {

    private final Nav nav;

    AdminMenu(Nav nav) {
        this.nav = nav;
    }

    @Override
    protected List<ModuleConfig> entries() {
        List<ModuleConfig> list = new ArrayList<>();
        for (ModuleConfig config : nav.core.configs().loaded().values()) {
            if (!ConfigEdits.HIDDEN_FILES.contains(config.fileName())) {
                list.add(config);
            }
        }
        list.sort(Comparator.comparing(ModuleConfig::fileName));
        return list;
    }

    @Override
    protected MenuButton entry(ModuleConfig config) {
        List<SettingInfo> infos = nav.core.configs().describe(config);
        long editable = infos.stream().filter(i -> ConfigEdits.canEdit(viewer, i.level())).count();
        long changed = infos.stream().filter(AdminFileMenu::isChanged).count();
        int problems = nav.core.configs().problems().getOrDefault(config.fileName(), List.of()).size();
        List<Component> lore = new ArrayList<>(nav.lines(viewer, "admin.file.lore",
                Msg.number("settings", infos.size()), Msg.number("editable", editable), Msg.number("changed", changed)));
        if (problems > 0) {
            lore.addAll(nav.lines(viewer, "admin.file.problems", Msg.number("count", problems)));
        }
        lore.addAll(nav.lines(viewer, "hub.action"));
        Material icon = problems > 0 ? Material.REDSTONE_TORCH : changed > 0 ? Material.WRITABLE_BOOK : Material.BOOK;
        return MenuButton.of(nav.item(icon, nav.text(viewer, "admin.file.name", Msg.p("file", config.fileName())), lore),
                click -> openChild(new AdminFileMenu(nav, config)));
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(nav.text(viewer, "admin.title"));
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "admin.info";
    }

    @Override
    public String helpTopic() {
        return "admin";
    }
}
