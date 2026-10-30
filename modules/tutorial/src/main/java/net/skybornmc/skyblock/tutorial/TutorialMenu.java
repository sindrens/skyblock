package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tutorial.TutorialService.State;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;

import java.time.Duration;
import java.util.UUID;

/**
 * {@code /tutorial} (TUTORIAL.md §9.6): progress, Resume / Take me there, I'm stuck, the step list, Watch again and,
 * when allowed, Skip this step. Bedrock-safe: every choice is a button, nothing needs clickable chat.
 * <pre>
 *  . . . . P . . . .     13 progress
 *  . R S L W K . . .     20 resume, 21 stuck, 22 steps, 23 watch again, 24 skip/start
 *  . . . O U . . . .     30 optional steps, 31 what unlocks when
 * </pre>
 */
final class TutorialMenu extends Menu {

    private final TutorialModule module;
    private final UUID player;

    TutorialMenu(TutorialModule module, UUID player) {
        this.module = module;
        this.player = player;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, TutorialModule.ID, "menu.title"));
    }

    @Override
    public String messageModule() {
        return TutorialModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return TutorialModule.ID;
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected void build() {
        frame();
        Progress p = module.engine().progress(player);
        if (p == null) {
            set(22, service.item(Material.CLOCK, viewer, TutorialModule.ID, "menu.loading"));
            return;
        }
        TutorialFlow flow = module.engine().flow();
        TutorialEngine engine = module.engine();
        if (p.state == State.ACTIVE && p.step != null) {
            long in = Math.max(0, System.currentTimeMillis() - (p.startedAt == null ? p.stepStartedAt : p.startedAt));
            TagResolver[] ph = {Msg.number("number", flow.number(p.step)), Msg.number("total", flow.size()),
                    Msg.c("step", engine.stepName(viewer, p.step)), Msg.c("objective", service.text(viewer, TutorialModule.ID, "steps." + p.step + ".objective")),
                    Msg.c("hint", engine.hint(viewer, p.step)), Msg.p("time", Format.duration(Duration.ofMillis(Math.max(1000, in))))};
            set(13, service.item(Material.WRITABLE_BOOK, viewer, TutorialModule.ID, "menu.progress", ph));
            set(20, service.item(Material.ENDER_PEARL, viewer, TutorialModule.ID, "menu.resume", ph), click -> {
                click.player().closeInventory();
                module.targets().takeMeThere(click.player(), p, true);
            });
            set(21, service.item(Material.LANTERN, viewer, TutorialModule.ID, "menu.stuck", ph), click -> {
                click.player().closeInventory();
                engine.stuck(click.player());
            });
            if (engine.canSkip(p)) {
                set(24, service.item(Material.FEATHER, viewer, TutorialModule.ID, "menu.skip", ph), click -> {
                    click.player().closeInventory();
                    engine.skipStuck(click.player());
                });
            }
        } else if (p.state == State.NEW) {
            set(13, service.item(Material.BOOK, viewer, TutorialModule.ID, "menu.not-started"));
            if (module.config().enabled) {
                set(24, service.item(Material.LIME_CONCRETE, viewer, TutorialModule.ID, "menu.start"), click -> {
                    click.player().closeInventory();
                    engine.start(click.player());
                });
            }
        } else {
            set(13, service.item(Material.ENCHANTED_BOOK, viewer, TutorialModule.ID, "menu.complete",
                    Msg.c("state", service.text(viewer, TutorialModule.ID, "states." + TutorialStore.key(p.state)))));
            set(24, service.item(Material.COMPASS, viewer, TutorialModule.ID, "menu.where-next"),
                    click -> click.menu().openChild(new WhereNextMenu(module)));
        }
        set(22, service.item(Material.BOOKSHELF, viewer, TutorialModule.ID, "menu.steps", Msg.number("done", p.done.size()),
                Msg.number("total", flow.size())), click -> click.menu().openChild(new StepsMenu(module, player)));
        boolean replaying = module.replays().running(viewer.getUniqueId());
        set(23, service.item(replaying ? Material.BARRIER : Material.SPYGLASS, viewer, TutorialModule.ID,
                replaying ? "menu.replay-stop" : "menu.replay"), click -> {
            click.player().closeInventory();
            if (module.replays().running(click.player().getUniqueId())) {
                module.replays().stop(click.player());
            } else {
                module.replays().start(click.player());
            }
        });
        if (module.config().optionalEnabled && !module.optional().visible().isEmpty()) {
            set(30, service.item(Material.KNOWLEDGE_BOOK, viewer, TutorialModule.ID, "menu.optional",
                    Msg.number("done", module.optional().doneCount(p)), Msg.number("total", module.optional().visible().size())),
                    click -> click.menu().openChild(new OptionalMenu(module, player)));
        }
        java.util.List<Component> lore = new java.util.ArrayList<>(service.lines(viewer, TutorialModule.ID, "menu.unlocks.lore"));
        lore.addAll(unlocks(p));
        set(31, service.item(Material.IRON_DOOR, service.text(viewer, TutorialModule.ID, "menu.unlocks.name"), lore));
    }

    /** "Team features · step 4" lines for every locked feature, in the plan's order (lore). */
    private java.util.List<Component> unlocks(Progress p) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        var gates = module.engine().gates();
        for (String gate : net.skybornmc.skyblock.common.tutorial.Gates.ALL) {
            String step = gates.get(gate);
            if (step == null) {
                continue;
            }
            boolean open = GateRules.open(gate, gates, module.engine().flow(), p.state, p.doneSet(), false);
            out.add(service.text(viewer, TutorialModule.ID, open ? "menu.unlock-open" : "menu.unlock-locked",
                    Msg.c("feature", service.text(viewer, TutorialModule.ID, "gates." + gate)),
                    Msg.c("when", step.equals(Steps.FINISH) ? service.text(viewer, TutorialModule.ID, "menu.unlock-at-finish")
                            : service.text(viewer, TutorialModule.ID, "menu.unlock-at-step",
                            Msg.number("number", module.engine().flow().number(step)), Msg.c("step", module.engine().stepName(viewer, step))))));
        }
        return out;
    }
}
