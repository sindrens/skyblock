package net.skybornmc.skyblock.essentials;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Owner 3 Nov 2026: no hunger anywhere ({@code essentials.yml hunger.disabled}); eating still works. */
final class NoHunger implements Listener {

    private final EssentialsConfig config;

    NoHunger(EssentialsConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (config.hungerDisabled && event.getEntity() instanceof Player p && event.getFoodLevel() < p.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (config.hungerDisabled) {
            event.getPlayer().setFoodLevel(20);
            event.getPlayer().setSaturation(20f);
        }
    }
}
