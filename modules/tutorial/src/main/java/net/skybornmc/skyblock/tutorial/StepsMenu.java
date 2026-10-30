package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tutorial.TutorialService.State;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every core step with its state: done (lime dye), now (blaze powder, click = Take me there) or later (gray dye).
 * Up to 14 steps in the content rows 10-16 and 19-25.
 */
final class StepsMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final TutorialModule module;
    private final UUID player;

    StepsMenu(TutorialModule module, UUID player) {
        this.module = module;
        this.player = player;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, TutorialModule.ID, "menu.title"),
                service.text(viewer, TutorialModule.ID, "steps-menu.title"));
    }

    @Override
    public String messageModule() {
        return TutorialModule.ID;
    }

    @Override
    public String infoKey() {
        return "steps-menu.info";
    }

    @Override
    public String helpTopic() {
        return TutorialModule.ID;
    }

    @Override
    protected void build() {
        frame();
        Progress p = module.engine().progress(player);
        TutorialFlow flow = module.engine().flow();
        List<String> steps = flow.steps();
        for (int i = 0; i < steps.size() && i < SLOTS.length; i++) {
            String step = steps.get(i);
            boolean done = p != null && (p.isDone(step) || p.state.complete());
            boolean now = p != null && p.state == State.ACTIVE && step.equals(p.step);
            String key = done ? "steps-menu.done" : now ? "steps-menu.now" : "steps-menu.later";
            Material icon = done ? Material.LIME_DYE : now ? Material.BLAZE_POWDER : Material.GRAY_DYE;
            List<Component> lore = new ArrayList<>(service.lines(viewer, TutorialModule.ID, "steps." + step + ".lore"));
            lore.addAll(service.lines(viewer, TutorialModule.ID, key + ".lore"));
            Component name = service.text(viewer, TutorialModule.ID, key + ".name", Msg.number("number", i + 1),
                    Msg.c("step", module.engine().stepName(viewer, step)));
            if (now) {
                set(SLOTS[i], service.item(icon, name, lore), click -> {
                    click.player().closeInventory();
                    module.targets().takeMeThere(click.player(), p, true);
                });
            } else {
                set(SLOTS[i], service.item(icon, name, lore));
            }
        }
    }
}
