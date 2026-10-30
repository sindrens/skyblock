package net.skybornmc.skyblock.npcs;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.skybornmc.skyblock.common.npc.NpcClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;


/**
 * Clicks on NPCs and holograms, and protection: our entities never take damage, burn, trade or get targeted.
 * Right-click (and, with {@code npcs.left-click-acts}, a hit, because Bedrock touch players tap = hit) runs the NPC's
 * action or shows its help; sneak-click shows the help of action NPCs. Holograms with a click box show their help.
 * One click per player per {@code npcs.click-cooldown-ms} (spam guard). Paper 26.x fires only
 * {@code PlayerInteractAtEntityEvent} for a right-click (see {@link ClickGate}), so every main-hand interact acts.
 */
final class NpcListener implements Listener {

    private final NpcsModule module;
    private final ClickGate gate = new ClickGate();

    NpcListener(NpcsModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        Placed placed = find(event.getRightClicked());
        if (placed == null) {
            return;
        }
        event.setCancelled(true); // no trading, no name tags, no leads
        if (!ClickGate.interactActs(event.getHand() == EquipmentSlot.HAND)) {
            return; // the off-hand copy of the same right-click
        }
        click(event.getPlayer(), placed);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Placed placed = find(event.getAttacked());
        if (placed == null) {
            return;
        }
        event.setCancelled(true);
        if (module.config().leftClickActs) {
            click(event.getPlayer(), placed);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains(Placer.TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity().getScoreboardTags().contains(Placer.TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && event.getTarget().getScoreboardTags().contains(Placer.TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gate.forget(event.getPlayer().getUniqueId());
    }

    private Placed find(Entity entity) {
        Placer placer = module.placer();
        return placer == null ? null : placer.find(entity);
    }

    private void click(Player player, Placed placed) {
        if (!gate.pass(player.getUniqueId(), System.currentTimeMillis(), module.config().clickCooldownMs)) {
            return;
        }
        if (placed.kind != Placed.Kind.NPC) {
            module.help().showHologram(player, placed);
            return;
        }
        Spots.Npc npc = placed.npc;
        RoleRules.Action action = module.roles().actionOf(npc.role());
        RoleRules.Decision decision = RoleRules.decide(action, player.isSneaking(), module.config().sneakForHelp);
        NpcClickEvent call = new NpcClickEvent(player, npc.id(), npc.role(), decision == RoleRules.Decision.HELP);
        Bukkit.getPluginManager().callEvent(call);
        if (call.isCancelled()) {
            return;
        }
        if (decision == RoleRules.Decision.RUN) {
            module.core().sounds().play(player, "click");
            player.performCommand(action.command());
        } else {
            module.help().showNpc(player, npc, action);
        }
    }
}
