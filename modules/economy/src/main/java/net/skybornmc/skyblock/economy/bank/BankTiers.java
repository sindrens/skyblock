package net.skybornmc.skyblock.economy.bank;

import net.skybornmc.skyblock.common.util.Format;

import java.util.ArrayList;
import java.util.List;

/**
 * The parsed tier tables of {@code bank.yml}: personal tiers 1..n (capacity, unlock cost, payout cap) and island levels
 * 0..n (capacity, payout cap). Invalid lines are reported and replaced by the built-in defaults, so a typo never leaves
 * banks without limits.
 */
record BankTiers(List<PersonalTier> personal, List<IslandLevel> island, List<String> problems) {

    /** A personal bank tier ({@code tier} starts at 1). */
    record PersonalTier(int tier, long capacity, long cost, long payoutCap) {
    }

    /** An island bank level ({@code level} starts at 0). */
    record IslandLevel(int level, long capacity, long payoutCap) {
    }

    BankTiers {
        personal = List.copyOf(personal);
        island = List.copyOf(island);
        problems = List.copyOf(problems);
    }

    static BankTiers parse(BankConfig config) {
        List<String> problems = new ArrayList<>();
        List<PersonalTier> personal = parsePersonal(config.personalTiers, problems);
        if (personal == null) {
            personal = parsePersonal(new BankConfig().personalTiers, new ArrayList<>());
        }
        List<IslandLevel> island = parseIsland(config.islandLevels, problems);
        if (island == null) {
            island = parseIsland(new BankConfig().islandLevels, new ArrayList<>());
        }
        return new BankTiers(personal, island, problems);
    }

    /** Null (and a problem) if any line is invalid, capacities don't grow, or tier 1 is not free. */
    static List<PersonalTier> parsePersonal(List<String> lines, List<String> problems) {
        if (lines == null || lines.isEmpty()) {
            problems.add("personal.tiers is empty");
            return null;
        }
        List<PersonalTier> out = new ArrayList<>();
        long previous = 0;
        for (int i = 0; i < lines.size(); i++) {
            long[] v = numbers(lines.get(i), 3, i == 0);
            if (v == null) {
                problems.add("personal.tiers line " + (i + 1) + " '" + lines.get(i) + "': needs capacity, cost and payout cap");
                return null;
            }
            if (v[0] <= previous) {
                problems.add("personal.tiers line " + (i + 1) + ": capacity must be larger than the tier before");
                return null;
            }
            if (i == 0 && v[1] != 0) {
                problems.add("personal.tiers line 1: tier 1 must be free (cost 0)");
                return null;
            }
            previous = v[0];
            out.add(new PersonalTier(i + 1, v[0], v[1], v[2]));
        }
        return out;
    }

    static List<IslandLevel> parseIsland(List<String> lines, List<String> problems) {
        if (lines == null || lines.isEmpty()) {
            problems.add("island.levels is empty");
            return null;
        }
        List<IslandLevel> out = new ArrayList<>();
        long previous = 0;
        for (int i = 0; i < lines.size(); i++) {
            long[] v = numbers(lines.get(i), 2, false);
            if (v == null || v[0] <= previous) {
                problems.add("island.levels line " + (i + 1) + " '" + lines.get(i) + "': needs a growing capacity and a payout cap");
                return null;
            }
            previous = v[0];
            out.add(new IslandLevel(i, v[0], v[1]));
        }
        return out;
    }

    /** {@code count} amounts separated by spaces (0 allowed at index 1 when {@code zeroCost}); null if invalid. */
    private static long[] numbers(String line, int count, boolean zeroCost) {
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split("[\\s|;]+");
        if (parts.length != count) {
            return null;
        }
        long[] out = new long[count];
        for (int i = 0; i < count; i++) {
            String p = parts[i].trim();
            if (p.equals("0") && i == 1 && zeroCost) {
                out[i] = 0;
                continue;
            }
            try {
                out[i] = Format.parseAmount(p);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return out;
    }

    /** Tier {@code tier} (clamped into the table). */
    PersonalTier tier(int tier) {
        return personal.get(Math.max(1, Math.min(personal.size(), tier)) - 1);
    }

    int maxTier() {
        return personal.size();
    }

    /** The island bank capacity at a Bank Capacity level (clamped). */
    long islandCapacity(int capacityLevel) {
        return island.get(Math.max(0, Math.min(island.size() - 1, capacityLevel))).capacity();
    }

    /** The island payout cap at a Bank Interest level (clamped). */
    long islandCap(int interestLevel) {
        return island.get(Math.max(0, Math.min(island.size() - 1, interestLevel))).payoutCap();
    }
}
