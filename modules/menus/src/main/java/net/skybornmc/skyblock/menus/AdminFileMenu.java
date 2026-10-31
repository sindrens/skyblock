package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.config.ConfigEdits;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.config.SettingInfo;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.NumberPickerMenu;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.menu.PickListMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One config file in {@code /admin}: every setting with its description, value, default and range. Booleans switch,
 * enums open a pick list, whole numbers use the number picker, text and decimals use chat input, lists open a list editor.
 * Every change goes through {@link ConfigEdits}: validated, saved, logged, applied live (or flagged "needs restart").
 */
final class AdminFileMenu extends PagedMenu<SettingInfo> {

    static final int DESCRIPTION_WIDTH = 34;

    private final Nav nav;
    private final ModuleConfig config;

    AdminFileMenu(Nav nav, ModuleConfig config) {
        this.nav = nav;
        this.config = config;
    }

    static boolean isChanged(SettingInfo info) {
        return !Objects.equals(ConfigManager.display(info.value()), ConfigManager.display(info.defaultValue()));
    }

    @Override
    protected List<SettingInfo> entries() {
        return nav.core.configs().describe(config);
    }

    @Override
    protected MenuButton entry(SettingInfo info) {
        boolean editable = ConfigEdits.canEdit(viewer, info.level());
        List<Component> lore = new ArrayList<>();
        for (String line : wrap(info.description(), DESCRIPTION_WIDTH)) {
            lore.add(nav.text(viewer, "admin.setting.description", Msg.p("text", line)));
        }
        lore.add(Component.empty());
        lore.add(nav.text(viewer, "admin.setting.value", Msg.p("value", shorten(ConfigManager.display(info.value())))));
        lore.add(nav.text(viewer, "admin.setting.default", Msg.p("value", shorten(ConfigManager.display(info.defaultValue())))));
        if (info.min() != Double.NEGATIVE_INFINITY || info.max() != Double.POSITIVE_INFINITY) {
            lore.add(nav.text(viewer, "admin.setting.range", Msg.p("min", number(info.min())), Msg.p("max", number(info.max()))));
        }
        if (info.type().isEnum()) {
            List<String> names = new ArrayList<>();
            for (Object constant : info.type().getEnumConstants()) {
                names.add(ConfigManager.display(constant));
            }
            lore.add(nav.text(viewer, "admin.setting.choices", Msg.p("choices", String.join(", ", names))));
        }
        if (info.restart()) {
            lore.addAll(nav.lines(viewer, "admin.setting.restart"));
        }
        if (info.level() == Setting.Level.OWNER) {
            lore.addAll(nav.lines(viewer, "admin.setting.owner"));
        }
        lore.addAll(nav.lines(viewer, editable ? actionKey(info) : "admin.setting.locked"));
        Material icon = icon(info);
        String nameKey = isChanged(info) ? "admin.setting.name-changed" : "admin.setting.name";
        return MenuButton.of(nav.item(icon, nav.text(viewer, nameKey, Msg.p("path", info.path())), lore), click -> {
            if (!editable) {
                nav.core.sounds().play(viewer, "error");
                nav.msg.send(viewer, "admin.no-permission");
                return;
            }
            edit(info);
        });
    }

    private static String actionKey(SettingInfo info) {
        Class<?> t = info.type();
        if (t == boolean.class || t == Boolean.class) {
            return "admin.setting.action-toggle";
        }
        if (t.isEnum()) {
            return "admin.setting.action-cycle";
        }
        if (List.class.isAssignableFrom(t)) {
            return "admin.setting.action-list";
        }
        return "admin.setting.action-edit";
    }

    private static Material icon(SettingInfo info) {
        Class<?> t = info.type();
        if (t == boolean.class || t == Boolean.class) {
            return Boolean.TRUE.equals(info.value()) ? Material.LIME_DYE : Material.RED_DYE;
        }
        if (t.isEnum()) {
            return Material.REPEATER;
        }
        if (List.class.isAssignableFrom(t)) {
            return Material.PAPER;
        }
        if (t == String.class) {
            return Material.OAK_SIGN;
        }
        return Material.GOLD_NUGGET;
    }

