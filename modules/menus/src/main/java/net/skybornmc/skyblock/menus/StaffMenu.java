package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /staff}: the staff hub (layout {@code menu.yml → staff-entries}). Display, the config editor, the tags editor
 * and every staff tool in one place; tools that aren't built yet keep their slot in the "Coming soon" look, tools a
 * rank can't use show locked.
 */
final class StaffMenu extends Menu {

    private final Nav nav;

    StaffMenu(Nav nav) {
        this.nav = nav;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "staff.title");
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "staff.info";
    }

    @Override
    public String helpTopic() {
        return "staff";
    }

    @Override
    protected void build() {
        frame();
        set(4, nav.item(Material.SHIELD, nav.text(viewer, "staff.header.name"), nav.lines(viewer, "staff.header.lore")));
        for (HubLayout.Entry entry : nav.staff.entries()) {
            Component name = nav.text(viewer, "staff.entries." + entry.id() + ".name");
            List<Component> description = nav.lines(viewer, "staff.entries." + entry.id() + ".lore");
            if (!entry.active()) {
                set(entry.slot(), nav.comingSoon(viewer, entry.icon(), name, description));
                continue;
            }
            if (!entry.allowed(viewer)) {
                List<Component> lore = new ArrayList<>(description);
                lore.addAll(nav.lines(viewer, "staff.locked"));
                set(entry.slot(), nav.item(entry.icon(), name, lore));
                continue;
            }
            List<Component> lore = new ArrayList<>(description);
            lore.addAll(nav.lines(viewer, "hub.action"));
            set(entry.slot(), nav.item(entry.icon(), name, lore), click -> {
                String command = entry.command().strip();
                if (command.toLowerCase(Locale.ROOT).equals("admin")) {
                    openChild(new AdminMenu(nav));
                    return;
                }
                click.player().closeInventory();
                click.player().performCommand(command);
            });
        }
    }
}
