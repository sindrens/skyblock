package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Parses the starter chest list ({@code material:amount}) from islands.yml. Pure; materials are checked by the caller. */
public final class StarterItems {

    /** One stack: a lowercase material key (e.g. {@code lava_bucket}) and an amount of 1-64. */
    public record Entry(String material, int amount) {
    }

    /** Parsed entries plus a readable problem per bad line (bad lines are skipped). */
    public record Result(List<Entry> entries, List<String> problems) {
    }

    private StarterItems() {
    }

    /**
     * @param lines      e.g. {@code ["lava_bucket:1", "bread:16"]}; a line without {@code :amount} means 1
     * @param isMaterial whether a lowercase key names a real item
     */
    public static Result parse(List<String> lines, Predicate<String> isMaterial) {
        List<Entry> entries = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            String line = raw == null ? "" : raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.lastIndexOf(':');
            String material = (colon < 0 ? line : line.substring(0, colon)).strip().toLowerCase(Locale.ROOT);
            if (material.startsWith("minecraft:")) {
                material = material.substring("minecraft:".length());
            }
            int amount = 1;
            if (colon >= 0) {
                try {
                    amount = Integer.parseInt(line.substring(colon + 1).strip());
                } catch (NumberFormatException e) {
                    problems.add("starter-chest.items: '" + line + "' has no valid amount (use material:amount).");
                    continue;
                }
            }
            if (amount < 1 || amount > 64) {
                problems.add("starter-chest.items: '" + line + "' amount must be 1-64.");
                continue;
            }
            if (material.isEmpty() || !isMaterial.test(material)) {
                problems.add("starter-chest.items: '" + line + "' is not a known item.");
                continue;
            }
            entries.add(new Entry(material, amount));
        }
        return new Result(List.copyOf(entries), List.copyOf(problems));
    }
}