    private void edit(SettingInfo info) {
        Class<?> t = info.type();
        if (t == boolean.class || t == Boolean.class) {
            apply(info.path(), !Boolean.TRUE.equals(info.value()));
            refresh();
        } else if (t.isEnum()) {
            // GUI-4b/G9: a pick list of the choices instead of stepping forward one value per click.
            List<PickListMenu.Option<String>> options = new ArrayList<>();
            String current = ConfigManager.display(info.value());
            for (Object constant : t.getEnumConstants()) {
                String name = ConfigManager.display(constant);
                options.add(PickListMenu.Option.of(name, Material.REPEATER, nav.text(viewer, "admin.setting.choice", Msg.p("value", name)),
                        name.equals(current)));
            }
            openChild(new PickListMenu<>(List.of(Component.text(config.fileName()), Component.text(info.path())), options,
                    value -> apply(info.path(), value)));
        } else if (t == int.class || t == Integer.class || t == long.class || t == Long.class) {
            long max = t == int.class || t == Integer.class ? Integer.MAX_VALUE : Long.MAX_VALUE;
            long min = t == int.class || t == Integer.class ? Integer.MIN_VALUE : Long.MIN_VALUE;
            long lo = info.min() == Double.NEGATIVE_INFINITY ? min : (long) Math.max(min, Math.ceil(info.min()));
            long hi = info.max() == Double.POSITIVE_INFINITY ? max : (long) Math.min(max, Math.floor(info.max()));
            long current = ((Number) info.value()).longValue();
            openChild(new NumberPickerMenu(p -> service.title(p, Component.text(info.path())), lo, hi, current,
                    steps((long) Math.min(Long.MAX_VALUE, (double) hi - (double) lo)), (p, value) -> {
                        apply(info.path(), value);
                        service.open(p, this);
                    }));
        } else if (List.class.isAssignableFrom(t)) {
            openChild(new AdminListMenu(nav, config, info.path()));
        } else {
            String current = ConfigManager.display(info.value());
            service.prompt(viewer, Nav.TEXTS, "admin.prompt", 500, input -> {
                apply(info.path(), input.equalsIgnoreCase("default") ? ConfigManager.display(info.defaultValue()) : input);
                service.open(viewer, this);
            }, () -> service.open(viewer, this), Msg.p("path", info.path()), Msg.p("value", shorten(current)));
        }
    }

    /** Picker steps that fit the range: 1/10/100 for small ranges, bigger for large ones. */
    static long[] steps(long range) {
        if (range <= 100) {
            return new long[]{1, 5, 10};
        }
        if (range <= 10_000) {
            return new long[]{1, 10, 100};
        }
        if (range <= 10_000_000) {
            return new long[]{10, 1_000, 100_000};
        }
        return new long[]{1_000, 1_000_000, 1_000_000_000};
    }

    void apply(String path, Object value) {
        Player player = viewer;
        ConfigEdits.Result result = ConfigEdits.apply(nav.core, player, config, path, value);
        if (!result.ok()) {
            nav.msg.send(player, "admin.failed", Msg.p("path", path), Msg.p("reason", String.valueOf(result.error())));
            nav.core.sounds().play(player, "error");
            return;
        }
        nav.msg.send(player, result.restart() ? "admin.saved-restart" : "admin.saved", Msg.p("file", config.fileName()),
                Msg.p("path", path), Msg.p("old", shorten(result.oldValue())), Msg.p("new", shorten(result.newValue())));
        nav.core.sounds().play(player, "success");
    }

    static String shorten(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 60 ? value : value.substring(0, 57) + "...";
    }

    static String number(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** Word-wraps a description for lore lines. */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(nav.text(viewer, "admin.title"), Component.text(config.fileName()));
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "admin.file-info";
    }

    @Override
    public String helpTopic() {
        return "admin";
    }
}
