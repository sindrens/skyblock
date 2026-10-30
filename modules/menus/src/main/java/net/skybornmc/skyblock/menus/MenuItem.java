package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.format.TextDecoration;
import net.skybornmc.skyblock.common.menu.MenuService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
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
 * Right- or left-click (also a tap on Bedrock) opens {@code /menu}. It always stays in the inventory (FIXES1): it can't
 * be dropped, moved, put in containers, crafted with or lost on death, and it is put back on join, respawn, world
 * change, game mode change, after closing any inventory and by a light check every {@code menu-item.check-seconds}.
 * Players can still turn it off in {@code /settings} ({@code menu-item}). Staff mode owns the hotbar while it is on.
 *
 * <p>FIXES1 root cause of "Removed 1 menu item(s) from X's inventory" and the item vanishing: this item used the PDC
 * key {@code phoenixskyblock:menu_item}, the same key the menu framework puts on GUI buttons
 * ({@link MenuService#BUTTON_KEY}). The framework deletes every item with that key from a player's own inventory on join
 * and after every menu close (defence against buttons leaking out of a GUI), so it deleted this item each time the
 * player joined or closed /menu. The item now has its own key ({@link #KEY}); old copies are swapped silently.</p>
 */
final class MenuItem implements Listener {

    static final String SETTING = "menu-item";
    /** PDC key of the hotbar menu item; must never equal {@link MenuService#BUTTON_KEY}. */
    static final String KEY = "hotbar_menu_item";

    private final Nav nav;
    private final MenusConfig config;
    private final NamespacedKey key;
    /** The old key (shared with GUI buttons): only used to recognise and replace old copies of this item. */
    private final NamespacedKey legacyKey;
    private Scheduler.Task task;

    MenuItem(Nav nav, MenusConfig config) {
        this.nav = nav;
        this.config = config;
        this.key = new NamespacedKey(nav.core.plugin(), KEY);
        this.legacyKey = new NamespacedKey(nav.core.plugin(), MenuService.BUTTON_KEY);
    }

    private int slot() {
        return Math.max(0, Math.min(8, config.menuItemSlot - 1));
    }

    void start() {
        stop();
        if (config.menuItemCheckSeconds > 0) {
            long period = config.menuItemCheckSeconds * 20L;
            task = nav.core.scheduler().repeat("menus:menu-item", period, period, () -> Bukkit.getOnlinePlayers().forEach(this::ensure));
        }
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
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

    /**
     * SMALL2: reads the item's data through the read-only PDC view; {@code getItemMeta()} built a full copy of the meta
     * (name, lore, enchantments, data) for every slot it looked at, which made each check and scan expensive.
     */
    boolean is(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(key);
    }

    /**
     * An old copy of this item (before FIXES1): the shared button key on the menu item material. Only checked on join,
     * before the menu framework's leak check runs, so the framework never logs it as a leaked GUI button.
     */
    private boolean isLegacy(ItemStack item) {
        if (item == null || item.isEmpty() || !item.getPersistentDataContainer().has(legacyKey)) {
            return false;
        }
        Material material = Material.matchMaterial(config.menuItemMaterial);
        return item.getType() == (material == null ? Material.NETHER_STAR : material) || item.getType() == Material.NETHER_STAR;
    }

    private boolean wanted(Player player) {
        return config.menuItemEnabled && nav.settings.get(player, SETTING);
    }

    /** Staff mode owns the hotbar (its kit uses slot 9), and a profile switch is swapping the inventory. */
    private boolean paused(Player player) {
        if (nav.core.placeholders().has("staff_mode")
                && nav.core.placeholders().text(player, "staff_mode").map("true"::equalsIgnoreCase).orElse(false)) {
            return true;
        }
        return nav.core.services().optional(ProfileService.class).map(p -> p.isSwitching(player.getUniqueId())).orElse(false);
    }

    /** What a cheap check does about the menu item slot (pure, unit-tested). */
    enum Placement {
        /** Nothing: not wanted, paused, or already in its slot. */
        NONE,
        /** The slot is empty: put the item there. */
        PUT,
        /** The slot holds the player's own item: move it to {@code firstEmpty} first, then put the item. */
        MOVE_THEN_PUT,
        /** The slot holds the player's own item and the inventory is full: keep theirs (no scan; tried again next check). */
        NO_ROOM
    }

    static Placement placement(boolean wanted, boolean paused, boolean inSlot, boolean slotEmpty, int firstEmpty) {
        if (!wanted || inSlot || paused) {
            return Placement.NONE;
        }
        if (slotEmpty) {
            return Placement.PUT;
        }
        return firstEmpty >= 0 ? Placement.MOVE_THEN_PUT : Placement.NO_ROOM;
    }

    /**
     * Cheap check (one slot): runs {@link #apply} only when the item is wanted, missing from its slot <b>and</b> there is
     * room to put it back. SMALL2 root cause of 'menus:menu-item' being slow: this check runs for every online player
     * every {@code check-seconds}; a player whose slot 9 held their own item with a full inventory fell through to
     * {@link #apply} on every run, which read all 41 slots and built a full item meta copy for each
     * ({@code getItemMeta()}) just to find nothing to do. Now such a player costs one slot read and {@code firstEmpty()},
     * the full scan only happens when the item really goes back, and {@link #is} reads the PDC view without a meta copy.
     */
    void ensure(Player player) {
        if (!player.isOnline() || !wanted(player)) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack current = inv.getItem(slot());
        if (is(current)) {
            return;
        }
        boolean slotEmpty = current == null || current.isEmpty();
        Placement what = placement(true, paused(player), false, slotEmpty, slotEmpty ? -1 : inv.firstEmpty());
        if (what == Placement.PUT || what == Placement.MOVE_THEN_PUT) {
            apply(player);
        }
    }

    /** Puts the item in its slot if the player wants it, or removes every copy if not. */
    void apply(Player player) {
        if (paused(player)) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        boolean wanted = wanted(player);
        // Remove copies outside the slot (old slot setting, plugin reload) so there is never more than one.
        for (int i = 0; i < inv.getSize(); i++) {
            if (i != slot() && is(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (is(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
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

    private void ensureLater(Player player) {
        Bukkit.getScheduler().runTask(nav.core.plugin(), () -> ensure(player));
    }

    // ------------------------------------------------------------------ events

    /** Before the menu framework's leak check (NORMAL): old copies with the shared key are taken out silently. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinEarly(PlayerJoinEvent event) {
        PlayerInventory inv = event.getPlayer().getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isLegacy(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    /** Island profiles: a new profile's inventory is loaded, so the menu item is put back (its setting is per profile too). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent event) {
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        ensureLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        ensureLater(event.getPlayer());
    }

    /** Any inventory closed (staff mode left, a chest, /invsee edits, /clear in between): put it back if it is gone. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            ensureLater(player);
        }
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
        boolean offhandSwap = event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && is(event.getWhoClicked().getInventory().getItemInOffHand());
        if (is(event.getCurrentItem()) || is(event.getCursor()) || hotbarSwap || offhandSwap) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE) {
                    // The creative inventory trusts the client: resync so the item is never lost client-side.
                    Bukkit.getScheduler().runTask(nav.core.plugin(), () -> {
                        player.updateInventory();
                        ensure(player);
                    });
                }
                if (is(event.getCurrentItem()) && event.getView().getTopInventory().getHolder() == player) {
                    // Clicking it in your own inventory opens the menu too.
                    Bukkit.getScheduler().runTask(nav.core.plugin(), () -> player.performCommand("menu"));
                }
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
