package net.skybornmc.skyblock.perks;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.item.ItemRegistry;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code /trash} disposal menu. Players drop items into the top rows; everything left there is destroyed when the
 * menu closes (and logged with an item summary). The bottom bar uses the menu framework's look (filler + info item)
 * and is fully protected.
 *
 * <p>This is a dedicated inventory instead of a {@code Menu}: the menu framework blocks every item from entering a
 * menu by design, while a trash can must accept items.</p>
 */
public final class TrashMenu implements Listener {

    private final Core core;
    private final PerksConfig config;
    private final ModuleMessages msg;
    private final NamespacedKey barKey;

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
        inventory.setItem(bar + 4, bar(core.menus().item(Material.LAVA_BUCKET, player, "perks", "trash.info")));
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

    // ------------------------------------------------------------------ protection of the bottom bar

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        // Double-click collect could pull bar items; number-key/offhand swaps into the bar; any click on the bar.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || isBar(event.getCurrentItem()) || isBar(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (raw >= holder.disposalSlots && raw < event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        destroy(player, event.getInventory(), holder);
    }

    /** Closes every open trash menu (module disable): items inside are destroyed and logged like a normal close. */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                player.closeInventory();
            }
        }
    }

    private void destroy(Player player, Inventory inventory, Holder holder) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        List<String> uniqueIds = new ArrayList<>();
        int total = 0;
        int custom = 0;
        for (int slot = 0; slot < holder.disposalSlots; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || isBar(item)) {
                continue;
            }
            String id = ItemRegistry.idOf(item).orElse(item.getType().getKey().getKey());
            if (ItemRegistry.isCustom(item)) {
                custom += item.getAmount();
                ItemRegistry.uuidOf(item).map(UUID::toString).ifPresent(uniqueIds::add);
            }
            summary.merge(id, item.getAmount(), Integer::sum);
            total += item.getAmount();
            inventory.setItem(slot, null);
        }
        // Bar items are never real items: clear them too so nothing can be duplicated from a stale view.
        inventory.clear();
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
        core.sounds().play(player, "menu-close");
    }

    private static Map<String, Integer> trimmed(Map<String, Integer> summary) {
        Map<String, Integer> out = new LinkedHashMap<>();
        summary.entrySet().stream().limit(40).forEach(e -> out.put(e.getKey(), e.getValue()));
        out.put("_more_kinds", summary.size() - 40);
        return out;
    }
}
