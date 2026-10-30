package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Wardrobe Chest (WARDROBE §4.4): the profile's stored unique items; click one to take it out. Slot 47 opens the
 * list of storable items in your inventory (click one to store it), slot 51 shows the space. Bedrock-safe: nothing is
 * dragged, everything is a button.
 */
final class ChestMenu extends PagedMenu<WardrobeStore.Stored> {

    private final WardrobeModule module;
    private final List<WardrobeStore.Stored> rows;

    ChestMenu(WardrobeModule module, List<WardrobeStore.Stored> rows) {
        this.module = module;
        this.rows = List.copyOf(rows);
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.chest.info";
    }

    @Override
    public String helpTopic() {
        return "wardrobe";
    }

    @Override
    protected String emptyKey() {
        return "menu.chest.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.title"), module.menus().text(viewer, "menu.chest.title"));
    }

    @Override
    protected List<WardrobeStore.Stored> entries() {
        return rows;
    }

    @Override
    protected MenuButton entry(WardrobeStore.Stored row) {
        ItemStack display;
        try {
            display = ItemStack.deserializeBytes(row.item());
            display.editPersistentDataContainer(pdc -> {
                pdc.remove(net.skybornmc.skyblock.common.item.ItemKeys.UUID);
                pdc.remove(net.skybornmc.skyblock.common.item.ItemKeys.ID);
            });
        } catch (RuntimeException e) {
            display = new ItemStack(Material.BARRIER);
        }
        List<Component> lore = new ArrayList<>();
        List<Component> current = display.lore();
        if (current != null) {
            lore.addAll(current);
        }
        lore.add(Component.empty());
        lore.addAll(module.menus().lines(viewer, "menu.chest.take"));
        display.lore(lore);
        return MenuButton.of(display, click -> module.chest().take(viewer, row.itemUuid()));
    }

    @Override
    protected void decorate() {
        set(47, service.item(Material.HOPPER, viewer, WardrobeModule.ID, "menu.chest.store",
                Msg.p("count", module.chest().storableSlots(viewer).size())), click -> openChild(new StorePickMenu(module)));
        set(51, service.item(Material.CHEST, viewer, WardrobeModule.ID, "menu.chest.space",
                Msg.p("used", rows.size()), Msg.p("size", module.chest().capacity(viewer))));
    }
}
