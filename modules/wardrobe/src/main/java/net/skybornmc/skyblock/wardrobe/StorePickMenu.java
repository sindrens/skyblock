package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Pick an item from your inventory to put in the Wardrobe Chest (unique items only: tools, books, scrolls, tokens). */
final class StorePickMenu extends PagedMenu<Integer> {

    private final WardrobeModule module;

    StorePickMenu(WardrobeModule module) {
        this.module = module;
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.store.info";
    }

    @Override
    public String helpTopic() {
        return "wardrobe";
    }

    @Override
    protected String emptyKey() {
        return "menu.store.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.chest.title"), module.menus().text(viewer, "menu.store.title"));
    }

    @Override
    protected List<Integer> entries() {
        return module.chest().storableSlots(viewer);
    }

    @Override
    protected MenuButton entry(Integer slot) {
        ItemStack item = viewer.getInventory().getItem(slot);
        ItemStack display = item == null ? new ItemStack(org.bukkit.Material.AIR) : item.clone();
        display.editPersistentDataContainer(pdc -> {
            pdc.remove(ItemKeys.UUID);
            pdc.remove(ItemKeys.ID);
        });
        List<Component> lore = new ArrayList<>();
        List<Component> current = display.lore();
        if (current != null) {
            lore.addAll(current);
        }
        lore.add(Component.empty());
        lore.addAll(module.menus().lines(viewer, "menu.store.click"));
        display.lore(lore);
        return MenuButton.of(display, click -> module.chest().store(viewer, slot));
    }
}
