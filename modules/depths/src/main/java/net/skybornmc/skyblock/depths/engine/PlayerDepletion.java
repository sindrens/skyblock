package net.skybornmc.skyblock.depths.engine;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * Everything the engine keeps for one online player: their depleted blocks (one world at a time), the block they
 * started breaking (anti fast-break) and their flag counters. Created on first contact, dropped on quit.
 */
final class PlayerDepletion {

    /** One block depleted for this player. {@code token} matches its timer in the wheel. */
    record Depleted(int token, BlockData shown, String regionId) {
    }

    static final long NO_BLOCK = Long.MIN_VALUE;

    final Player player;
    /** Session handle used as the timer owner (never reused, so old timers can't hit a new session). */
    final int handle;
    /** World of the depleted blocks ({@code null} = none yet). */
    String world;
    final Long2ObjectOpenHashMap<Depleted> depleted = new Long2ObjectOpenHashMap<>(16);
    private int tokens;

    // Block damage start (anti fast-break).
    long damageKey = NO_BLOCK;
    String damageWorld;
    long damageStartNanos;
    float damageSpeed;
    boolean damageInstant;

    // Flag counters.
    int flagsInWindow;
    long flagWindowStartMillis;
    long lastAlertMillis = Long.MIN_VALUE / 2;
    int flagsTotal;

    Visibility visibility = Visibility.EVERYONE;

    PlayerDepletion(Player player, int handle) {
        this.player = player;
        this.handle = handle;
    }

    int nextToken() {
        return ++tokens;
    }

    boolean isDepleted(String inWorld, long key) {
        return !depleted.isEmpty() && inWorld.equals(world) && depleted.containsKey(key);
    }

    void clearDamage() {
        damageKey = NO_BLOCK;
        damageWorld = null;
    }

    boolean damageMatches(String inWorld, long key) {
        return damageKey == key && inWorld.equals(damageWorld);
    }
}
