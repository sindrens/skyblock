package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Tutorial highlights (GUI-ICONS-FINAL step 6, owner C4-Q4 note "make the Depths light up"): while the tutorial's
 * current step equals a rule's {@code step}, the menu tile {@code target} ({@code hub.<button id>} or
 * {@code travel.<place id>}) shows the rule's icon key, glints and gets one {@code ● Start here} line. Pure: parsing and
 * picking are unit-testable without a server. Rules live in {@code menu.yml → highlights}; a rule whose step is
 * {@code none} never matches (switch one off without deleting it: deleted keys come back with the next update).
 */
final class Highlights {

    /** One rule: {@code target} tile, tutorial {@code step} id, icon catalogue key shown while it matches. */
    record Rule(String target, String step, String icon) {
    }

    private Highlights() {
    }

    /**
     * The first rule for {@code target} whose step is the current tutorial step; empty when the tutorial is not active,
     * there is no current step, or no rule matches.
     */
    static Optional<Rule> pick(List<Rule> rules, String target, boolean tutorialActive, Optional<String> currentStep) {
        if (!tutorialActive || currentStep == null || currentStep.isEmpty() || target == null) {
            return Optional.empty();
        }
        String step = currentStep.get();
        for (Rule rule : rules) {
            if (rule.target().equals(target) && rule.step().equals(step)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /**
     * Reads {@code highlights.<id>.{target, step, icon}}; a rule with a missing field or an unknown icon key
     * ({@code icons} false) is reported and skipped.
     */
    static List<Rule> parse(ConfigurationSection section, Predicate<String> icons, Consumer<String> problem) {
        List<Rule> out = new ArrayList<>();
        if (section == null) {
            return out;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection rule = section.getConfigurationSection(id);
            String target = rule == null ? null : rule.getString("target");
            String step = rule == null ? null : rule.getString("step");
            String icon = rule == null ? null : rule.getString("icon");
            if (target == null || target.isBlank() || step == null || step.isBlank() || icon == null || icon.isBlank()) {
                problem.accept("menu.yml: highlights." + id + " needs target, step and icon; skipped");
                continue;
            }
            if (!icons.test(icon)) {
                problem.accept("menu.yml: highlights." + id + " has an unknown icon key '" + icon + "'; skipped");
                continue;
            }
            out.add(new Rule(target.trim(), step.trim(), icon.trim()));
        }
        return List.copyOf(out);
    }
}
