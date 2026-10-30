package net.skybornmc.skyblock.tools;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import net.skybornmc.skyblock.common.tools.ToolKind;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import net.skybornmc.skyblock.common.stacking.StackKillEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Optional;

/**
 * Bukkit side of living tools outside The Depths: tool menu on shift + right-click, keep on death, vanilla guards (no
 * anvil, smithing, grindstone, crafting or enchanting table with custom tools or blueprints), island block and kill XP,
 * the Auto-smelt and Lifesteal abilities, first-join starter kit and saving on quit.
 */
final class ToolsListener implements Listener {

    /** What the listener needs from The Depths (implemented by {@link DepthsHooks}; absent without the depths module). */
    interface Depths {
        boolean inDepths(World world);

        /** Combat tier of the zone at a location, 0 outside combat zones. */
        int combatTier(Location location);
    }

    private final ToolsModule module;

    ToolsListener(ToolsModule module) {
        this.module = module;
    }

    // ------------------------------------------------------------------ tool menu

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (!module.config().shiftRightClick || event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Optional<ToolData> tool = ToolItems.read(player.getInventory().getItemInMainHand());
        if (tool.isEmpty() || !player.hasPermission(ToolsModule.PERM_MENU)) {
            return;
        }
        event.setCancelled(true); // no stripping, tilling or casting while opening the menu
        module.openToolMenu(player, tool.get().id());
    }

    // ------------------------------------------------------------------ death

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!module.config().keepOnDeath || event.getKeepInventory()) {
            return;
        }
        Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack drop = drops.next();
            if (ToolItems.read(drop).isPresent()) {
                drops.remove();
                event.getItemsToKeep().add(drop);
            }
        }
    }

    // ------------------------------------------------------------------ vanilla guards

    private static boolean protectedItem(ItemStack stack) {
        return ToolItems.read(stack).isPresent() || ToolItems.readBlueprint(stack).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareResult(PrepareResultEvent event) {
        for (ItemStack stack : event.getInventory().getContents()) {
            if (protectedItem(stack)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (protectedItem(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter) {
            for (ItemStack stack : crafter.getInventory().getContents()) {
                if (protectedItem(stack)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (protectedItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (protectedItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ island XP and abilities

    /** Blocks broken outside The Depths (Depths breaks are cancelled by the engine and handled in {@link DepthsHooks}). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Optional<ToolData> tool = ToolItems.read(player.getInventory().getItemInMainHand());
        if (tool.isEmpty()) {
            return;
        }
        Depths depths = module.depths();
        if (depths != null && depths.inDepths(event.getBlock().getWorld())) {
            return;
        }
        Material type = event.getBlock().getType();
        double xp = module.catalog().islandXp(type);
        if (xp <= 0 || !fits(tool.get().kind(), type)) {
            return;
        }
        module.tracker().gain(player, xp * module.config().islandFactor,
                tool.get().kind() == ToolKind.AXE ? ToolData.STAT_LOGS : ToolData.STAT_BLOCKS);
    }

    /** Pickaxes level on ores, axes on logs. */
    private static boolean fits(ToolKind kind, Material block) {
        String id = block.getKey().getKey();
        boolean log = id.endsWith("_log") || id.endsWith("_stem");
        return kind == ToolKind.AXE ? log : kind == ToolKind.PICKAXE && !log;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Optional<ToolData> tool = ToolItems.read(event.getPlayer().getInventory().getItemInMainHand());
        if (tool.isEmpty() || !ToolItems.ABILITY_AUTO_SMELT.equals(tool.get().ability())) {
            return;
        }
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (ItemStackHelper.isPlain(stack)) {
                module.catalog().smelted(stack.getType()).ifPresent(to -> item.setItemStack(stack.withType(to)));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        Optional<ToolData> tool = ToolItems.read(player.getInventory().getItemInMainHand());
        if (tool.isEmpty() || !ToolItems.ABILITY_LIFESTEAL.equals(tool.get().ability())) {
            return;
        }
        double share = module.catalog().ability(ToolItems.ABILITY_LIFESTEAL).map(ToolCatalog.AbilityDef::value).orElse(0.0);
        double heal = Math.min(module.config().lifestealMaxHeal, event.getFinalDamage() * share);
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (heal > 0 && max != null && !player.isDead()) {
            player.setHealth(Math.min(max.getValue(), player.getHealth() + heal));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || entity instanceof Player) {
            return;
        }
        if (entity.fromMobSpawner() && !module.config().spawnerMobsXp) {
            return;
        }
        Optional<ToolData> tool = ToolItems.read(killer.getInventory().getItemInMainHand());
        Depths depths = module.depths();
        int combatTier = depths == null ? 0 : depths.combatTier(entity.getLocation());
        if (tool.isPresent() && tool.get().kind() == ToolKind.SWORD) {
            double xp;
            if (combatTier > 0) {
                xp = module.config().tierXp(combatTier) * module.config().killMultiplier;
            } else if (depths != null && depths.inDepths(entity.getWorld())) {
                xp = 0; // ambient mobs in the mine/forest give no tool XP
            } else {
                xp = module.config().tierXp(1) * module.config().killMultiplier * module.config().islandFactor;
            }
            if (xp > 0) {
                module.tracker().gain(killer, xp, ToolData.STAT_KILLS);
            }
        }
        if (combatTier > 0) {
            module.drops().roll(killer, "combat", combatTier, entity.getLocation());
        }
    }

    /**
     * Tool XP for stacked kills that had no death event of their own (E3 batch kills), paid in one call with the same
     * rules as {@link #onKill} on the stack's entity (islands only: stacks never exist in The Depths).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStackKill(StackKillEvent event) {
        Player killer = event.killer();
        if (killer == null || event.extraKills() <= 0) {
            return;
        }
        if (event.entity().fromMobSpawner() && !module.config().spawnerMobsXp) {
            return;
        }
        Optional<ToolData> tool = ToolItems.read(killer.getInventory().getItemInMainHand());
        if (tool.isEmpty() || tool.get().kind() != ToolKind.SWORD) {
            return;
        }
        Depths depths = module.depths();
        if (depths != null && depths.inDepths(event.location().getWorld())) {
            return;
        }
        double xp = module.config().tierXp(1) * module.config().killMultiplier * module.config().islandFactor * event.extraKills();
        if (xp > 0) {
            module.tracker().gain(killer, xp, ToolData.STAT_KILLS, event.extraKills());
        }
    }

    // ------------------------------------------------------------------ join and quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (module.config().starterOnFirstJoin && !event.getPlayer().hasPlayedBefore()) {
            Player player = event.getPlayer();
            module.core.scheduler().later("tools-first-join", 40, () -> {
                if (player.isOnline()) {
                    module.forge().claimStarter(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.tracker().renderAll(event.getPlayer());
        module.tracker().flushAsync();
    }

    /** Small item checks. */
    static final class ItemStackHelper {
        private ItemStackHelper() {
        }

        /** A vanilla drop (no custom item, no name): safe to smelt. */
        static boolean isPlain(ItemStack stack) {
            return stack != null && !stack.isEmpty() && net.skybornmc.skyblock.common.item.ItemRegistry.idOf(stack).isEmpty()
                    && !(stack.hasItemMeta() && stack.getItemMeta().hasDisplayName());
        }
    }
}
