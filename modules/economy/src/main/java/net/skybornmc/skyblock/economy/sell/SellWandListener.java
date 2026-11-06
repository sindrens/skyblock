package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.sell.SellRequest;
import net.skybornmc.skyblock.common.sell.SellResult;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.common.sell.SellWand;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Sell Wand ({@link SellWand}, owner 4 Nov 2026): right-click a chest or barrel on an island you are a member of, and
 * everything sellable in it is sold at once through {@link SellService} (the /sell rules: plugin, named and enchanted
 * items never sell and stay in the chest). One use per sale that paid something; the wand breaks at 0.
 *
 * <p>Dupe-safe like /sell: the stacks leave the chest first, then the sale runs; whatever was not sold goes back into the
 * same chest (what no longer fits goes to the player's inventory, then drops at the chest). One wand sale at a time per
 * player. The click runs after island protection (HIGHEST, ignoreCancelled), so only players who may open the chest can
 * use it.</p>
 */
public final class SellWandListener implements Listener {

    private final Core core;
    private final ModuleMessages msg;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    public SellWandListener(Core core, ModuleMessages msg) {
        this.core = core;
        this.msg = msg;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack wand = p.getInventory().getItemInMainHand();
        int uses = SellWand.uses(wand);
        if (uses < 0) {
            return;
        }
        event.setCancelled(true); // never opens the chest or places anything
        Block block = event.getClickedBlock();
        Inventory inv;
        if (block.getState() instanceof Chest chest) {
            inv = chest.getInventory();
        } else if (block.getState() instanceof Barrel barrel) {
            inv = barrel.getInventory();
        } else {
            msg.actionBar(p, "sell-wand.not-chest");
            return;
        }
        if (uses <= 0) {
            msg.send(p, "sell-wand.empty");
            return;
        }
        if (!memberHere(p, block.getLocation())) {
            msg.send(p, "sell-wand.not-island");
            return;
        }
        Optional<SellService> sell = core.services().optional(SellService.class);
        if (sell.isEmpty()) {
            msg.send(p, "sell-wand.unavailable");
            return;
        }
        if (!busy.add(p.getUniqueId())) {
            return;
        }
        // Take every stack out of the chest (the sale decides which ones come back).
        List<ItemStack> taken = new ArrayList<>();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && !contents[i].isEmpty()) {
                taken.add(contents[i].clone());
                inv.setItem(i, null);
            }
        }
        if (taken.isEmpty()) {
            busy.remove(p.getUniqueId());
            msg.send(p, "sell-wand.nothing");
            return;
        }
        // FX-04: charge the use now, on this exact wand, before the async sale (swapping items can no longer dodge it).
        int left = uses - 1;
        if (left <= 0) {
            p.getInventory().setItemInMainHand(null);
        } else {
            SellWand.setUses(core, wand, left);
            p.getInventory().setItemInMainHand(wand);
        }
        UUID profile = core.services().optional(ProfileService.class).map(s -> s.profileKey(p)).orElse(p.getUniqueId());
        SellRequest request = new SellRequest(p.getUniqueId(), p.getName(), profile, taken, sell.get().bonusPercent(p), "wand",
                Actor.player(p));
        Location at = block.getLocation();
        sell.get().sell(request).whenComplete((result, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            busy.remove(p.getUniqueId());
            List<ItemStack> back = error != null || result == null ? taken : result.giveBack();
            putBack(at, p, back);
            if (error != null || result == null || result.status() != SellResult.Status.SOLD) {
                refund(p, wand, left);
                msg.send(p, "sell-wand.nothing");
                return;
            }
            if (left <= 0) {
                msg.send(p, "sell-wand.broke");
            }
            msg.send(p, "sell-wand.sold", Msg.number("items", result.quote().items()), Msg.p("amount", Format.money(result.quote().total())),
                    Msg.number("uses", Math.max(0, left)));
            core.sounds().play(p, "money");
        }));
    }

    /** Nothing was sold: the charged use goes back onto the same wand (found by its id), or a 1-use wand when it broke. */
    private void refund(Player p, ItemStack charged, int left) {
        if (!p.isOnline()) {
            return;
        }
        if (left <= 0) {
            net.skybornmc.skyblock.common.box.ItemBox.give(core.services(), p, net.skybornmc.skyblock.common.box.BoxSource.SELL, SellWand.create(core, 1));
            return;
        }
        String id = charged.getItemMeta().getPersistentDataContainer().get(SellWand.ID, org.bukkit.persistence.PersistentDataType.STRING);
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && SellWand.uses(item) >= 0 && id != null
                    && id.equals(item.getItemMeta().getPersistentDataContainer().get(SellWand.ID, org.bukkit.persistence.PersistentDataType.STRING))) {
                SellWand.setUses(core, item, SellWand.uses(item) + 1);
                return;
            }
        }
    }

    /** Unsold stacks back into the chest, then the player, then on the ground at the chest. Main thread. */
    private void putBack(Location at, Player p, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        Block block = at.getBlock();
        Inventory inv = block.getState() instanceof Chest c ? c.getInventory() : block.getState() instanceof Barrel b ? b.getInventory() : null;
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack item : items) {
            HashMap<Integer, ItemStack> rest = inv == null ? new HashMap<>(java.util.Map.of(0, item)) : inv.addItem(item);
            overflow.addAll(rest.values());
        }
        if (overflow.isEmpty()) {
            return;
        }
        // Never dropped: the player's inventory, then the mailbox (also when they left meanwhile).
        UUID profile = core.services().optional(ProfileService.class).map(s -> s.profileKey(p)).orElse(p.getUniqueId());
        if (p.isOnline()) {
            net.skybornmc.skyblock.common.box.ItemBox.give(core.services(), p, net.skybornmc.skyblock.common.box.BoxSource.SELL, overflow);
        } else {
            net.skybornmc.skyblock.common.box.ItemBox.of(core.services()).ifPresent(b -> b.deliver(profile, overflow,
                    net.skybornmc.skyblock.common.box.BoxSource.SELL, Actor.system("sell_wand")));
        }
    }

    private boolean memberHere(Player p, Location at) {
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        if (islands.isEmpty()) {
            return false;
        }
        UUID key = core.services().optional(ProfileService.class).map(s -> s.profileKey(p)).orElse(p.getUniqueId());
        return islands.get().islandAt(at).map(info -> islands.get().isMember(info.id(), key)).orElse(false);
    }
}
