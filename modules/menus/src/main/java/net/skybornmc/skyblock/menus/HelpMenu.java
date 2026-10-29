package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** {@code /help}: the category screen. Categories of features that aren't built yet are shown locked. */
final class HelpMenu extends Menu {

    /** Inner slots used for categories without an explicit slot in help.yml, in this order. */
    private static final int[] AUTO_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 38, 39, 40, 41, 42};

    private final Nav nav;

    HelpMenu(Nav nav) {
        this.nav = nav;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "help.title");
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "help.info";
    }

    @Override
    public String helpTopic() {
        return "help";
    }

    @Override
    protected void build() {
        frame();
        Set<Integer> used = new HashSet<>();
        List<HelpBook.Category> pending = new ArrayList<>();
        for (HelpBook.Category category : nav.help.categories()) {
            if (category.slot() >= 10 && category.slot() <= 43 && category.slot() % 9 != 0 && category.slot() % 9 != 8
                    && used.add(category.slot())) {
                place(category.slot(), category);
            } else {
                pending.add(category);
            }
        }
        int next = 0;
        for (HelpBook.Category category : pending) {
            while (next < AUTO_SLOTS.length && used.contains(AUTO_SLOTS[next])) {
                next++;
            }
            if (next >= AUTO_SLOTS.length) {
                break;
            }
            used.add(AUTO_SLOTS[next]);
            place(AUTO_SLOTS[next++], category);
        }
    }

    private void place(int slot, HelpBook.Category category) {
        Component name = nav.parse(viewer, category.name());
        List<Component> description = nav.parse(viewer, category.lore());
        if (category.status() == HelpBook.Status.COMING_SOON) {
            set(slot, nav.comingSoon(viewer, category.icon(), name, description));
            return;
        }
        List<Component> lore = new ArrayList<>(description);
        if (!category.allCommands()) {
            lore.add(Component.empty());
            lore.addAll(nav.lines(viewer, "help.category-topics", net.skybornmc.skyblock.common.text.Msg.number("count", category.topics().size())));
        }
        lore.addAll(nav.lines(viewer, "help.open-action"));
        set(slot, nav.item(category.icon(), name, lore), click -> {
            if (category.allCommands()) {
                openChild(new HelpCommandsMenu(nav, List.of(), name));
            } else if (category.topics().size() == 1) {
                nav.help.topic(category.topics().getFirst()).ifPresent(t -> openChild(new HelpTopicMenu(nav, t)));
            } else {
                openChild(new HelpCategoryMenu(nav, category));
            }
        });
    }

    /** Fallback item for empty states etc. */
    static Material icon() {
        return Material.KNOWLEDGE_BOOK;
    }
}
