package net.skybornmc.skyblock.stacking;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dropped items (owner S14–S19): items of the same kind within {@code items.merge-radius} become one entity whose count
 * (a long, no max) lives in its persistent data; the visible stack is a single item and the name reads
 * {@code Diamond x1,234}. Players pick up what fits (the rest stays), hoppers take up to {@code items.hopper-per-transfer}
 * at once, mobs never pick up stacks, and items despawn {@code items.despawn-minutes} after the last item joined.
 * Unique custom items (with a unique id from core.items) are never touched.
 *
 * <p>Merging is event-driven: a new drop looks once for a neighbour (Paper's entity-section lookup, only the chunks
 * around the drop), and vanilla's own item merge ({@link ItemMergeEvent}) joins stacks that float together later.
 * No timers.</p>
 */
final class ItemStacker implements Listener {

    private final StackingModule module;
    /** True while this class spawns an item itself (its own spawn event must not merge it again). */
    private boolean internal;

    /** Profile that dropped an item (written by the profiles module); items of different droppers never merge (P4). */
    private final NamespacedKey dropKey;

    ItemStacker(StackingModule module) {
        this.module = module;
        this.dropKey = new NamespacedKey(module.core().plugin(), "profile_drop");
    }

    /** The profile tag of an item entity, or null for items nobody dropped (mob drops, blocks). */
    private String dropper(Item item) {
        return item.getPersistentDataContainer().get(dropKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ data

    static boolean isUnique(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(ItemKeys.UUID, PersistentDataType.STRING);
    }

    /** Items held by this entity: the stored count, or the visible amount for items that never stacked. */
    static long count(Item item) {
        Long stored = item.getPersistentDataContainer().get(StackKeys.ITEM_COUNT, PersistentDataType.LONG);
        return stored != null ? stored : item.getItemStack().getAmount();
    }

    static boolean isStacked(Item item) {
        return item.getPersistentDataContainer().has(StackKeys.ITEM_COUNT, PersistentDataType.LONG);
    }

    /** Sets the count (removes the entity at 0), keeps the visible stack at one item and renames it. */
    void setCount(Item item, long count, boolean touch) {
        if (count <= 0) {
            item.remove();
            return;
        }
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        pdc.set(StackKeys.ITEM_COUNT, PersistentDataType.LONG, count);
        if (touch || !pdc.has(StackKeys.ITEM_SINCE, PersistentDataType.LONG)) {
            pdc.set(StackKeys.ITEM_SINCE, PersistentDataType.LONG, System.currentTimeMillis());
        }
        ItemStack visible = item.getItemStack();
        if (visible.getAmount() != 1) {
            visible.setAmount(1);
            item.setItemStack(visible);
        }
        rename(item, visible, count);
    }

    private void rename(Item item, ItemStack stack, long count) {
        if (count < module.config().itemNameMinCount) {
            if (item.customName() != null) {
                item.customName(null);
                item.setCustomNameVisible(false);
            }
            return;
        }
        Component name = module.msg().component(Audience.empty(), "item-name", Msg.c("name", stack.effectiveName()),
                Msg.p("count", Format.number(count)));
        item.customName(name);
        item.setCustomNameVisible(true);
    }

    /** True for items other plugins use as displays (never merged or counted). */
    private static boolean isDisplay(Item item) {
        return item.getPickupDelay() >= Short.MAX_VALUE || item.isUnlimitedLifetime() || !item.canPlayerPickup();
    }

    private boolean mergeable(Item item) {
        return item.isValid() && !isDisplay(item) && !isUnique(item.getItemStack());
    }

    /** The nearest item within the merge radius that can take {@code stack}, or null. */
    private Item neighbour(Location at, ItemStack stack, Item self) {
        String dropper = self == null ? null : dropper(self);
        double radius = module.config().itemMergeRadius;
        Item best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Item other : at.getWorld().getNearbyEntitiesByType(Item.class, at, radius)) {
            if (other == self || !mergeable(other) || !other.getItemStack().isSimilar(stack)
                    || !java.util.Objects.equals(dropper, dropper(other))) {
                continue;
            }
            double d = other.getLocation().distanceSquared(at);
            if (d < bestDistance) {
                bestDistance = d;
                best = other;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ dropping (used by mob kills)

    /**
     * Drops {@code count} of {@code prototype} at {@code at} as one stacked item (merged into a neighbour when there is
     * one). Unique items are dropped one by one, unstacked.
     */
    void drop(Location at, ItemStack prototype, long count) {
        if (count <= 0 || prototype == null || prototype.isEmpty()) {
            return;
        }
        World world = at.getWorld();
        if (isUnique(prototype) || !module.eligible(world)) {
            int max = Math.max(1, prototype.getMaxStackSize());
            long left = Math.min(count, 64L * max); // never thousands of entities outside stacking worlds
            while (left > 0) {
                ItemStack part = prototype.clone();
                int amount = (int) Math.min(left, max);
                part.setAmount(amount);
                world.dropItemNaturally(at, part);
                left -= amount;
            }
            return;
        }
        Item target = neighbour(at, prototype, null);
        if (target != null) {
            setCount(target, count(target) + count, true);
            return;
        }
        ItemStack one = prototype.clone();
        one.setAmount(1);
        internal = true;
        try {
            world.dropItemNaturally(at, one, item -> setCount(item, count, true));
        } finally {
            internal = false;
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        if (internal) {
            return;
        }
        Item item = event.getEntity();
        if (!module.eligible(item.getWorld())) {
            return;
        }
        long start = module.perf().start();
        try {
            ItemStack stack = item.getItemStack();
            if (isUnique(stack) || isDisplay(item)) {
                return;
            }
            long amount = isStacked(item) ? count(item) : stack.getAmount();
            Item target = neighbour(event.getLocation(), stack, item);
            if (target != null) {
                setCount(target, count(target) + amount, true);
                event.setCancelled(true);
                return;
            }
            setCount(item, amount, true);
        } finally {
            module.perf().end(start);
        }
    }

    /** Vanilla merges items that touch; with stacked counts the counts are added instead. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        Item source = event.getEntity();
        Item target = event.getTarget();
        if (!module.eligible(source.getWorld())) {
            return;
        }
        event.setCancelled(true);
        if (!mergeable(source) || !mergeable(target) || !source.getItemStack().isSimilar(target.getItemStack())
                || !java.util.Objects.equals(dropper(source), dropper(target))) {
            return;
        }
        long start = module.perf().start();
        try {
            long total = count(source) + count(target);
            source.remove();
            setCount(target, total, true);
        } finally {
            module.perf().end(start);
        }
    }

    /** Players take as much as fits (owner S16); mobs never pick up a stack. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (!isStacked(item) || !module.eligible(item.getWorld())) {
            return;
        }
        LivingEntity entity = event.getEntity();
        long count = count(item);
        if (!(entity instanceof Player player)) {
            if (count > 1) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        long start = module.perf().start();
        try {
            ItemStack prototype = item.getItemStack();
            int max = Math.max(1, prototype.getMaxStackSize());
            long offered = Math.min(count, 41L * max); // the whole inventory at most, never more allocations
            long taken = offered - leftover(player.getInventory(), prototype, offered);
            if (taken <= 0) {
                return;
            }
            // The client shrinks its copy of the item by the animation amount and removes it at 0: send 1 (= the visible
            // item) only when nothing is left, 0 while the stack stays on the ground.
            player.playPickupItemAnimation(item, count - taken <= 0 ? 1 : 0);
            setCount(item, count - taken, false);
        } finally {
            module.perf().end(start);
        }
    }

    /** Hoppers take up to {@code items.hopper-per-transfer} at once (owner S17). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent event) {
        Item item = event.getItem();
        if (!isStacked(item) || !module.eligible(item.getWorld())) {
            return;
        }
        event.setCancelled(true);
        long start = module.perf().start();
        try {
            long count = count(item);
            long offered = Math.min(count, module.config().hopperPerTransfer);
            long taken = offered - leftover(event.getInventory(), item.getItemStack(), offered);
            if (taken > 0) {
                setCount(item, count - taken, false);
            }
        } finally {
            module.perf().end(start);
        }
    }

    /** Owner S18: items on islands stay {@code items.despawn-minutes} after the last item joined. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        Item item = event.getEntity();
        if (!module.eligible(item.getWorld())) {
            return;
        }
        Long since = item.getPersistentDataContainer().get(StackKeys.ITEM_SINCE, PersistentDataType.LONG);
        if (since != null && System.currentTimeMillis() - since < module.config().itemDespawnMinutes * 60_000L) {
            event.setCancelled(true); // vanilla starts the item's age again; checked once more at the next despawn time
        }
    }

    /** Adds {@code amount} of {@code prototype} to {@code inventory}; returns what did not fit. */
    static long leftover(Inventory inventory, ItemStack prototype, long amount) {
        int max = Math.max(1, prototype.getMaxStackSize());
        List<ItemStack> parts = new ArrayList<>();
        long left = amount;
        while (left > 0) {
            ItemStack part = prototype.clone();
            int n = (int) Math.min(left, max);
            part.setAmount(n);
            parts.add(part);
            left -= n;
        }
        Map<Integer, ItemStack> rest = inventory.addItem(parts.toArray(ItemStack[]::new));
        long notAdded = 0;
        for (ItemStack r : rest.values()) {
            notAdded += r.getAmount();
        }
        return notAdded;
    }
}
