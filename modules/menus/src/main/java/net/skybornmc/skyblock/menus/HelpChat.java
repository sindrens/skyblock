package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.command.HelpEntry;
import net.skybornmc.skyblock.common.menu.Offer;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** The chat version of {@code /help} (console, "Show in chat", and anyone who prefers chat). */
final class HelpChat {

    private HelpChat() {
    }

    static void categories(Nav nav, CommandSender sender) {
        nav.msg.send(sender, "help.chat.header");
        for (HelpBook.Category category : nav.help.categories()) {
            if (category.status() == HelpBook.Status.COMING_SOON) {
                nav.msg.send(sender, "help.chat.category-locked", Msg.c("name", nav.crumb(sender, category.name())));
            } else {
                nav.msg.send(sender, "help.chat.category", Nav.pre("id", category.allCommands() ? "commands" : category.id()),
                        Msg.c("name", nav.parse(sender, category.name())),
                        Msg.c("lore", nav.parse(sender, String.join(" ", category.lore()))));
            }
        }
        nav.msg.send(sender, "help.chat.footer");
    }

    static void category(Nav nav, CommandSender sender, HelpBook.Category category) {
        if (category.allCommands()) {
            commands(nav, sender, List.of());
            return;
        }
        nav.msg.send(sender, "help.chat.category-header", Msg.c("name", nav.parse(sender, category.name())));
        for (String id : category.topics()) {
            nav.help.topic(id).ifPresent(topic -> nav.msg.send(sender, "help.chat.topic-line", Nav.pre("id", topic.id()),
                    Msg.c("name", nav.parse(sender, topic.name())),
                    Msg.c("summary", nav.parse(sender, String.join(" ", topic.summary())))));
        }
    }

    static void topic(Nav nav, CommandSender sender, HelpBook.Topic topic) {
        nav.msg.send(sender, "help.chat.topic-header", Msg.c("name", nav.parse(sender, topic.name())));
        for (HelpBook.Page page : topic.pages()) {
            nav.msg.send(sender, "help.chat.page-title", Msg.c("title", nav.parse(sender, page.title())));
            for (String line : page.lines()) {
                if (!line.isBlank()) {
                    nav.msg.send(sender, "help.chat.page-line", Msg.c("line", nav.parse(sender, line)));
                }
            }
        }
        String target = topic.takeMeThere().strip();
        if (sender instanceof Player && !target.isEmpty() && HubLayout.commandExists(target)) {
            nav.msg.send(sender, "help.chat.take-me-there", Nav.pre("command", target));
        }
        if (!topic.commandCategories().isEmpty()) {
            List<HelpEntry> list = HelpCommandsMenu.commands(nav, sender, topic.commandCategories());
            if (!list.isEmpty()) {
                nav.msg.send(sender, "help.chat.commands-header");
                list.forEach(entry -> suggestLine(nav, sender, entry));
            }
        }
    }

    static void commands(Nav nav, CommandSender sender, List<String> categories) {
        List<HelpEntry> list = HelpCommandsMenu.commands(nav, sender, categories);
        nav.msg.send(sender, "help.chat.all-commands-header", Msg.number("count", list.size()));
        String current = null;
        for (HelpEntry entry : list) {
            if (!entry.category().equals(current)) {
                current = entry.category();
                nav.msg.send(sender, "help.chat.commands-category", Msg.p("category", current));
            }
            suggestLine(nav, sender, entry);
        }
    }

    private static void suggestLine(Nav nav, CommandSender sender, HelpEntry entry) {
        nav.msg.send(sender, "help.chat.command-line", Msg.p("usage", entry.usage()), Nav.pre("command", "/" + entry.path() + " "),
                Msg.p("description", entry.description()));
    }

    /** After clicking a command in the menu: one clickable line that puts it in the chat box. */
    static void suggest(Nav nav, Player player, HelpEntry entry) {
        // GUI-5: Bedrock can't click the line to put it in chat. A command that needs nothing more gets a popup with a
        // button that runs it; one that needs arguments keeps the line (it shows the usage to type).
        Offer.Builder offer = Offer.builder(Nav.TEXTS, "help.popup")
                .id("help:suggest")
                .icon(org.bukkit.Material.PAPER)
                .placeholders(Msg.p("usage", entry.usage()), Msg.p("description", entry.description()))
                .chat(p -> nav.msg.send(p, "help.chat.suggest", Msg.p("usage", entry.usage()),
                        Nav.pre("command", "/" + entry.path() + " "), Msg.p("description", entry.description())));
        if (runsAlone(entry.usage())) {
            offer.button(Offer.Button.run(entry.path()));
        }
        nav.core.menus().offers().offer(player, offer.build());
    }

    /** True if the usage needs no argument ({@code /spawn}, {@code /is home [name]}), so a button can run it. Pure, tested. */
    static boolean runsAlone(String usage) {
        return usage != null && !usage.isBlank() && usage.indexOf('<') < 0;
    }
}
