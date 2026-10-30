package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

/**
 * {@code /shop}: the categories (slots from {@code shop.yml}) and a summary with the balance in slot 4. One tap opens a
 * category (Bedrock-safe, no right/shift clicks needed).
 */
final class ShopMenu extends Menu {

    static final int SLOT_SUMMARY = 4;

    private final ShopModule module;

    ShopMenu(ShopModule module) {
        this.module = module;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, ShopModule.ID, "menu.title"));
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "shop";
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_SUMMARY, service.item(Material.EMERALD, viewer, ShopModule.ID, "menu.summary",
                module.money("balance", module.balance(viewer))));
        for (ShopCatalog.Category c : module.catalog().categories()) {
            Material icon = Material.matchMaterial(c.icon());
            int count = c.enabledItems().size();
            set(c.slot(), service.item(icon == null ? Material.CHEST : icon,
                    service.text(viewer, ShopModule.ID, "menu.category.name", Msg.c("name", CategoryMenu.name(module, viewer, c))),
                    concat(CategoryMenu.description(module, viewer, c),
                            service.lines(viewer, ShopModule.ID, "menu.category.lore", Msg.number("items", count)))),
                    click -> openChild(new CategoryMenu(module, c)));
        }
        if (module.catalog().categories().isEmpty()) {
            set(22, service.item(Material.COBWEB, viewer, ShopModule.ID, "menu.empty"));
        }
    }

    static java.util.List<Component> concat(java.util.List<Component> a, java.util.List<Component> b) {
        java.util.List<Component> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
