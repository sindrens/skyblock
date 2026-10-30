package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.menu.MenuButton;
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
import java.util.OptionalLong;

/**
 * One category as a <b>quick-buy grid</b> (SELL2, design board 4). Java: left-click buys 1, right-click buys 64,
 * shift-click opens the amount screen; Bedrock: a tap opens the amount screen ({@link QuickBuy}). Each item shows
 * {@code | Buy Price}, {@code | Sell Price} (when /sell buys it), an {@code | Information} section (stack price, how many
 * you can afford) and the bold action lines. Locked/unavailable items say why. The Sell button (51) opens the sell chest.
 */
final class CategoryMenu extends PagedMenu<ShopCatalog.Item> {

    static final int SLOT_SELL = ShopMenu.SLOT_SELL;

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
        return module.bedrock(viewer) ? "category.info-bedrock" : "category.info";
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
        module.sellButton(viewer).ifPresent(button -> set(SLOT_SELL, button, click -> module.openSell(click.player(), this)));
    }

    @Override
    protected MenuButton entry(ShopCatalog.Item item) {
        Material material = ShopModule.material(item);
        ItemStack icon = new ItemStack(material == null ? Material.BARRIER : material);
        Component itemName = material == null ? Component.text(item.id()) : ShopModule.itemName(material);
        List<Component> extra = new ArrayList<>();
        String state = state(module, viewer, item, extra);
        List<Component> lore = new ArrayList<>(lore(item, "open".equals(state)));
        if (!"open".equals(state)) {
            lore.add(Component.empty());
            lore.addAll(extra);
            lore.addAll(service.lines(viewer, ShopModule.ID, "category.entry." + state));
        }
        ItemStack shown = service.item(icon, service.text(viewer, ShopModule.ID, "category.entry.name", Msg.c("item", itemName)), lore);
        return MenuButton.of(shown, click -> {
            Player player = click.player();
            String now = state(module, player, item, new ArrayList<>());
            if (!"open".equals(now)) {
                module.fail(player, "unavailable".equals(now) ? "buy.unavailable" : "buy.locked-click");
                return;
            }
            QuickBuy.Action action = QuickBuy.action(click.type(), module.bedrock(player));
            switch (action) {
                case PICK_AMOUNT -> openChild(new BuyAmountMenu(module, item, module.settings.startAmount));
                case BUY_ONE, BUY_STACK -> module.attempt(this, player, item,
                        QuickBuy.amount(action, module.settings.leftAmount, module.settings.rightAmount), () -> afterBuy(player));
                default -> {
                }
            }
        });
    }

    /** V2 lore (STYLE2 LoreBuilder): prices, | Information, then the action lines (only when the item can be bought). */
    private List<Component> lore(ShopCatalog.Item item, boolean actions) {
        long balance = module.balance(viewer);
        LoreBuilder lore = LoreBuilder.forMessages(module.core.messages());
        lore.buyPrice(module.moneyText(item.price()), true);
        OptionalLong sell = module.sellPrice(item);
        if (sell.isPresent() && sell.getAsLong() > 0) {
            lore.sellPrice(module.moneyText(sell.getAsLong()), true);
        }
        int stack = module.settings.rightAmount;
        long stackPrice = ShopMath.total(item.price(), stack).orElse(Long.MAX_VALUE);
        lore.section(module.raw("quick.info-title"))
                .row(LoreBuilder.fill(module.raw("quick.stack"), Map.of("amount", Format.number(stack))), module.moneyText(stackPrice))
                .row(module.raw("quick.afford"), Format.number(ShopMath.affordable(item.price(), balance)));
        if (actions) {
            if (module.bedrock(viewer)) {
                lore.action(module.raw("quick.action.tap-input"), module.raw("quick.action.tap"));
            } else {
                lore.action(module.raw("quick.action.left-input"), LoreBuilder.fill(module.raw("quick.action.left"),
                        Map.of("amount", Format.number(module.settings.leftAmount))));
                lore.action(module.raw("quick.action.right-input"), LoreBuilder.fill(module.raw("quick.action.right"),
                        Map.of("amount", Format.number(module.settings.rightAmount))));
                lore.action(module.raw("quick.action.shift-input"), module.raw("quick.action.shift"));
            }
        }
        return lore.components(module.core.messages(), viewer);
    }

    /** Fresh "You can afford" numbers after a purchase (or back here after a confirm screen). */
    private void afterBuy(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (isOpen()) {
            refresh();
        } else if (module.core.menus().openMenu(player) == null && module.problem(player).isEmpty()) {
            service.open(player, this);
        }
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
