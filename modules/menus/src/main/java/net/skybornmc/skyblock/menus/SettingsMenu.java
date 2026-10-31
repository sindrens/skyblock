package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PickListLayout;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * {@code /settings} (GUI-4c: fewer rows): one tab per category in row 0 (Chat, Social, Display, Sounds, Gameplay;
 * GUI_DESIGN §1.5, in place) and only the selected category's switches below, centred in rows 1-4 (up to 28, so no
 * second page). Was one row per category over 8 rows and 2 pages. Categories without a built switch get no tab
 * (GUI_DESIGN §3: hidden, not grey). Switches say On/Off in their name (common {@code MenuService#toggle}).
 */
final class SettingsMenu extends Menu {

    private final Nav nav;
    private SettingCategory tab;

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
        java.util.Map<SettingCategory, List<SettingToggle>> visible = new java.util.EnumMap<>(SettingCategory.class);
        java.util.Map<SettingCategory, Integer> counts = new java.util.EnumMap<>(SettingCategory.class);
        for (SettingCategory category : SettingCategory.values()) {
            // GUI-4 (GUI_DESIGN §3): switches of features that aren't built yet are hidden, not grey.
            List<SettingToggle> toggles = nav.settings.visible(viewer, category).stream().filter(t -> !t.comingSoon()).toList();
            visible.put(category, toggles);
            counts.put(category, toggles.size());
        }
        List<SettingCategory> tabs = tabs(counts);
        if (tabs.isEmpty()) {
            setEmpty(Nav.TEXTS, "settings.empty");
            return;
        }
        tab = pick(tabs, tab);
        int[] slots = TopLayout.tabSlots(tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            SettingCategory category = tabs.get(i);
            String key = "settings.categories." + category.name().toLowerCase(Locale.ROOT);
            List<Component> lore = new java.util.ArrayList<>(nav.lines(viewer, key + ".lore"));
            lore.add(nav.text(viewer, "settings.tab-count", Msg.number("count", counts.get(category))));
            set(slots[i], service.tab(viewer, new org.bukkit.inventory.ItemStack(icon(category)), nav.text(viewer, key + ".name"), lore,
                    category == tab, () -> tab = category));
        }
        List<SettingToggle> toggles = visible.get(tab);
        int shown = Math.min(toggles.size(), PickListLayout.PER_PAGE);
        for (int i = 0; i < shown; i++) {
            setToggle(PickListLayout.slot(i, shown), toggles.get(i));
        }
    }

    /** The categories that get a tab: those with at least one built switch, in category order. Pure (unit-tested). */
    static List<SettingCategory> tabs(java.util.Map<SettingCategory, Integer> counts) {
        List<SettingCategory> out = new java.util.ArrayList<>();
        for (SettingCategory category : SettingCategory.values()) {
            if (counts.getOrDefault(category, 0) > 0) {
                out.add(category);
            }
        }
        return out;
    }

    /** The selected tab: the current one while it still has switches, else the first. Pure (unit-tested). */
    static SettingCategory pick(List<SettingCategory> tabs, SettingCategory current) {
        return current != null && tabs.contains(current) ? current : tabs.getFirst();
    }

    private void setToggle(int slot, SettingToggle toggle) {
        if (toggle.comingSoon()) {
            set(slot, nav.comingSoon(viewer, Material.GRAY_DYE,
                    service.text(viewer, toggle.textModule(), toggle.textKey() + ".name"),
                    service.lines(viewer, toggle.textModule(), toggle.textKey() + ".lore")));
            return;
        }
        if (toggle.isChoice()) {
            setChoice(slot, toggle);
            return;
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

    /**
     * VIS: a multiple-choice setting is one button (the setting's icon) listing every choice with the current one marked;
     * a click opens a small pick list (GUI-4/G9), one click picks. No typing, so it works the same on Bedrock.
     */
    private void setChoice(int slot, SettingToggle toggle) {
        String current = nav.settings.choice(viewer, toggle.id());
        List<Component> lore = new java.util.ArrayList<>(service.lines(viewer, toggle.textModule(), toggle.textKey() + ".lore"));
        lore.add(Component.empty());
        for (String choice : toggle.choices()) {
            Component name = service.text(viewer, toggle.textModule(), toggle.textKey() + ".choices." + choice);
            lore.add(nav.text(viewer, choice.equals(current) ? "settings.choice-selected" : "settings.choice-other", Msg.c("choice", name)));
        }
        lore.addAll(nav.lines(viewer, "settings.choice-action"));
        set(slot, nav.item(toggle.icon(), service.text(viewer, toggle.textModule(), toggle.textKey() + ".name"), lore), click -> {
            List<net.skybornmc.skyblock.common.menu.PickListMenu.Option<String>> options = new java.util.ArrayList<>();
            for (String choice : toggle.choices()) {
                options.add(new net.skybornmc.skyblock.common.menu.PickListMenu.Option<>(choice, new org.bukkit.inventory.ItemStack(toggle.icon()),
                        service.text(viewer, toggle.textModule(), toggle.textKey() + ".choices." + choice), List.of(), choice.equals(current)));
            }
            openChild(new net.skybornmc.skyblock.common.menu.PickListMenu<>(List.of(nav.text(viewer, "settings.title"),
                    service.text(viewer, toggle.textModule(), toggle.textKey() + ".name")), options, choice -> {
                if (!nav.settings.setChoice(viewer, toggle.id(), choice)) {
                    nav.core.sounds().play(viewer, "error");
                }
            }));
        });
    }

    private static Material icon(SettingCategory category) {
        return switch (category) {
            case CHAT -> Material.OAK_SIGN;
            case SOCIAL -> Material.PLAYER_HEAD;
            case DISPLAY -> Material.PAINTING;
            case SOUNDS -> Material.NOTE_BLOCK;
            case GAMEPLAY -> Material.IRON_PICKAXE;
        };
    }
}
