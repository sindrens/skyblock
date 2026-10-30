package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The amount screen of the quick-buy shop (SELL2, design board 4): shift-click on Java, a tap on Bedrock. Taps only.
 * <pre>
 *  R O B B B B B O R
 *  B · · · · · · · B
 *  B −64 −16 −1 [item] +1 +16 +64 B      19-21 / 22 / 23-25 (steps from buy.picker-steps)
 *  B · · · [Type an amount] · · · B      31
 *  B ✘ · ✔ [Buy N for $X] ✔ · · B       37 Cancel, 39-41 Buy
 *  ← O B ?  ✘  H B O R
 * </pre>
 * Buying more than fits buys what fits (charged only for that); purchases never ask to confirm (owner answer 8).
 */
final class BuyAmountMenu extends Menu {

    static final int SLOT_ITEM = 22;
    static final int SLOT_TYPE = 31;
    static final int SLOT_CANCEL = 37;
    static final int[] BUY_SLOTS = {39, 40, 41};
    private static final int[] MINUS_SLOTS = {21, 20, 19};
    private static final int[] PLUS_SLOTS = {23, 24, 25};

    private final ShopModule module;
    private final ShopCatalog.Item item;
    private long value;

    BuyAmountMenu(ShopModule module, ShopCatalog.Item item, long start) {
        this.module = module;
        this.item = item;
        this.value = clamp(start, module.settings.maxAmount);
    }

    static long clamp(long value, long max) {
        return Math.max(1, Math.min(Math.max(1, max), value));
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, ShopModule.ID, "menu.title"),
                service.text(viewer, ShopModule.ID, "amount.title", Msg.c("item", name())));
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    @Override
    public String infoKey() {
        return "amount.info";
    }

    @Override
    public String helpTopic() {
        return "shop";
    }

    long value() {
        return value;
    }

    private Material material() {
        Material m = ShopModule.material(item);
        return m == null ? Material.BARRIER : m;
    }

    private Component name() {
        return ShopModule.itemName(material());
    }

    @Override
    protected void build() {
        frame();
        long[] steps = module.settings.steps();
        for (int i = 0; i < Math.min(3, steps.length); i++) {
            long step = steps[i];
            set(MINUS_SLOTS[i], stepItem(Material.RED_DYE, "amount.minus", step), click -> change(-step));
            set(PLUS_SLOTS[i], stepItem(Material.LIME_DYE, "amount.plus", step), click -> change(step));
        }
        long fits = InventorySpace.fits(viewer.getInventory(), new ItemStack(material()));
        long balance = module.balance(viewer);
        long total = ShopMath.total(item.price(), value).orElse(Long.MAX_VALUE);
        ShopMath.BuyPlan plan = ShopMath.plan(value, fits, module.settings.maxAmount);

        ItemStack shown = new ItemStack(material(), (int) Math.max(1, Math.min(material().getMaxStackSize(), value)));
        List<Component> lore = new ArrayList<>(service.lines(viewer, ShopModule.ID, "amount.item.lore",
                Msg.number("amount", value), module.money("price", item.price()), module.money("total", total),
                Msg.number("fits", fits), module.money("balance", balance),
                Msg.number("afford", ShopMath.affordable(item.price(), balance))));
        set(SLOT_ITEM, service.item(shown, service.text(viewer, ShopModule.ID, "amount.item.name", Msg.c("item", name())), lore));

        set(SLOT_TYPE, service.item(Material.NAME_TAG, viewer, ShopModule.ID, "amount.type",
                Msg.number("max", module.settings.maxAmount)), click -> ask(click.player()));
        set(SLOT_CANCEL, service.item(Material.RED_CONCRETE, viewer, ShopModule.ID, "amount.cancel"), click -> back());

        String state = plan.amount() == 0 ? "no-room" : balance < total ? "too-expensive" : plan.partial() ? "partial" : "ok";
        List<Component> buyLore = new ArrayList<>(service.lines(viewer, ShopModule.ID, "amount.buy.lore",
                Msg.number("amount", value), module.money("price", item.price()), module.money("total", total)));
        buyLore.addAll(service.lines(viewer, ShopModule.ID, "amount.buy." + state, Msg.number("fits", plan.amount()),
                module.money("balance", balance)));
        Material buyMaterial = "ok".equals(state) || "partial".equals(state) ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
        ItemStack buy = service.item(new ItemStack(buyMaterial), service.text(viewer, ShopModule.ID, "amount.buy.name",
                Msg.number("amount", value), module.money("total", total)), buyLore);
        for (int slot : BUY_SLOTS) {
            set(slot, buy, click -> module.attempt(this, click.player(), item, (int) Math.min(Integer.MAX_VALUE, value),
                    () -> reopen(click.player())));
        }
    }

    private ItemStack stepItem(Material material, String key, long step) {
        ItemStack stack = service.item(material, viewer, ShopModule.ID, key, Msg.p("step", Format.number(step)));
        stack.setAmount((int) Math.max(1, Math.min(64, step)));
        return stack;
    }

    private void change(long delta) {
        long next = clamp(value + delta, module.settings.maxAmount);
        if (next == value) {
            service.sound(viewer, "error");
            return;
        }
        value = next;
        refresh();
    }

    /** "Type an amount": a chat prompt (accepts 1.5k), then back to this screen. */
    private void ask(Player player) {
        long max = module.settings.maxAmount;
        service.prompt(player, ShopModule.ID, "amount.ask", 16, text -> {
            try {
                long parsed = Format.parseAmount(text);
                if (parsed < 1 || parsed > max) {
                    throw new IllegalArgumentException("out of range");
                }
                value = parsed;
            } catch (IllegalArgumentException e) {
                module.fail(player, "amount.invalid", Msg.number("max", max));
            }
            reopenAlways(player);
        }, () -> reopenAlways(player), Msg.number("max", max));
    }

    private void reopenAlways(Player player) {
        if (player.isOnline() && module.problem(player).isEmpty()) {
            service.open(player, this);
        }
    }

    /** After a purchase: fresh numbers here, or back here after a confirm screen. */
    private void reopen(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (isOpen()) {
            refresh();
        } else if (module.core.menus().openMenu(player) == null && module.problem(player).isEmpty()) {
            service.open(player, this);
        }
    }
}
