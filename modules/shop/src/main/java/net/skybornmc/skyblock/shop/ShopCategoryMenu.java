package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Centered;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /shop}: the category menu (GUI-P3 5.1; plan 4 Nov C §3 "more air"). The categories of {@code shop.yml}, in file
 * order, laid out by {@link Centered#airy}: up to 7 on row 1 of a 3-row menu (up to 4 with an empty column between
 * them); 8 to 14 on rows 1 and 3 of a 6-row menu with an empty row between (nine = 5 + 4). Plain vanilla tab icons.
 * Slot 4 = the wallet tile ({@code Your Money}, MENUS-V3). Bottom row: Back (when opened from another menu), the sell
 * chest on action A, Undo on action B, Close, Help. A click opens that category's grid ({@link ShopMenu}) with Back here.
 */
final class ShopCategoryMenu extends Menu {

    /** Owner 3 Nov 2026: the shop is plain vanilla (no frame layer, no tinted panels). */
    @Override
    public boolean panel() {
        return false;
    }

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
        return Centered.airy(categories().size()).rows();
    }

    /**
     * Plan 4 Nov C §3: at most 7 tabs per row, centred, an empty row between two rows ({@link Centered#airy}). Pure.
     */
    static int[] tabSlots(int n) {
        return Centered.airy(n).slots();
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
        int[] slots = tabSlots(list.size());
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
        helpButton();
    }
}
