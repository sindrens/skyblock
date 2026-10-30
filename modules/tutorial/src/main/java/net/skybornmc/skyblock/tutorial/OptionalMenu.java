package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Optional steps in {@code /tutorial} (TUTORIAL.md §3.1, TU6): one button per step, done (lime dye) or open (book, click
 * = Show me: hint, teleport and camera when the step has them), with progress and the crystal reward. Bedrock-safe:
 * every choice is a button. Content rows 10-16, 19-25.
 */
final class OptionalMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final TutorialModule module;
    private final UUID player;

    OptionalMenu(TutorialModule module, UUID player) {
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
                service.text(viewer, TutorialModule.ID, "optional-menu.title"));
    }

    @Override
    public String messageModule() {
        return TutorialModule.ID;
    }

    @Override
    public String infoKey() {
        return "optional-menu.info";
    }

    @Override
    public String helpTopic() {
        return TutorialModule.ID;
    }

    @Override
    protected void build() {
        frame();
        Progress p = module.engine().progress(player);
        OptionalSteps optional = module.optional();
        List<OptionalStep> steps = optional.visible();
        if (p == null || !module.config().optionalEnabled) {
            set(22, service.item(Material.CLOCK, viewer, TutorialModule.ID, "menu.loading"));
            return;
        }
        boolean counts = optional.counts(p);
        set(4, service.item(Material.KNOWLEDGE_BOOK, viewer, TutorialModule.ID, counts ? "optional-menu.header" : "optional-menu.header-later",
                Msg.number("done", optional.doneCount(p)), Msg.number("total", steps.size())));
        for (int i = 0; i < steps.size() && i < SLOTS.length; i++) {
            OptionalStep s = steps.get(i);
            OptionalRules.Entry e = p.optional.get(s.id());
            boolean done = e != null && e.done();
            List<Component> lore = new ArrayList<>(service.lines(viewer, TutorialModule.ID, "optional." + s.id() + ".lore"));
            String state = done ? "optional-menu.done" : "optional-menu.open";
            lore.addAll(service.lines(viewer, TutorialModule.ID, state + ".lore", Msg.number("count", OptionalRules.progress(s, e)),
                    Msg.number("needed", s.needed()), Msg.number("crystals", s.reward())));
            Component name = service.text(viewer, TutorialModule.ID, state + ".name", Msg.c("step", optional.name(viewer, s)));
            Material icon = done ? Material.LIME_DYE : icon(s);
            if (done) {
                set(SLOTS[i], service.item(icon, name, lore));
            } else {
                set(SLOTS[i], service.item(icon, name, lore), click -> {
                    click.player().closeInventory();
                    optional.showMe(click.player(), s);
                });
            }
        }
    }

    private static Material icon(OptionalStep s) {
        return switch (s.kind()) {
            case MENU -> Material.BOOK;
            case NPC -> Material.PLAYER_HEAD;
            case BREAK -> Material.COBBLESTONE;
            case VISIT -> Material.FILLED_MAP;
            case NEAR -> Material.COMPASS;
        };
    }
}
