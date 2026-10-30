package net.skybornmc.skyblock.moderation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * "Mute ended" timers (spec 13.13): one scheduled task per online player with an active timed mute, at its expiry, and
 * rescheduled whenever the mute changes. No database polling. The scheduler is injected so the logic is unit-tested; on
 * the server it is a Bukkit task. A task that fires early (ticks run behind the wall clock when the server lags) checks
 * the clock and reschedules itself for the rest. Thread-safe (all methods synchronized).
 */
final class EndTimers {

    /** Runs {@code action} after {@code delayMillis}; returns a handle to cancel it. */
    @FunctionalInterface
    interface Scheduler {
        Runnable schedule(long delayMillis, Runnable action);
    }

    private record Entry(long muteId, long expires, Runnable cancel) {
    }

    private final Scheduler scheduler;
    private final LongSupplier clock;
    private final BiConsumer<UUID, Long> ended;
    private final Map<UUID, Entry> entries = new HashMap<>();

    /**
     * @param ended called once per mute that reached its end, with the player and the mute id
     */
    EndTimers(Scheduler scheduler, LongSupplier clock, BiConsumer<UUID, Long> ended) {
        this.scheduler = scheduler;
        this.clock = clock;
        this.ended = ended;
    }

    /**
     * Tracks the player's current mute: {@code muteId}/{@code expires} of the mute that lasts longest, or
     * {@code expires == null} (permanent or no mute) to stop tracking. Re-tracking the same mute keeps the timer.
     */
    synchronized void track(UUID player, long muteId, Long expires) {
        Entry old = entries.get(player);
        if (expires == null) {
            cancel(player);
            return;
        }
        if (old != null && old.muteId() == muteId && old.expires() == expires) {
            return;
        }
        cancel(player);
        schedule(player, muteId, expires);
    }

    private void schedule(UUID player, long muteId, long expires) {
        long delay = Math.max(0, expires - clock.getAsLong());
        Runnable[] handle = new Runnable[1];
        Entry entry = new Entry(muteId, expires, () -> {
            if (handle[0] != null) {
                handle[0].run();
            }
        });
        entries.put(player, entry);
        handle[0] = scheduler.schedule(delay, () -> fire(player, entry));
    }

    private void fire(UUID player, Entry entry) {
        boolean due;
        synchronized (this) {
            if (entries.get(player) != entry) {
                return; // replaced or cancelled
            }
            if (entry.expires() > clock.getAsLong()) {
                schedule(player, entry.muteId(), entry.expires()); // fired early (lag): wait for the rest
                return;
            }
            entries.remove(player);
            due = true;
        }
        if (due) {
            ended.accept(player, entry.muteId());
        }
    }

    /** Stops tracking (player quit, unmuted, permanent mute). */
    synchronized void cancel(UUID player) {
        Entry old = entries.remove(player);
        if (old != null) {
            old.cancel().run();
        }
    }

    /** Stops everything (module disabled). */
    synchronized void clear() {
        entries.values().forEach(e -> e.cancel().run());
        entries.clear();
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized boolean tracking(UUID player) {
        return entries.containsKey(player);
    }
}
