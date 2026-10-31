package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PickListMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Editing one warp (M2.4, leader and mods): 13 the warp; 29 teleport, 30 rename (GUI-4b: chat, or a form popup on
 * Bedrock), 31 icon (picker), 32 public/private, 33 category (pick list), 40 delete (confirm; alone in the danger slot like
 * Leave in /is). One plain click each (Bedrock-safe). Closes itself when the warp or the viewer's right to manage it is gone.
 */
final class WarpEditMenu extends Menu {

    static final int SLOT_WARP = 13;
    static final int SLOT_TELEPORT = 29;
    static final int SLOT_RENAME = 30;
    static final int SLOT_ICON = 31;
    static final int SLOT_PUBLIC = 32;
    static final int SLOT_CATEGORY = 33;
    static final int SLOT_DELETE = 40;

    private final IslandsModule module;
    private final IslandManager islands;
    private final WarpManager warps;
    private final Island island;
    private final IslandWarp warp;

    WarpEditMenu(IslandsModule module, IslandManager islands, WarpManager warps, Island island, IslandWarp warp) {
        this.module = module;
        this.islands = islands;
        this.warps = warps;
        this.island = island;
        this.warp = warp;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, List.of(text("menu.crumb"), text("warps.crumb"), text("warps.edit.crumb", Msg.p("warp", warp.name()))),
                null);
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "warps.edit.info";
    }

    @Override
    public String helpTopic() {
        return "visiting";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        if (!valid()) {
            viewer.closeInventory();
        }
    }

    /** A small list of the categories; one click picks (GUI-4/G9: no forward-only cycling). */
    private void pickCategory() {
        List<PickListMenu.Option<WarpCategory>> options = new ArrayList<>();
        for (WarpCategory category : WarpCategory.values()) {
            Material icon = Material.matchMaterial(module.config().categoryIcon(category));
            options.add(new PickListMenu.Option<>(category, new ItemStack(icon == null || !icon.isItem() ? Material.NAME_TAG : icon),
                    text("warps.edit.category-pick", Msg.p("category", warps.categoryName(category))), List.of(), category == warp.category()));
        }
        openChild(new PickListMenu<>(List.of(text("warps.crumb"), text("warps.edit.category-crumb")), options, category -> {
            if (valid()) {
                warps.setCategory(viewer, island, warp, category, this::refresh);
            }
        }));
    }

    private boolean valid() {
        return islands.byId(island.id()) == island && warps.warps(island.id()).contains(warp) && warps.canManage(viewer, island);
    }

    @Override
    protected void build() {
        frame();
        TagResolver[] p = {
                Msg.p("warp", warp.name()),
                Msg.p("category", warps.categoryName(warp.category())),
                Msg.p("visits", warp.visits())};
        List<Component> lore = new ArrayList<>(lines("warps.edit.warp.lore", p));
        if (warp.sign() != null) {
            lore.addAll(lines("warps.entry.sign"));
        }
        set(SLOT_WARP, service.item(new ItemStack(warps.icon(warp)), text("warps.edit.warp.name", p), lore));
        set(SLOT_TELEPORT, service.item(Material.ENDER_PEARL, viewer, IslandsModule.ID, "warps.edit.teleport", p), click -> {
            viewer.closeInventory();
            warps.teleport(viewer, island, warp);
        });
        set(SLOT_RENAME, service.item(Material.OAK_SIGN, viewer, IslandsModule.ID, warp.sign() != null ? "warps.edit.rename-sign" : "warps.edit.rename", p),
                click -> {
                    if (valid()) {
                        warps.promptRename(viewer, island, warp, () -> service.open(viewer, this));
                    }
                });
        set(SLOT_ICON, service.item(warps.icon(warp), viewer, IslandsModule.ID, "warps.edit.icon", p),
                click -> openChild(new IconPickerMenu(module, islands, warps, island, warp)));
        set(SLOT_PUBLIC, service.item(warp.isPublic() ? Material.LIME_DYE : Material.GRAY_DYE, viewer, IslandsModule.ID,
                warp.isPublic() ? "warps.edit.public" : "warps.edit.private", p), click -> {
            if (valid()) {
                warps.togglePublic(viewer, island, warp, this::refresh);
            }
        });
        set(SLOT_CATEGORY, service.item(Material.NAME_TAG, viewer, IslandsModule.ID, "warps.edit.category",
                Msg.p("category", warps.categoryName(warp.category()))), click -> {
            if (valid()) {
                pickCategory();
            }
        });
        set(SLOT_DELETE, service.item(Material.LAVA_BUCKET, viewer, IslandsModule.ID, "warps.edit.delete", p), click -> {
            if (!valid()) {
                return;
            }
            openChild(new ConfirmMenu(
                    v -> text("warps.delete.crumb"),
                    service.item(new ItemStack(warps.icon(warp)), text("warps.delete.shown", p), List.of()),
                    v -> lines("warps.delete.details", p),
                    v -> warps.delete(v, island, warp, WarpManager.REASON_MENU),
                    null));
        });
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
