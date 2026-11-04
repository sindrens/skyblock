package net.skybornmc.skyblock.tutorial;

import java.util.Map;

/**
 * One real thing a player did, turned into the tutorial's terms (pure; built by {@link TutorialListener} and
 * {@link AuditSignals}).
 *
 * @param type    which kind of step it can finish
 * @param target  role, menu id, place id, grind area, {@code category:action} or material (see {@link StepType})
 * @param amount  how much it counts (1 per sale, money for a deposit, items for a harvest)
 * @param details extra facts to match ({@code block}, {@code drop} for grind breaks; the audit row's details)
 */
record Signal(StepType type, String target, long amount, Map<String, String> details) {

    Signal {
        target = target == null ? "" : target;
        amount = Math.max(0, amount);
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    static Signal of(StepType type, String target, long amount) {
        return new Signal(type, target, amount, Map.of());
    }

    static Signal of(StepType type) {
        return new Signal(type, "", 1, Map.of());
    }
}
