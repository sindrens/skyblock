package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 3-row confirmation (UI_STYLE §5.2). Big click targets so it works with taps on Bedrock:
 * <pre>
 *  R O B B B B B O R
 *  B ✔ ✔ ✔ ◆ ✘ ✘ ✘ B     10-12 Confirm (lime, 11 = concrete), 13 = what is confirmed, 14-16 Cancel (red, 15 = concrete)
 *  R O B B B B B O R
 * </pre>
 * The Confirm lore lists exactly what happens and what it costs. One-shot: a confirm can never run twice.
 *
 * <pre>{@code
 * new ConfirmMenu(v -> service.text(v, "island", "disband.crumb"), shownItem,
 *         v -> service.lines(v, "island", "disband.consequences"),
 *         player -> islands.disband(player), player -> {}).parent(this)
 * }</pre>
 */
public final class ConfirmMenu extends Menu {

    /** Action with the confirming player. */
    @FunctionalInterface
    public interface Action {
        void run(Player player);
    }

    private static final int[] CONFIRM_SLOTS = {10, 11, 12};
    private static final int[] CANCEL_SLOTS = {14, 15, 16};
    private static final int SLOT_SHOWN = 13;

    private final Function<Player, Component> crumb;
    private final ItemStack shown;
    private final Function<Player, List<Component>> details;
    private final Action onConfirm;
    private final Action onCancel;
    private boolean used;

    /**
     * @param crumb     title breadcrumb for the viewer, e.g. "Disband island" ({@code null} = "Are you sure?")
     * @param shown     the item/action being confirmed (middle slot); {@code null} = a default "?" item
     * @param details   lines on the Confirm buttons: what exactly happens and what it costs
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

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, crumb != null ? crumb.apply(viewer) : service.text(viewer, "confirm.crumb"));
    }

    @Override
    protected void build() {
        frame();
        List<Component> lore = new ArrayList<>(service.lines(viewer, "confirm.yes.lore-top"));
        lore.addAll(details == null ? List.of() : details.apply(viewer));
        lore.addAll(service.lines(viewer, "confirm.yes.lore"));
        Component yesName = service.text(viewer, "confirm.yes.name");
        Consumer<MenuClick> confirm = click -> {
            if (used) {
                return;
            }
            used = true;
            click.player().closeInventory();
            service.sound(click.player(), "success");
            onConfirm.run(click.player());
        };
        for (int slot : CONFIRM_SLOTS) {
            Material material = slot == 11 ? Material.LIME_CONCRETE : Material.LIME_STAINED_GLASS_PANE;
            set(slot, service.item(material, yesName, lore), confirm);
        }

        set(SLOT_SHOWN, shown != null ? shown : service.item(Material.PAPER, viewer, "confirm.shown"));

        Consumer<MenuClick> cancel = click -> {
            if (used) {
                return;
            }
            used = true;
            back();
            if (onCancel != null) {
                onCancel.run(click.player());
            }
        };
        ItemStack no = service.item(Material.RED_CONCRETE, viewer, "confirm.no");
        for (int slot : CANCEL_SLOTS) {
            set(slot, slot == 15 ? no : service.item(Material.RED_STAINED_GLASS_PANE, viewer, "confirm.no"), cancel);
        }
    }
}
