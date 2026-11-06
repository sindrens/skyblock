package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
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
        if (config.hungerDisabled && event.getEntity() instanceof Player p && event.getFoodLevel() < p.getFoodLevel()
                && !hungerWorld(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        fill(event.getPlayer());
    }

    /** A profile switch restores that profile's saved food level, which may be from before hunger was turned off. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfile(ProfileSwitchedEvent event) {
        fill(event.getPlayer());
    }

    /** PvP worlds keep hunger ({@code hunger.pvp-worlds}). */
    private boolean hungerWorld(Player player) {
        String world = player.getWorld().getName();
        return config.hungerWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    /** Leaving a PvP world: the food bar is full again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        fill(event.getPlayer());
    }

    private void fill(Player player) {
        if (config.hungerDisabled && !hungerWorld(player)) {
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }
}
