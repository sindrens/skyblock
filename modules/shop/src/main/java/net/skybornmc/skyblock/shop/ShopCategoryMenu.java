package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Centered;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /shop}: the category menu (GUI-P3 5.1, I9 section 2). The categories of {@code shop.yml}, in file order, centred
 * by {@link Centered}: nine = 4 rows, 5 + 4 tiles (9, 11, 13, 15, 17 / 19, 21, 23, 25); eight or fewer re-centre on 3
 * rows. Slot 4 = the wallet tile ({@code Your Money}, MENUS-V3). Bottom row: Back (when opened from another menu), the sell chest on action A, Undo on
 * action B, Close. A click opens that category's grid ({@link ShopMenu}) with Back to here.
 */
final class ShopCategoryMenu extends Menu {

    private final ShopModule module;

    ShopCategoryMenu(ShopModule module) {
        this.module = module;
    }

    private List<ShopCatalog.Category> categories() {
        List<ShopCatalog.Category> all = module.catalog().categories();
        return all.size() > Centered.MAX_SMALL ? all.subList(0, Centered.MAX_SMALL) : all;
    }

    @Override
    public int rows() {
        return Centered.layout(categories().size()).rows();
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, ShopModule.ID, "menu.title"));
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    /** GUI-FONT plaque: {@code menu.short} ({@code SHOP}). */
    @Override
    public String plaqueKey() {
        return "menu.short";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    /** Once a second: the Undo countdown and the balance (nothing else changes here). */
    @Override
    protected void tick() {
        setWallet("money", module.moneyText(module.balance(viewer)), null);
        int undoSlot = MenuLayout.actionB(rows());
        module.undoButton(viewer).ifPresentOrElse(b -> set(undoSlot, b), () -> set(undoSlot, (net.skybornmc.skyblock.common.menu.MenuButton) null));
    }

    @Override
    protected void build() {
        navBar();
        List<ShopCatalog.Category> list = categories();
        if (list.isEmpty()) {
            setEmpty(ShopModule.ID, "menu.empty");
            return;
        }
        setWallet("money", module.moneyText(module.balance(viewer)), null);
        int[] slots = Centered.layout(list.size()).slots();
        for (int i = 0; i < list.size(); i++) {
            ShopCatalog.Category c = list.get(i);
            List<Component> lore = new ArrayList<>(module.categoryLore(viewer, c));
            lore.addAll(service.lines(viewer, ShopModule.ID, "category.action"));
            set(slots[i], module.categoryIcon(viewer, c, module.categoryName(viewer, c), lore),
                    click -> openChild(new ShopMenu(module, c.id())));
        }
        int rows = rows();
        module.sellChestButton(viewer, this).ifPresent(b -> set(MenuLayout.actionA(rows), b));
        module.undoButton(viewer).ifPresent(b -> set(MenuLayout.actionB(rows), b));
    }
}
