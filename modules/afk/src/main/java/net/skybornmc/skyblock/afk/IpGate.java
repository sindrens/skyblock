package net.skybornmc.skyblock.afk;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owner RW3 anti-abuse (pure, tested): only one account per IP address earns at a time, and an IP address gets at most
 * {@code ip-daily-cap-minutes} per day over all its accounts (so alts can't take turns). The account that holds an
 * IP keeps it while it stays a candidate; when it leaves, the candidate who entered the pool first takes over.
 * Main thread only.
 */
final class IpGate {

    /** A player who could earn this check: in the pool, allowed, loaded, below their own cap. */
    record Candidate(UUID player, String ip, long enteredAt) {
    }

    private final Map<String, UUID> holders = new HashMap<>();
    /** Credited time per IP today: ip → {day, millis}. In memory only (a restart resets it). */
    private final Map<String, long[]> ipToday = new HashMap<>();

    /** Picks the holder of every IP and returns the candidates that are paused because another account holds their IP. */
    Set<UUID> update(List<Candidate> candidates) {
        Map<String, List<Candidate>> byIp = new HashMap<>();
        for (Candidate c : candidates) {
            byIp.computeIfAbsent(c.ip(), k -> new java.util.ArrayList<>()).add(c);
        }
        holders.keySet().retainAll(byIp.keySet());
        Set<UUID> blocked = new HashSet<>();
        for (Map.Entry<String, List<Candidate>> e : byIp.entrySet()) {
            List<Candidate> group = e.getValue();
            UUID current = holders.get(e.getKey());
            UUID holder = current;
            if (current == null || group.stream().noneMatch(c -> c.player().equals(current))) {
                holder = group.stream().min(Comparator.comparingLong(Candidate::enteredAt).thenComparing(Candidate::player))
                        .orElseThrow().player();
                holders.put(e.getKey(), holder);
            }
            for (Candidate c : group) {
                if (!c.player().equals(holder)) {
                    blocked.add(c.player());
                }
            }
        }
        return blocked;
    }

    /** The account earning for this IP, or null. */
    UUID holder(String ip) {
        return holders.get(ip);
    }

    /** Time this IP may still be credited today ({@code Long.MAX_VALUE} when the IP cap is off). */
    long remaining(String ip, int day, long capMillis) {
        if (capMillis <= 0) {
            return Long.MAX_VALUE;
        }
        long[] v = ipToday.get(ip);
        long used = v != null && v[0] == day ? v[1] : 0;
        return Math.max(0, capMillis - used);
    }

    void add(String ip, int day, long millis) {
        long[] v = ipToday.computeIfAbsent(ip, k -> new long[]{day, 0});
        if (v[0] != day) {
            v[0] = day;
            v[1] = 0;
        }
        v[1] += millis;
    }

    /** Drops yesterday's IP totals (called now and then so the map never grows). */
    void prune(int today) {
        ipToday.values().removeIf(v -> v[0] != today);
    }

    void clear() {
        holders.clear();
    }
}
