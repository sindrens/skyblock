package net.skybornmc.skyblock.common.stacking;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired by the stacking module after a player killed mobs of a stack in one batch (ENCHANTS §7.2), once per batch:
 * <ul>
 *   <li>{@link Cause#HIT}: a lethal hit on a stacked (or spawner) mob, fired inside its {@code EntityDeathEvent}, before
 *       the rest of the stack lives on. The dying mob itself also gets its normal death event, so
 *       {@link #extraKills()} is {@code killed - 1}.</li>
 *   <li>{@link Cause#AREA}: {@link StackingService#killFromStack} (Reaper's Sweep, Phoenix Wrath): no death event at all,
 *       so {@link #extraKills()} equals {@link #killed()}.</li>
 * </ul>
 * Drops and vanilla XP for all killed mobs are already handled by the stacking module. Listeners that pay per kill
 * (skill XP, tool XP, book drops) pay {@link #extraKills()} more here, in one call, with the same rules as for a normal
 * kill of {@link #entity()}. Not cancellable. Main thread.
 */
public final class StackKillEvent extends Event {

    /** Skill XP source used for stacked kills paid through this event. */
    public static final String SKILL_XP_SOURCE = "stack_kill";

    /** What killed the mobs. */
    public enum Cause {
        /** A lethal player hit on the stack (the entity dies normally; extra kills come from the weapon). */
        HIT,
        /** An area effect through {@link StackingService#killFromStack}: no death event. */
        AREA
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player killer;
    private final ItemStack weapon;
    private final LivingEntity entity;
    private final EntityType type;
    private final Location location;
    private final int killed;
    private final int extraKills;
    private final int requested;
    private final boolean spawnerMob;
    private final Cause cause;

    public StackKillEvent(Player killer, ItemStack weapon, LivingEntity entity, Location location, int killed, int extraKills,
                          int requested, boolean spawnerMob, Cause cause) {
        this.killer = killer;
        this.weapon = weapon;
        this.entity = entity;
        this.type = entity.getType();
        this.location = location.clone();
        this.killed = Math.max(0, killed);
        this.extraKills = Math.max(0, Math.min(this.killed, extraKills));
        this.requested = Math.max(1, requested);
        this.spawnerMob = spawnerMob;
        this.cause = cause;
    }

    public Player killer() {
        return killer;
    }

    /** The killer's main-hand item at the time of the kill (may be air). */
    public ItemStack weapon() {
        return weapon;
    }

    /** The stack (dying for {@link Cause#HIT}; for {@link Cause#AREA} it may already be removed when the whole stack died). */
    public LivingEntity entity() {
        return entity;
    }

    public EntityType type() {
        return type;
    }

    public Location location() {
        return location.clone();
    }

    /** Mobs killed in this batch (at least 1). */
    public int killed() {
        return killed;
    }

    /** Killed mobs that got no death event of their own (pay these on top of the normal per-kill rewards). */
    public int extraKills() {
        return extraKills;
    }

    /** What the weapon (or effect) asked for before the stack size clamped it (for "25% of the Slaughter count"). */
    public int requested() {
        return requested;
    }

    /** True if the stack came from a stacked spawner (no AI, vanilla loot only). */
    public boolean spawnerMob() {
        return spawnerMob;
    }

    public Cause cause() {
        return cause;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
