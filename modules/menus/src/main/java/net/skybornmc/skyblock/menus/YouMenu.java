package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code /menu → You} (GUI_DESIGN §7.2, GUI-2): opened by clicking your head in the hub. 3 rows: 4 your head (display),
 * 10-16 the buttons of {@code menu.yml → you} (Profiles, Titles & Tags, Achievements, Settings, Store, Rules & links),
 * 18 Back (to the hub), 22 Close. Buttons of features that are not built are hidden (owner G2), like in the hub.
 */
final class YouMenu extends net.skybornmc.skyblock.common.menu.Menu {

    static final int SLOT_HEAD = 4;

    private final Nav nav;
    private final HubMenu.Buttons buttons;

    YouMenu(Nav nav) {
        this.nav = nav;
        this.buttons = new HubMenu.Buttons(nav);
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "hub.title", "you.title");
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    @Override
    public String infoKey() {
        return "you.info";
    }

    @Override
    public String helpTopic() {
        return "menu";
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_HEAD, nav.head(viewer, "you.head"));
        Set<String> services = buttons.services();
        for (ButtonLayout.Button button : nav.you.buttons()) {
            ButtonLayout.State state = buttons.state(button, services);
            if (state == ButtonLayout.State.HIDDEN) {
                continue;
            }
            Component name = nav.textOr(viewer, "you.buttons." + button.id() + ".name", "hub.fallback-name", button.id());
            List<Component> lore = new ArrayList<>(nav.lines(viewer, "you.buttons." + button.id() + ".lore"));
            if (state == ButtonLayout.State.COMING_SOON) {
                set(button.slot(), nav.comingSoon(viewer, Nav.material(button.icon(), Material.BOOK), name, lore));
            } else if (!button.allowed(viewer)) {
                lore.addAll(nav.lines(viewer, "hub.locked"));
                set(button.slot(), nav.item(Nav.material(button.icon(), Material.BOOK), name, lore));
            } else {
                lore.addAll(nav.lines(viewer, "hub.action"));
                set(button.slot(), nav.item(Nav.material(button.icon(), Material.BOOK), name, lore), click -> open(click.player(), button));
            }
        }
    }

    private void open(Player player, ButtonLayout.Button button) {
        switch (button.action()) {
            case ButtonLayout.SETTINGS -> openChild(new SettingsMenu(nav));
            case ButtonLayout.HELP -> openChild(new HelpMenu(nav));
            case ButtonLayout.TRAVEL -> openChild(new TravelMenu(nav));
            case ButtonLayout.TOP -> openChild(new TopMenu(nav));
            case ButtonLayout.COMMAND -> service.navigate(player, this, () -> player.performCommand(button.command()));
            default -> back(); // you / rewards belong to the hub: lead back there
        }
    }
}
