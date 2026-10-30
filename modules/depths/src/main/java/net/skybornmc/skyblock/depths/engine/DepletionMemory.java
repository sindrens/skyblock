package net.skybornmc.skyblock.depths.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SECFIX1 F-11: depleted blocks outlive a quit and a world change. Before, the engine dropped a player's depleted blocks
 * when they left or changed world, so relogging reset every respawn timer and the same ores could be mined again at once.
 * Now the still-running timers are parked here (per player, per world, with their end time) and handed back when the
 * player is in that world again; expired ones are simply dropped. Memory only: a server restart resets everyone the same
 * way, which gives no one an advantage. Not thread-safe (main thread). Bukkit-free ({@code T} = what the block shows),
 * unit-tested.
 */
final class DepletionMemory<T> {

    /** One parked depleted block: packed position, what it shows, its region and when its timer ends (epoch millis). */
    record Entry<T>(long key, T shown, String regionId, long endsAt) {
    }

    private final Map<UUID, Map<String, List<Entry<T>>>> parked = new HashMap<>();
    private final int maxPerPlayer;

    /** @param maxPerPlayer most entries kept per player (newest timers win), a guard against unbounded memory */
    DepletionMemory(int maxPerPlayer) {
        this.maxPerPlayer = Math.max(1, maxPerPlayer);
    }

    /** Parks the still-running timers of {@code player} in {@code world} (replaces what was parked there). */
    void park(UUID player, String world, List<Entry<T>> entries, long now) {
        if (player == null || world == null) {
            return;
        }
        prune(now);
        List<Entry<T>> alive = new ArrayList<>(entries.size());
        for (Entry<T> e : entries) {
            if (e.endsAt() > now) {
                alive.add(e);
            }
        }
        Map<String, List<Entry<T>>> worlds = parked.computeIfAbsent(player, k -> new HashMap<>());
        if (alive.isEmpty()) {
            worlds.remove(world);
        } else {
            worlds.put(world, alive);
        }
        trim(worlds);
        if (worlds.isEmpty()) {
            parked.remove(player);
        }
    }

    /** Takes (and forgets) the parked timers of {@code player} in {@code world} that have not ended yet. */
    List<Entry<T>> take(UUID player, String world, long now) {
        Map<String, List<Entry<T>>> worlds = parked.get(player);
        if (worlds == null) {
            return List.of();
        }
        List<Entry<T>> list = worlds.remove(world);
        if (worlds.isEmpty()) {
            parked.remove(player);
        }
        if (list == null) {
            return List.of();
        }
        List<Entry<T>> alive = new ArrayList<>(list.size());
        for (Entry<T> e : list) {
            if (e.endsAt() > now) {
                alive.add(e);
            }
        }
        return alive;
    }

    /** Drops every ended timer (cheap: parked lists only hold timers of players who are away). */
    void prune(long now) {
        Iterator<Map<String, List<Entry<T>>>> players = parked.values().iterator();
        while (players.hasNext()) {
            Map<String, List<Entry<T>>> worlds = players.next();
            worlds.values().removeIf(list -> {
                list.removeIf(e -> e.endsAt() <= now);
                return list.isEmpty();
            });
            if (worlds.isEmpty()) {
                players.remove();
            }
        }
    }

    /** Forgets everything (module stop, region definitions changed). */
    void clear() {
        parked.clear();
    }

    /** Parked entries of all players (debug and tests). */
    int size() {
        int n = 0;
        for (Map<String, List<Entry<T>>> worlds : parked.values()) {
            for (List<Entry<T>> list : worlds.values()) {
                n += list.size();
            }
        }
        return n;
    }

    /** Ticks left until {@code endsAt} (at least 1), for re-scheduling a parked timer. Pure. */
    static int ticksLeft(long endsAt, long now) {
        long ms = Math.max(0, endsAt - now);
        long ticks = (ms + 49) / 50;
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, ticks));
    }

    private void trim(Map<String, List<Entry<T>>> worlds) {
        int total = 0;
        for (List<Entry<T>> list : worlds.values()) {
            total += list.size();
        }
        while (total > maxPerPlayer) {
            // Drop the timer that ends first (the least valuable to keep).
            List<Entry<T>> holder = null;
            int index = -1;
            long first = Long.MAX_VALUE;
            for (List<Entry<T>> list : worlds.values()) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).endsAt() < first) {
                        first = list.get(i).endsAt();
                        holder = list;
                        index = i;
                    }
                }
            }
            if (holder == null) {
                return;
            }
            holder.remove(index);
            total--;
            worlds.values().removeIf(List::isEmpty);
        }
    }
}
