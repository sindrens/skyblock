package net.skybornmc.skyblock.perks;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * The {@code /trash} disposal menu. Players put items into the top rows, then click Destroy: a confirm shows
 * "Destroy N items?" with the details on the middle item (GUI_DESIGN §4). Only a confirmed batch is destroyed (and
 * logged with an item summary). Closing the trash, cancelling or closing the confirm, quitting or a reload gives every
 * item back ({@link TrashBin}: a batch leaves exactly once). The bottom bar uses the menu framework's look: ? at 3,
 * Close at 4, Destroy at 6 of the bar row (GUI_DESIGN §1.1), and is fully protected.
 *
 * <p>This is a dedicated inventory instead of a {@code Menu}: the menu framework blocks every item from entering a
 * menu by design, while a trash can must accept items.</p>
 */
public final class TrashMenu implements Listener {

    /** Bar positions (column in the bottom row). */
    static final int BAR_INFO = 3;
    static final int BAR_CLOSE = 4;
    static final int BAR_DESTROY = 6;

    private final Core core;
    private final PerksConfig config;
    private final ModuleMessages msg;
    private final NamespacedKey barKey;
    private final TrashBin<ItemStack> bin = new TrashBin<>();
    /** The confirm each player has open for their held batch (to give the items back when it closes unanswered). */
    private final Map<UUID, Menu> confirms = new ConcurrentHashMap<>();

    public TrashMenu(Core core, PerksConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.barKey = new NamespacedKey(core.plugin(), "trash_bar");
    }

    /** Inventory holder of an open trash menu. */
    static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final int disposalSlots;

