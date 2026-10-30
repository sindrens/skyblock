package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.skills.SkillXpGainEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Small hooks: the skill XP family (Scholar's Insight) on skill XP gains, and books/scrolls kept out of anvils,
 * grindstones, smithing tables, crafting and enchanting tables (no renaming a Common book into "Vein Rend III").
 */
final class EnchantsListener implements Listener {

    private final EnchantsModule module;

    EnchantsListener(EnchantsModule module) {
        this.module = module;
    }

    /**
     * Skill XP family (ENCHANTS §8.1): applied once here for the tool in the main hand, capped in the BonusService. Other
     * skill XP sources should register a BonusSource instead of changing the event, so the cap holds.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSkillXp(SkillXpGainEvent event) {
        BonusService bonus = module.core.services().optional(BonusService.class).orElse(null);
        if (bonus == null) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        BonusContext context = BonusContext.of(event.getPlayer(), held, event.getSkill().id());
        // Soul Reaper (E3): its share joins the family only for stacked kills; the cap still applies once.
        double soul = module.stacks() == null ? 0
                : module.stacks().soulReaperSkillPercent(event.getPlayer(), held, event.getSkill(), event.getSource());
        double multiplier = soul > 0
                ? StackKillMath.soulReaperMultiplier(bonus.total(BonusFamily.SKILL_XP, context), soul, bonus.cap(BonusFamily.SKILL_XP))
                : bonus.multiplier(BonusFamily.SKILL_XP, context);
        if (multiplier != 1.0) {
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        if (ours(event.getInventory().getFirstItem()) || ours(event.getInventory().getSecondItem())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        if (ours(event.getInventory().getUpperItem()) || ours(event.getInventory().getLowerItem())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        for (ItemStack stack : event.getInventory().getContents()) {
            if (ours(stack)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (ours(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Enchanting tables: books and scrolls can't go into the item slot. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchantingTable(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ENCHANTING) {
            return;
        }
        boolean intoTable = event.getClickedInventory() == event.getInventory() && ours(event.getCursor());
        boolean shiftIn = event.isShiftClick() && event.getClickedInventory() != event.getInventory() && ours(event.getCurrentItem());
        boolean hotbarIn = event.getClickedInventory() == event.getInventory() && event.getHotbarButton() >= 0
                && ours(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
        if (intoTable || shiftIn || hotbarIn) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.ops().forget(event.getPlayer().getUniqueId());
    }

    private static boolean ours(ItemStack stack) {
        return stack != null && BookItems.isOurs(stack);
    }
}
