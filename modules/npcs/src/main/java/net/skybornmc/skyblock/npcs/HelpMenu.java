package net.skybornmc.skyblock.npcs;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.List;

/**
 * Bedrock version of the NPC / hologram help (no clickable chat there): 3 rows, the help text on a book in the middle
 * and, for roles with a command, a big Open button. Big tap targets, short lore.
 * <pre>
 *  R O B B B B B O R
 *  B . . . ? . O O B     13 = help (book), 15-16 = Open (when the role has a command)
 *  R O B B B B B O R
 * </pre>
 */
final class HelpMenu extends Menu {

    private static final int SLOT_TEXT = 13;
    private static final int[] SLOTS_OPEN = {15, 16};

    private final NpcsModule module;
    private final String nameRaw;
    private final String linesKey;
    private final String command;

    HelpMenu(NpcsModule module, String nameRaw, String linesKey, String command) {
        this.module = module;
        this.nameRaw = nameRaw;
        this.linesKey = linesKey;
        this.command = command;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, NpcsModule.ID, "help.menu.crumb", Msg.mm("name", nameRaw)));
    }

    @Override
    public String messageModule() {
        return NpcsModule.ID;
    }

    @Override
    protected void build() {
        frame();
        Component name = service.text(viewer, NpcsModule.ID, "help.header", Msg.mm("name", nameRaw));
        List<Component> lore = service.lines(viewer, NpcsModule.ID, linesKey);
        set(SLOT_TEXT, service.item(Material.BOOK, name, lore));
        if (command == null) {
            return;
        }
        var open = service.item(Material.LIME_CONCRETE, viewer, NpcsModule.ID, "help.menu.open", Msg.p("command", "/" + command));
        for (int slot : SLOTS_OPEN) {
            set(slot, open, click -> {
                click.player().closeInventory();
                click.player().performCommand(command);
            });
        }
    }
}
