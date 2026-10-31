package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.menu.MenuIcons;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.NumberPickerMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The buy page (Shop v2, SHOP_DESIGN §4): the same tabs and bottom bar as the shop, one plain click per button (Java and
 * Bedrock alike).
 * <pre>
 *  row 0: the 9 tabs (a click goes back to that tab)
 *  13 the item (price each, stack price, you can afford, room for)
 *  20 Buy 1 · 22 Buy 16 (8 for 16-stackers) · 24 Buy 64 (one stack)
 *  30 Fill inventory · 32 Type an amount            (items that don't stack: only Buy 1 and Type an amount)
 *  45 Back · 46 Balance · 47 Undo · 48 ? · 49 Close · 50 Main menu · 51 Sell
 * </pre>
 * The page stays open after a buy and the numbers refresh. A buy over half your money arms its button (orange, F14) and
 * needs a second click within 5 s. Type an amount: a chat prompt on Java (1.5k works); the number picker on Bedrock
 * (taps, with its own Type button).
 */
final class BuyMenu extends Menu {

    static final int SLOT_ITEM = 13;
    static final int[] QUICK_SLOTS = {20, 22, 24};
    static final int SLOT_FILL = 30;
    static final int SLOT_TYPE = 32;

    private final ShopModule module;
    private final ShopCatalog.Item item;
    private final String tabId;
    private final BigBuyGuard guard = new BigBuyGuard();
    /** A typed amount waiting for its second click (big buys only); 0 = none. */
    private long typed;

    BuyMenu(ShopModule module, ShopCatalog.Item item, String tabId) {
        this.module = module;
        this.item = item;
        this.tabId = tabId;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, ShopModule.ID, "menu.title"), module.displayName(viewer, item));
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    @Override
    public String infoKey() {
        return module.bedrock(viewer) ? "buy.info-bedrock" : "buy.info";
    }

    @Override
    public String helpTopic() {
        return "shop";
    }

    @Override
    public int tickInterval() {
        return 10;
    }

    @Override
    protected void tick() {
        if (guard.expired(System.currentTimeMillis())) {
            guard.disarm();
            typed = 0;
            refresh();
            return;
        }
        if (ticksOpen() % 20 == 0) {
            module.undoButton(viewer).ifPresent(b -> set(ShopMenu.SLOT_UNDO, b));
        }
    }

    @Override
    protected void build() {
        frame();
        for (ShopCatalog.Category c : module.catalog().categories()) {
            set(c.slot(), module.tabButton(viewer, c, c.id().equals(tabId), click -> toTab(click.player(), c.id())));
        }
        module.decorateBar(this, viewer, (slot, button) -> set(slot, button));

        long now = System.currentTimeMillis();
        long balance = module.balance(viewer);
        long fits = module.fits(viewer, item);
        int maxStack = module.maxStack(item);
        ItemStack base = module.icon(item);
        base.setAmount(1);
        set(SLOT_ITEM, service.item(base, service.text(viewer, ShopModule.ID, "buy.item.name",
                Msg.c("item", module.displayName(viewer, item))), itemLore(balance, fits, maxStack)));

        int[] amounts = ShopMath.quickAmounts(maxStack);
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i];
            set(QUICK_SLOTS[i], buyButton("amount:" + amount, amount, balance, fits, now, "buy.quick"));
        }
        if (maxStack > 1) {
            long fill = ShopMath.fillAmount(item.price(), balance, fits, module.settings.maxAmount);
            if (fill > 0) {
                set(SLOT_FILL, buyButton("fill", fill, balance, fits, now, "buy.fill"));
            } else {
                String why = fits < 1 ? "no-room" : "too-expensive";
                set(SLOT_FILL, service.item(Material.GRAY_DYE, viewer, ShopModule.ID, "buy.fill-none." + why));
            }
        }
        if (typed > 0 && guard.armed("typed", now)) {
            set(SLOT_TYPE, buyButton("typed", typed, balance, fits, now, "buy.typed"));
        } else {
            typed = 0;
            set(SLOT_TYPE, MenuIcons.tag(service.item(Material.NAME_TAG, viewer, ShopModule.ID, "buy.type",
                    Msg.number("max", module.settings.maxAmount)), MenuIcons.TYPE_AMOUNT), click -> askAmount(click.player()));
        }
    }

    /** The plate art exists for Buy 1, 16 and 64; other amounts (8 on 16-stackers) keep the vanilla item. */
    static String quickIcon(long amount) {
        return amount == 1 ? MenuIcons.BUY_1 : amount == 16 ? MenuIcons.BUY_16 : amount == 64 ? MenuIcons.BUY_64 : null;
    }

    private List<Component> itemLore(long balance, long fits, int maxStack) {
        List<Component> lore = new ArrayList<>(ShopMenu.gridLore(module, viewer, item, false));
        lore.addAll(service.lines(viewer, ShopModule.ID, "buy.item.room", Msg.number("fits", fits),
                module.money("balance", balance)));
        if (maxStack <= 1) {
            lore.addAll(service.lines(viewer, ShopModule.ID, "buy.item.no-stack"));
        }
        return lore;
    }

    /**
     * One buy button: lime when it can be bought (the item count shows the amount), grey with the reason when not, orange
     * while armed (big buy, F14).
     */
    private MenuButton buyButton(String key, long amount, long balance, long fits, long now, String textKey) {
        long count = Math.max(0, Math.min(amount, Math.min(fits, module.settings.maxAmount)));
        long total = ShopMath.total(item.price(), Math.max(1, count)).orElse(Long.MAX_VALUE);
        String state = fits < 1 ? "no-room" : total > balance ? "too-expensive" : count < amount ? "partial" : "ok";
        boolean armed = guard.armed(key, now);
        Material material = armed ? Material.ORANGE_CONCRETE
                : "ok".equals(state) || "partial".equals(state) ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
        List<Component> lore = new ArrayList<>(service.lines(viewer, ShopModule.ID, "buy.button.lore",
                Msg.number("amount", count), module.money("price", item.price()), module.money("total", total)));
        if (armed) {
            lore.addAll(service.lines(viewer, ShopModule.ID, "buy.button.armed",
                    Msg.number("percent", ShopMath.percentOf(total, balance)),
                    module.money("after", Math.max(0, balance - total))));
        } else {
            lore.addAll(service.lines(viewer, ShopModule.ID, "buy.button." + state, Msg.number("fits", count),
                    module.money("balance", balance)));
        }
        ItemStack stack = service.item(new ItemStack(material), service.text(viewer, ShopModule.ID, textKey + ".name",
                Msg.number("amount", amount), module.money("total", total)), lore);
        if (material == Material.LIME_CONCRETE) { // the green plates only; grey (can't buy) and orange (armed) stay as they are
            MenuIcons.tag(stack, "fill".equals(key) ? MenuIcons.FILL : quickIcon(amount));
        }
        stack.setAmount((int) Math.max(1, Math.min(64, amount)));
        if (armed) {
            stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return MenuButton.of(stack, click -> press(click.player(), key, amount));
    }

    /** A buy button click: arms a big buy first (F14), otherwise buys and refreshes this page. */
    private void press(Player player, String key, long amount) {
        if (module.refuse(player, item)) {
            return;
        }
        long now = System.currentTimeMillis();
        int want = (int) Math.min(Integer.MAX_VALUE, amount);
        long total = module.plannedTotal(player, item, want);
        boolean big = module.needsSecondClick(player, total);
        if (!guard.click(key, big, now, module.settings.bigBuyArmSeconds * 1000L)) {
            service.sound(player, "notify");
            refresh();
            return;
        }
        typed = 0;
        module.buy(player, item, want, () -> afterBuy(player));
    }

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

    /** Type an amount: Java types it in chat; Bedrock gets the number picker (taps, with its own Type button). */
    private void askAmount(Player player) {
        guard.disarm();
        long max = module.settings.maxAmount;
        if (module.bedrock(player)) {
            openChild(new NumberPickerMenu(p -> service.text(p, ShopModule.ID, "buy.picker-title",
                    Msg.c("item", module.displayName(p, item))), 1, max, 1, new long[]{1, 16, 64},
                    (p, value) -> typedAmount(p, value)));
            return;
        }
        service.prompt(player, ShopModule.ID, "buy.ask", 16, text -> {
            long parsed;
            try {
                parsed = Format.parseAmount(text);
            } catch (IllegalArgumentException e) {
                parsed = -1;
            }
            if (parsed < 1 || parsed > max) {
                module.fail(player, "buy.invalid", Msg.number("max", max));
                reopen(player);
                return;
            }
            typedAmount(player, parsed);
        }, () -> reopen(player), Msg.number("max", max));
    }

    /** A typed amount: buys now, or (big buy) shows it armed on the Type button for the second click. */
    private void typedAmount(Player player, long value) {
        if (!player.isOnline()) {
            return;
        }
        reopen(player);
        if (module.refuse(player, item)) {
            return;
        }
        int want = (int) Math.min(Integer.MAX_VALUE, value);
        long total = module.plannedTotal(player, item, want);
        if (module.needsSecondClick(player, total)) {
            typed = value;
            guard.click("typed", true, System.currentTimeMillis(), module.settings.bigBuyArmSeconds * 1000L);
            service.sound(player, "notify");
            if (isOpen()) {
                refresh();
            }
            return;
        }
        module.buy(player, item, want, () -> afterBuy(player));
    }

    private void reopen(Player player) {
        if (player.isOnline() && module.problem(player).isEmpty() && !isOpen()) {
            service.open(player, this);
        }
    }

    /** A tab click on the buy page: back to the shop page of that tab. */
    private void toTab(Player player, String id) {
        if (parent() instanceof ShopMenu shop) {
            shop.selectTab(id);
            back();
        } else {
            module.rememberTab(player, id);
            ShopMenu shop = new ShopMenu(module, id);
            service.open(player, shop);
        }
    }
}
