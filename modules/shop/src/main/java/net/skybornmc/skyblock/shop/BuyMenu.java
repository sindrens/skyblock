package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Centered;
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
 * The buy GUI (GUI-P3 5.3, I9 section 12): 3 rows, the same layout as the sell GUI ({@link SellItemMenu}).
 * <pre>
 *  4 the item (buy and sell price, stack price, you can afford, room for, balance)
 *  row 1, centred: Buy 1 · Buy 16 (8 for 16-stackers) · Buy 64 · Fill inventory · Type an amount   (9 11 13 15 17)
 *                  items that don't stack: Buy 1 · Type an amount                                  (12 14)
 *  18 Back (to the grid) · 19 Sell (the sell GUI of this item) · 20 Undo · 22 Close
 * </pre>
 * The page stays open after a buy and the numbers refresh. A buy over half your money arms its button (orange, F14) and
 * needs a second click within 5 s. Type an amount: a chat prompt on Java (1.5k works); the number picker on Bedrock
 * (taps, with its own Type button).
 */
final class BuyMenu extends Menu {

    /** Owner 3 Nov 2026: the shop is plain vanilla (no frame layer, no tinted panels). */
    @Override
    public boolean panel() {
        return false;
    }

    static final int SLOT_ITEM = SLOT_SUMMARY;
    /** Bottom row, 3 rows: switch to the sell GUI (action A) and Undo (action B). */
    static final int SLOT_SWITCH = 19;
    static final int SLOT_UNDO = 20;

    private final ShopModule module;
    private final ShopCatalog.Item item;
    private final String categoryId;
    private final BigBuyGuard guard = new BigBuyGuard();
    /** A typed amount waiting for its second click (big buys only); 0 = none. */
    private long typed;

    BuyMenu(ShopModule module, ShopCatalog.Item item, String categoryId) {
        this.module = module;
        this.item = item;
        this.categoryId = categoryId;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, ShopModule.ID, "menu.title"), module.displayName(viewer, item));
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    /** GUI-FONT plaque (MENUS-V3 6.4): {@code buy.short} ({@code BUY}). */
    @Override
    public String plaqueKey() {
        return "buy.short";
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
            module.undoButton(viewer).ifPresent(b -> set(SLOT_UNDO, b));
        }
    }

    @Override
    protected void build() {
        navBar();
        long now = System.currentTimeMillis();
        long balance = module.balance(viewer);
        long fits = module.fits(viewer, item);
        int maxStack = module.maxStack(item);
        ItemStack base = module.icon(item);
        base.setAmount(1);
        set(SLOT_ITEM, service.item(base, service.text(viewer, ShopModule.ID, "buy.item.name",
                Msg.c("item", module.displayName(viewer, item))), itemLore(balance, fits, maxStack)));

        List<MenuButton> choices = new ArrayList<>();
        for (int amount : ShopMath.quickAmounts(maxStack)) {
            choices.add(buyButton("amount:" + amount, amount, balance, fits, now, "buy.quick"));
        }
        if (maxStack > 1) {
            long fill = ShopMath.fillAmount(item.price(), balance, fits, module.settings.maxAmount);
            if (fill > 0) {
                MenuButton b = buyButton("fill", fill, balance, fits, now, "buy.fill");
                choices.add(fillLook(b));
            } else {
                String why = fits < 1 ? "no-room" : "too-expensive";
                choices.add(MenuButton.display(service.item(Material.GRAY_DYE, viewer, ShopModule.ID, "buy.fill-none." + why)));
            }
        }
        if (typed > 0 && guard.armed("typed", now)) {
            choices.add(buyButton("typed", typed, balance, fits, now, "buy.typed"));
        } else {
            typed = 0;
            TagResolver max = Msg.number("max", module.settings.maxAmount);
            choices.add(MenuButton.of(service.icon(viewer, "ui/type", service.text(viewer, ShopModule.ID, "buy.type.name", max),
                    service.lines(viewer, ShopModule.ID, "buy.type.lore", max)), click -> askAmount(click.player())));
        }
        int[] columns = Centered.columns(choices.size());
        for (int i = 0; i < choices.size(); i++) {
            set(9 + columns[i], choices.get(i));
        }

        if (module.sellService().isPresent()) {
            set(SLOT_SWITCH, service.icon(viewer, "shop/sell", service.text(viewer, ShopModule.ID, "buy.switch-sell.name"),
                    service.lines(viewer, ShopModule.ID, "buy.switch-sell.lore")), click -> {
                        guard.disarm();
                        module.switchTo(this, new SellItemMenu(module, item, categoryId));
                    });
        }
        module.undoButton(viewer).ifPresent(b -> set(SLOT_UNDO, b));
    }

    /** Fill inventory keeps its own icon ({@code shop/fill}, chest) while it can buy; the armed look stays orange. */
    private MenuButton fillLook(MenuButton button) {
        ItemStack shown = button.item();
        if (shown.getType() != Material.LIME_CONCRETE) {
            return button;
        }
        ItemStack icon = service.icon(viewer, "shop/fill", shown.getItemMeta().displayName(), shown.getItemMeta().lore());
        icon.setAmount(shown.getAmount());
        return new MenuButton(icon, button.onClick());
    }

    private List<Component> itemLore(long balance, long fits, int maxStack) {
        List<Component> lore = new ArrayList<>(ShopTooltips.buyTile(module, viewer, item, module.sellInfo(viewer, item, false)));
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
        boolean buyable = "ok".equals(state) || "partial".equals(state);
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
        Component name = service.text(viewer, ShopModule.ID, textKey + ".name", Msg.number("amount", amount), module.money("total", total));
        // Lime concrete with our confirm icon when it can be bought; orange (armed, second click) and grey stay plain.
        ItemStack stack = armed ? service.item(new ItemStack(Material.ORANGE_CONCRETE), name, lore)
                : buyable ? service.icon(viewer, "state/confirm", name, lore)
                : service.item(new ItemStack(Material.GRAY_CONCRETE), name, lore);
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
}
