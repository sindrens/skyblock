package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The menu item: a Nether Star in hotbar slot 9 that opens {@code /menu}. It can't be dropped, moved, put in
 * containers, swapped, cloned or lost on death, and is given back on join, respawn, world and game mode change.
 * Players can switch it off ({@code /settings} later → {@link #setEnabled}); published as a service.
 */
public final class MenuItem implements Listener {

    /** Player setting key: {@code "off"} hides the item. */
    public static final String SETTING = "menu-item";

    private final Core core;
    private final EssentialsConfig config;
    private final ModuleMessages msg;
    private final NamespacedKey key;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    MenuItem(Core core, EssentialsConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.key = new NamespacedKey(core.plugin(), "hotbar_menu");
    }

    public boolean isMenuItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getType() == Material.NETHER_STAR
                && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Whether this player sees the menu item (server switch + their own setting). */
    public boolean isEnabled(Player player) {
        return config.menuItemEnabled && core.players().get(player.getUniqueId())
                .map(d -> !"off".equals(d.setting(SETTING, null))).orElse(true);
    }

    /** Turns the item on or off for a player (saved in their profile) and updates their hotbar right away. */
    public void setEnabled(Player player, boolean enabled) {
        core.players().get(player.getUniqueId()).ifPresent(d -> d.setSetting(SETTING, enabled ? null : "off"));
        refresh(player);
    }

    ItemStack create(Player viewer) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(msg.component(viewer, "menu-item.name")));
        List<Component> lore = new ArrayList<>();
        String raw = core.messages().raw(CommandGroup.MODULE, "menu-item.lore");
        if (raw != null) {
            for (String line : raw.split("<newline>")) {
                lore.add(noItalic(core.messages().parse(viewer, line)));
            }
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Puts the item in its slot (or removes it when disabled). Removes stray copies elsewhere. */
    void refresh(Player player) {
        PlayerInventory inv = player.getInventory();
        int slot = config.menuItemSlot;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i != slot && isMenuItem(contents[i])) {
                inv.setItem(i, null);
            }
        }
        if (isMenuItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        if (!isEnabled(player)) {
            if (isMenuItem(inv.getItem(slot))) {
                inv.setItem(slot, null);
            }
            return;
        }
        ItemStack current = inv.getItem(slot);
        if (current != null && !current.isEmpty() && !isMenuItem(current)) {
            // Move whatever is in the slot somewhere else; if the inventory is full, keep it and skip the item.
            int free = firstFreeNotHotbarSlot(inv, slot);
            if (free < 0) {
                return;
            }
            inv.setItem(free, current);
        }
        inv.setItem(slot, create(player));
    }

    private static int firstFreeNotHotbarSlot(PlayerInventory inv, int reserved) {
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.isEmpty()) {
                return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (i != reserved && (s == null || s.isEmpty())) {
                return i;
            }
        }
        return -1;
    }

    private void open(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastUse.put(player.getUniqueId(), now);
        if (last != null && now - last < 300) {
            return;
        }
        String command = config.menuItemCommand;
        if (Bukkit.getCommandMap().getCommand(command) == null) {
            msg.send(player, "menu-item.coming-soon");
            return;
        }
        core.sounds().play(player, "click");
        player.performCommand(command);
    }

    private void later(Player player) {
        Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }

    // ------------------------------------------------------------------ give back

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        later(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ use

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || !isMenuItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isMenuItem(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
            open(event.getPlayer());
        }
    }

    // ------------------------------------------------------------------ protection

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean involved = isMenuItem(event.getCurrentItem()) || isMenuItem(event.getCursor());
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            involved |= isMenuItem(player.getInventory().getItem(event.getHotbarButton()));
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            involved |= isMenuItem(player.getInventory().getItemInOffHand());
        }
        if (!involved) {
            return;
        }
        event.setCancelled(true);
        // Clicking the star in your own inventory opens the menu (nice on Bedrock and from the E screen).
        if (isMenuItem(event.getCurrentItem()) && event.getClickedInventory() instanceof PlayerInventory
                && event.getView().getTopInventory().getType() == InventoryType.CRAFTING
                && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
            Bukkit.getScheduler().runTask(core.plugin(), () -> {
                player.closeInventory();
                open(player);
            });
        }
        later(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (isMenuItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isMenuItem(event.getMainHandItem()) || isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isMenuItem);
        event.getItemsToKeep().removeIf(this::isMenuItem);
    }
}
