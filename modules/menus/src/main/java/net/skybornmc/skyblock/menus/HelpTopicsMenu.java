package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /help} → All topics: the guide categories (the old /help start screen, plan 4 Nov C §1: it stays as the short
 * "All topics" page behind the goals of {@link HelpMenu}). Categories of features that aren't built yet are hidden.
 */
final class HelpTopicsMenu extends Menu {

    /** Inner slots used for categories without an explicit slot in help.yml, in this order. */

    private final Nav nav;

    HelpTopicsMenu(Nav nav) {
        this.nav = nav;
    }

    /**
     * Owner 3 Nov 2026 ("compact, symmetrical"): only open categories, in help.yml order, centred in even rows
     * ({@link net.skybornmc.skyblock.common.menu.Centered#split}: 10 = 5 + 5), and only as many rows as they need.
     * The {@code slot} of a category is no longer used. Coming-soon categories are hidden.
     */
    private List<HelpBook.Category> shown() {
        List<HelpBook.Category> out = new ArrayList<>();
        for (HelpBook.Category c : nav.help.categories()) {
            if (c.status() != HelpBook.Status.COMING_SOON) {
                out.add(c);
            }
        }
        return out.size() > 28 ? out.subList(0, 28) : out;
    }

    /** Pure: rows per line of tiles for {@code n} categories (at most 4 lines of 7). */
    static int[] lines(int n) {
        return net.skybornmc.skyblock.common.menu.Centered.split(Math.min(28, Math.max(0, n)));
    }

    @Override
    public int rows() {
        return Math.max(3, lines(shown().size()).length + 2);
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "help.title", "help.all-topics.crumb");
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
        navBar();
        List<HelpBook.Category> list = shown();
        int[] slots = net.skybornmc.skyblock.common.menu.Centered.grid(lines(list.size()));
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            place(slots[i], list.get(i));
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
