package net.skybornmc.skyblock.wardrobe;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Who earns set XP for an action (WARDROBE §3.1, WD10/WD11). Pure and unit-tested.
 * <ul>
 *   <li>Every action ({@link Action}) belongs to one XP activity (mining, foraging, farming, combat, boss, fishing, sell,
 *       conquest, pvp) and gives a configured amount of set XP per unit.</li>
 *   <li>An entry earns only while at least {@code minPieces} (2) of its pieces are worn: 2-3 pieces earn the partial share
 *       (50%), 4 pieces the full share (100%).</li>
 *   <li>A set earns from the activities its own activity lists in {@code wardrobe.yml → xp.sources.<activity>} at the
 *       listed rate (Lorekeeper 75% of every skill). A merged entry earns from both sets' activities, each at the higher
 *       of the two rates.</li>
 * </ul>
 */
final class SetXp {

    /** XP activities (what earns, {@code xp.sources} values). */
    static final List<String> ACTIVITIES = List.of("mining", "foraging", "farming", "combat", "boss", "fishing", "sell", "conquest", "pvp");

    /** Every action that gives set XP; ids are what other modules pass to {@code WardrobeService#setXp}. */
    enum Action {
        DEPTHS_ORE("depths_ore", "mining"),
        ISLAND_ORE("island_ore", "mining"),
        DEPTHS_LOG("depths_log", "foraging"),
        ISLAND_LOG("island_log", "foraging"),
        CROP("crop", "farming"),
        DEPTHS_KILL("depths_kill", "combat"),
        MINI_BOSS("mini_boss", "boss"),
        WORLD_BOSS("world_boss", "boss"),
        FISH("fish", "fishing"),
        SELL_ITEM("sell_item", "sell"),
        SELL_ISLAND_ITEM("sell_island_item", "sell"),
        KOTH_TICK("koth_tick", "conquest"),
        KOTH_CAPTURE("koth_capture", "conquest"),
        ENVOY("envoy", "conquest"),
        WARZONE_KILL("warzone_kill", "pvp");

        private final String id;
        private final String activity;

        Action(String id, String activity) {
            this.id = id;
            this.activity = activity;
        }

        String id() {
            return id;
        }

        String activity() {
            return activity;
        }

        static Optional<Action> byId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            String key = id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            for (Action a : values()) {
                if (a.id.equals(key)) {
                    return Optional.of(a);
                }
            }
            return Optional.empty();
        }
    }

    /** One worn entry: its set(s) and how many of its pieces are worn. */
    record WornEntry(UUID entry, String setId, String mergedSetId, int pieces) {
    }

    /**
     * @param minPieces pieces needed to earn at all (2)
     * @param partial   share with fewer than 4 pieces (0.5)
     * @param full      share with all 4 pieces (1.0)
     */
    record Shares(int minPieces, double partial, double full) {
        double of(int pieces) {
            if (pieces < Math.max(1, minPieces)) {
                return 0;
            }
            return pieces >= 4 ? Math.max(0, full) : Math.max(0, partial);
        }
    }

    private SetXp() {
    }

    /**
     * Splits {@code xp} of an activity over the worn entries.
     *
     * @param sources activity → rate for a set id (empty for unknown or disabled sets)
     * @return entry id → XP it earns (entries that earn nothing are left out)
     */
    static Map<UUID, Double> split(Collection<WornEntry> worn, String activity, double xp,
                                   Function<String, Map<String, Double>> sources, Shares shares) {
        Map<UUID, Double> out = new LinkedHashMap<>();
        if (!(xp > 0) || !Double.isFinite(xp) || activity == null) {
            return out;
        }
        for (WornEntry w : worn) {
            double pieceShare = shares.of(w.pieces());
            if (pieceShare <= 0) {
                continue;
            }
            double rate = rate(sources.apply(w.setId()), activity);
            if (w.mergedSetId() != null && !w.mergedSetId().equals(w.setId())) {
                rate = Math.max(rate, rate(sources.apply(w.mergedSetId()), activity));
            }
            if (rate > 0) {
                out.merge(w.entry(), xp * pieceShare * rate, Double::sum);
            }
        }
        return out;
    }

    private static double rate(Map<String, Double> sources, String activity) {
        if (sources == null) {
            return 0;
        }
        Double r = sources.get(activity);
        return r == null || !Double.isFinite(r) ? 0 : Math.max(0, r);
    }

    /** Counts worn pieces per entry from the worn slots. */
    static Map<UUID, Integer> piecesPerEntry(Collection<UUID> wornEntryIds) {
        Map<UUID, Integer> out = new HashMap<>();
        for (UUID id : wornEntryIds) {
            out.merge(id, 1, Integer::sum);
        }
        return out;
    }

    /**
     * Parses {@code xp.sources.<activity>} entries: {@code "mining"} (rate 1) or {@code "mining:0.75"}. Unknown activities
     * and bad numbers are reported and skipped.
     */
    static Map<String, Double> parseSources(List<String> raw, Consumer<String> problems, String where) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String[] parts = s.trim().toLowerCase(Locale.ROOT).split(":", 2);
            String activity = parts[0].trim();
            if (!ACTIVITIES.contains(activity)) {
                problems.accept(where + ": unknown XP activity '" + activity + "' (use " + String.join(", ", ACTIVITIES) + ")");
                continue;
            }
            double rate = 1;
            if (parts.length == 2) {
                try {
                    rate = Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException e) {
                    problems.accept(where + ": '" + s + "' has no valid rate (use activity:rate, e.g. mining:0.75)");
                    continue;
                }
            }
            if (!Double.isFinite(rate) || rate < 0) {
                problems.accept(where + ": '" + s + "' has a negative or broken rate");
                continue;
            }
            out.put(activity, rate);
        }
        return out;
    }

    /**
     * Per-player hourly XP caps per activity (WD12: Coinmaster at most 8,000 set XP per hour). The window is the clock
     * hour. Not thread-safe (one per session, main thread).
     */
    static final class HourlyCap {
        private long hour = Long.MIN_VALUE;
        private final Map<String, Double> used = new HashMap<>();

        /**
         * @param cap 0 or less = no cap
         * @return how much of {@code amount} is still allowed this hour (and books it)
         */
        double allow(String activity, double amount, double cap, long now) {
            if (!(amount > 0)) {
                return 0;
            }
            if (!(cap > 0)) {
                return amount;
            }
            long h = Math.floorDiv(now, 3_600_000L);
            if (h != hour) {
                hour = h;
                used.clear();
            }
            double have = used.getOrDefault(activity, 0.0);
            double allowed = Math.max(0, Math.min(amount, cap - have));
            if (allowed > 0) {
                used.put(activity, have + allowed);
            }
            return allowed;
        }
    }
}
