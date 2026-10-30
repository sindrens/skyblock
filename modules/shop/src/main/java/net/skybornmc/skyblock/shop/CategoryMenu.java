package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One category's items with their price; a tap opens the item page (amounts). Locked/unavailable items say why. */
final class CategoryMenu extends PagedMenu<ShopCatalog.Item> {

    private final ShopModule module;
    private final String categoryId;

    CategoryMenu(ShopModule module, ShopCatalog.Category category) {
        this.module = module;
        this.categoryId = category.id();
    }

    /** The category as it is now (after a reload it may have changed or gone). */
    private Optional<ShopCatalog.Category> category() {
        return module.catalog().category(categoryId);
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    @Override
    public String infoKey() {
        return "category.info";
    }

    @Override
    public String helpTopic() {
        return "shop";
    }

    @Override
    protected String emptyKey() {
        return "category.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        List<Component> crumbs = new ArrayList<>();
        crumbs.add(service.text(viewer, ShopModule.ID, "menu.title"));
        category().ifPresent(c -> crumbs.add(name(module, viewer, c)));
        return crumbs;
    }

    @Override
    protected List<ShopCatalog.Item> entries() {
        return category().map(ShopCatalog.Category::enabledItems).orElse(List.of());
    }

    @Override
    protected void decorate() {
        set(ShopMenu.SLOT_SUMMARY, service.item(Material.EMERALD, viewer, ShopModule.ID, "menu.summary",
                module.money("balance", module.balance(viewer))));
    }

    @Override
    protected MenuButton entry(ShopCatalog.Item item) {
        Material material = ShopModule.material(item);
        ItemStack icon = new ItemStack(material == null ? Material.BARRIER : material);
        Component itemName = material == null ? Component.text(item.id()) : ShopModule.itemName(material);
        List<Component> lore = new ArrayList<>(service.lines(viewer, ShopModule.ID, "category.entry.lore",
                module.money("price", item.price()), module.money("stack", ShopMath.total(item.price(), 64).orElse(Long.MAX_VALUE))));
        String state = state(module, viewer, item, lore);
        lore.addAll(service.lines(viewer, ShopModule.ID, "category.entry." + state));
        ItemStack shown = service.item(icon, service.text(viewer, ShopModule.ID, "category.entry.name", Msg.c("item", itemName)), lore);
        return MenuButton.of(shown, click -> {
            String now = state(module, viewer, item, new ArrayList<>());
            if (!"open".equals(now)) {
                module.fail(viewer, "unavailable".equals(now) ? "buy.unavailable" : "buy.locked-click");
                return;
            }
            openChild(new ItemMenu(module, item));
        });
    }

    /**
     * {@code open}, {@code locked} (adds the requirement lines to {@code lore}) or {@code unavailable} (price at or below
     * the /sell value).
     */
    static String state(ShopModule module, Player viewer, ShopCatalog.Item item, List<Component> lore) {
        if (!module.priceSafe(item)) {
            return "unavailable";
        }
        Optional<Map.Entry<String, Integer>> missing = module.missingRequirement(viewer, item);
        if (missing.isPresent()) {
            lore.addAll(module.core.menus().lines(viewer, ShopModule.ID, "category.entry.requires",
                    Msg.c("skill", module.skillName(viewer, missing.get().getKey())), Msg.number("level", missing.get().getValue())));
            return "locked";
        }
        return "open";
    }

    static Component name(ShopModule module, Player viewer, ShopCatalog.Category c) {
        String key = "categories." + c.id() + ".name";
        return module.msg.has(key) ? module.core.menus().text(viewer, ShopModule.ID, key) : Component.text(words(c.id()));
    }

    static List<Component> description(ShopModule module, Player viewer, ShopCatalog.Category c) {
        return module.core.menus().lines(viewer, ShopModule.ID, "categories." + c.id() + ".lore");
    }

    static String words(String id) {
        StringBuilder out = new StringBuilder();
        for (String part : id.split("[-_]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
