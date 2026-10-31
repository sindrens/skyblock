package net.skybornmc.skyblock.common.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.random.RandomGenerator;

/**
 * Per-mob cooldowns of the cooldown abilities (M8.1). Pure and allocation-free after construction: one shared
 * scheduler asks every mob's clock once per run; {@link #nextReady()} lets it skip a mob with a single comparison, and
 * the proximity check (a player within range) is only asked for when something is ready.
 *
 * <p>Rules: one ability at a time per mob (a started ability keeps the mob busy until its telegraph and action are
 * over); abilities are tried round-robin so one ability never starves the others; once off cooldown an ability is
 * rolled with its chance, a failed roll waits {@code retryTicks} before the next try; nothing happens (and no cooldown is
 * used) while no player is near or the shared effect cap is full. Health-triggered abilities ({@link AbilityId#ENRAGE})
 * are not on the clock.</p>
 */
public final class AbilityClock {

    private final AbilitySpec[] abilities;
    private final long[] ready;
    private long busyUntil = Long.MIN_VALUE;
    private int next;

    /**
     * @param resolved        the mob's abilities with every value resolved; health-triggered ones are left out
     * @param now             current tick
     * @param firstDelayTicks no ability starts earlier than this after the spawn (or its cooldown, if shorter)
     */
    public AbilityClock(List<AbilitySpec> resolved, long now, int firstDelayTicks) {
        List<AbilitySpec> list = new ArrayList<>();
        for (AbilitySpec a : resolved) {
            if (!a.id().healthTriggered()) {
                list.add(a);
            }
        }
        this.abilities = list.toArray(AbilitySpec[]::new);
        this.ready = new long[abilities.length];
        for (int i = 0; i < abilities.length; i++) {
            ready[i] = now + Math.max(0, Math.min(firstDelayTicks, abilities[i].cooldownTicks()));
        }
    }

    /** Number of cooldown abilities. */
    public int size() {
        return abilities.length;
    }

    public AbilitySpec ability(int index) {
        return abilities[index];
    }

    /** Earliest tick at which {@link #poll} can start something ({@link Long#MAX_VALUE} = never). */
    public long nextReady() {
        if (abilities.length == 0) {
            return Long.MAX_VALUE;
        }
        long min = Long.MAX_VALUE;
        for (long r : ready) {
            min = Math.min(min, r);
        }
        return Math.max(min, busyUntil);
    }

    /** True while a started ability is still running (telegraph or action). */
    public boolean busy(long now) {
        return now < busyUntil;
    }

    /** Marks the mob busy until {@code untilTick} (its started ability's telegraph and action). */
    public void busyUntil(long untilTick) {
        busyUntil = Math.max(busyUntil, untilTick);
    }

    /**
     * Picks the ability to start now, or {@code -1}.
     *
     * @param playerNear asked only when an ability is ready: true if a player is within range
     * @param capacity   false while the shared cap of running effects is full (nothing is used up)
     * @param retryTicks wait after a failed chance roll
     */
    public int poll(long now, BooleanSupplier playerNear, boolean capacity, int retryTicks, RandomGenerator rng) {
        if (abilities.length == 0 || now < busyUntil || !capacity || now < nextReady() || !playerNear.getAsBoolean()) {
            return -1;
        }
        int n = abilities.length;
        for (int k = 0; k < n; k++) {
            int i = (next + k) % n;
            if (now < ready[i]) {
                continue;
            }
            if (rng.nextDouble() < abilities[i].rollChance()) {
                ready[i] = now + abilities[i].cooldownTicks();
                next = (i + 1) % n;
                return i;
            }
            ready[i] = now + Math.max(1, retryTicks);
        }
        return -1;
    }
}
