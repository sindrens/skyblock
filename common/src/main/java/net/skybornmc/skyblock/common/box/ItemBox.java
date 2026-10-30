package net.skybornmc.skyblock.common.box;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * The item mailbox ({@code /box}, owner answer 7): items are <b>never dropped on the ground</b> when a player's inventory
 * is full. Whatever does not fit waits in the box of the profile it belongs to until the player takes it out. Published by
 * the box module; every module that hands items to a player uses it instead of {@code dropItemNaturally}:
 * <pre>{@code
 * ItemBox.give(core.services(), player, BoxSource.FORGE, item);            // inventory first, the rest to the box
 * ItemBox.overflow(core.services(), player, BoxSource.SHOP, leftovers);    // addItem(...) already ran, store the rest
 * ItemBox.of(core.services()).ifPresent(b -> b.deliver(profileKey, items, BoxSource.TRADE, actor)); // offline / profile key
 * }</pre>
 *
 * <h2>Guarantees</h2>
 * <ul>
 *   <li>{@link #deliver} stores all stacks of one call in <b>one transaction</b>. When the database fails (after retries)
 *       the stacks are written to a spool file and imported later, so a delivery is never lost.</li>
 *   <li>Taking an item out is remove-then-give: the row is deleted first (exactly once), then given; anything that does
 *       not fit or a player who left gets it back into the box. No duplication.</li>
 *   <li>The per-profile cap ({@code box.yml}) only warns: nothing is ever deleted or refused silently. Voluntary paths
 *       (a reward the player claims on purpose) may check {@link #full} and refuse before taking money.</li>
 *   <li>SECFIX1 F-09: a hard safety cap far above it ({@code cap.hard-max-entries}, 5,000): actions that can stop call
 *       {@link #refuses(Player)} first and are refused with a message, so no box grows forever. Items that already left
 *       somewhere are still stored.</li>
 * </ul>
 */
public interface ItemBox {

    /** Audit action of a delivery (category item). */
    String AUDIT_DELIVER = "box_deliver";

    /** What a delivery did. */
    record Delivery(int stacks, int boxCount, boolean overCap, boolean spooled) {

        public static final Delivery NONE = new Delivery(0, 0, false, false);
    }

    /** The published box, if the box module runs. Cheap: look it up per call. */
    static Optional<ItemBox> of(ServiceRegistry services) {
        return services.optional(ItemBox.class);
    }

    // ------------------------------------------------------------------ the service

    /**
     * Stores stacks in the box of {@code profileKey} (any thread; the stacks are serialized before this returns, so the
     * caller may reuse them). Empty and null stacks are skipped. Tells the owner when they are online on that profile.
     *
     * @param source where the items came from ({@link BoxSource}); shown in the item lore
     */
    CompletableFuture<Delivery> deliver(UUID profileKey, Collection<ItemStack> items, String source, Actor actor);

    /**
     * Main thread. Puts stacks into the player's inventory; whatever does not fit goes to the box of their active
     * profile. While the player switches profile or is in staff mode everything goes to the box.
     *
     * @return copies of the stacks that went to the box (empty when everything fit)
     */
    List<ItemStack> giveItems(Player player, Collection<ItemStack> items, String source);

    /** Main thread. Stores stacks that already did not fit (e.g. the result of {@code addItem}) in the player's box. */
    void store(Player player, Collection<ItemStack> leftovers, String source);

    /** Cached number of waiting stacks of a profile of an online player; -1 when not loaded. */
    int cachedCount(UUID profileKey);

    /** True when the box of this profile is at or over its cap (voluntary paths may refuse then). */
    boolean full(UUID profileKey);

    /**
     * SECFIX1 F-09: true (and the player told why) when the box of the player's active profile is at its hard safety cap
     * ({@code box.yml cap.hard-max-entries}). Call it before an action that may send items to the box and can simply not
     * happen (breaking a stacked spawner, claiming, buying): refuse the action instead of growing the box forever. Main
     * thread.
     */
    default boolean refuses(Player player) {
        return false;
    }

    /**
     * R3-22: the same hard-cap test as {@link #refuses(Player)} but silent, for paths that warn the player themselves
     * (throttled, e.g. Depths mining on a full satchel). Main thread.
     */
    default boolean hardFull(Player player) {
        return false;
    }

    /** The hard safety cap in stacks ({@code box.yml cap.hard-max-entries}), for messages. */
    default int hardCap() {
        return 5000;
    }

    /** Opens the admin view of a player's box (the Box button in {@code /inspect}). */
    void openStaffView(Player staff, UUID player, String name, Menu back);

    // ------------------------------------------------------------------ helpers for every module

    /**
     * Main thread. Inventory first, the rest to the box. When the box module is off the rest is dropped at the player's
     * feet (owner-protected) and a warning is logged: that is the only case where anything can land on the ground.
     *
     * @return copies of the stacks that did not fit the inventory
     */
    static List<ItemStack> give(ServiceRegistry services, Player player, String source, ItemStack... items) {
        return give(services, player, source, Arrays.asList(items));
    }

    /** {@link #give(ServiceRegistry, Player, String, ItemStack...)} for a collection. */
    static List<ItemStack> give(ServiceRegistry services, Player player, String source, Collection<ItemStack> items) {
        Optional<ItemBox> box = of(services);
        if (box.isPresent()) {
            return box.get().giveItems(player, items, source);
        }
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.isEmpty()) {
                rest.addAll(player.getInventory().addItem(item.clone()).values());
            }
        }
        dropFallback(player, rest, source);
        return rest;
    }

    /** Main thread. Stores leftovers that did not fit; same fallback as {@link #give(ServiceRegistry, Player, String, ItemStack...)}. */
    static void overflow(ServiceRegistry services, Player player, String source, Collection<ItemStack> leftovers) {
        if (leftovers == null || leftovers.isEmpty()) {
            return;
        }
        Optional<ItemBox> box = of(services);
        if (box.isPresent()) {
            box.get().store(player, leftovers, source);
        } else {
            dropFallback(player, new ArrayList<>(leftovers), source);
        }
    }

    /** {@link #refuses(Player)} through the registry (false when the box module is off). Main thread. */
    static boolean refuses(ServiceRegistry services, Player player) {
        return player != null && of(services).map(b -> b.refuses(player)).orElse(false);
    }

    /** {@link #hardFull(Player)} through the registry (false when the box module is off). Main thread, no message. */
    static boolean hardFull(ServiceRegistry services, Player player) {
        return player != null && of(services).map(b -> b.hardFull(player)).orElse(false);
    }

    /** True when the player's active profile box is full (false when the box module is off). */
    static boolean full(ServiceRegistry services, UUID profileKey) {
        return of(services).map(b -> b.full(profileKey)).orElse(false);
    }

    private static void dropFallback(Player player, List<ItemStack> rest, String source) {
        if (rest.isEmpty()) {
            return;
        }
        Logger.getLogger("PhoenixSkyblock").warning("Item box module is off: dropped " + rest.size() + " stack(s) from '" + source
                + "' at " + player.getName() + "'s feet");
        for (ItemStack r : rest) {
            player.getWorld().dropItem(player.getLocation(), r, drop -> drop.setOwner(player.getUniqueId()));
        }
    }
}
