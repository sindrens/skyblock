package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * {@code /settings}: one row per category (label on the left, up to 6 switches next to it). Switches are lime/red
 * dyes from the menu framework; settings whose feature isn't built yet show the locked "Coming soon" look.
 */
final class SettingsMenu extends Menu {

    static final int MAX_PER_ROW = 6;
    private final Nav nav;

    SettingsMenu(Nav nav) {
        this.nav = nav;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "settings.title");
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "settings.info";
    }

    @Override
    public String helpTopic() {
        return "settings";
    }

    @Override
    protected void build() {
        frame();
        SettingCategory[] categories = SettingCategory.values();
        for (int row = 0; row < Math.min(4, categories.length); row++) {
            SettingCategory category = categories[row];
            int base = (row + 1) * 9;
            String key = "settings.categories." + category.name().toLowerCase(Locale.ROOT);
            set(base + 1, nav.item(icon(category), nav.text(viewer, key + ".name"), nav.lines(viewer, key + ".lore")));
            List<SettingToggle> toggles = nav.settings.visible(viewer, category);
            if (toggles.isEmpty()) {
                set(base + 2, nav.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, nav.text(viewer, "settings.empty.name"),
                        nav.lines(viewer, "settings.empty.lore")));
            }
            for (int i = 0; i < Math.min(MAX_PER_ROW, toggles.size()); i++) {
                SettingToggle toggle = toggles.get(i);
                int slot = base + 2 + i;
                if (toggle.comingSoon()) {
                    set(slot, nav.comingSoon(viewer, Material.GRAY_DYE,
                            service.text(viewer, toggle.textModule(), toggle.textKey() + ".name"),
                            service.lines(viewer, toggle.textModule(), toggle.textKey() + ".lore")));
                    continue;
                }
                boolean state = nav.settings.get(viewer, toggle.id());
                set(slot, service.toggle(viewer, toggle.textModule(), toggle.textKey(), state, value -> {
                    if (nav.settings.set(viewer, toggle.id(), value)) {
                        nav.core.sounds().play(viewer, value ? "success" : "click");
                    } else {
                        nav.core.sounds().play(viewer, "error");
                    }
                }));
            }
        }
    }

    private static Material icon(SettingCategory category) {
        return switch (category) {
            case CHAT -> Material.OAK_SIGN;
            case SOCIAL -> Material.PLAYER_HEAD;
            case DISPLAY -> Material.PAINTING;
            case SOUNDS -> Material.NOTE_BLOCK;
        };
    }
}
