package net.skybornmc.skyblock.collection;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The cached log of one profile (main thread only; pure, unit tested). Finds that arrive while the stored log is still
 * loading wait in {@link #early} and are checked against the stored entries when it arrives, so a find is new exactly
 * once.
 */
final class ProfileLog {

    /** One stored find: when and from what. */
    record Found(long at, String source) {
    }

    /** A find that is new (to be saved and shown). */
    record NewFind(String entry, long at, String source) {
    }

    private final UUID profile;
    private boolean loaded;
    private final Map<String, Found> found = new HashMap<>();
    private final Set<String> milestones = new HashSet<>();
    private final Map<String, Found> early = new LinkedHashMap<>();
    /** Batch 9: how many of each entry (stored plus found since); an entry without a value counts once. */
    private final Map<String, Long> amounts = new HashMap<>();
    private final Map<String, Long> earlyAmounts = new HashMap<>();
    /** Milestones being written right now (not reached twice while their transaction runs). */
    private final Set<String> writing = new HashSet<>();

    ProfileLog(UUID profile) {
        this.profile = profile;
    }

    UUID profile() {
        return profile;
    }

    boolean loaded() {
        return loaded;
    }

    /** Found entry ids (read-only view). */
    Set<String> foundIds() {
        return Collections.unmodifiableSet(found.keySet());
    }

    Found found(String entry) {
        return found.get(entry);
    }

    /** Reached milestones, the ones being written included (read-only view of a copy). */
    Set<String> milestones() {
        Set<String> all = new HashSet<>(milestones);
        all.addAll(writing);
        return all;
    }

    /** True while a find of this entry waits for the stored log (it is in the batch queue too, but may still be new). */
    boolean waiting(String entry) {
        return early.containsKey(entry);
    }

    /** How many of the entry the profile found (0 = not found). */
    long amount(String entry) {
        Long n = amounts.get(entry);
        return n != null ? n : found.containsKey(entry) ? 1 : 0;
    }

    boolean hasMilestone(String id) {
        return milestones.contains(id);
    }

    /**
     * A find from a source. Returns the new find, or null when the entry was found before. While loading, the find waits
     * (null now; {@link #loaded} returns it if it is new).
     */
    NewFind add(String entry, long at, String source) {
        return add(entry, at, source, 1);
    }

    /** {@link #add(String, long, String)} for {@code amount} at once (a catch of 3 fish, an area harvest). */
    NewFind add(String entry, long at, String source, long amount) {
        long n = Math.max(1, amount);
        if (!loaded) {
            early.putIfAbsent(entry, new Found(at, source));
            earlyAmounts.merge(entry, n, Long::sum);
            return null;
        }
        if (found.containsKey(entry)) {
            amounts.put(entry, amount(entry) + n);
            return null;
        }
        found.put(entry, new Found(at, source));
        amounts.put(entry, n);
        return new NewFind(entry, at, source);
    }

    /** The stored log arrived: merges it and returns the waiting finds that are really new. */
    List<NewFind> loaded(Map<String, Found> stored, Set<String> storedMilestones) {
        return loaded(stored, Map.of(), storedMilestones);
    }

    /** {@link #loaded(Map, Set)} with the stored amounts. */
    List<NewFind> loaded(Map<String, Found> stored, Map<String, Long> storedAmounts, Set<String> storedMilestones) {
        found.putAll(stored);
        amounts.putAll(storedAmounts);
        milestones.addAll(storedMilestones);
        loaded = true;
        List<NewFind> out = new ArrayList<>();
        early.forEach((entry, f) -> {
            long n = earlyAmounts.getOrDefault(entry, 1L);
            if (!found.containsKey(entry)) {
                found.put(entry, f);
                amounts.put(entry, n);
                out.add(new NewFind(entry, f.at(), f.source()));
            } else {
                amounts.put(entry, amount(entry) + n);
            }
        });
        early.clear();
        earlyAmounts.clear();
        return out;
    }

    /** Marks milestones as being written; returns those not reached or written already. */
    List<String> startWriting(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (!milestones.contains(id) && writing.add(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** The milestone transaction ended: committed ones are reached, the rest can be tried again. */
    void doneWriting(List<String> ids, boolean committed) {
        for (String id : ids) {
            writing.remove(id);
            if (committed) {
                milestones.add(id);
            }
        }
    }
}
