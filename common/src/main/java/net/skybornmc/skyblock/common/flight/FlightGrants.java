package net.skybornmc.skyblock.common.flight;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is giving a player survival flight right now (FIXES1). Several features can let a survival player fly at the same
 * time: {@code /fly} (staff powers), island fly (Wings / visitor fly), staff mode, freeze (hovering), later donor-rank
 * fly. Before this registry each feature read {@code Player#getAllowFlight()} to decide whether flight was "on", so one
 * feature could switch off flight another one had given ({@code /fly} on an island printed "Flying off." and island fly
 * gave it straight back).
 *
 * <p>The rule: a feature records its own grant here ({@link #grant}) and, when it ends, calls {@link #revoke}; it only
 * clears {@code allowFlight} when {@code revoke} returns {@code true} (nobody else still gives flight). Creative and
 * spectator own their flight and are never touched by any source. Thread-safe; ids are lowercase words such as
 * {@code fly}, {@code island}, {@code staff-mode}, {@code freeze}.</p>
 */
public final class FlightGrants {

    /** {@code /fly} (essentials, staff powers). */
    public static final String FLY = "fly";
    /** Island fly (islands: Wings mastery, fly permission, visitor fly). */
    public static final String ISLAND = "island";
    /** Staff mode (staff module). */
    public static final String STAFF_MODE = "staff-mode";
    /** Frozen players hover (staff module). */
    public static final String FREEZE = "freeze";

    private static final Map<UUID, Set<String>> HOLDERS = new ConcurrentHashMap<>();

    private FlightGrants() {
    }

    /** {@code source} gives {@code player} flight now. */
    public static void grant(UUID player, String source) {
        HOLDERS.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(source);
    }

    /**
     * {@code source} stops giving {@code player} flight. Returns {@code true} when no other source still gives flight, so
     * the caller may switch {@code allowFlight} off (survival/adventure only).
     */
    public static boolean revoke(UUID player, String source) {
        Set<String> left = HOLDERS.computeIfPresent(player, (k, set) -> {
            set.remove(source);
            return set.isEmpty() ? null : set;
        });
        return left == null || left.isEmpty();
    }

    /** Does {@code source} give {@code player} flight? */
    public static boolean holds(UUID player, String source) {
        Set<String> set = HOLDERS.get(player);
        return set != null && set.contains(source);
    }

    /** Does any source other than {@code except} give {@code player} flight? */
    public static boolean othersHold(UUID player, String except) {
        Set<String> set = HOLDERS.get(player);
        if (set == null) {
            return false;
        }
        for (String s : set) {
            if (!s.equals(except)) {
                return true;
            }
        }
        return false;
    }

    /** The first source other than {@code except} that gives flight, or {@code null} (for the "you can still fly" text). */
    public static String otherHolder(UUID player, String except) {
        Set<String> set = HOLDERS.get(player);
        if (set == null) {
            return null;
        }
        for (String s : set) {
            if (!s.equals(except)) {
                return s;
            }
        }
        return null;
    }

    /** Does anything give {@code player} flight? */
    public static boolean any(UUID player) {
        Set<String> set = HOLDERS.get(player);
        return set != null && !set.isEmpty();
    }

    /** Copy of the current sources (logs, tests). */
    public static Set<String> sources(UUID player) {
        Set<String> set = HOLDERS.get(player);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    /** Forgets everything about {@code player} (quit). */
    public static void forget(UUID player) {
        HOLDERS.remove(player);
    }
}
