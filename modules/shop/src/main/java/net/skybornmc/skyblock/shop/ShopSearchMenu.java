package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** {@code /shop <text>} with several matches: the results, best first; a click opens the item's buy screen. */
final class ShopSearchMenu extends PagedMenu<ShopCatalog.Item> {

    private final ShopModule module;
    private final String query;
    private final List<ShopCatalog.Item> results;

    ShopSearchMenu(ShopModule module, String query, List<ShopCatalog.Item> results) {
        this.module = module;
        this.query = query;
        this.results = List.copyOf(results);
    }

    @Override
    protected boolean compact() {
        return true;
    }

    @Override
    public boolean panel() {
        return false; // the shop is plain vanilla
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    @Override
    protected List<ShopCatalog.Item> entries() {
        return results;
    }

    @Override
    protected List<Component> titleCrumbs() {
        List<Component> crumbs = new ArrayList<>();
        crumbs.add(service.text(viewer, ShopModule.ID, "menu.title"));
        crumbs.add(service.text(viewer, ShopModule.ID, "search.crumb", Msg.p("query", query)));
        return crumbs;
    }

    @Override
    protected MenuButton entry(ShopCatalog.Item item) {
        ItemStack icon = module.icon(item);
        SellInfo info = module.sellInfo(viewer, item, false);
        List<Component> lore = new ArrayList<>(ShopTooltips.grid(module, viewer, item, info, !module.bedrock(viewer), true));
        ItemStack shown = service.item(icon, service.text(viewer, ShopModule.ID, "entry.name", Msg.c("item", module.displayName(viewer, item))), lore);
        return MenuButton.of(shown, click -> {
            if (!module.refuse(click.player(), item)) {
                openChild(new BuyMenu(module, item, item.category()));
            }
        });
    }
}
