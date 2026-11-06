package net.skybornmc.skyblock.islandtools;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owner 7 Nov 2026: the skins of one tool (4 rows): Diamond and the bought or buyable looks in the middle row, the tool's
 * style (bold name, muted subtitle, | rows, one click hint). Buying asks to confirm (credits are bought with real money).
 */
final class SkinMenu extends Menu {

    private final IslandToolsModule m;
    private final Kind kind;

    SkinMenu(IslandToolsModule m, Kind kind) {
        this.m = m;
        this.kind = kind;
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, IslandToolsModule.ID, "item.name." + kind.id()),
                service.text(viewer, IslandToolsModule.ID, "skins.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandToolsModule.ID;
    }

    @Override
    public String infoKey() {
        return "skins.info";
    }

    @Override
    protected void build() {
        navBar();
        ToolSkins s = m.skins();
        UUID profile = m.profile(viewer);
        List<ToolSkins.Skin> all = s.skins();
        int chosen = s.chosen(profile, kind).map(all::indexOf).orElse(-1) + 1;
        int first = 9 + ToolMenu.start(all.size() + 1);
        set(first, tile(Material.matchMaterial("diamond_" + kind.id()), "diamond", 0, true, chosen == 0), click -> {
            s.choose(click.player(), kind, 0, this::refreshIfOpen);
        });
        for (int i = 0; i < all.size() && i < 8; i++) {
            ToolSkins.Skin skin = all.get(i);
            int index = i + 1;
            boolean owned = s.owns(profile, kind, skin);
            set(first + index, tile(skin.material(kind), skin.id(), skin.price(), owned, chosen == index), click -> {
                if (owned) {
                    s.choose(click.player(), kind, index, this::refreshIfOpen);
                    return;
                }
                String name = m.raw("skins.name." + skin.id());
                openChild(new ConfirmMenu(v -> service.text(v, IslandToolsModule.ID, "skins.confirm-crumb"), new ItemStack(skin.material(kind)),
                        v -> service.lines(v, IslandToolsModule.ID, "skins.confirm-details", Msg.p("skin", name),
                                Msg.p("tool", m.raw("item.name." + kind.id())), Msg.number("price", skin.price())),
                        pl -> s.buy(pl, kind, skin, index, () -> m.core().menus().open(pl, new SkinMenu(m, kind))), pl -> { }));
            });
        }
    }

    private void refreshIfOpen() {
        if (isOpen()) {
            refresh();
        }
    }

    private ItemStack tile(Material icon, String id, long price, boolean owned, boolean worn) {
        ItemStack base = new ItemStack(icon == null ? Material.PAPER : icon);
        base.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES));
        List<Component> lore = new ArrayList<>(service.lines(viewer, IslandToolsModule.ID, "skins.tile.lore",
                Msg.p("tool", m.raw("item.name." + kind.id()))));
        lore.addAll(service.lines(viewer, IslandToolsModule.ID, worn ? "skins.tile.worn" : owned ? "skins.tile.owned" : "skins.tile.price",
                Msg.number("price", price)));
        return service.item(base, m.msg().component(viewer, worn ? "skins.tile.name-worn" : "skins.tile.name",
                Msg.p("skin", m.raw("skins.name." + id))), lore);
    }
}
