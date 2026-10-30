package net.skybornmc.skyblock.economy.sell;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.bonus.BonusPart;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellQuote;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /sell}: the sell box (6 rows, standard frame). Bedrock-safe, every action is one plain tap:
 * <ul>
 *   <li>tap a stack in <b>your inventory</b> to put it in the box; tap it <b>in the box</b> to take it out;</li>
 *   <li>46 "Add all sellable", 47 "Take all out", 51 sell bonus, 52/53 sell (one big target), 4 the live total.</li>
 * </ul>
 * The stacks never leave the inventory until the sale (the box remembers slots; every inventory click is blocked while it
 * is open), so closing it, logging out or a crash can't lose anything. Prices and amounts refresh live.
 */
final class SellMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    static final int SLOT_ADD_ALL = 46;
    static final int SLOT_CLEAR = 47;
    static final int SLOT_BONUS = 51;
    static final int SLOT_CONFIRM = 52;
    static final int SLOT_CONFIRM_2 = 53;
    static final int SLOT_EMPTY = 22;
    static final int[] CONTENT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38,
            39, 40, 41, 42, 43};

    private final SellFeature feature;
    /** Inventory slot → the stack seen there when it was put in (kept up to date while the menu is open). */
    private final Map<Integer, ItemStack> selected = new LinkedHashMap<>();
    private boolean busy;

    SellMenu(SellFeature feature) {
        this.feature = feature;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, EconomyModule.ID, "sell.menu.title"));
    }

    @Override
    public String messageModule() {
        return EconomyModule.ID;
    }

    @Override
    public String infoKey() {
        return "sell.menu.info";
    }

    @Override
    public String helpTopic() {
        return "sell";
    }

    @Override
    public int tickInterval() {
        return feature.config().sellMenuRefreshTicks;
    }

    @Override
    protected void tick() {
        if (!busy) {
            refresh();
        }
    }

    // ------------------------------------------------------------------ selection

    /** Drops stacks that moved away or changed kind; follows amount changes (e.g. picked up more). */
    private void validate() {
        var inv = viewer.getInventory();
        selected.entrySet().removeIf(e -> {
            ItemStack now = inv.getItem(e.getKey());
            if (now == null || now.isEmpty() || !now.isSimilar(e.getValue()) || !feature.service().sellable(now)) {
                return true;
            }
            if (now.getAmount() != e.getValue().getAmount()) {
                e.setValue(now.clone());
            }
            return false;
        });
    }

    /** A tap on an inventory slot (from {@link SellFeature#onInventoryClick}). */
    void toggle(int slot) {
        if (busy) {
            return;
        }
        if (selected.remove(slot) != null) {
            feature.sound(viewer, "click");
            refresh();
            return;
        }
        ItemStack item = viewer.getInventory().getItem(slot);
        if (item == null || item.isEmpty()) {
            return;
        }
        Optional<String> reason = feature.service().unsellableReason(item);
        if (reason.isPresent()) {
            feature.msg.actionBar(viewer, "sell.reason." + reason.get(), Msg.c("item", SellFeature.itemName(item)));
            feature.sound(viewer, "error");
            return;
        }
        selected.put(slot, item.clone());
        feature.sound(viewer, "click");
        refresh();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void build() {
        frame();
        validate();
        List<ItemStack> stacks = new ArrayList<>(selected.values());
        double bonus = feature.service().bonusPercent(viewer);
        SellQuote quote = feature.service().quote(stacks, bonus);

        // Box content.
        List<Map.Entry<Integer, ItemStack>> entries = new ArrayList<>(selected.entrySet());
        boolean overflow = entries.size() > CONTENT.length;
        int shown = overflow ? CONTENT.length - 1 : entries.size();
        for (int i = 0; i < shown; i++) {
            int invSlot = entries.get(i).getKey();
            ItemStack stack = entries.get(i).getValue();
            set(CONTENT[i], boxItem(stack), click -> {
                if (!busy && selected.remove(invSlot) != null) {
                    refresh();
                }
            });
        }
        if (overflow) {
            set(CONTENT[CONTENT.length - 1], service.item(Material.CHEST, viewer, EconomyModule.ID, "sell.menu.more",
                    Msg.number("stacks", entries.size() - shown)));
        }
        if (entries.isEmpty()) {
            set(SLOT_EMPTY, service.item(Material.COBWEB, viewer, EconomyModule.ID, "sell.menu.empty"));
        }

        set(SLOT_SUMMARY, summary(quote));
        set(SLOT_ADD_ALL, service.item(Material.HOPPER, viewer, EconomyModule.ID, "sell.menu.add-all"), click -> {
            if (busy) {
                return;
            }
            Map<Integer, ItemStack> all = feature.sellableSlots(viewer);
            if (all.isEmpty()) {
                feature.msg.actionBar(viewer, "sell.all-nothing");
                feature.sound(viewer, "error");
                return;
            }
            all.forEach(selected::putIfAbsent);
            refresh();
        });
        set(SLOT_CLEAR, service.item(Material.BUCKET, viewer, EconomyModule.ID, "sell.menu.clear"), click -> {
            if (!busy) {
                selected.clear();
                refresh();
            }
        });
        set(SLOT_BONUS, bonusItem(quote));
        ItemStack confirm = confirmItem(quote);
        set(SLOT_CONFIRM, confirm, click -> confirm());
        set(SLOT_CONFIRM_2, confirm, click -> confirm());
    }

    private void confirm() {
        if (busy) {
            return;
        }
        validate();
        if (selected.isEmpty()) {
            feature.msg.actionBar(viewer, "sell.menu.nothing-picked");
            feature.sound(viewer, "error");
            return;
        }
        busy = true;
        refresh();
        Map<Integer, ItemStack> picked = new LinkedHashMap<>(selected);
        feature.sell(viewer, picked, "gui", result -> {
            busy = false;
            selected.clear();
            if (isOpen()) {
                refresh();
            }
        });
    }

    private ItemStack boxItem(ItemStack stack) {
        Optional<SellPrice> price = feature.service().price(stack);
        List<Component> lore = new ArrayList<>();
        if (price.isPresent()) {
            SellPrice p = price.get();
            SellQuote one = feature.service().quote(List.of(stack), 0);
            TagResolver[] r = {Msg.c("price", feature.unitPrice(viewer, p.unit())), Msg.c("percent", feature.fromBase(viewer, p)),
                    Msg.c("trend", feature.trend(viewer, p.trend())), Msg.c("worth", feature.money(viewer, one.subtotal())),
                    Msg.c("category", feature.categoryName(viewer, p.category())), Msg.number("amount", stack.getAmount())};
            lore.addAll(service.lines(viewer, EconomyModule.ID, "sell.menu.item.lore", r));
            if (p.hot()) {
                lore.addAll(service.lines(viewer, EconomyModule.ID, "sell.menu.item.hot",
                        Msg.p("percent", SellFeature.percentText(feature.config().sellHotPercent))));
            }
        }
        lore.addAll(service.lines(viewer, EconomyModule.ID, "sell.menu.item.footer"));
        ItemStack display = service.item(stack, SellFeature.itemName(stack), lore);
        display.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), stack.getAmount())));
        return display;
    }

    private ItemStack summary(SellQuote quote) {
        List<Component> lore = new ArrayList<>(service.lines(viewer, EconomyModule.ID, "sell.menu.summary.lore",
                Msg.number("items", quote.items()), Msg.number("stacks", selected.size()),
                Msg.c("subtotal", feature.money(viewer, quote.subtotal())),
                Msg.p("bonus", SellFeature.percentText(quote.cappedBonusPercent())),
                Msg.c("total", feature.money(viewer, quote.total()))));
        String hot = feature.service().hotKey();
        if (hot != null) {
            lore.addAll(service.lines(viewer, EconomyModule.ID, "sell.menu.summary.hot", Msg.c("item", SellFeature.keyName(hot)),
                    Msg.p("percent", SellFeature.percentText(feature.config().sellHotPercent))));
        }
        return service.item(Material.GOLD_NUGGET, service.text(viewer, EconomyModule.ID, "sell.menu.summary.name",
                Msg.c("total", feature.money(viewer, quote.total()))), lore);
    }

    private ItemStack bonusItem(SellQuote quote) {
        List<Component> lore = new ArrayList<>();
        List<BonusPart> parts = feature.service().bonusBreakdown(viewer);
        if (parts.isEmpty()) {
            lore.addAll(service.lines(viewer, EconomyModule.ID, "sell.menu.bonus.none"));
        }
        for (BonusPart part : parts) {
            lore.addAll(service.lines(viewer, EconomyModule.ID, "sell.menu.bonus.line", Msg.c("source", feature.sourceName(viewer, part.source())),
                    Msg.p("value", SellFeature.percentText(part.value()))));
        }
        lore.addAll(service.lines(viewer, EconomyModule.ID, quote.capped() ? "sell.menu.bonus.capped" : "sell.menu.bonus.footer",
                Msg.p("cap", SellFeature.percentText(feature.service().bonusCapPercent())),
                Msg.p("total", SellFeature.percentText(quote.bonusPercent()))));
        return service.item(Material.SUNFLOWER, service.text(viewer, EconomyModule.ID, "sell.menu.bonus.name",
                Msg.p("bonus", SellFeature.percentText(quote.cappedBonusPercent()))), lore);
    }

    private ItemStack confirmItem(SellQuote quote) {
        if (busy) {
            return service.item(Material.CLOCK, viewer, EconomyModule.ID, "sell.menu.busy");
        }
        if (quote.isEmpty()) {
            return service.item(Material.GRAY_CONCRETE, viewer, EconomyModule.ID, "sell.menu.confirm-empty");
        }
        return service.item(Material.LIME_CONCRETE, viewer, EconomyModule.ID, "sell.menu.confirm",
                Msg.c("total", feature.money(viewer, quote.total())), Msg.number("items", quote.items()),
                Msg.p("bonus", SellFeature.percentText(quote.cappedBonusPercent())));
    }
}
