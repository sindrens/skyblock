package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Centered;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code /menu → You} (G9 v3, I9 v2): opened by clicking your head in the hub. Your head on 4 (display), then the
 * visible entries of {@code menu.yml → you} in file order, centred by {@link Centered}: up to 8 entries in one row of a
 * 3-row menu, 9 to 14 in two rows (5 + 4 for nine) of a 4-row menu. Back to the hub and Close sit on the bottom row.
 * Entries of features that are not built are hidden (owner G2), so hidden entries never leave holes.
 */
final class YouMenu extends net.skybornmc.skyblock.common.menu.Menu {

    static final int SLOT_HEAD = 4;

    private final Nav nav;
    private final HubMenu.Buttons buttons;

    YouMenu(Nav nav) {
        this.nav = nav;
        this.buttons = new HubMenu.Buttons(nav);
    }

    /** One visible entry with its state. */
    private record Entry(ButtonLayout.Button button, ButtonLayout.State state) {
    }

    /** The entries this viewer sees, in file order, at most {@link Centered#MAX_SMALL}. */
    private List<Entry> visible() {
        Set<String> services = buttons.services();
        List<Entry> out = new ArrayList<>();
        for (ButtonLayout.Button button : nav.you.buttons()) {
            ButtonLayout.State state = buttons.state(button, services);
            if (state == ButtonLayout.State.HIDDEN || (viewer != null && button.hiddenFor(viewer))) {
                continue;
            }
            if (out.size() >= Centered.MAX_SMALL) {
                break;
            }
            out.add(new Entry(button, state));
        }
        return out;
    }

    @Override
    public int rows() {
        return Centered.layout(visible().size()).rows();
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
        navBar();
        set(SLOT_HEAD, nav.head(viewer, "you.head"));
        List<Entry> entries = visible();
        Centered.Layout layout = Centered.layout(entries.size());
        if (layout.rows() * 9 != getInventory().getSize()) {
            // An entry appeared or went away since the window opened: reopen it at the new size (next tick).
            nav.core.scheduler().main("menus:you-resize", () -> {
                if (isOpen()) {
                    service.open(viewer, this);
                }
            });
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            ButtonLayout.Button button = entries.get(i).button();
            int slot = layout.slots()[i];
            Component name = nav.textOr(viewer, "you.buttons." + button.id() + ".name", "hub.fallback-name", button.id());
            List<Component> lore = new ArrayList<>(nav.lines(viewer, "you.buttons." + button.id() + ".lore"));
            if (entries.get(i).state() == ButtonLayout.State.COMING_SOON) {
                set(slot, nav.comingSoon(viewer, button.icon(), name, lore));
            } else if (!button.allowed(viewer)) {
                lore.addAll(nav.lines(viewer, "hub.locked"));
                set(slot, nav.icon(viewer, button.icon(), name, lore));
            } else {
                lore.addAll(nav.lines(viewer, "hub.action"));
                set(slot, nav.icon(viewer, button.icon(), name, lore), click -> open(click.player(), button));
            }
        }
    }

    private void open(Player player, ButtonLayout.Button button) {
        switch (button.action()) {
            case ButtonLayout.SETTINGS -> openChild(new SettingsMenu(nav));
            case ButtonLayout.HELP -> openChild(new HelpMenu(nav));
            case ButtonLayout.TRAVEL -> openChild(new TravelMenu(nav));
            case ButtonLayout.TOP -> openChild(new TopMenu(nav));
            case ButtonLayout.COMMAND, ButtonLayout.STAFF -> service.navigate(player, this, () -> player.performCommand(
                    button.command().isBlank() ? "staff" : button.command()));
            default -> back(); // you / rewards belong to the hub: lead back there
        }
    }
}
