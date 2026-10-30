package net.skybornmc.skyblock.tools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tool menu → Skins: every skin that fits this tool type. Owned skins apply with a click (cosmetic only: name, and the
 * resource-pack model on Java); others show where they come from. The collection is kept across seasons.
 */
final class SkinsMenu extends PagedMenu<ToolCatalog.SkinDef> {

    static final int SLOT_REMOVE = 4;

    private final ToolsModule module;
    private final UUID toolId;
    private final Set<String> owned;

    SkinsMenu(ToolsModule module, UUID toolId, Set<String> owned) {
        this.module = module;
        this.toolId = toolId;
        this.owned = owned;
    }

    @Override
    public String messageModule() {
        return ToolsModule.ID;
    }

    @Override
    public String infoKey() {
        return "skins.menu.info";
    }

    @Override
    public String helpTopic() {
        return "tools";
    }

    @Override
    protected String emptyKey() {
        return "skins.menu.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(text("tool-menu.title"), text("skins.menu.title"));
    }

    private Optional<ToolData> tool() {
        int slot = ToolTracker.find(viewer.getInventory(), toolId);
        return slot < 0 ? Optional.empty() : ToolItems.read(viewer.getInventory().getItem(slot));
    }

    @Override
    protected List<ToolCatalog.SkinDef> entries() {
        Optional<ToolData> tool = tool();
        if (tool.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(module.catalog().skins().values().stream().filter(s -> s.fits(tool.get().kind())).toList());
    }

    @Override
    protected void decorate() {
        tool().filter(t -> t.skin() != null).ifPresent(t -> set(SLOT_REMOVE,
                service.item(Material.BARRIER, viewer, ToolsModule.ID, "skins.menu.remove", Msg.mm("skin", module.items().skinName(t.skin()))),
                click -> {
                    module.applySkin(viewer, toolId, null);
                    service.open(viewer, this);
                }));
    }

    @Override
    protected MenuButton entry(ToolCatalog.SkinDef skin) {
        Optional<ToolData> tool = tool();
        boolean applied = tool.isPresent() && skin.id().equals(tool.get().skin());
        boolean has = owned.contains(skin.id());
        String state = applied ? "applied" : has ? "owned" : "locked";
        List<Component> lore = new ArrayList<>(lines("skin." + skin.id() + ".description"));
        lore.add(Component.empty());
        lore.addAll(lines("skins.menu.state." + state));
        ItemStack icon = service.item(new ItemStack(has ? Material.ARMOR_STAND : Material.GRAY_DYE), text("skins.menu.name",
                Msg.mm("skin", "<rarity:" + skin.rarity().id() + ">" + module.items().skinName(skin.id()))), lore);
        if (applied) {
            icon.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        if (!has || applied) {
            return MenuButton.of(icon, click -> service.sound(viewer, has ? "click" : "error"));
        }
        return MenuButton.of(icon, click -> {
            module.applySkin(viewer, toolId, skin.id());
            service.open(viewer, this);
        });
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ToolsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ToolsModule.ID, key, placeholders);
    }
}
