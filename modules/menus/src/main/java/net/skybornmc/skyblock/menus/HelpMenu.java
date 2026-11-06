package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.nextstep.NextStep;
import net.skybornmc.skyblock.common.nextstep.NextStepService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code /help}: "What do you want to do?" (plan 4 Nov C §1). One card per goal of {@code help.yml → goals} (Make
 * money, Upgrade my tools, Grow my island, Play with friends, Find my way, Rules & support) with 1-2 buttons next to it
 * that DO the thing (teleport with the usual warm-up, open the shop or a menu, run the shared next step). A button
 * whose command does not exist on this server is left out; a click on the card does what its first button does.
 * "All topics" (46) opens the old guide categories ({@link HelpTopicsMenu}). Layout: {@link HelpGoalLayout}.
 */
final class HelpMenu extends Menu {

    private final Nav nav;

    HelpMenu(Nav nav) {
        this.nav = nav;
    }

    @Override
    public int rows() {
        return HelpGoalLayout.ROWS;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "help.goals.title");
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    /** A goal button that works here: its command exists (or it is the next step). */
    private record Action(HelpBook.GoalButton button, String command) {
    }

    @Override
    protected void build() {
        navBar();
        List<HelpBook.Goal> goals = nav.help.goals();
        int count = Math.min(HelpGoalLayout.MAX, goals.size());
        Optional<NextStep> step = NextStepService.of(nav.core.services()).current(viewer);
        String stepText = step.map(NextStep::text).orElseGet(() -> plain("help.goals.next-none"));
        for (int i = 0; i < count; i++) {
            HelpBook.Goal goal = goals.get(i);
            List<Action> actions = actions(goal, step);
            int card = HelpGoalLayout.card(i, count);
            if (actions.isEmpty()) {
                set(card, card(goal, actions, stepText));
            } else {
                Action first = actions.getFirst();
                set(card, card(goal, actions, stepText), click -> run(click.player(), first));
            }
            for (int b = 0; b < actions.size(); b++) {
                Action a = actions.get(b);
                set(HelpGoalLayout.button(card, b), button(a, stepText), click -> run(click.player(), a));
            }
        }
        if (goals.isEmpty()) {
            setEmpty(Nav.TEXTS, "help.empty");
        }
        set(HelpGoalLayout.SLOT_ALL_TOPICS, nav.item(Material.BOOKSHELF, nav.text(viewer, "help.all-topics.name"),
                nav.lines(viewer, "help.all-topics.lore")), click -> openChild(new HelpTopicsMenu(nav)));
    }

    /** The buttons of a goal that work on this server, with the command each one runs now. */
    private List<Action> actions(HelpBook.Goal goal, Optional<NextStep> step) {
        List<Action> out = new ArrayList<>();
        for (HelpBook.GoalButton button : goal.buttons()) {
            String command = button.nextStep()
                    ? step.filter(NextStep::hasAction).map(NextStep::command).orElse(goal.fallback())
                    : button.command();
            if (command != null && !command.isBlank() && HubLayout.commandExists(command)) {
                out.add(new Action(button, command.strip()));
            }
        }
        return out;
    }

    /** The card: bold name, muted subtitle, up to 3 lines, then {@code » CLICK to <first action>}. */
    private ItemStack card(HelpBook.Goal goal, List<Action> actions, String stepText) {
        TagResolver next = Msg.p("next", stepText);
        List<Component> lore = new ArrayList<>();
        if (!goal.subtitle().isBlank()) {
            lore.add(nav.parse(viewer, goal.subtitle(), next));
        }
        if (!goal.lore().isEmpty()) {
            lore.add(Component.empty());
            lore.addAll(nav.parse(viewer, goal.lore(), next));
        }
        if (!actions.isEmpty()) {
            lore.addAll(nav.lines(viewer, "help.goals.action", Msg.p("action", actions.getFirst().button().action())));
        }
        return nav.item(Nav.material(goal.icon(), Material.BOOK), nav.parse(viewer, goal.name()), lore);
    }

    private ItemStack button(Action a, String stepText) {
        List<Component> lore = new ArrayList<>(nav.lines(viewer, "help.goals.button", Msg.p("command", "/" + a.command()),
                Msg.p("next", stepText)));
        lore.addAll(nav.lines(viewer, "help.goals.action", Msg.p("action", a.button().action())));
        return nav.item(Nav.material(a.button().icon(), Material.COMPASS), nav.parse(viewer, a.button().name()), lore);
    }

    /** Runs the command; a menu it opens gets Back to /help, a teleport (with its warm-up) closes this menu. */
    private void run(Player player, Action a) {
        service.navigate(player, this, () -> player.performCommand(a.command()));
    }

    private String plain(String key) {
        String raw = nav.core.messages().raw(Nav.TEXTS, key);
        return raw == null ? "" : raw;
    }
}
