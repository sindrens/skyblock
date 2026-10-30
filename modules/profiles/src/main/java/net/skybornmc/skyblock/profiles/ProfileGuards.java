package net.skybornmc.skyblock.profiles;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Owner decision P4 for items in the world, and the lock while a switch runs.
 *
 * <ul>
 *   <li><b>Switch lock:</b> between saving the current inventory and loading the new one the player can't drop, pick
 *       up, click, open, use, break, place, run commands or take damage, so nothing can be duplicated or lost.</li>
 *   <li><b>Dropped items and thrown tridents/arrows</b> remember the profile that dropped them; another profile of the
 *       same player can never pick them up (drop at spawn → switch → pick up is blocked). Audited once per item.</li>
 * </ul>
 */
final class ProfileGuards implements Listener {

    private final ProfileServiceImpl profiles;
    private final NamespacedKey dropKey;

    ProfileGuards(ProfilesModule module, ProfileServiceImpl profiles) {
        this.profiles = profiles;
        this.dropKey = new NamespacedKey(module.core.plugin(), "profile_drop");
    }

    // ------------------------------------------------------------------ switch lock

    private boolean locked(Entity entity) {
        return entity instanceof Player p && profiles.isSwitching(p.getUniqueId());
    }

    private void lock(Entity entity, Cancellable event) {
        if (locked(entity)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        lock(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        lock(event.getWhoClicked(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        lock(event.getWhoClicked(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent event) {
        lock(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        lock(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        lock(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        lock(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        lock(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        lock(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        lock(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        lock(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        lock(event.getEntity(), event);
    }

    // ------------------------------------------------------------------ dropped items and projectiles

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void tagDrop(PlayerDropItemEvent event) {
        UUID key = profiles.profileKey(event.getPlayer().getUniqueId());
        event.getItemDrop().getPersistentDataContainer().set(dropKey, PersistentDataType.STRING, key.toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void tagProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter) {
            UUID key = profiles.profileKey(shooter.getUniqueId());
            arrow.getPersistentDataContainer().set(dropKey, PersistentDataType.STRING, key.toString());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (locked(player)) {
            event.setCancelled(true);
            return;
        }
        if (foreignOwnProfile(player, event.getItem())) {
            event.setCancelled(true);
            return;
        }
        // REVIEW3 R3-2: bound loot (from bound crate keys) is only picked up by the profile it is bound to.
        org.bukkit.inventory.ItemStack stack = event.getItem().getItemStack();
        if (net.skybornmc.skyblock.common.item.BoundItems.isBound(stack)
                && !net.skybornmc.skyblock.common.item.BoundItems.mayReceive(stack, profiles.profileKey(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        if (locked(event.getPlayer()) || foreignOwnProfile(event.getPlayer(), event.getArrow())) {
            event.setCancelled(true);
        }
    }

    /** True if the entity was dropped/thrown by ANOTHER profile of this player (audited the first time per entity). */
    private boolean foreignOwnProfile(Player player, Entity entity) {
        String raw = entity.getPersistentDataContainer().get(dropKey, PersistentDataType.STRING);
        if (raw == null) {
            return false;
        }
        UUID from;
        try {
            from = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return false;
        }
        UUID to = profiles.profileKey(player.getUniqueId());
        if (!profiles.ownOtherProfile(from, to)) {
            return false;
        }
        NamespacedKey audited = new NamespacedKey(dropKey.getNamespace(), dropKey.getKey() + "_audited");
        if (!entity.getPersistentDataContainer().has(audited)) {
            entity.getPersistentDataContainer().set(audited, PersistentDataType.BYTE, (byte) 1);
            profiles.blockOwnTransfer(AuditCategory.ITEM, "item_pickup", from, to, Actor.player(player));
        }
        return true;
    }
}
