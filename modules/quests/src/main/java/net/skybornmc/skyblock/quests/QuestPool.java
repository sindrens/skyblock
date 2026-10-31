package net.skybornmc.skyblock.quests;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * The quest pool of one board ({@code quests.yml → <board>.pool}) and how a set is picked from it. Pure, unit-tested.
 *
 * <p>A pool line is {@code "<type> [target]"}: {@code "mine"} works its target out from the play rate at the player's
 * tier ({@link QuestMath#target}), {@code "visit 3"} asks for a fixed number. A set never holds the same type twice and
 * at most one social quest (visit, like).</p>
 */
final class QuestPool {

    /**
     * One pool line.
     *
     * @param fixed fixed target, or 0 = from the play rate (mine, chop, kill, sell only)
     */
    record Entry(QuestType type, long fixed) {
    }

    /**
     * What a player has unlocked right now (filled by the module on the main thread from the live services).
     *
     * @param depths        the depths module runs and the tutorial opened the Depths for this player
     * @param mineTiers     open mine tiers (0 = none), likewise forest and combat
     * @param sell          the sell service runs
     * @param tool          the tools module runs and the player carries a living tool
     * @param enchants      the enchants module runs
     * @param islands       the islands module runs and the tutorial opened island visits for this player
     * @param likesAllowed  the player has played long enough to like islands
     * @param farming       island farming is on (PHASE-7 M7.3) and the player has an island
     */
    record Unlocks(boolean depths, int mineTiers, int forestTiers, int combatTiers, boolean sell, boolean tool, boolean enchants,
                   boolean islands, boolean likesAllowed, boolean farming) {
    }

    private QuestPool() {
    }

    /** True if a quest type is live for a player (PHASE-10: only live content, nothing locked or unbuilt). */
    static boolean live(QuestType type, Unlocks u) {
        return switch (type) {
            case MINE -> u.depths() && u.mineTiers() >= 1;
            case CHOP -> u.depths() && u.forestTiers() >= 1;
            case KILL -> u.depths() && u.combatTiers() >= 1;
            case SELL -> u.sell();
            case TOOL_LEVELS, FORGE -> u.tool();
            case ENCHANT -> u.enchants() && u.tool();
            case VISIT -> u.islands();
            case LIKE -> u.islands() && u.likesAllowed();
            case HARVEST -> u.farming();
        };
    }

    /** The entries of a pool that are live for these unlocks, in pool order. */
    static List<Entry> eligible(List<Entry> pool, Unlocks unlocks) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : pool) {
            if (live(e.type(), unlocks)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Parses pool lines; broken lines are reported and skipped. */
    static List<Entry> parse(List<String> lines, String where, Consumer<String> problems) {
        List<Entry> out = new ArrayList<>();
        Set<QuestType> seen = EnumSet.noneOf(QuestType.class);
        for (String raw : lines) {
            Optional<Entry> e = parse(raw);
            if (e.isEmpty()) {
                problems.accept(where + ": '" + raw + "' is not '<type> [target]' with a type of " + ids() + " (skipped)");
                continue;
            }
            if (!seen.add(e.get().type())) {
                problems.accept(where + ": '" + raw + "' lists " + e.get().type().id() + " twice (the first line is used)");
                continue;
            }
            out.add(e.get());
        }
        return List.copyOf(out);
    }

    static Optional<Entry> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length > 2) {
            return Optional.empty();
        }
        Optional<QuestType> type = QuestType.byId(parts[0]);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        long fixed = 0;
        if (parts.length == 2) {
            try {
                fixed = Long.parseLong(parts[1].replace("_", "").replace(",", ""));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (fixed <= 0) {
                return Optional.empty();
            }
        } else if (!type.get().auto()) {
            return Optional.empty(); // tool levels, forge, enchant, visit and like need a number
        }
        return Optional.of(new Entry(type.get(), fixed));
    }

    private static String ids() {
        List<String> ids = new ArrayList<>();
        for (QuestType t : QuestType.values()) {
            ids.add(t.id());
        }
        return String.join(", ", ids);
    }

    /**
     * Picks up to {@code count} entries in random order: never a type in {@code taken} or twice, at most one social quest
     * (counting the social ones already in {@code taken}). Fewer when the eligible pool is small.
     */
    static List<Entry> pick(List<Entry> eligible, int count, Set<QuestType> taken, RandomGenerator random) {
        List<Entry> shuffled = new ArrayList<>(eligible);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Entry tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }
        Set<QuestType> used = EnumSet.noneOf(QuestType.class);
        used.addAll(taken);
        boolean social = taken.stream().anyMatch(QuestType::social);
        List<Entry> out = new ArrayList<>();
        for (Entry e : shuffled) {
            if (out.size() >= count) {
                break;
            }
            if (used.contains(e.type()) || (e.type().social() && social)) {
                continue;
            }
            used.add(e.type());
            social |= e.type().social();
            out.add(e);
        }
        return out;
    }
}
