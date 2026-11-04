package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

/**
 * {@code /start} (the guide at spawn): three big buttons, Island, Farm and Mine (3 rows, plain vanilla icons,
 * Bedrock-safe). Each button closes the menu and runs the normal command ({@code start.*-command} in essentials.yml), so
 * that command's own rules, warmups and permissions apply.
 */
final class StartMenu extends Menu {

    static final int SLOT_ISLAND = 11;
    static final int SLOT_FARM = 13;
    static final int SLOT_MINE = 15;

    private final StartGuide guide;

    StartMenu(StartGuide guide) {
        this.guide = guide;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, CommandGroup.MODULE, "start.menu.title", Msg.mm("guide", guide.guideName())));
    }

    @Override
    public String messageModule() {
        return CommandGroup.MODULE;
    }

    @Override
    protected void build() {
        navBar();
        button(SLOT_ISLAND, Material.GRASS_BLOCK, "island", guide.config().startIslandCommand);
        button(SLOT_FARM, Material.WHEAT, "farm", guide.config().startFarmCommand);
        button(SLOT_MINE, Material.IRON_PICKAXE, "mine", guide.config().startMineCommand);
    }

    private void button(int slot, Material icon, String key, String rawCommand) {
        String command = StartRules.command(rawCommand);
        set(slot, service.item(icon, viewer, CommandGroup.MODULE, "start.menu." + key), click -> {
            click.player().closeInventory();
            if (!command.isEmpty()) {
                click.player().performCommand(command);
            }
        });
    }
}
