package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.NumberPickerMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One item's page: the item and its price (slot 13), quick-buy buttons ×1/×16/×64 ({@code buy.amounts}) and
 * "Custom amount" (number picker, up to what fits and {@code buy.max-amount}). Purchases from {@code buy.confirm-above}
 * ask to confirm. Taps only (Bedrock).
 */
final class ItemMenu extends Menu {

    static final int SLOT_ITEM = 13;
    static final int SLOT_CUSTOM = 31;

    private final ShopModule module;
    private final ShopCatalog.Item item;

    ItemMenu(ShopModule module, ShopCatalog.Item item) {
        this.module = module;
        this.item = item;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, ShopModule.ID, "menu.title"), name());
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    @Override
    public String infoKey() {
        return "item.info";
    }

    @Override
    public String helpTopic() {
        return "shop";
    }

    private Material material() {
        Material m = ShopModule.material(item);
        return m == null ? Material.BARRIER : m;
    }

    private Component name() {
        return ShopModule.itemName(material());
    }

    /** Quick-buy slots for 1, 2 or 3 buttons (centered in row 3). */
    static int[] amountSlots(int count) {
        return switch (count) {
            case 1 -> new int[]{22};
            case 2 -> new int[]{21, 23};
            default -> new int[]{20, 22, 24};
        };
    }

    @Override
    protected void build() {
        frame();
        long fits = InventorySpace.fits(viewer.getInventory(), new ItemStack(material()));
        long balance = module.balance(viewer);
        set(ShopMenu.SLOT_SUMMARY, service.item(Material.EMERALD, viewer, ShopModule.ID, "menu.summary",
                module.money("balance", balance)));
        List<Component> shownLore = new java.util.ArrayList<>(service.lines(viewer, ShopModule.ID, "item.shown.lore",
                module.money("price", item.price()), Msg.number("fits", fits), module.money("balance", balance)));
        shownLore.addAll(module.priceLines(viewer, item, balance));
        set(SLOT_ITEM, service.item(new ItemStack(material()), service.text(viewer, ShopModule.ID, "item.shown.name",
                Msg.c("item", name())), shownLore));
        List<Integer> amounts = module.settings.quickAmounts();
        int[] slots = amountSlots(amounts.size());
        for (int i = 0; i < amounts.size() && i < slots.length; i++) {
            int amount = amounts.get(i);
            set(slots[i], amountButton(amount, fits, balance), click -> attempt(click.player(), amount, false));
        }
        long max = ShopMath.maxAmount(item.price(), fits, module.settings.maxAmount);
        set(SLOT_CUSTOM, service.item(Material.NAME_TAG, viewer, ShopModule.ID, max > 0 ? "item.custom" : "item.custom-full",
                Msg.number("max", max)), click -> openPicker(click.player()));
    }

    private ItemStack amountButton(int amount, long fits, long balance) {
        long total = ShopMath.total(item.price(), amount).orElse(Long.MAX_VALUE);
        String state = fits < amount ? "no-room" : balance < total ? "too-expensive" : "buy";
        List<Component> lore = new ArrayList<>(service.lines(viewer, ShopModule.ID, "item.amount.lore",
                module.money("total", total), module.money("price", item.price()), Msg.number("amount", amount)));
        lore.addAll(service.lines(viewer, ShopModule.ID, "item.amount." + state, Msg.number("fits", fits)));
        ItemStack stack = new ItemStack(material(), Math.max(1, Math.min(material().getMaxStackSize(), amount)));
        return service.item(stack, service.text(viewer, ShopModule.ID, "item.amount.name", Msg.number("amount", amount),
                Msg.c("item", name())), lore);
    }

    private void openPicker(Player player) {
        long fits = InventorySpace.fits(player.getInventory(), new ItemStack(material()));
        long max = ShopMath.maxAmount(item.price(), fits, module.settings.maxAmount);
        if (max < 1) {
            module.fail(player, "buy.no-room", Msg.number("fits", 0), Msg.number("amount", 1));
            return;
        }
        NumberPickerMenu picker = new NumberPickerMenu(v -> service.text(v, ShopModule.ID, "item.picker-title", Msg.c("item", name())),
                1, max, Math.min(max, 16), module.settings.steps(), (p, value) -> attempt(p, (int) Math.min(Integer.MAX_VALUE, value), true));
        openChild(picker);
    }

    /** Buys now, or asks to confirm first when the total reaches {@code buy.confirm-above}. */
    private void attempt(Player player, int amount, boolean reopenAfter) {
        long total = ShopMath.total(item.price(), amount).orElse(Long.MAX_VALUE);
        if (total >= module.settings.confirmAbove) {
            Component itemName = name();
            ConfirmMenu confirm = new ConfirmMenu(v -> service.text(v, ShopModule.ID, "confirm.crumb"),
                    amountButton(amount, Long.MAX_VALUE, Long.MAX_VALUE),
                    v -> service.lines(v, ShopModule.ID, "confirm.lines", Msg.number("amount", amount), Msg.c("item", itemName),
                            module.money("total", total), module.money("balance", module.balance(v))),
                    p -> module.buy(p, item, amount, () -> reopen(p, true)), null);
            confirm.parent(this);
            service.open(player, confirm);
            return;
        }
        module.buy(player, item, amount, () -> reopen(player, reopenAfter));
    }

    /** Back on this page with fresh numbers (after a purchase or the picker). */
    private void reopen(Player player, boolean ifClosed) {
        if (!player.isOnline()) {
            return;
        }
        if (isOpen()) {
            refresh();
        } else if (ifClosed && module.core.menus().openMenu(player) == null && module.problem(player).isEmpty()) {
            service.open(player, this);
        }
    }
}
