package net.skybornmc.skyblock.depths.combat;

/**
 * The mini-boss placeholder timer of one combat zone (M4.8; real bosses in Phase 8). A mini-boss is due every
 * {@code interval} after the previous one died or was removed; it only appears while players are in the zone, and
 * only one is alive at a time. Pure, time passed in.
 */
public final class MiniBossClock {

    private long nextAt;
    private boolean alive;

    /** @param now when the zone was set up (the first mini-boss comes one interval later) */
    public MiniBossClock(long now, long intervalMs) {
        this.nextAt = now + Math.max(0, intervalMs);
    }

    /** True if a mini-boss should spawn now. */
    public boolean due(long now, boolean playersPresent) {
        return !alive && playersPresent && now >= nextAt;
    }

    public void spawned() {
        alive = true;
    }

    /** The mini-boss died or was removed: the next one is due one interval from now. */
    public void gone(long now, long intervalMs) {
        alive = false;
        nextAt = now + Math.max(0, intervalMs);
    }

    public boolean alive() {
        return alive;
    }

    /** Milliseconds until the next one is due (0 when due or alive). */
    public long remaining(long now) {
        return alive ? 0 : Math.max(0, nextAt - now);
    }
}
