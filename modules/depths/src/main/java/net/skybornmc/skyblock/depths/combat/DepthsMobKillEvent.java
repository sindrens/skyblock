package net.skybornmc.skyblock.depths.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A player is about to get the rewards of a Depths combat mob (M4.8): once per rewarded player (the killer, and for a
 * mini-boss everyone with enough damage), on the main thread, only fired when a listener is registered. Hook for custom
 * enchants (E2: Depths sword effects, book drops): change the items, crystals or XP here. Items still need room in the
 * satchel (a full satchel shows its warning and keeps nothing).
 */
public final class DepthsMobKillEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity entity;
    private final String mob;
    private final int tier;
    private final boolean miniBoss;
    private final Map<String, Integer> items;
    private long crystals;
    private double xp;

    public DepthsMobKillEvent(Player player, LivingEntity entity, String mob, int tier, boolean miniBoss,
                              Map<String, Integer> items, long crystals, double xp) {
        super(player);
        this.entity = entity;
        this.mob = mob;
        this.tier = tier;
        this.miniBoss = miniBoss;
        this.items = new LinkedHashMap<>(items);
        this.crystals = crystals;
        this.xp = xp;
    }

    /** The dead mob (do not modify it). */
    public LivingEntity getEntity() {
        return entity;
    }

    /** Mob id in mobs.yml. */
    public String getMob() {
        return mob;
    }

    /** Combat tier of the zone. */
    public int getTier() {
        return tier;
    }

    public boolean isMiniBoss() {
        return miniBoss;
    }

    /** Satchel items, drop item id → amount (editable; ids must be drop items). */
    public Map<String, Integer> getItems() {
        return items;
    }

    public long getCrystals() {
        return crystals;
    }

    public void setCrystals(long crystals) {
        this.crystals = Math.max(0, crystals);
    }

    /** Combat skill XP. */
    public double getXp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = Double.isNaN(xp) ? 0 : Math.max(0, xp);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
