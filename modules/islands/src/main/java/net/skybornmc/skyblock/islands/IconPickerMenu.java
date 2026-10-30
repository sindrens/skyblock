package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Picks a warp icon from {@code warps.icons} (M2.4). One click sets it and goes back to the warp. */
final class IconPickerMenu extends PagedMenu<Material> {

    private final IslandsModule module;
    private final IslandManager islands;
    private final WarpManager warps;
    private final Island island;
    private final IslandWarp warp;

    IconPickerMenu(IslandsModule module, IslandManager islands, WarpManager warps, Island island, IslandWarp warp) {
        this.module = module;
        this.islands = islands;
        this.warps = warps;
        this.island = island;
        this.warp = warp;
    }

    @Override
    protected List<Material> entries() {
        return warps.pickableIcons();
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, IslandsModule.ID, "warps.crumb"),
                service.text(viewer, IslandsModule.ID, "warps.icons.crumb", Msg.p("warp", warp.name())));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "warps.icons.info";
    }

    @Override
    public String helpTopic() {
        return "visiting";
    }

    @Override
    protected String emptyKey() {
        return "warps.icons.empty";
    }

    @Override
    protected MenuButton entry(Material icon) {
        boolean current = warps.icon(warp) == icon;
        ItemStack item = service.item(new ItemStack(icon), service.text(viewer, IslandsModule.ID,
                current ? "warps.icons.current" : "warps.icons.pick", Msg.p("warp", warp.name())), List.of());
        if (current) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> {
            warps.setIcon(viewer, island, warp, icon, () -> {
                if (isOpen()) {
                    back();
                }
            });
        });
    }
}
