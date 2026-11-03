package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.MenuClick;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The item grid of one shop category (GUI-P3 5.2, MENUS-V3 6.2): 6 rows, the grouped 7-wide grid ({@link ShopGrid}: one
 * group per row, centred, whole groups per page), page arrows, no tab row. 4 the wallet tile ({@code Your Money}) · 45
 * Back (to the categories) · 46 sell chest · 47 Undo · 49 Close · 52/53 pages.
 * <p>Clicks on a tile ({@code ClickRoute}): left / Bedrock tap = buy GUI; right = sell GUI (error sound when not
 * sellable); Shift + Left = buy one stack (a big buy arms first); middle or Shift + Right = sell every one you carry.</p>
 */
final class ShopMenu extends PagedMenu<ShopCatalog.Item> {

    /** Owner 3 Nov 2026: the shop is plain vanilla (no frame layer, no tinted panels). */
    @Override
    public boolean panel() {
        return false;
    }

    private final ShopModule module;
    private final String categoryId;
    private final BigBuyGuard guard = new BigBuyGuard();

    ShopMenu(ShopModule module, String categoryId) {
        this.module = module;
        this.categoryId = categoryId;
    }

    String categoryId() {
        return categoryId;
    }

    private Optional<ShopCatalog.Category> category() {
        return module.catalog().category(categoryId);
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    protected void tick() {
        if (guard.expired(System.currentTimeMillis())) {
            guard.disarm();
        }
        module.undoButton(viewer).ifPresentOrElse(b -> set(SLOT_ACTION_B, b), () -> set(SLOT_ACTION_B, (net.skybornmc.skyblock.common.menu.MenuButton) null));
    }

    /** GUI-FONT plaque: the tab's short noun ({@code categories.<id>.short}, e.g. {@code FARMING}). */
    @Override
    public String plaqueKey() {
        return "categories." + categoryId + ".short";
    }

    /**
     * MENUS-V3 6.2: the grouped grid ({@link ShopGrid}): one group per row, centred, a page holds whole groups. Without
     * {@code groups:} in shop.yml the tab is one group in file order.
     */
    @Override
    protected net.skybornmc.skyblock.common.menu.PageLayout<ShopCatalog.Item> pageLayout() {
        return entries -> {
            java.util.Map<String, ShopCatalog.Item> shown = new java.util.LinkedHashMap<>();
            entries.forEach(i -> shown.put(i.id(), i));
            List<ShopCatalog.Group> groups = category().map(ShopCatalog.Category::displayGroups).orElse(List.of());
            List<net.skybornmc.skyblock.common.menu.PageLayout.Page<ShopCatalog.Item>> out = new ArrayList<>();
            for (ShopGrid.Page page : ShopGrid.pages(groups, shown::get)) {
                out.add(new net.skybornmc.skyblock.common.menu.PageLayout.Page<>(page.items(), page.slots()));
            }
            if (out.isEmpty()) {
                out.add(new net.skybornmc.skyblock.common.menu.PageLayout.Page<>(List.of(), new int[0]));
            }
            return out;
        };
    }

    @Override
    protected String emptyKey() {
        return category().map(c -> c.kind() == ShopCatalog.Kind.SPAWNER && !module.settings.spawnersEnabled).orElse(false)
                ? "menu.spawners-off" : "menu.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        List<Component> crumbs = new ArrayList<>();
        crumbs.add(service.text(viewer, ShopModule.ID, "menu.title"));
        category().ifPresent(c -> crumbs.add(module.categoryCrumb(viewer, c)));
        return crumbs;
    }

    @Override
    protected List<ShopCatalog.Item> entries() {
        return category().map(c -> c.kind() == ShopCatalog.Kind.SPAWNER && !module.settings.spawnersEnabled
                ? List.<ShopCatalog.Item>of() : c.enabledItems()).orElse(List.of());
    }

    @Override
    protected void decorate() {
        setWallet("money", module.moneyText(module.balance(viewer)), null);
        module.sellChestButton(viewer, this).ifPresent(b -> set(SLOT_ACTION_A, b));
        module.undoButton(viewer).ifPresentOrElse(b -> set(SLOT_ACTION_B, b), () -> set(SLOT_ACTION_B, (net.skybornmc.skyblock.common.menu.MenuButton) null));
    }

    @Override
    protected MenuButton entry(ShopCatalog.Item item) {
        ItemStack icon = module.icon(item);
        List<Component> extra = new ArrayList<>();
        String state = module.state(viewer, item, extra);
        boolean open = "open".equals(state);
        SellInfo info = module.sellInfo(viewer, item, false);
        List<Component> lore = new ArrayList<>(ShopTooltips.grid(module, viewer, item, info, !module.bedrock(viewer), open));
        if (!open) {
            lore.add(Component.empty());
            lore.addAll(extra);
            lore.addAll(service.lines(viewer, ShopModule.ID, "entry." + state));
        }
        ItemStack shown = service.item(icon, service.text(viewer, ShopModule.ID, "entry.name", Msg.c("item", module.displayName(viewer, item))), lore);
        return MenuButton.of(shown, click -> openBuy(click.player(), item))
                .withRight(click -> openSell(click.player(), item))
                .withShift(click -> shiftBuy(click, item))
                .withMiddle(click -> module.sellAll(click.player(), item, () -> afterChange(click.player())));
    }

    private void openBuy(Player player, ShopCatalog.Item item) {
        if (module.refuse(player, item)) {
            return;
        }
        guard.disarm();
        openChild(new BuyMenu(module, item, categoryId));
    }

    /** Right click: the sell GUI, or only the error sound when the item can't be sold (D8). */
    private void openSell(Player player, ShopCatalog.Item item) {
        if (!module.sellInfo(player, item, false).sellable()) {
            service.sound(player, "error");
            return;
        }
        guard.disarm();
        openChild(new SellItemMenu(module, item, categoryId));
    }

    /** Java shift-click: one stack straight away (a big buy arms first and needs a second shift-click). */
    private void shiftBuy(MenuClick click, ShopCatalog.Item item) {
        Player player = click.player();
        if (module.refuse(player, item)) {
            return;
        }
        int stack = module.maxStack(item);
        long now = System.currentTimeMillis();
        long total = module.plannedTotal(player, item, stack);
        boolean big = module.needsSecondClick(player, total);
        if (!guard.click("shift:" + item.id(), big, now, module.settings.bigBuyArmSeconds * 1000L)) {
            module.armedNotice(player, item, total);
            return;
        }
        module.buy(player, item, stack, () -> afterChange(player));
    }

    /** After a buy or a sale: the balance and the Undo tile change. */
    private void afterChange(Player player) {
        if (player.isOnline() && isOpen()) {
            refresh();
        }
    }

    static Material material(ShopCatalog.Item item) {
        return item.spawner() ? Material.SPAWNER : Material.matchMaterial(item.id());
    }
}
