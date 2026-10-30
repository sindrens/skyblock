package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Parses the starter chest and pack lists of {@code tutorial.yml} (TUTORIAL.md §6). Pure, unit-tested; materials are
 * checked by the caller's predicate. Bad lines are skipped with a readable problem.
 */
final class StarterSpec {

    /** One stack: chest slot (or -1 for the pack), lowercase item id, amount 1-64. */
    record Entry(int slot, String material, int amount) {
    }

    record Result(List<Entry> entries, List<String> problems) {
    }

    /** Chest size of a single chest. */
    static final int CHEST_SLOTS = 27;

    private StarterSpec() {
    }

    /** {@code "15: wheat_seeds: 8"} lines (also {@code 15:wheat_seeds:8}, or a YAML map read as {@code {15=wheat_seeds: 8}}). */
    static Result chest(Collection<String> lines, Predicate<String> isItem) {
        List<Entry> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            String line = clean(raw);
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("[:=]");
            if (parts.length < 2 || parts.length > 3) {
                problems.add("starter-chest.items: '" + raw + "' must look like 'slot: item: amount'.");
                continue;
            }
            int slot;
            try {
                slot = Integer.parseInt(parts[0].strip());
            } catch (NumberFormatException e) {
                problems.add("starter-chest.items: '" + raw + "' has no slot number.");
                continue;
            }
            if (slot < 0 || slot >= CHEST_SLOTS) {
                problems.add("starter-chest.items: slot " + slot + " must be 0-" + (CHEST_SLOTS - 1) + ".");
                continue;
            }
            Entry e = item(parts[1], parts.length == 3 ? parts[2] : "1", raw, "starter-chest.items", isItem, problems);
            if (e == null) {
                continue;
            }
            if (!used.add(slot)) {
                problems.add("starter-chest.items: slot " + slot + " is used twice; the second item goes to the next free slot.");
                slot = -1;
            }
            out.add(new Entry(slot, e.material(), e.amount()));
        }
        return new Result(List.copyOf(out), List.copyOf(problems));
    }

    /** {@code "bread: 16"} lines for the Starter pack. */
    static Result pack(Collection<String> lines, Predicate<String> isItem) {
        List<Entry> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            String line = clean(raw);
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("[:=]");
            if (parts.length > 2) {
                problems.add("starter-pack.items: '" + raw + "' must look like 'item: amount'.");
                continue;
            }
            Entry e = item(parts[0], parts.length == 2 ? parts[1] : "1", raw, "starter-pack.items", isItem, problems);
            if (e != null) {
                out.add(e);
            }
        }
        return new Result(List.copyOf(out), List.copyOf(problems));
    }

    /** Kit slots ({@code "10"}...); bad or out-of-range values are dropped. */
    static List<Integer> slots(Collection<String> lines) {
        List<Integer> out = new ArrayList<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            try {
                int s = Integer.parseInt(clean(raw));
                if (s >= 0 && s < CHEST_SLOTS && !out.contains(s)) {
                    out.add(s);
                }
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        return List.copyOf(out);
    }

    private static Entry item(String rawMaterial, String rawAmount, String line, String key, Predicate<String> isItem,
                              List<String> problems) {
        String material = rawMaterial.strip().toLowerCase(Locale.ROOT);
        int amount;
        try {
            amount = Integer.parseInt(rawAmount.strip());
        } catch (NumberFormatException e) {
            problems.add(key + ": '" + line + "' has no valid amount.");
            return null;
        }
        if (amount < 1 || amount > 64) {
            problems.add(key + ": '" + line + "' amount must be 1-64.");
            return null;
        }
        if (material.isEmpty() || !isItem.test(material)) {
            problems.add(key + ": '" + line + "' is not a known item.");
            return null;
        }
        return new Entry(-1, material, amount);
    }

    private static String clean(String raw) {
        String line = raw == null ? "" : raw.strip();
        if (line.startsWith("{") && line.endsWith("}")) {
            line = line.substring(1, line.length() - 1).strip();
        }
        return line;
    }
}
