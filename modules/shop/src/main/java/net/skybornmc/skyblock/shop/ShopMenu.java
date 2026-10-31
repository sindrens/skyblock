package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.MenuClick;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * {@code /shop} (Shop v2, SHOP_DESIGN §3): one tabbed page for every tab, no category screen.
 * <pre>
 *  0 Essentials | 1 Farming | 2 Wood | 3 Stone &amp; Earth | 4 Special | 5 Colors | 6 Decoration | 7 Redstone | 8 Spawners
 *  rows 1-4: 28 items (Colors has 2 pages)
 *  45 Back · 46 Balance · 47 Undo · 48 ? · 49 Close · 50 Main menu · 51 Sell · 52 ‹ Prev · 53 Next ›
 * </pre>
 * A tab click switches in place (same window). A plain click (or tap) on an item opens its buy page; Java shift-click buys
 * one stack straight away (only in the ? guide, never in lore). Big buys need a second click (F14).
 */
final class ShopMenu extends PagedMenu<ShopCatalog.Item> {

    static final int SLOT_BALANCE = 46;
    static final int SLOT_UNDO = 47;
    static final int SLOT_SELL = 51;

    private final ShopModule module;
    private String tabId;
    private final BigBuyGuard guard = new BigBuyGuard();

    ShopMenu(ShopModule module, String tabId) {
        this.module = module;
        this.tabId = tabId;
    }

    String tabId() {
        return tabId;
    }

    /** Switches to another tab (first page); the caller redraws. */
    void selectTab(String id) {
        if (!id.equals(tabId)) {
            tabId = id;
            page(0);
        }
        guard.disarm();
        module.rememberTab(viewer, id);
    }

    private Optional<ShopCatalog.Category> tab() {
        return module.catalog().category(tabId);
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    @Override
    public String infoKey() {
        return module.bedrock(viewer) ? "menu.info-bedrock" : "menu.info";
    }

    @Override
    public String helpTopic() {
        return "shop";
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
        module.undoButton(viewer).ifPresentOrElse(b -> set(SLOT_UNDO, b), () -> { });
    }

    @Override
    protected String emptyKey() {
        return tab().map(c -> c.kind() == ShopCatalog.Kind.SPAWNER && !module.settings.spawnersEnabled).orElse(false)
                ? "menu.spawners-off" : "menu.empty";
    }

    @Override
    protected List<Component> titleCrumbs() {
        List<Component> crumbs = new ArrayList<>();
        crumbs.add(service.text(viewer, ShopModule.ID, "menu.title"));
        tab().ifPresent(c -> crumbs.add(module.tabName(viewer, c)));
        return crumbs;
    }

    @Override
    protected List<ShopCatalog.Item> entries() {
        return tab().map(c -> c.kind() == ShopCatalog.Kind.SPAWNER && !module.settings.spawnersEnabled
                ? List.<ShopCatalog.Item>of() : c.enabledItems()).orElse(List.of());
    }

    @Override
    protected void decorate() {
        for (ShopCatalog.Category c : module.catalog().categories()) {
            set(c.slot(), module.tabButton(viewer, c, c.id().equals(tabId), click -> {
                selectTab(c.id());
                redraw();
            }));
        }
        module.decorateBar(this, viewer, (slot, button) -> set(slot, button));
    }

    @Override
    protected MenuButton entry(ShopCatalog.Item item) {
        ItemStack icon = module.icon(item);
        List<Component> extra = new ArrayList<>();
        String state = module.state(viewer, item, extra);
        List<Component> lore = new ArrayList<>(gridLore(module, viewer, item, "open".equals(state)));
        if (!"open".equals(state)) {
            lore.add(Component.empty());
            lore.addAll(extra);
            lore.addAll(service.lines(viewer, ShopModule.ID, "entry." + state));
        }
        ItemStack shown = service.item(icon, service.text(viewer, ShopModule.ID, "entry.name", Msg.c("item", module.displayName(viewer, item))), lore);
        return MenuButton.of(shown, click -> {
            if (module.refuse(click.player(), item)) {
                return;
            }
            guard.disarm();
            openChild(new BuyMenu(module, item, tabId));
        }).withShift(click -> shiftBuy(click, item));
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
        module.buy(player, item, stack, () -> afterBuy(player));
    }

    private void afterBuy(Player player) {
        if (player.isOnline() && isOpen()) {
            refresh();
        }
    }

    /**
     * The grid lore (STYLE2 LoreBuilder): | Buy Price, | Sell Price (when /sell buys it), | Information (stack price and
     * how many you can afford; spawners: how well the next one works and the payback time), then » CLICK.
     */
    static List<Component> gridLore(ShopModule module, Player viewer, ShopCatalog.Item item, boolean action) {
        long balance = module.balance(viewer);
        LoreBuilder lore = LoreBuilder.forMessages(module.core.messages());
        lore.buyPrice(module.moneyText(item.price()), true);
        OptionalLong sell = item.spawner() ? OptionalLong.empty() : module.sellPrice(item);
        if (sell.isPresent() && sell.getAsLong() > 0) {
            lore.sellPrice(module.moneyText(sell.getAsLong()), true);
        }
        int stack = module.maxStack(item);
        LoreBuilder.Section info = lore.section(module.raw("lore.info-title"));
        if (stack > 1) {
            long stackPrice = ShopMath.total(item.price(), stack).orElse(Long.MAX_VALUE);
            info.row(LoreBuilder.fill(module.raw("lore.stack"), Map.of("amount", Format.number(stack))), module.moneyText(stackPrice));
        }
        info.row(module.raw("lore.afford"), Format.number(ShopMath.affordable(item.price(), balance)));
        if (item.spawner()) {
            int percent = module.nextOutputPercent(viewer, item);
            info.row(module.raw("lore.next-works"), LoreBuilder.fill(module.raw("lore.percent"), Map.of("percent", String.valueOf(percent))));
            OptionalDouble hours = ShopMath.paybackHours(item.price(), module.incomePerHour(item), percent);
            // Owner F2: green up to 100 h, yellow up to 300 h, red "Poor deal" above (shop-settings.yml spawners.payback-*).
            ShopMath.Payback grade = ShopMath.payback(hours, module.settings.paybackGoodHours, module.settings.paybackOkHours);
            String hoursText = hours.isPresent() ? Format.number(Math.max(1, Math.round(hours.getAsDouble()))) : "";
            info.rowMini(LoreBuilder.escape(module.raw("lore.payback")), hours.isPresent()
                    ? LoreBuilder.fill(module.raw(switch (grade) {
                        case GOOD -> "lore.payback-good";
                        case OK -> "lore.payback-ok";
                        case POOR -> "lore.payback-poor";
                    }), Map.of("hours", hoursText))
                    : module.raw("lore.never"));
        }
        if (action) {
            lore.action(module.raw("lore.action-input"), module.raw("lore.action"));
        }
        return lore.components(module.core.messages(), viewer);
    }

    static Material material(ShopCatalog.Item item) {
        return item.spawner() ? Material.SPAWNER : Material.matchMaterial(item.id());
    }
}
