package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * One help topic: header (slot 4), the pages as items in the middle row, and the actions below:
 * Show in chat · Take me there · Commands.
 */
final class HelpTopicMenu extends Menu {

    static final int SLOT_HEADER = 4;
    static final int SLOT_CHAT = 29;
    static final int SLOT_TAKE_ME_THERE = 31;
    static final int SLOT_COMMANDS = 33;
    /** GUI-4: up to 14 pages in rows 2-3 (10-16, 19-25), a short last row centred (was 7; longer topics were cut). */
    static final int MAX_PAGES = 14;

    private final Nav nav;
    private final HelpBook.Topic topic;

    HelpTopicMenu(Nav nav, HelpBook.Topic topic) {
        this.nav = nav;
        this.topic = topic;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        List<Component> crumbs = new ArrayList<>();
        crumbs.add(nav.text(viewer, "help.title"));
        nav.help.categoryOf(topic).ifPresent(c -> crumbs.add(nav.crumb(viewer, c.name())));
        crumbs.add(nav.crumb(viewer, topic.name()));
        return service.title(viewer, crumbs, null);
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "help.topic-info";
    }

    @Override
    public String helpTopic() {
        return "help";
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_HEADER, nav.item(topic.icon(), nav.parse(viewer, topic.name()), nav.parse(viewer, topic.summary())));

        List<HelpBook.Page> pages = topic.pages();
        int count = Math.min(MAX_PAGES, pages.size());
        for (int i = 0; i < count; i++) {
            HelpBook.Page page = pages.get(i);
            List<Component> lore = new ArrayList<>(nav.parse(viewer, page.lines()));
            lore.add(Component.empty());
            lore.addAll(nav.lines(viewer, "help.page-footer", Msg.number("page", i + 1), Msg.number("pages", pages.size())));
            set(net.skybornmc.skyblock.common.menu.PickListLayout.slot(i, count), nav.item(i == 0 ? Material.BOOK : Material.PAPER,
                    nav.text(viewer, "help.page-name", Msg.c("title", nav.parse(viewer, page.title())), Msg.number("page", i + 1)), lore));
        }
        if (pages.isEmpty()) {
            set(13, nav.item(Material.COBWEB, nav.text(viewer, "help.no-pages.name"), nav.lines(viewer, "help.no-pages.lore")));
        }

        set(SLOT_CHAT, nav.item(Material.OAK_SIGN, nav.text(viewer, "help.show-in-chat.name"), nav.lines(viewer, "help.show-in-chat.lore")),
                click -> {
                    click.player().closeInventory();
                    HelpChat.topic(nav, click.player(), topic);
                });

        String target = topic.takeMeThere().strip();
        if (!target.isEmpty() && HubLayout.commandExists(target)) {
            set(SLOT_TAKE_ME_THERE, nav.item(Material.COMPASS, nav.text(viewer, "help.take-me-there.name"),
                    nav.lines(viewer, "help.take-me-there.lore", Msg.p("command", "/" + target))), click -> {
                click.player().closeInventory();
                click.player().performCommand(target);
            });
        } else if (!target.isEmpty()) {
            set(SLOT_TAKE_ME_THERE, nav.comingSoon(viewer, Material.COMPASS, nav.text(viewer, "help.take-me-there.name"), List.of()));
        }

        if (!topic.commandCategories().isEmpty()) {
            int available = HelpCommandsMenu.commands(nav, viewer, topic.commandCategories()).size();
            set(SLOT_COMMANDS, nav.item(Material.COMMAND_BLOCK, nav.text(viewer, "help.commands-button.name"),
                    nav.lines(viewer, "help.commands-button.lore", Msg.number("count", available))),
                    click -> openChild(new HelpCommandsMenu(nav, topic.commandCategories(), nav.crumb(viewer, topic.name()))));
        }
    }
}
