package net.skybornmc.skyblock.economy.bank;

import java.util.ArrayList;
import java.util.List;

/**
 * The bank withdrawal fee per island mastery level (BANK2, owner #169): {@code withdraw.fee-percent} (2%) at level 0,
 * then one step of {@code withdraw.mastery-fee-percent} per bank-fee mastery level (1.5, 1.0, 0.5%). Levels above the
 * list keep the last step. Pure and unit-tested.
 *
 * <p>Steps are cleaned when parsed so a typo never makes the fee <b>higher</b> with progress: every step is between 0 and
 * the step before (the base for the first); invalid entries are reported and skipped.</p>
 *
 * @param basePercent fee at mastery level 0
 * @param steps       fee at mastery level 1, 2, 3 ... (each at most the one before)
 * @param problems    what was wrong in the config (empty = all good)
 */
record WithdrawFee(double basePercent, List<Double> steps, List<String> problems) {

    WithdrawFee {
        steps = List.copyOf(steps);
        problems = List.copyOf(problems);
    }

    static WithdrawFee parse(double basePercent, List<String> lines) {
        double base = Math.max(0, basePercent);
        List<Double> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        double previous = base;
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i) == null ? "" : lines.get(i).trim().replace("%", "");
                double value;
                try {
                    value = Double.parseDouble(line);
                } catch (NumberFormatException e) {
                    problems.add("withdraw.mastery-fee-percent entry " + (i + 1) + " '" + lines.get(i) + "' is not a number (skipped)");
                    continue;
                }
                if (Double.isNaN(value) || value < 0) {
                    problems.add("withdraw.mastery-fee-percent entry " + (i + 1) + " must be 0 or more (skipped)");
                    continue;
                }
                if (value > previous) {
                    problems.add("withdraw.mastery-fee-percent entry " + (i + 1) + " (" + line + ") is higher than the step before; "
                            + "lowered to " + previous);
                    value = previous;
                }
                out.add(value);
                previous = value;
            }
        }
        return new WithdrawFee(base, out, problems);
    }

    /** Fee steps of a config (problems are reported by the caller). */
    static WithdrawFee of(BankConfig config) {
        return parse(config.withdrawFeePercent, config.masteryFeePercent);
    }

    /** The fee percent at bank-fee mastery level {@code level} (0 or less = the base). */
    double percent(int level) {
        if (level <= 0 || steps.isEmpty()) {
            return basePercent;
        }
        return steps.get(Math.min(level, steps.size()) - 1);
    }

    /** How many percent points masteries take off at {@code level} (0 = no discount). */
    double discount(int level) {
        // Decimal subtraction: 2.0 - 0.3 is 1.7, not 1.6999999999999997.
        return Math.max(0, java.math.BigDecimal.valueOf(basePercent).subtract(java.math.BigDecimal.valueOf(percent(level))).doubleValue());
    }

    /** The highest level that still lowers the fee (= the number of steps). */
    int maxLevel() {
        return steps.size();
    }
}
