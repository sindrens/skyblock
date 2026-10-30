package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUICK1 island auto-pickup (owner answer O15, QoL #1): blocks a member breaks on their <b>own</b> island drop straight
 * into their inventory. Switch in {@code /settings} ({@code auto-pickup}, default on).
 *
 * <p><b>Item safety.</b> {@link BlockDropItemEvent} hands over the drops before they exist in the world. Each stack is
 * added to the inventory and removed from the event (or shrunk to what did not fit) in the same main-thread step, so a
 * stack is either in the inventory or on the ground, never both. What does not fit stays on the ground exactly like a
 * normal drop: it is not sent to {@code /box}, because one full inventory at a cobblestone generator would write a box
 * row per block (security finding F-09). Runs at HIGHEST, after auto-smelt (tools) and staff-item marking (essentials)
 * changed the stacks.</p>
 *
 * <p>Only members of the island (its stored member keys) in survival or adventure, with the island role permission
 * {@code pickup}, never while their profile switches. Mob drops are not included yet (stacked-mob loot is spawned by the
 * stacking module; see docs/OPEN_QUESTIONS.md).</p>
 */
final class IslandAutoPickup implements Listener {

    static final String SETTING = "auto-pickup";

    private final IslandsModule module;
    private final IslandManager islands;
    private volatile PlayerSettings settings;
    /** Last "inventory full" hint per player (rate-limited). */
    private final Map<UUID, Long> lastFullHint = new ConcurrentHashMap<>();

    IslandAutoPickup(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.islands = islands;
    }

    /** One tick after enable (the menus module publishes /settings). */
    void hookSettings() {
        PlayerSettings s = module.core().services().optional(PlayerSettings.class).orElse(null);
        if (s == null) {
            return;
        }
        try {
            s.register(new SettingToggle(IslandsModule.ID, SETTING, SettingCategory.GAMEPLAY, Material.HOPPER,
                    module.config().autoPickupDefault, null, "navigation", "settings.toggles." + SETTING, false));
            settings = s;
        } catch (RuntimeException e) {
            module.core().plugin().getLogger().warning("Islands: could not register the auto-pickup setting: " + e.getMessage());
        }
    }

    void stop() {
        settings = null;
        lastFullHint.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!module.config().autoPickupEnabled || event.getItems().isEmpty() || !wants(player)) {
            return;
        }
        Block block = event.getBlock();
        if (!islands.isIslandWorld(block.getWorld())) {
            return;
        }
        Island island = islands.islandAt(block.getX(), block.getZ());
        if (island == null || !eligible(player.getGameMode(), island.members().containsKey(islands.key(player)),
                islands.profiles().isSwitching(player.getUniqueId()))
                || !islands.roleAllows(island, islands.key(player), IslandPerm.PICKUP)) {
            return;
        }
        boolean full = false;
        for (Iterator<Item> it = event.getItems().iterator(); it.hasNext(); ) {
            Item item = it.next();
            ItemStack stack = item.getItemStack();
            if (stack.isEmpty()) {
                continue;
            }
            int left = leftover(player.getInventory().addItem(stack.clone()));
            if (left <= 0) {
                it.remove(); // in the inventory now: this drop never spawns
            } else {
                full = true;
                if (left < stack.getAmount()) {
                    ItemStack rest = stack.clone();
                    rest.setAmount(left);
                    item.setItemStack(rest); // only what did not fit lands on the ground
                }
            }
        }
        if (full) {
            fullHint(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastFullHint.remove(event.getPlayer().getUniqueId());
    }

    private boolean wants(Player player) {
        PlayerSettings s = settings;
        return s == null ? module.config().autoPickupDefault : s.get(player, SETTING);
    }

    private void fullHint(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastFullHint.get(player.getUniqueId());
        if (!hintDue(last, now, module.config().autoPickupFullHintSeconds * 1000L)) {
            return;
        }
        lastFullHint.put(player.getUniqueId(), now);
        module.msg().actionBar(player, "auto-pickup.full");
    }

    // ------------------------------------------------------------------ pure rules (unit-tested)

    /** Survival or adventure members of this island whose profile is not switching. */
    static boolean eligible(GameMode mode, boolean member, boolean switching) {
        return member && !switching && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
    }

    /** Items that did not fit, from {@code addItem}'s result. */
    static int leftover(Map<Integer, ItemStack> notFitted) {
        int left = 0;
        for (ItemStack rest : notFitted.values()) {
            left += rest == null ? 0 : rest.getAmount();
        }
        return left;
    }

    /** The "inventory full" hint at most once per {@code everyMillis} (0 = never). */
    static boolean hintDue(Long last, long now, long everyMillis) {
        if (everyMillis <= 0) {
            return false;
        }
        return last == null || now - last >= everyMillis;
    }
}
