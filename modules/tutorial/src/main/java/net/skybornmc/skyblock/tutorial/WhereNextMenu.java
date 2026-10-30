package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.Material;

/**
 * "Where next?" after the finish (TUTORIAL.md §5 Finish): My island, Keep mining, Help. 3 rows, big buttons (Bedrock).
 * Each button runs a normal command ({@code where-next.<button>.command} in the messages), so it follows that command's
 * own rules and permissions.
 */
final class WhereNextMenu extends Menu {

    private final TutorialModule module;

    WhereNextMenu(TutorialModule module) {
        this.module = module;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, TutorialModule.ID, "where-next.title"));
    }

    @Override
    public String messageModule() {
        return TutorialModule.ID;
    }

    @Override
    protected void build() {
        frame();
        button(11, Material.GRASS_BLOCK, "island");
        button(13, Material.IRON_PICKAXE, "mine");
        button(15, Material.KNOWLEDGE_BOOK, "help");
    }

    private void button(int slot, Material icon, String key) {
        String command = module.core().messages().raw(TutorialModule.ID, "where-next." + key + ".command");
        set(slot, service.item(icon, viewer, TutorialModule.ID, "where-next." + key), click -> {
            click.player().closeInventory();
            if (command != null && !command.isBlank()) {
                click.player().performCommand(command.strip());
            }
        });
    }
}