        Holder(int disposalSlots) {
            this.disposalSlots = disposalSlots;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        int rows = Math.max(2, Math.min(6, config.trashRows));
        Holder holder = new Holder((rows - 1) * 9);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                core.menus().title(player, msg.component(player, "trash.title")));
        holder.inventory = inventory;
        ItemStack filler = bar(core.menus().filler(Material.BLACK_STAINED_GLASS_PANE));
        ItemStack accent = bar(core.menus().filler(Material.RED_STAINED_GLASS_PANE));
        int bar = holder.disposalSlots;
        for (int i = 0; i < 9; i++) {
            inventory.setItem(bar + i, i == 0 || i == 8 ? accent : filler);
        }
        inventory.setItem(bar + BAR_INFO, bar(core.menus().item(Material.BOOK, player, "perks", "trash.info")));
        inventory.setItem(bar + BAR_CLOSE, bar(core.menus().item(Material.BARRIER, player, "frame.close")));
        inventory.setItem(bar + BAR_DESTROY, bar(core.menus().item(Material.LAVA_BUCKET, player, "perks", "trash.destroy")));
        player.openInventory(inventory);
        core.sounds().play(player, "menu-open");
    }

    private ItemStack bar(ItemStack item) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(barKey, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    private boolean isBar(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(barKey);
    }

    // ------------------------------------------------------------------ protection of the bottom bar, its buttons

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        int raw = event.getRawSlot();
        boolean barSlot = raw >= holder.disposalSlots && raw < event.getView().getTopInventory().getSize();
        // Double-click collect could pull bar items; number-key/offhand swaps into the bar; any click on the bar.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || isBar(event.getCurrentItem()) || isBar(event.getCursor())
                || barSlot) {
            event.setCancelled(true);
        }
        if (!barSlot || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int column = raw - holder.disposalSlots;
        if (column == BAR_CLOSE) {
            Bukkit.getScheduler().runTask(core.plugin(), () -> player.closeInventory());
        } else if (column == BAR_DESTROY) {
            askDestroy(player, event.getView().getTopInventory(), holder);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        int top = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw >= holder.disposalSlots && raw < top) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Closing the trash never destroys: whatever is still inside goes back to the player. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof Holder holder) {
            giveBack(player, takeAll(event.getInventory(), holder));
            return;
        }
        Menu confirm = confirms.get(player.getUniqueId());
        if (confirm != null && event.getInventory().getHolder(false) == confirm) {
            // One tick later: the confirm's own buttons run right after the close (destroy or give back).
            Bukkit.getScheduler().runTask(core.plugin(), () -> {
                if (core.menus().openMenu(player) != confirm && confirms.remove(player.getUniqueId(), confirm)) {
                    giveBack(player, bin.release(player.getUniqueId()));
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        confirms.remove(player.getUniqueId());
        giveBack(player, bin.release(player.getUniqueId()));
    }

    /** Module disable: every open trash and every unanswered confirm gives its items back. */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                player.closeInventory();
            }
            if (bin.holding(player.getUniqueId())) {
                confirms.remove(player.getUniqueId());
                giveBack(player, bin.release(player.getUniqueId()));
                player.closeInventory();
            }
        }
    }

    // ------------------------------------------------------------------ destroy with a confirm

    /** Destroy: moves the items out of the trash into the {@link TrashBin} and asks "Destroy N items?". */
    private void askDestroy(Player player, Inventory inventory, Holder holder) {
        UUID id = player.getUniqueId();
        if (bin.holding(id)) {
            return; // a confirm is already on its way
        }
        List<ItemStack> items = takeAll(inventory, holder);
        if (items.isEmpty()) {
            msg.send(player, "trash.nothing");
            core.sounds().play(player, "error");
            return;
        }
        giveBack(player, bin.hold(id, items));
        int total = 0;
        int custom = 0;
        Map<String, Integer> kinds = new LinkedHashMap<>();
        for (ItemStack item : items) {
            total += item.getAmount();
            if (ItemRegistry.isCustom(item)) {
                custom += item.getAmount();
            }
            kinds.merge(ItemRegistry.idOf(item).orElse(item.getType().getKey().getKey()), item.getAmount(), Integer::sum);
        }
        int amount = total;
        int customAmount = custom;
        int kindCount = kinds.size();
        ConfirmMenu confirm = new ConfirmMenu(v -> msg.component(v, "trash.confirm.crumb", Msg.number("amount", amount)),
                core.menus().item(Material.LAVA_BUCKET, player, "perks", "trash.confirm.shown", Msg.number("amount", amount)),
                v -> {
                    List<net.kyori.adventure.text.Component> lines = new ArrayList<>(core.menus().lines(v, "perks",
                            "trash.confirm.details", Msg.number("amount", amount), Msg.number("kinds", kindCount)));
                    if (customAmount > 0) {
                        lines.addAll(core.menus().lines(v, "perks", "trash.confirm.custom", Msg.number("amount", customAmount)));
                    }
                    return lines;
                },
                p -> {
                    confirms.remove(p.getUniqueId());
                    destroy(p, bin.confirm(p.getUniqueId()));
                },
                p -> {
                    confirms.remove(p.getUniqueId());
                    giveBack(p, bin.release(p.getUniqueId()));
                });
        confirms.put(id, confirm);
        // Next tick: never swap windows inside a click event.
        Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (!player.isOnline() || confirms.get(id) != confirm) {
                return;
            }
            core.menus().open(player, confirm);
            if (core.menus().openMenu(player) != confirm && confirms.remove(id, confirm)) {
                giveBack(player, bin.release(id)); // the confirm failed to open
            }
        });
    }

    /** Takes every real item out of the disposal slots (bar items are cleared too, never handed out). */
    private List<ItemStack> takeAll(Inventory inventory, Holder holder) {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = 0; slot < holder.disposalSlots; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, null);
            if (!isBar(item)) {
                out.add(item);
            }
        }
        return out;
    }

    /** Puts items back into the player's inventory; what doesn't fit drops at their feet. */
    private void giveBack(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        int total = 0;
        for (ItemStack item : items) {
            total += item.getAmount();
            for (ItemStack left : player.getInventory().addItem(item.clone()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        if (player.isOnline()) {
            msg.send(player, "trash.returned", Msg.number("amount", total));
        }
    }

    private void destroy(Player player, List<ItemStack> items) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        List<String> uniqueIds = new ArrayList<>();
        int total = 0;
        int custom = 0;
        for (ItemStack item : items) {
            String id = ItemRegistry.idOf(item).orElse(item.getType().getKey().getKey());
            if (ItemRegistry.isCustom(item)) {
                custom += item.getAmount();
                ItemRegistry.uuidOf(item).map(UUID::toString).ifPresent(uniqueIds::add);
            }
            summary.merge(id, item.getAmount(), Integer::sum);
            total += item.getAmount();
        }
        if (total == 0) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("items", summary.size() > 40 ? trimmed(summary) : summary);
        if (!uniqueIds.isEmpty()) {
            details.put("unique_ids", uniqueIds);
        }
        core.audit().log(AuditCategory.ITEM, "trash", Actor.player(player), null, (long) total, player.getLocation(), details);
        msg.send(player, "trash.destroyed", Msg.number("amount", total));
        if (custom > 0 && config.trashWarnCustom) {
            msg.send(player, "trash.destroyed-custom", Msg.number("amount", custom));
        }
    }

    private static Map<String, Integer> trimmed(Map<String, Integer> summary) {
        Map<String, Integer> out = new LinkedHashMap<>();
        summary.entrySet().stream().limit(40).forEach(e -> out.put(e.getKey(), e.getValue()));
        out.put("_more_kinds", summary.size() - 40);
        return out;
    }
}
