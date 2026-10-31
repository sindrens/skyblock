package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.config.ConfigEdits;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.SettingInfo;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** {@code /admin}: a list setting (e.g. allowed domains). Click an entry to remove it (confirm); + adds one. */
final class AdminListMenu extends PagedMenu<String> {

    static final int SLOT_ADD = 51;

    private final Nav nav;
    private final ModuleConfig config;
    private final String path;

    AdminListMenu(Nav nav, ModuleConfig config, String path) {
        this.nav = nav;
        this.config = config;
        this.path = path;
    }

    private List<String> current() {
        return ConfigEdits.info(nav.core, config, path).map(SettingInfo::value)
                .filter(List.class::isInstance).map(v -> {
                    List<String> out = new ArrayList<>();
                    ((List<?>) v).forEach(o -> out.add(String.valueOf(o)));
                    return out;
                }).orElseGet(ArrayList::new);
    }

    @Override
    protected List<String> entries() {
        return current();
    }

    @Override
    protected MenuButton entry(String value) {
        return MenuButton.of(nav.item(Material.PAPER, nav.text(viewer, "admin.list.entry", Msg.p("value", AdminFileMenu.shorten(value))),
                nav.lines(viewer, "admin.list.entry-lore")), click -> openChild(new ConfirmMenu(
                p -> nav.text(p, "admin.list.remove-crumb"),
                nav.item(Material.PAPER, Component.text(AdminFileMenu.shorten(value)), List.of()),
                p -> nav.lines(p, "admin.list.remove-details", Msg.p("value", AdminFileMenu.shorten(value))),
                p -> {
                    List<String> list = current();
                    list.remove(value);
                    save(list);
                    nav.core.menus().open(p, this);
                },
                p -> { })));
    }

    @Override
    protected void decorate() {
        set(SLOT_ADD, nav.item(Material.LIME_DYE, nav.text(viewer, "admin.list.add.name"), nav.lines(viewer, "admin.list.add.lore")),
                click -> service.input(viewer, Nav.TEXTS, "admin.list.prompt", 300, null, true, input -> {
                    List<String> list = current();
                    list.add(input.strip());
                    save(list);
                    service.open(viewer, this);
                }, () -> service.open(viewer, this), Msg.p("path", path), Msg.p("max", 300)));
    }

    private void save(List<String> list) {
        ConfigEdits.Result result = ConfigEdits.apply(nav.core, viewer, config, path, list);
        if (result.ok()) {
            nav.msg.send(viewer, result.restart() ? "admin.saved-restart" : "admin.saved", Msg.p("file", config.fileName()),
                    Msg.p("path", path), Msg.p("old", AdminFileMenu.shorten(result.oldValue())),
                    Msg.p("new", AdminFileMenu.shorten(result.newValue())));
            nav.core.sounds().play(viewer, "success");
        } else {
            nav.msg.send(viewer, "admin.failed", Msg.p("path", path), Msg.p("reason", String.valueOf(result.error())));
            nav.core.sounds().play(viewer, "error");
        }
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(nav.text(viewer, "admin.title"), Component.text(config.fileName()), Component.text(path));
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    protected String emptyKey() {
        return "admin.list.empty";
    }

    @Override
    public String infoKey() {
        return "admin.list.info";
    }

    @Override
    public String helpTopic() {
        return "admin";
    }
}
