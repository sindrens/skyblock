package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * The menu item (owner decision): a Nether Star with a red glint in hotbar slot 9, "Menu · Right-click".
 * Right- or left-click (also a tap on Bedrock) opens {@code /menu}. It can't be dropped, moved, put in containers,
 * crafted with or lost on death. Players can turn it off in {@code /settings} ({@code menu-item}).
 */
final class MenuItem implements Listener {

    static final String SETTING = "menu-item";

    private final Nav nav;
    private final MenusConfig config;
    private final NamespacedKey key;

    MenuItem(Nav nav, MenusConfig config) {
        this.nav = nav;
        this.config = config;
        this.key = new NamespacedKey(nav.core.plugin(), "menu_item");
    }

    private int slot() {
        return Math.max(0, Math.min(8, config.menuItemSlot - 1));
    }

    ItemStack create(Player player) {
        Material material = Material.matchMaterial(config.menuItemMaterial);
        ItemStack item = new ItemStack(material == null || !material.isItem() ? Material.NETHER_STAR : material);
        item.editMeta(meta -> {
            meta.displayName(nav.text(player, "menu-item.name").decoration(TextDecoration.ITALIC, false));
            meta.lore(nav.lines(player, "menu-item.lore"));
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    boolean is(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key);
    }

    /** Puts the item in its slot if the player wants it, or removes every copy if not. */
    void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        boolean wanted = config.menuItemEnabled && nav.settings.get(player, SETTING);
        // Remove copies outside the slot (old slot setting, plugin reload) so there is never more than one.
        for (int i = 0; i < inv.getSize(); i++) {
            if (i != slot() && is(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        ItemStack current = inv.getItem(slot());
        if (!wanted) {
            if (is(current)) {
                inv.setItem(slot(), null);
            }
            return;
        }
        if (is(current)) {
            inv.setItem(slot(), create(player)); // refresh name/lore (language/text changes)
            return;
        }
        if (current != null && !current.getType().isAir()) {
            // Move the player's own item to an empty slot; if there is none, keep theirs and skip the menu item.
            int free = inv.firstEmpty();
            if (free < 0) {
                return;
            }
            inv.setItem(free, current);
        }
        inv.setItem(slot(), create(player));
    }

    void removeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                if (is(inv.getItem(i))) {
                    inv.setItem(i, null);
                }
            }
        }
    }

    void applyAll() {
        Bukkit.getOnlinePlayers().forEach(this::apply);
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(nav.core.plugin(), () -> {
            if (player.isOnline()) {
                apply(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::is);
        event.getItemsToKeep().removeIf(this::is);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL || !is(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().performCommand("menu");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (is(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                event.getPlayer().performCommand("menu");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        boolean hotbarSwap = event.getHotbarButton() >= 0
                && is(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
        if (is(event.getCurrentItem()) || is(event.getCursor()) || hotbarSwap) {
            event.setCancelled(true);
            if (is(event.getCurrentItem()) && event.getWhoClicked() instanceof Player player
                    && event.getView().getTopInventory().getHolder() == player) {
                // Clicking it in your own inventory opens the menu too.
                Bukkit.getScheduler().runTask(nav.core.plugin(), () -> player.performCommand("menu"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (is(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (is(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (is(event.getMainHandItem()) || is(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (is(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
