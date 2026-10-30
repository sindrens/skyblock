package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * {@code /settings}: one row per category (label on the left, up to 6 switches next to it; more continue on the next
 * row, more than 4 rows on the next page). Switches are lime/red
 * dyes from the menu framework; settings whose feature isn't built yet show the locked "Coming soon" look.
 */
final class SettingsMenu extends Menu {

    static final int MAX_PER_ROW = 6;
    static final int ROWS_PER_PAGE = 4;
    private final Nav nav;
    private int page;

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
        // QUICK1: a category with more than 6 switches continues on the next row, and more than 4 rows continue on the
        // next page (the frame's arrows), so no switch is ever hidden.
        java.util.Map<SettingCategory, List<SettingToggle>> visible = new java.util.EnumMap<>(SettingCategory.class);
        java.util.Map<SettingCategory, Integer> counts = new java.util.EnumMap<>(SettingCategory.class);
        for (SettingCategory category : SettingCategory.values()) {
            List<SettingToggle> toggles = nav.settings.visible(viewer, category);
            visible.put(category, toggles);
            counts.put(category, toggles.size());
        }
        List<Row> rows = rows(counts, MAX_PER_ROW);
        int pages = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int r = 0; r < ROWS_PER_PAGE; r++) {
            int index = page * ROWS_PER_PAGE + r;
            if (index >= rows.size()) {
                break;
            }
            Row row = rows.get(index);
            int base = (r + 1) * 9;
            String key = "settings.categories." + row.category().name().toLowerCase(Locale.ROOT);
            set(base + 1, nav.item(icon(row.category()), nav.text(viewer, key + ".name"), nav.lines(viewer, key + ".lore")));
            List<SettingToggle> toggles = visible.get(row.category());
            if (toggles.isEmpty()) {
                set(base + 2, nav.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, nav.text(viewer, "settings.empty.name"),
                        nav.lines(viewer, "settings.empty.lore")));
            }
            for (int i = row.from(); i < row.to(); i++) {
                setToggle(base + 2 + (i - row.from()), toggles.get(i));
            }
        }
        if (page > 0) {
            set(SLOT_PREVIOUS, service.navItem("previous", Material.ARROW, viewer, "frame.previous",
                    Msg.p("page", page), Msg.p("pages", pages)), click -> turn(-1));
        }
        if (page < pages - 1) {
            set(SLOT_NEXT, service.navItem("next", Material.ARROW, viewer, "frame.next",
                    Msg.p("page", page + 2), Msg.p("pages", pages)), click -> turn(1));
        }
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
     * a click moves to the next choice. One click, no typing, so it works the same on Bedrock.
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
            if (nav.settings.setChoice(viewer, toggle.id(), toggle.nextChoice(current))) {
                nav.core.sounds().play(viewer, "click");
            } else {
                nav.core.sounds().play(viewer, "error");
            }
            refresh();
        });
    }

    private void turn(int delta) {
        page += delta;
        service.open(viewer, this);
    }

    /** One menu row: a category and the switches {@code [from, to)} of it shown on that row. */
    record Row(SettingCategory category, int from, int to) {
    }

    /**
     * The rows of the menu in category order: every category gets at least one row (an empty one shows "Nothing here
     * yet"), a category with more than {@code perRow} switches continues on further rows. Pure (unit-tested).
     */
    static List<Row> rows(java.util.Map<SettingCategory, Integer> counts, int perRow) {
        List<Row> out = new java.util.ArrayList<>();
        for (SettingCategory category : SettingCategory.values()) {
            int count = Math.max(0, counts.getOrDefault(category, 0));
            if (count == 0) {
                out.add(new Row(category, 0, 0));
                continue;
            }
            for (int from = 0; from < count; from += perRow) {
                out.add(new Row(category, from, Math.min(count, from + perRow)));
            }
        }
        return out;
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
