package net.skybornmc.skyblock.stacking;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.function.Supplier;

/**
 * Feedback 1 Nov 2026 ("make so spawner mobs doesn't move, they stand still and no damage on island"): spawner mobs are
 * unaware (owner S7, {@link #calm}); this also stops the damage that needs no goals - contact (pufferfish, magma cubes,
 * guardians' thorns), attack effects (poison, wither), an ignited creeper's explosion and arrows. Players still kill them
 * as before ({@code stacking.yml mobs.spawner-mobs-hurt-players}). Mobs loaded from disk are calmed again.
 */
final class SpawnerMobSafety implements Listener {

    private final Supplier<StackingConfig> config;

    SpawnerMobSafety(Supplier<StackingConfig> config) {
        this.config = config;
    }

    /** Pure: true when a hit/effect from a spawner mob on this victim must be stopped. */
    static boolean blocks(boolean sourceIsSpawnerMob, boolean victimIsPlayer, boolean mobsHurtPlayers) {
        return sourceIsSpawnerMob && victimIsPlayer && !mobsHurtPlayers;
    }

    private boolean off() {
        return config.get().spawnerMobsHurtPlayers;
    }

    /** The spawner mob behind a damager (itself, the shooter of its projectile, the source of its TNT), or null. */
    private static Entity source(Entity damager) {
        if (damager instanceof Projectile p) {
            ProjectileSource shooter = p.getShooter();
            return shooter instanceof Entity e ? e : null;
        }
        if (damager instanceof TNTPrimed tnt) {
            return tnt.getSource();
        }
        return damager;
    }

    private static boolean spawnerMob(Entity e) {
        return e != null && MobStacker.isSpawnerMob(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (blocks(spawnerMob(source(event.getDamager())), event.getEntity() instanceof Player, off())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (blocks(spawnerMob(event.getEntity()), event.getTarget() instanceof Player, off())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (event.getCause() == EntityPotionEffectEvent.Cause.ATTACK && event.getEntity() instanceof Player && !off()
                && event.getAction() != EntityPotionEffectEvent.Action.REMOVED) {
            // The attacker is not on the event; a spawner mob's hit was already cancelled above, so this only catches
            // contact effects (pufferfish) right next to one. Checked by proximity to keep it cheap.
            for (Entity near : event.getEntity().getNearbyEntities(2, 2, 2)) {
                if (spawnerMob(near)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (!off() && spawnerMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!off() && spawnerMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!off() && spawnerMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Fish (cod, salmon, pufferfish, tropical fish, tadpole), guardians (and elder guardians) and dolphins flop around on
     * land by themselves (their own movement code, not their goals), so they keep the full no-AI flag and stay still;
     * every other spawner mob only loses awareness and keeps gravity and pushing.
     */
    static boolean flopsOnLand(Mob mob) {
        return mob instanceof org.bukkit.entity.Fish || mob instanceof org.bukkit.entity.Guardian
                || mob instanceof org.bukkit.entity.Dolphin;
    }

    /**
     * Feedback 4 Nov 2026 (D13): a spawner mob is unaware (Paper: no goals, so it doesn't walk, fly, jump or attack by
     * itself) but keeps its AI flag, so it has gravity and players and water can push it. Before this they had no AI at
     * all and floated where they spawned. Returns true when something changed.
     */
    static boolean calm(Mob mob) {
        boolean noAi = flopsOnLand(mob);
        if (!mob.isAware() && mob.hasAI() == !noAi) {
            return false;
        }
        mob.setAware(false);
        mob.setAI(!noAi);
        return true;
    }

    /** Again after a restart or chunk load; older spawner mobs (no AI at all) get their gravity back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof Mob mob && spawnerMob(mob)) {
                calm(mob);
            }
        }
    }
}
