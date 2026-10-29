package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pending island invites in memory (main thread, pure: no Bukkit). Mirrors {@code island_invites}; the database stays
 * the authority for {@code /is accept} (checked again in one transaction), this cache serves tab completion, the
 * "you have N invites" view and the expiry sweep. One invite per island and target; a new invite replaces the old one.
 */
public final class Invites {

    /** One pending invite. */
    public record Invite(UUID islandId, UUID target, UUID inviter, long createdAt, long expiresAt) {
        public boolean expired(long now) {
            return now >= expiresAt;
        }
    }

    private final Map<UUID, List<Invite>> byTarget = new HashMap<>();

    /** Adds or replaces the invite of {@code invite.islandId()} for {@code invite.target()}. */
    public void put(Invite invite) {
        List<Invite> list = byTarget.computeIfAbsent(invite.target(), k -> new ArrayList<>());
        list.removeIf(i -> i.islandId().equals(invite.islandId()));
        list.add(invite);
    }

    /** Removes one invite; true if it existed. */
    public boolean remove(UUID islandId, UUID target) {
        List<Invite> list = byTarget.get(target);
        if (list == null) {
            return false;
        }
        boolean removed = list.removeIf(i -> i.islandId().equals(islandId));
        if (list.isEmpty()) {
            byTarget.remove(target);
        }
        return removed;
    }

    /** Removes every invite of {@code target} (they joined an island). */
    public List<Invite> removeTarget(UUID target) {
        List<Invite> removed = byTarget.remove(target);
        return removed == null ? List.of() : removed;
    }

    /** Removes every invite sent by {@code islandId} (island gone, M2.7). */
    public List<Invite> removeIsland(UUID islandId) {
        List<Invite> removed = new ArrayList<>();
        Iterator<Map.Entry<UUID, List<Invite>>> it = byTarget.entrySet().iterator();
        while (it.hasNext()) {
            List<Invite> list = it.next().getValue();
            list.removeIf(i -> {
                if (i.islandId().equals(islandId)) {
                    removed.add(i);
                    return true;
                }
                return false;
            });
            if (list.isEmpty()) {
                it.remove();
            }
        }
        return removed;
    }

    /** Valid invites of {@code target}, newest first. */
    public List<Invite> of(UUID target, long now) {
        List<Invite> list = byTarget.get(target);
        if (list == null) {
            return List.of();
        }
        return list.stream().filter(i -> !i.expired(now)).sorted(Comparator.comparingLong(Invite::createdAt).reversed()).toList();
    }

    /** The valid invite of {@code islandId} for {@code target}. */
    public Optional<Invite> get(UUID islandId, UUID target, long now) {
        return of(target, now).stream().filter(i -> i.islandId().equals(islandId)).findFirst();
    }

    /** Number of valid invites sent by {@code islandId}. */
    public int pendingFrom(UUID islandId, long now) {
        int count = 0;
        for (List<Invite> list : byTarget.values()) {
            for (Invite invite : list) {
                if (invite.islandId().equals(islandId) && !invite.expired(now)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Valid invites sent by {@code islandId}. */
    public List<Invite> from(UUID islandId, long now) {
        List<Invite> out = new ArrayList<>();
        for (List<Invite> list : byTarget.values()) {
            for (Invite invite : list) {
                if (invite.islandId().equals(islandId) && !invite.expired(now)) {
                    out.add(invite);
                }
            }
        }
        return out;
    }

    /** Removes and returns every invite that expired at {@code now}. */
    public List<Invite> expire(long now) {
        List<Invite> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, List<Invite>>> it = byTarget.entrySet().iterator();
        while (it.hasNext()) {
            List<Invite> list = it.next().getValue();
            list.removeIf(i -> {
                if (i.expired(now)) {
                    expired.add(i);
                    return true;
                }
                return false;
            });
            if (list.isEmpty()) {
                it.remove();
            }
        }
        return expired;
    }

    public void clear() {
        byTarget.clear();
    }

    public int size() {
        int n = 0;
        for (List<Invite> list : byTarget.values()) {
            n += list.size();
        }
        return n;
    }
}
