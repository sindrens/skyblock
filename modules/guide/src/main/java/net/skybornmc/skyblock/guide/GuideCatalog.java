package net.skybornmc.skyblock.guide;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Kindle's quests and trades from {@code guide-quests.yml} (pure, unit-tested): the island story (in order, shared), the
 * errands and the finds (rotating per player). Broken entries are reported and skipped.
 */
final class GuideCatalog {

    static final String FILE = "guide-quests.yml";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,40}");

    enum Kind {
        STORY("story", "ask", "rewards"),
        ERRAND("errands", "ask", "rewards"),
        TRADE("trades", "price", "gives");

        final String section;
        final String costKey;
        final String rewardKey;

        Kind(String section, String costKey, String rewardKey) {
            this.section = section;
            this.costKey = costKey;
            this.rewardKey = rewardKey;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    enum RewardType { MONEY, CRYSTALS, ITEM, KEY }

    /** One reward: money/crystals amount, or an item/key ({@code what}) times amount. */
    record Reward(RewardType type, String what, long amount) {
    }

    /** One quest or trade. {@code cost} = item id → amount (insertion order kept). */
    record Entry(Kind kind, String id, Map<String, Integer> cost, List<Reward> rewards, int level, int weight) {

        Entry {
            cost = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(cost));
            rewards = List.copyOf(rewards);
        }
    }

    private final Map<Kind, List<Entry>> entries;

    private GuideCatalog(Map<Kind, List<Entry>> entries) {
        this.entries = entries;
    }

    static GuideCatalog empty() {
        return new GuideCatalog(Map.of(Kind.STORY, List.of(), Kind.ERRAND, List.of(), Kind.TRADE, List.of()));
    }

    List<Entry> all(Kind kind) {
        return entries.getOrDefault(kind, List.of());
    }

    Optional<Entry> get(Kind kind, String id) {
        return all(kind).stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /**
     * Parses the file.
     *
     * @param validItem true for a known item id (the server's material registry; tests pass a simple check)
     * @param validCrate true for a known crate id
     */
    static GuideCatalog load(YamlConfiguration yaml, Predicate<String> validItem, Predicate<String> validCrate, Consumer<String> warn) {
        Map<Kind, List<Entry>> out = new LinkedHashMap<>();
        for (Kind kind : Kind.values()) {
            List<Entry> list = new ArrayList<>();
            List<Map<?, ?>> raw = yaml.getMapList(kind.section);
            for (int i = 0; i < raw.size(); i++) {
                String where = FILE + " → " + kind.section + "[" + i + "]";
                parse(kind, raw.get(i), where, validItem, validCrate, warn).ifPresent(e -> {
                    if (list.stream().anyMatch(o -> o.id().equals(e.id()))) {
                        warn.accept(where + ": duplicate id '" + e.id() + "', skipped");
                    } else {
                        list.add(e);
                    }
                });
            }
            out.put(kind, List.copyOf(list));
        }
        return new GuideCatalog(out);
    }

    private static Optional<Entry> parse(Kind kind, Map<?, ?> map, String where, Predicate<String> validItem,
                                         Predicate<String> validCrate, Consumer<String> warn) {
        String id = String.valueOf(map.get("id")).toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            warn.accept(where + ": missing or bad 'id', skipped");
            return Optional.empty();
        }
        Map<String, Integer> cost = new LinkedHashMap<>();
        if (map.get(kind.costKey) instanceof Map<?, ?> c) {
            for (Map.Entry<?, ?> e : c.entrySet()) {
                String item = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT).replace("minecraft:", "");
                int n = e.getValue() instanceof Number num ? num.intValue() : -1;
                if (!validItem.test(item) || n < 1 || n > 100_000) {
                    warn.accept(where + " (" + id + "): bad item '" + item + "' or amount '" + e.getValue() + "', skipped");
                    return Optional.empty();
                }
                cost.put(item, n);
            }
        }
        if (cost.isEmpty()) {
            warn.accept(where + " (" + id + "): '" + kind.costKey + "' is empty, skipped");
            return Optional.empty();
        }
        List<Reward> rewards = new ArrayList<>();
        Object rawRewards = map.get(kind.rewardKey);
        if (rawRewards instanceof List<?> lines) {
            for (Object line : lines) {
                Optional<Reward> r = reward(String.valueOf(line), validItem, validCrate);
                if (r.isEmpty()) {
                    warn.accept(where + " (" + id + "): bad reward '" + line + "', skipped");
                    return Optional.empty();
                }
                rewards.add(r.get());
            }
        }
        if (rewards.isEmpty()) {
            warn.accept(where + " (" + id + "): no rewards, skipped");
            return Optional.empty();
        }
        int level = map.get("level") instanceof Number n ? Math.max(0, n.intValue()) : 0;
        int weight = map.get("weight") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        return Optional.of(new Entry(kind, id, cost, rewards, level, weight));
    }

    /** {@code money 500}, {@code crystals 20}, {@code item bone_meal 32}, {@code key blaze 1}. Pure. */
    static Optional<Reward> reward(String line, Predicate<String> validItem, Predicate<String> validCrate) {
        String[] p = line.trim().toLowerCase(Locale.ROOT).split("\\s+");
        try {
            switch (p[0]) {
                case "money", "crystals" -> {
                    if (p.length != 2) {
                        return Optional.empty();
                    }
                    long n = Long.parseLong(p[1]);
                    return n < 1 ? Optional.empty() : Optional.of(new Reward(p[0].equals("money") ? RewardType.MONEY : RewardType.CRYSTALS, "", n));
                }
                case "item", "key" -> {
                    if (p.length != 3) {
                        return Optional.empty();
                    }
                    long n = Long.parseLong(p[2]);
                    boolean ok = p[0].equals("item") ? validItem.test(p[1]) : validCrate.test(p[1]);
                    if (!ok || n < 1 || n > (p[0].equals("item") ? 640 : 16)) {
                        return Optional.empty();
                    }
                    return Optional.of(new Reward(p[0].equals("item") ? RewardType.ITEM : RewardType.KEY, p[1], n));
                }
                default -> {
                    return Optional.empty();
                }
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ rules

    /** The island's current story quest: the first one not done (null when all are done). */
    static Entry currentStory(List<Entry> story, java.util.Set<String> done) {
        for (Entry e : story) {
            if (!done.contains(e.id())) {
                return e;
            }
        }
        return null;
    }

    /** Rotation number: which {@code hours}-long window {@code epochMillis} falls in. */
    static long period(long epochMillis, int hours) {
        long len = Math.max(1, hours) * 3_600_000L;
        return Math.floorDiv(epochMillis, len);
    }

    /**
     * The entries a player is offered this rotation: up to {@code count} different ones by weight from those the island
     * level allows, the same every time inside one rotation (seeded by player and rotation).
     */
    static List<Entry> picks(List<Entry> pool, int islandLevel, int count, long seed) {
        List<Entry> open = new ArrayList<>(pool.stream().filter(e -> e.level() <= islandLevel).toList());
        java.util.SplittableRandom rng = new java.util.SplittableRandom(seed);
        List<Entry> out = new ArrayList<>();
        while (out.size() < count && !open.isEmpty()) {
            int total = open.stream().mapToInt(Entry::weight).sum();
            int x = rng.nextInt(total);
            for (int i = 0; i < open.size(); i++) {
                x -= open.get(i).weight();
                if (x < 0) {
                    out.add(open.remove(i));
                    break;
                }
            }
        }
        return out;
    }

    /** Seed of a player's rotation (profile + rotation + kind), stable across restarts. */
    static long seed(java.util.UUID profile, long period, Kind kind) {
        long h = profile.getMostSignificantBits() * 31 + profile.getLeastSignificantBits();
        h = h * 1_000_003L + period;
        return h * 131 + kind.ordinal();
    }
}
