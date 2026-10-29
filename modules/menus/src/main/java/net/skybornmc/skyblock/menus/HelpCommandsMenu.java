package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.command.HelpEntry;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Commands the viewer may use, generated from the command framework's help index (so it is always complete and
 * permission-filtered). Clicking one puts it in the chat box, ready to finish.
 */
final class HelpCommandsMenu extends PagedMenu<HelpEntry> {

    private final Nav nav;
    private final List<String> categories;
    private final Component crumb;

    /** @param categories help-index categories to show; empty = all */
    HelpCommandsMenu(Nav nav, List<String> categories, Component crumb) {
        this.nav = nav;
        this.categories = List.copyOf(categories);
        this.crumb = crumb;
    }

    /** The commands {@code sender} may use in {@code categories} (empty = all), sorted by category then name. */
    static List<HelpEntry> commands(Nav nav, CommandSender sender, List<String> categories) {
        return nav.core.commands().helpIndex(sender).stream()
                .filter(e -> categories.isEmpty() || categories.stream().anyMatch(c -> c.equalsIgnoreCase(e.category())))
                .filter(e -> !e.playerOnly() || sender instanceof Player)
                .sorted(Comparator.comparing(HelpEntry::category).thenComparing(HelpEntry::path))
                .toList();
    }

    @Override
    protected List<HelpEntry> entries() {
        return commands(nav, viewer, categories);
    }

    @Override
    protected MenuButton entry(HelpEntry entry) {
        List<Component> lore = new ArrayList<>();
        if (!entry.description().isBlank()) {
            lore.add(nav.text(viewer, "help.command.description", Msg.p("description", entry.description())));
        }
        lore.add(nav.text(viewer, "help.command.category", Msg.p("category", entry.category())));
        if (!entry.aliases().isEmpty()) {
            lore.add(nav.text(viewer, "help.command.aliases", Msg.p("aliases", "/" + String.join(", /", entry.aliases()))));
        }
        if (entry.example() != null && !entry.example().isBlank()) {
            lore.add(nav.text(viewer, "help.command.example", Msg.p("example", entry.example())));
        }
        lore.addAll(nav.lines(viewer, "help.command.action"));
        Material icon = entry.category().equalsIgnoreCase("Staff") ? Material.REDSTONE_TORCH : Material.PAPER;
        return MenuButton.of(nav.item(icon, nav.text(viewer, "help.command.name", Msg.p("usage", entry.usage())), lore), click -> {
            click.player().closeInventory();
            HelpChat.suggest(nav, click.player(), entry);
        });
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(nav.text(viewer, "help.title"), crumb);
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    protected String emptyKey() {
        return "help.no-commands";
    }

    @Override
    public String infoKey() {
        return "help.commands-info";
    }

    @Override
    public String helpTopic() {
        return "commands";
    }
}
