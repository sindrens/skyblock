package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;

import java.util.ArrayList;
import java.util.List;

/** One help category: its topics as a paged list. */
final class HelpCategoryMenu extends PagedMenu<HelpBook.Topic> {

    private final Nav nav;
    private final HelpBook.Category category;

    HelpCategoryMenu(Nav nav, HelpBook.Category category) {
        this.nav = nav;
        this.category = category;
    }

    @Override
    protected List<HelpBook.Topic> entries() {
        List<HelpBook.Topic> list = new ArrayList<>();
        for (String id : category.topics()) {
            nav.help.topic(id).ifPresent(list::add);
        }
        return list;
    }

    @Override
    protected MenuButton entry(HelpBook.Topic topic) {
        List<Component> lore = new ArrayList<>(nav.parse(viewer, topic.summary()));
        lore.add(Component.empty());
        lore.addAll(nav.lines(viewer, "help.topic-pages", net.skybornmc.skyblock.common.text.Msg.number("count", topic.pages().size())));
        lore.addAll(nav.lines(viewer, "help.read-action"));
        return MenuButton.of(nav.item(topic.icon(), nav.parse(viewer, topic.name()), lore),
                click -> openChild(new HelpTopicMenu(nav, topic)));
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(nav.text(viewer, "help.title"), nav.crumb(viewer, category.name()));
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    protected String emptyKey() {
        return "help.empty";
    }

    @Override
    public String infoKey() {
        return "help.info";
    }

    @Override
    public String helpTopic() {
        return "help";
    }
}
