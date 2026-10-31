package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 3-row confirmation (GUI_DESIGN §4, §7.7). Big click targets so it works with taps on Bedrock:
 * <pre>
 *  R O B B B B B O R
 *  B ✔ ✔ ✔ ◆ ✘ ✘ ✘ B     10-12 Confirm (lime, 11 = concrete), 13 = what you lose, 14-16 Cancel (red, 15 = concrete)
 *  ← O B B ✘ B B O R     18 Back, 22 Close
 * </pre>
 * The details (what happens, what it costs) go on the middle item, so Bedrock players read them before tapping.
 * One-shot: a confirm can never run twice. {@link #twoStep()} adds a second check with the sides swapped, for things
 * that can't be undone (disband, delete profile, permanent ban).
 *
 * <pre>{@code
 * new ConfirmMenu(v -> service.text(v, "island", "disband.crumb"), shownItem,
 *         v -> service.lines(v, "island", "disband.consequences"),
 *         player -> islands.disband(player), player -> {}).twoStep()
 * }</pre>
 */
public final class ConfirmMenu extends Menu {

    /** Action with the confirming player. */
    @FunctionalInterface
    public interface Action {
        void run(Player player);
    }

    private final Function<Player, Component> crumb;
    private final ItemStack shown;
    private final Function<Player, List<Component>> details;
    private final Action onConfirm;
    private final Action onCancel;
    private ConfirmFlow flow = new ConfirmFlow(false);

    /**
     * @param crumb     title, a verb and its object, e.g. "Disband island?" ({@code null} = "Are you sure?")
     * @param shown     the item/action being confirmed (middle slot); {@code null} = a default "Please confirm" item
     * @param details   lines on the middle item: what exactly happens and what it costs
     * @param onConfirm runs once on confirm (after the menu closes; open another menu from here if needed)
     * @param onCancel  runs on cancel (after going back to the parent, or closing)
     */
    public ConfirmMenu(Function<Player, Component> crumb, ItemStack shown,
                       Function<Player, List<Component>> details, Action onConfirm, Action onCancel) {
        this.crumb = crumb;
        this.shown = shown;
        this.details = details;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    /** Asks twice (the second screen swaps the buttons). Call before opening. */
    public ConfirmMenu twoStep() {
        if (!flow.twoStep()) {
            flow = new ConfirmFlow(true);
        }
        return this;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        Component main = crumb != null ? crumb.apply(viewer) : service.text(viewer, "confirm.crumb");
        Component suffix = flow.twoStep()
                ? service.text(viewer, "confirm.step", Msg.p("step", flow.step()), Msg.p("steps", flow.steps()))
                : null;
        return service.title(viewer, List.of(main), suffix);
    }

    @Override
    protected void build() {
        frame();
        boolean second = flow.swapped();
        set(ConfirmFlow.DETAILS_SLOT, detailsItem(second));

        Component yesName = service.text(viewer, second ? "confirm.second.yes.name" : "confirm.yes.name");
        List<Component> yesLore = service.lines(viewer, second ? "confirm.second.yes.lore" : "confirm.yes.lore");
        Consumer<MenuClick> confirm = click -> {
            switch (flow.confirm()) {
                case NEXT_STEP -> {
                    service.sound(click.player(), "notify");
                    redraw();
                }
                case RUN -> {
                    click.player().closeInventory();
                    service.sound(click.player(), "success");
                    onConfirm.run(click.player());
                }
                default -> {
                }
            }
        };
        for (int slot : flow.confirmSlots()) {
            Material material = ConfirmFlow.bigButton(slot) ? Material.LIME_CONCRETE : Material.LIME_STAINED_GLASS_PANE;
            set(slot, MenuIcons.tag(service.item(material, yesName, yesLore),
                    ConfirmFlow.bigButton(slot) ? MenuIcons.CONFIRM : MenuIcons.CONFIRM_SIDE), confirm);
        }

        Consumer<MenuClick> cancel = click -> {
            if (flow.cancel() != ConfirmFlow.Result.CANCEL) {
                return;
            }
            back();
            if (onCancel != null) {
                onCancel.run(click.player());
            }
        };
        for (int slot : flow.cancelSlots()) {
            Material material = ConfirmFlow.bigButton(slot) ? Material.RED_CONCRETE : Material.RED_STAINED_GLASS_PANE;
            set(slot, MenuIcons.tag(service.item(material, viewer, "confirm.no"),
                    ConfirmFlow.bigButton(slot) ? MenuIcons.CANCEL : MenuIcons.CANCEL_SIDE), cancel);
        }
    }

    /** The middle item: what is confirmed (its name), then "This will:" and the details, then the last-check warning. */
    private ItemStack detailsItem(boolean second) {
        ItemStack base = shown != null ? shown : service.item(Material.PAPER, viewer, "confirm.shown");
        List<Component> lore = new ArrayList<>();
        List<Component> own = base.hasItemMeta() && base.getItemMeta().hasLore() ? base.getItemMeta().lore() : null;
        if (own != null && !own.isEmpty()) {
            lore.addAll(own);
            lore.add(Component.empty());
        }
        List<Component> lines = details == null ? List.of() : details.apply(viewer);
        if (!lines.isEmpty()) {
            lore.addAll(service.lines(viewer, "confirm.details-top"));
            lore.addAll(lines);
        }
        if (second) {
            lore.addAll(service.lines(viewer, "confirm.second.warning"));
        }
        Component name = base.hasItemMeta() && base.getItemMeta().hasDisplayName()
                ? base.getItemMeta().displayName()
                : service.text(viewer, "confirm.shown.name");
        return service.item(base, name, lore);
    }
}
