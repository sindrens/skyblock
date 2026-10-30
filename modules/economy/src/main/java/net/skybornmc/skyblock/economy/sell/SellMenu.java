package net.skybornmc.skyblock.economy.sell;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.bonus.BonusPart;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.sell.SellQuote;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /sell}: the <b>sell chest</b> (SELL2, design board 5). Six rows: the top four (slots 0-35) are free space the
 * player puts items into (click, shift-click from the inventory, drag), row 5 is a divider, the bottom row holds the
 * buttons:
 * <pre>
 *  0 ..................................... 35   free space (4 rows)
 *  36 ─────────── divider ───────────── 44
 *  45 Back  46 Sell inventory  47 Total  48 Info  49 Close  50 Help  51 Your sell bonus  52/53 SELL
 * </pre>
 *
 * <p><b>Item safety.</b></p>
 * <ul>
 *   <li>Every move is done by the server: the menu framework cancels every click; this menu re-allows only plain
 *       clicks and drags that stay inside the free space and the player's own inventory, after checking the item can be
 *       sold (unsellable items stay where they are and the action bar says why). Shift-click into the chest and
 *       "Sell inventory" are moved here, never by the client. Double-click (collect), number keys, drop and creative
 *       clicks are never allowed (they could reach the buttons).</li>
 *   <li>The contents are copied into the player's own data ({@link SellChestStore}) after every change, and a slot that
 *       is about to lose its item is removed from that copy <i>before</i> the move. Vanilla saves that copy in the same
 *       file as the inventory, so a crash can neither dupe nor lose chest items; the next join hands them back.</li>
 *   <li>Closing the chest for any reason (Close, Esc, logout, death, teleport, profile switch, shutdown) hands every
 *       item back: inventory, then ender chest, then the feet (with a message).</li>
 *   <li>Selling takes the items out first (escrow) and goes through {@code SellService} as one transaction; while it
 *       runs the chest is locked, and closing it meanwhile is safe (the sale gives back what it could not sell).</li>
 * </ul>
 *
 * <p>Never call {@link #refresh()} on this menu: it clears the whole chest, items included. Live updates only touch the
 * button slots ({@link #updateControls()}).</p>
 */
final class SellMenu extends Menu {

    static final int FREE_SLOTS = 36;
    static final int SLOT_SELL_INVENTORY = 46;
    static final int SLOT_TOTAL = 47;
    static final int SLOT_BONUS = 51;
    static final int SLOT_SELL = 52;
    static final int SLOT_SELL_2 = 53;
    static final int DIVIDER_FIRST = 36;
    static final int DIVIDER_LAST = 44;

    private final SellFeature feature;
    /** A sale is running: the chest is locked. */
    private boolean busy;
    /** A copy + button update is queued for the next tick. */
    private boolean syncQueued;
    /** A data save is queued. */
    private boolean saveQueued;
    private boolean built;
    private long lastShiftClick;

    SellMenu(SellFeature feature) {
        this.feature = feature;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, EconomyModule.ID, "sell.chest.title"));
    }

    @Override
    public String messageModule() {
        return EconomyModule.ID;
    }

    @Override
    public String infoKey() {
        return "sell.chest.info";
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
        updateControls();
    }

    boolean busy() {
        return busy;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void build() {
        if (built) {
            // A rebuild on the same inventory would have cleared the items; this never happens (see the class comment).
            feature.core.plugin().getLogger().severe("Sell chest of " + viewer.getName() + " was rebuilt; this must not happen");
        }
        built = true;
        frame();
        for (int slot = 0; slot < FREE_SLOTS; slot++) {
            set(slot, (MenuButton) null); // free space: no frame, no button
        }
        ItemStack divider = service.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = DIVIDER_FIRST; slot <= DIVIDER_LAST; slot++) {
            set(slot, divider);
        }
        updateControls();
    }

    /** Redraws the bottom-row buttons from the chest contents (never touches the free space). */
    void updateControls() {
        if (!isOpen()) {
            return;
        }
        List<ItemStack> stacks = contents();
        double bonus = feature.service().bonusPercent(viewer);
        SellQuote quote = feature.service().quote(stacks, bonus);
        int unsellable = quote.unsellable().size();

        set(SLOT_SELL_INVENTORY, service.item(Material.HOPPER, viewer, EconomyModule.ID, "sell.chest.sell-inventory"),
                click -> sellInventory());
        set(SLOT_TOTAL, totalItem(quote, unsellable));
        set(SLOT_BONUS, bonusItem(quote));
        ItemStack sell = sellItem(quote);
        set(SLOT_SELL, sell, click -> sell());
        set(SLOT_SELL_2, sell, click -> sell());
    }

    private ItemStack totalItem(SellQuote quote, int unsellable) {
        List<Component> lore = new ArrayList<>(service.lines(viewer, EconomyModule.ID, "sell.chest.total.lore",
                Msg.number("items", quote.items()),
                Msg.c("value", feature.money(viewer, quote.subtotal())),
                Msg.p("bonus", SellFeature.percentText(quote.cappedBonusPercent())),
                Msg.c("total", feature.money(viewer, quote.total()))));
        String hot = feature.service().hotKey();
        if (hot != null) {
            lore.addAll(service.lines(viewer, EconomyModule.ID, "sell.chest.total.hot", Msg.c("item", SellFeature.keyName(hot)),
                    Msg.p("percent", SellFeature.percentText(feature.config().sellHotPercent))));
        }
        return service.item(Material.GOLD_NUGGET, service.text(viewer, EconomyModule.ID, "sell.chest.total.name",
                Msg.c("total", feature.money(viewer, quote.total()))), lore);
    }

    private ItemStack bonusItem(SellQuote quote) {
        List<Component> lore = new ArrayList<>();
        List<BonusPart> parts = feature.service().bonusBreakdown(viewer);
        if (parts.isEmpty()) {
            lore.addAll(service.lines(viewer, EconomyModule.ID, "sell.chest.bonus.none"));
        }
        for (BonusPart part : parts) {
            lore.addAll(service.lines(viewer, EconomyModule.ID, "sell.chest.bonus.line",
                    Msg.c("source", feature.sourceName(viewer, part.source())), Msg.p("value", SellFeature.percentText(part.value()))));
        }
        lore.addAll(service.lines(viewer, EconomyModule.ID, quote.capped() ? "sell.chest.bonus.capped" : "sell.chest.bonus.footer",
                Msg.p("cap", SellFeature.percentText(feature.service().bonusCapPercent())),
                Msg.p("total", SellFeature.percentText(quote.bonusPercent()))));
        return service.item(Material.SUNFLOWER, service.text(viewer, EconomyModule.ID, "sell.chest.bonus.name",
                Msg.p("bonus", SellFeature.percentText(quote.cappedBonusPercent()))), lore);
    }

    private ItemStack sellItem(SellQuote quote) {
        if (busy) {
            return service.item(Material.CLOCK, viewer, EconomyModule.ID, "sell.chest.busy");
        }
        if (quote.isEmpty()) {
            return service.item(Material.GRAY_CONCRETE, viewer, EconomyModule.ID, "sell.chest.sell-empty");
        }
        return service.item(Material.LIME_CONCRETE, viewer, EconomyModule.ID, "sell.chest.sell",
                Msg.number("items", quote.items()), Msg.c("total", feature.money(viewer, quote.total())),
                Msg.p("bonus", SellFeature.percentText(quote.cappedBonusPercent())));
    }

    // ------------------------------------------------------------------ contents

    private Inventory chest() {
        return getInventory();
    }

    /** The stacks in the free space (copies). */
    List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = 0; slot < FREE_SLOTS; slot++) {
            ItemStack item = chest().getItem(slot);
            if (item != null && !item.isEmpty()) {
                out.add(item.clone());
            }
        }
        return out;
    }

    /** Slot → stack of the free space, without {@code skip} (-1 = none). */
    private Map<Integer, ItemStack> snapshot(int skip) {
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        for (int slot = 0; slot < FREE_SLOTS; slot++) {
            ItemStack item = chest().getItem(slot);
            if (slot != skip && item != null && !item.isEmpty()) {
                out.put(slot, item.clone());
            }
        }
        return out;
    }

    /** Writes the crash copy of the chest now ({@code skip}: a slot about to lose its item). */
    private void saveCopy(int skip) {
        feature.saveChestCopy(viewer, snapshot(skip));
    }

    /** After the next tick (when the click has been applied): bounce unsellable items, save the copy, update buttons. */
    private void queueSync() {
        if (syncQueued) {
            return;
        }
        syncQueued = true;
        Bukkit.getScheduler().runTask(feature.core.plugin(), () -> {
            syncQueued = false;
            if (isOpen()) {
                sync();
            }
        });
    }

    private void sync() {
        List<ItemStack> bounce = new ArrayList<>();
        Optional<String> firstReason = Optional.empty();
        ItemStack firstItem = null;
        for (int slot = 0; slot < FREE_SLOTS; slot++) {
            ItemStack item = chest().getItem(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            Optional<String> reason = feature.service().unsellableReason(item);
            if (reason.isPresent()) {
                bounce.add(item.clone());
                chest().setItem(slot, null);
                if (firstReason.isEmpty()) {
                    firstReason = reason;
                    firstItem = item.clone();
                }
            }
        }
        saveCopy(-1);
        if (!bounce.isEmpty()) {
            feature.returnItems(viewer, bounce);
            final ItemStack shown = firstItem;
            firstReason.ifPresent(r -> feature.msg.actionBar(viewer, "sell.reason." + r, Msg.c("item", SellFeature.itemName(shown))));
            feature.sound(viewer, "error");
        }
        updateControls();
        queueSave();
    }

    /** Saves the player's data a little later (inventory and copy together; fewer rollbacks after a crash). */
    private void queueSave() {
        int ticks = feature.config().sellChestSaveTicks;
        if (ticks <= 0 || saveQueued) {
            return;
        }
        saveQueued = true;
        Bukkit.getScheduler().runTaskLater(feature.core.plugin(), () -> {
            saveQueued = false;
            feature.savePlayerData(viewer);
        }, ticks);
    }

    // ------------------------------------------------------------------ clicks (from SellFeature, after the framework)

    /**
     * A click while this chest is open. The framework already cancelled it; plain moves inside the free space and the
     * player's own inventory are allowed again here after the checks.
     */
    void handleClick(InventoryClickEvent event) {
        if (busy) {
            return; // locked while selling
        }
        int raw = event.getRawSlot();
        int topSize = chest().getSize();
        ClickType type = event.getClick();
        boolean plain = type == ClickType.LEFT || type == ClickType.RIGHT;
        boolean shift = type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT;
        if (!plain && !shift) {
            return; // double-click, number keys, drop, middle, creative: never
        }
        if (raw >= 0 && raw < topSize) {
            if (raw >= FREE_SLOTS) {
                return; // a button: the framework handles it
            }
            InventoryAction action = event.getAction();
            ItemStack cursor = event.getCursor();
            boolean placing = action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR;
            if (placing && cursor != null && !cursor.isEmpty()) {
                Optional<String> reason = feature.service().unsellableReason(cursor);
                if (reason.isPresent()) {
                    refuse(reason.get(), cursor);
                    return;
                }
            }
            if (!allowedTopAction(action)) {
                return;
            }
            // The slot may lose its item: take it out of the crash copy first (never in both places).
            saveCopy(raw);
            event.setCancelled(false);
            queueSync();
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getBottomInventory()) {
            return;
        }
        if (plain) {
            event.setCancelled(false); // moving things around in your own inventory
            return;
        }
        // Shift-click from the inventory: moved by the server into the free space only (never onto the buttons).
        int slot = event.getSlot();
        ItemStack seen = event.getCurrentItem() == null ? null : event.getCurrentItem().clone();
        if (seen == null || seen.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastShiftClick < feature.config().sellMenuClickMs) {
            return;
        }
        lastShiftClick = now;
        Optional<String> reason = feature.service().unsellableReason(seen);
        if (reason.isPresent()) {
            refuse(reason.get(), seen);
            return;
        }
        Bukkit.getScheduler().runTask(feature.core.plugin(), () -> moveIn(slot, seen));
    }

    /** Vanilla actions allowed on a free slot (all stay between this slot, the cursor and the player's inventory). */
    static boolean allowedTopAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR,
                 MOVE_TO_OTHER_INVENTORY -> true;
            default -> false;
        };
    }

    /** A drag: allowed when it only covers free slots and the player's inventory, with a sellable item. */
    void handleDrag(InventoryDragEvent event) {
        if (busy) {
            return;
        }
        int topSize = chest().getSize();
        boolean intoChest = false;
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                if (raw >= FREE_SLOTS) {
                    return;
                }
                intoChest = true;
            }
        }
        if (intoChest) {
            ItemStack dragged = event.getOldCursor();
            Optional<String> reason = feature.service().unsellableReason(dragged);
            if (reason.isPresent()) {
                refuse(reason.get(), dragged);
                return;
            }
        }
        event.setCancelled(false); // a drag only adds to slots: the copy is updated next tick
        if (intoChest) {
            queueSync();
        }
    }

    private void refuse(String reason, ItemStack item) {
        feature.msg.actionBar(viewer, "sell.reason." + reason, Msg.c("item", SellFeature.itemName(item)));
        feature.sound(viewer, "error");
        Bukkit.getScheduler().runTask(feature.core.plugin(), viewer::updateInventory);
    }

    /** Moves the stack in inventory slot {@code slot} into the free space (if it is still the one that was clicked). */
    private void moveIn(int slot, ItemStack seen) {
        if (!isOpen() || busy) {
            return;
        }
        PlayerInventory inv = viewer.getInventory();
        ItemStack now = inv.getItem(slot);
        if (now == null || now.isEmpty() || !now.isSimilar(seen) || !feature.service().sellable(now)) {
            viewer.updateInventory();
            return;
        }
        int left = fill(now);
        if (left == now.getAmount()) {
            feature.msg.actionBar(viewer, "sell.chest.full");
            feature.sound(viewer, "error");
        } else {
            ItemStack rest = now.clone();
            rest.setAmount(left);
            inv.setItem(slot, left > 0 ? rest : null);
            feature.sound(viewer, "click");
        }
        saveCopy(-1); // moved by us: inventory and copy change in the same tick
        viewer.updateInventory();
        updateControls();
        queueSave();
    }

    /** Puts {@code stack} into the free space; returns how many did not fit. Changes the chest only. */
    private int fill(ItemStack stack) {
        List<ItemStack> slots = new ArrayList<>(Arrays.asList(new ItemStack[FREE_SLOTS]));
        for (int i = 0; i < FREE_SLOTS; i++) {
            ItemStack item = chest().getItem(i);
            slots.set(i, item == null || item.isEmpty() ? null : item);
        }
        int left = ChestFill.insert(slots, stack.clone(), SellFeature.STACK_OPS);
        if (left < stack.getAmount()) {
            for (int i = 0; i < FREE_SLOTS; i++) {
                chest().setItem(i, slots.get(i));
            }
        }
        return left;
    }

    // ------------------------------------------------------------------ buttons

    /** "Sell inventory": every sellable stack of the inventory into the chest (armor, offhand, hotbar tools stay). */
    private void sellInventory() {
        if (busy) {
            return;
        }
        PlayerInventory inv = viewer.getInventory();
        int moved = 0;
        boolean full = false;
        for (int slot = 0; slot < SellFeature.STORAGE_SLOTS; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.isEmpty() || !feature.service().sellable(item)) {
                continue;
            }
            if (slot < 9 && feature.config().sellChestKeepHotbarTools && SellFeature.isToolOrWeapon(item)) {
                continue;
            }
            int left = fill(item);
            int taken = item.getAmount() - left;
            if (taken > 0) {
                moved += taken;
                ItemStack rest = item.clone();
                rest.setAmount(left);
                inv.setItem(slot, left > 0 ? rest : null);
            }
            if (left > 0) {
                full = true;
                break;
            }
        }
        saveCopy(-1);
        Bukkit.getScheduler().runTask(feature.core.plugin(), viewer::updateInventory);
        if (moved == 0) {
            feature.msg.actionBar(viewer, full ? "sell.chest.full" : "sell.chest.nothing-to-add");
            feature.sound(viewer, "error");
        } else {
            feature.msg.actionBar(viewer, full ? "sell.chest.added-full" : "sell.chest.added", Msg.number("items", moved));
        }
        updateControls();
        queueSave();
    }

    /** The Sell button: everything sellable in the chest is taken out (escrow) and sold in one transaction. */
    private void sell() {
        if (busy) {
            return;
        }
        List<ItemStack> taken = new ArrayList<>();
        List<ItemStack> bounce = new ArrayList<>();
        String firstReason = null;
        ItemStack firstBounced = null;
        for (int slot = 0; slot < FREE_SLOTS; slot++) {
            ItemStack item = chest().getItem(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            Optional<String> reason = feature.service().unsellableReason(item);
            if (reason.isPresent()) {
                bounce.add(item.clone());
                if (firstReason == null) {
                    firstReason = reason.get();
                    firstBounced = item.clone();
                }
            } else {
                taken.add(item.clone());
            }
            chest().setItem(slot, null);
        }
        feature.clearChestCopy(viewer); // the chest is empty now: from here the items exist only in the sale
        if (!bounce.isEmpty()) {
            feature.returnItems(viewer, bounce);
            feature.msg.actionBar(viewer, "sell.reason." + firstReason, Msg.c("item", SellFeature.itemName(firstBounced)));
        }
        if (taken.isEmpty()) {
            if (bounce.isEmpty()) {
                feature.msg.actionBar(viewer, "sell.chest.nothing");
            }
            feature.sound(viewer, "error");
            updateControls();
            return;
        }
        busy = true;
        updateControls();
        feature.sellTaken(viewer, taken, "chest", result -> {
            busy = false;
            if (isOpen()) {
                updateControls();
            }
        });
    }

    // ------------------------------------------------------------------ closing

    /** Any close: every item goes back to the player (inventory, ender chest, feet). */
    @Override
    protected void onClose() {
        List<ItemStack> back = new ArrayList<>();
        for (int slot = 0; slot < FREE_SLOTS; slot++) {
            ItemStack item = chest().getItem(slot);
            if (item != null && !item.isEmpty()) {
                back.add(item.clone());
                chest().setItem(slot, null);
            }
        }
        feature.clearChestCopy(viewer);
        if (!back.isEmpty()) {
            feature.returnItems(viewer, back);
        }
    }
}
