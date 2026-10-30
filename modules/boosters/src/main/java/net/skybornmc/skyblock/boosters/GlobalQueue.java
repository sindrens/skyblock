package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.booster.BoosterType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global boosters in memory (pure, unit-tested): at most one <b>active</b> per type, the others <b>queued</b> in the
 * order they were started. Main thread only (the bonus source reads {@link #percent} from the main thread too).
 */
final class GlobalQueue {

    /** One global booster. {@code remaining} counts down while it is active. */
    static final class Entry {
        final long id;
        final BoosterType type;
        final int percent;
        final long duration;
        final UUID starter;
        final UUID starterProfile;
        final String starterName;
        final Set<UUID> thanked = ConcurrentHashMap.newKeySet();
        long remaining;
        boolean active;
        long endedAt;

        Entry(long id, BoosterType type, int percent, long duration, long remaining, UUID starter, UUID starterProfile,
              String starterName, boolean active) {
            this.id = id;
            this.type = type;
            this.percent = percent;
            this.duration = duration;
            this.remaining = remaining;
            this.starter = starter;
            this.starterProfile = starterProfile;
            this.starterName = starterName;
            this.active = active;
        }
    }

    /** What a tick changed: boosters that ended and queued ones that started in their place. */
    record Tick(List<Entry> ended, List<Entry> started) {

        boolean changed() {
            return !ended.isEmpty() || !started.isEmpty();
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /** Adds a booster loaded or just started (kept in id order). An active one replaces nothing: callers keep one per type. */
    void add(Entry e) {
        entries.add(e);
        entries.sort(Comparator.comparingLong(x -> x.id));
    }

    void clear() {
        entries.clear();
    }

    List<Entry> all() {
        return List.copyOf(entries);
    }

    /** The active booster of a type. */
    Optional<Entry> active(BoosterType type) {
        for (Entry e : entries) {
            if (e.active && e.type == type) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /** All active boosters, in id order. */
    List<Entry> active() {
        return entries.stream().filter(e -> e.active).toList();
    }

    /** Queued boosters of a type, next first. */
    List<Entry> queued(BoosterType type) {
        return entries.stream().filter(e -> !e.active && e.type == type).toList();
    }

    Optional<Entry> byId(long id) {
        return entries.stream().filter(e -> e.id == id).findFirst();
    }

    /** Percent the active global booster of the type adds (0 for none). */
    int percent(BoosterType type) {
        return active(type).filter(e -> e.remaining > 0).map(e -> e.percent).orElse(0);
    }

    /**
     * Makes sure every type with queued boosters has an active one (after loading: a crash between ending and starting
     * the next). Returns the boosters that were promoted.
     */
    List<Entry> promote() {
        List<Entry> started = new ArrayList<>();
        for (BoosterType type : BoosterType.values()) {
            if (active(type).isEmpty()) {
                List<Entry> q = queued(type);
                if (!q.isEmpty()) {
                    q.getFirst().active = true;
                    started.add(q.getFirst());
                }
            }
        }
        return started;
    }

    /** Counts {@code elapsed} ms down on every active booster; finished ones leave and the next of their type starts. */
    Tick tick(long elapsed, long now) {
        List<Entry> ended = new ArrayList<>();
        for (Entry e : List.copyOf(entries)) {
            if (!e.active) {
                continue;
            }
            e.remaining -= Math.max(0, elapsed);
            if (e.remaining <= 0) {
                e.remaining = 0;
                e.active = false;
                e.endedAt = now;
                entries.remove(e);
                ended.add(e);
            }
        }
        List<Entry> started = ended.isEmpty() ? List.of() : promote();
        return new Tick(ended, started);
    }
}
