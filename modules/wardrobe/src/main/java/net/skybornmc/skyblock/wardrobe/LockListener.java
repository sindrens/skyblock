package net.skybornmc.skyblock.wardrobe;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The lock (WARDROBE §4.3, WD20): worn pieces can only be put on and taken off through {@code /wardrobe}. Every way of
 * moving them is cancelled (click, shift-click, number keys, off-hand swap, drag, drop, armor stands, item frames,
 * dispensers, right-click armor swap) with a rate-limited hint, and the inventory is re-sent next tick so Bedrock
 * (Geyser) never shows a ghost item. Set Tokens can't be placed, crafted, renamed or dispensed. While a wardrobe action
 * is saving the player's inventory is locked.
 */
final class LockListener implements Listener {

    private final WardrobeModule module;

    LockListener(WardrobeModule module) {
        this.module = module;
    }

    private boolean busy(Player player) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        return s != null && s.busy;
    }

    private void deny(Player player, Cancellable event) {
        event.setCancelled(true);
        hint(player);
        module.core.scheduler().later("wardrobe-resend", 1L, () -> {
            if (player.isOnline()) {
                player.updateInventory();
            }
        });
    }

    private void hint(Player player) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (s != null && now - s.lastHint < module.config().hintCooldownSeconds * 1000L) {
            return;
        }
        if (s != null) {
            s.lastHint = now;
        }
        module.msg().actionBar(player, "locked-hint");
    }

    // ------------------------------------------------------------------ inventory

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (busy(player)) {
            event.setCancelled(true);
            return;
        }
        ItemStack hotbar = event.getHotbarButton() >= 0 ? player.getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack offhand = event.getClick() == ClickType.SWAP_OFFHAND ? player.getInventory().getItemInOffHand() : null;
        if (WardrobeItems.isPiece(event.getCurrentItem()) || WardrobeItems.isPiece(event.getCursor())
                || WardrobeItems.isPiece(hotbar) || WardrobeItems.isPiece(offhand)) {
            deny(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && (busy(player) || WardrobeItems.isPiece(event.getOldCursor()))) {
            deny(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (busy(event.getPlayer()) || WardrobeItems.isPiece(event.getItemDrop().getItemStack())) {
            deny(event.getPlayer(), event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (busy(event.getPlayer()) || WardrobeItems.isPiece(event.getMainHandItem()) || WardrobeItems.isPiece(event.getOffHandItem())) {
            deny(event.getPlayer(), event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (WardrobeItems.isPiece(stack)) {
            event.setCancelled(true);
            event.getItem().remove(); // a piece on the ground is always a copy
            return;
        }
        if (event.getEntity() instanceof Player player && busy(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (WardrobeItems.isPiece(item.getItemStack())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ world interactions

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (busy(event.getPlayer()) || WardrobeItems.isPiece(event.getPlayerItem()) || WardrobeItems.isPiece(event.getArmorStandItem())) {
            deny(event.getPlayer(), event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (busy(event.getPlayer()) || WardrobeItems.isPiece(hand) || WardrobeItems.isToken(hand)) {
            event.setCancelled(true); // item frames, allays, armor stands
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDispenseArmor(BlockDispenseArmorEvent event) {
        if (WardrobeItems.isPiece(event.getItem()) || WardrobeItems.isToken(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDispense(BlockDispenseEvent event) {
        if (WardrobeItems.isPiece(event.getItem()) || WardrobeItems.isToken(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (busy(event.getPlayer()) || WardrobeItems.isToken(event.getItemInHand()) || WardrobeItems.isPiece(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /**
     * Right-click: a Set Token is redeemed (main hand), and holding armor never swaps it with a worn piece (vanilla's
     * right-click equip would put the piece in the hand).
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        if (WardrobeItems.isToken(hand)) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            if (event.getHand() == EquipmentSlot.HAND && !busy(player)) {
                module.shop().redeem(player, player.getInventory().getHeldItemSlot());
            }
            return;
        }
        if (hand == null || hand.isEmpty()) {
            return;
        }
        EquipmentSlot target = equipSlot(hand);
        if (target != null && WardrobeItems.isPiece(player.getInventory().getItem(target))) {
            event.setUseItemInHand(Event.Result.DENY);
            hint(player);
            module.core.scheduler().later("wardrobe-resend", 1L, player::updateInventory);
        }
    }

    private static EquipmentSlot equipSlot(ItemStack item) {
        Material m = item.getType();
        EquipmentSlot slot = m.getEquipmentSlot();
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET ? slot : null;
    }

    // ------------------------------------------------------------------ tokens in crafting

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (WardrobeItems.isToken(item) || WardrobeItems.isPiece(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (WardrobeItems.isToken(item) || WardrobeItems.isPiece(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (WardrobeItems.isToken(item) || WardrobeItems.isPiece(item)) {
                event.setResult(null);
                return;
            }
        }
    }
}
