package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.SetEntryView;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Enchanter's Armor tab (E5): every set the active profile owns in its wardrobe (worn or not), as buttons. Armor
 * enchants go on these wardrobe entries, never on loose vanilla armor. Click a set to see its pieces and slots.
 */
final class ArmorEntriesMenu extends PagedMenu<SetEntryView> {

    private final EnchantsModule module;

    ArmorEntriesMenu(EnchantsModule module) {
        this.module = module;
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.armor.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    protected String emptyKey() {
        return "menu.armor.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.menus().text(viewer, "menu.title"), module.menus().text(viewer, "menu.mode.armor"));
    }

    @Override
    protected List<SetEntryView> entries() {
        WardrobeService w = module.wardrobe();
        return w == null ? List.of() : w.entries(viewer);
    }

    @Override
    protected MenuButton entry(SetEntryView entry) {
        WardrobeService w = module.wardrobe();
        ItemStack icon = w == null ? null : w.pieceIcon(viewer, entry.entryId(), ArmorPiece.CHEST);
        if (icon == null) {
            icon = new ItemStack(Material.LEATHER_CHESTPLATE);
        }
        List<Component> lore = new ArrayList<>(module.menus().lines(viewer, "menu.armor.entry", Msg.p("level", entry.level()),
                Msg.p("enchants", entry.enchantCount())));
        if (!entry.worn().isEmpty()) {
            lore.addAll(module.menus().lines(viewer, "menu.armor.worn", Msg.p("count", entry.worn().size())));
        }
        lore.addAll(module.menus().lines(viewer, "menu.armor.click"));
        Component name = module.menus().text(viewer, "menu.armor.name", Msg.mm("set", "<rarity:" + entry.rarity().id() + ">"
                + LoreBuilder.escape(entry.name())));
        return MenuButton.of(service.item(icon, name, lore), click -> openChild(new ArmorEntryMenu(module, entry.entryId())));
    }
}
