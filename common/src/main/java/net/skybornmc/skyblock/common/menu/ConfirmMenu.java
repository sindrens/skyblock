package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 3-row confirmation (UI_STYLE §5.2): 11 = Confirm (lime concrete, lore = exactly what happens + cost),
 * 13 = the thing being confirmed, 15 = Cancel. One-shot: a confirm can never run twice.
 *
 * <pre>{@code
 * new ConfirmMenu(v -> service.text(v, "island", "disband.title"), shownItem,
 *         v -> service.lines(v, "island", "disband.consequences"),
 *         player -> islands.disband(player), player -> {}).parent(this)
 * }</pre>
 */
public final class ConfirmMenu extends Menu {

    /** Action with the confirming player. */
    @FunctionalInterface
    public interface Action {
        void run(org.bukkit.entity.Player player);
    }

    private final Function<org.bukkit.entity.Player, Component> title;
    private final ItemStack shown;
    private final Function<org.bukkit.entity.Player, List<Component>> details;
    private final Action onConfirm;
    private final Action onCancel;
    private boolean used;

    /**
     * @param title     chest title for the viewer ({@code null} = default "Are you sure?")
     * @param shown     the item/action being confirmed (slot 13)
     * @param details   lines on the Confirm button: what exactly happens and what it costs
     * @param onConfirm runs once on confirm (after the menu closes; open another menu from here if needed)
     * @param onCancel  runs on cancel (after going back to the parent, or closing)
     */
    public ConfirmMenu(Function<org.bukkit.entity.Player, Component> title, ItemStack shown,
                       Function<org.bukkit.entity.Player, List<Component>> details, Action onConfirm, Action onCancel) {
        this.title = title;
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
        return title != null ? title.apply(viewer) : service.text(viewer, "confirm.title");
    }

    @Override
    protected void build() {
        frame();
        List<Component> lore = new ArrayList<>(details == null ? List.of() : details.apply(viewer));
        lore.addAll(service.lines(viewer, "confirm.yes.lore"));
        set(11, service.item(Material.LIME_CONCRETE, service.text(viewer, "confirm.yes.name"), lore), click -> {
            if (used) {
                return;
            }
            used = true;
            click.player().closeInventory();
            service.sound(click.player(), "success");
            onConfirm.run(click.player());
        });
        if (shown != null) {
            set(13, shown);
        }
        set(15, service.item(Material.RED_CONCRETE, viewer, "confirm.no"), click -> {
            if (used) {
                return;
            }
            used = true;
            back();
            if (onCancel != null) {
                onCancel.run(click.player());
            }
        });
    }
}
