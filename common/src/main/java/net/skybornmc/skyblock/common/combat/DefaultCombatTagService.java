package net.skybornmc.skyblock.common.combat;

import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * The core's {@link CombatTagService}: listens to damage between entities (MONITOR, only damage that really happened)
 * and tags who was involved. PvP tags both players; PvE tags the player who hit or was hit by a hostile mob
 * ({@link Enemy}: monsters, slimes, ghasts, phantoms …) when {@code combat.mobs} is on. Arrows and other projectiles
 * count for their shooter. A player hurting themselves (own arrow) is not combat. Settings: {@code core.yml → combat.*}.
 */
public final class DefaultCombatTagService implements CombatTagService, Listener {

    /** Who a damage event tags. */
    public enum Tag { NONE, VICTIM, ATTACKER, BOTH }

    private final CombatTags tags = new CombatTags();
    private final IntSupplier seconds;
    private final BooleanSupplier mobs;
    private final LongSupplier clock;

    public DefaultCombatTagService(IntSupplier seconds, BooleanSupplier mobs, LongSupplier clock) {
        this.seconds = seconds;
        this.mobs = mobs;
        this.clock = clock;
    }

    /**
     * Pure rule (unit-tested): what a hit between an attacker and a victim tags.
     *
     * @param attackerPlayer  the attacker (or the projectile's shooter) is a player
     * @param attackerHostile the attacker (or shooter) is a hostile mob
     * @param victimPlayer    the victim is a player
     * @param victimHostile   the victim is a hostile mob
     * @param samePlayer      attacker and victim are the same player
     * @param mobsCount       PvE counts ({@code combat.mobs})
     */
    public static Tag classify(boolean attackerPlayer, boolean attackerHostile, boolean victimPlayer, boolean victimHostile,
                               boolean samePlayer, boolean mobsCount) {
        if (samePlayer) {
            return Tag.NONE;
        }
        if (attackerPlayer && victimPlayer) {
            return Tag.BOTH;
        }
        if (!mobsCount) {
            return Tag.NONE;
        }
        if (victimPlayer && attackerHostile) {
            return Tag.VICTIM;
        }
        if (attackerPlayer && victimHostile) {
            return Tag.ATTACKER;
        }
        return Tag.NONE;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity attacker = source(event.getDamager());
        if (attacker == null) {
            return;
        }
        Tag tag = classify(attacker instanceof Player, attacker instanceof Enemy, victim instanceof Player, victim instanceof Enemy,
                attacker.getUniqueId().equals(victim.getUniqueId()), mobs.getAsBoolean());
        long now = clock.getAsLong();
        if (tag == Tag.VICTIM || tag == Tag.BOTH) {
            tags.tag(victim.getUniqueId(), now);
        }
        if (tag == Tag.ATTACKER || tag == Tag.BOTH) {
            tags.tag(attacker.getUniqueId(), now);
        }
    }

    /** The entity behind a hit: the shooter of a projectile, else the damager itself. */
    private static Entity source(Entity damager) {
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Entity shooter ? shooter : null;
        }
        return damager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // A combat logger keeps nothing; the tag only guards actions of online players.
        tags.forget(event.getPlayer().getUniqueId());
    }

    @Override
    public int tagSeconds() {
        return Math.max(0, seconds.getAsInt());
    }

    @Override
    public long lastCombat(UUID player) {
        return tags.last(player);
    }

    @Override
    public long remainingMillis(UUID player, int tagSeconds) {
        return tags.remaining(player, clock.getAsLong(), tagSeconds);
    }

    @Override
    public void tag(UUID player) {
        tags.tag(player, clock.getAsLong());
    }

    /** Housekeeping (called now and then): tags older than an hour are dropped. */
    public void prune() {
        tags.prune(clock.getAsLong(), 3600);
    }
}
