package net.skybornmc.skyblock.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Rule numbers (spec 14.2), pure. {@code punishments.yml → rules:} lists the server rules by number with an optional
 * link; every reason and warn button may point to one or more of them with {@code rule: 3} or {@code rule: [1, 3]}.
 * The titles are texts, so they live in {@code messages/moderation.yml → rules.titles.<number>} (PUNISH_SPEC_NOTES
 * correction 2); a legacy {@code title:} in the YAML is kept as a fallback, like a reason's {@code name:}.
 *
 * <p>The parser guarantees that every reference points to a rule that exists (an unknown number rejects the file), so
 * lookups here never fail; a reason without a rule falls back to the general {@code extras.rules-link}.</p>
 *
 * @param rules rule number → rule, in number order
 * @param refs  lower-case reason id / warn-button id → its rule numbers (in the order written)
 */
record RuleBook(Map<Integer, Rule> rules, Map<String, List<Integer>> refs) {

    /** Highest rule number the parser accepts. */
    static final int MAX_NUMBER = 999;

    static final RuleBook EMPTY = new RuleBook(Map.of(), Map.of());

    /**
     * One rule. {@code link == null} = the general {@code extras.rules-link}; {@code yamlTitle} = an optional legacy title
     * from the YAML (the message {@code rules.titles.<number>} wins).
     */
    record Rule(int number, String link, String yamlTitle) {
    }

    RuleBook {
        rules = java.util.Collections.unmodifiableMap(new TreeMap<>(rules));
        Map<String, List<Integer>> copy = new java.util.LinkedHashMap<>();
        refs.forEach((k, v) -> copy.put(k.toLowerCase(Locale.ROOT), List.copyOf(v)));
        refs = java.util.Collections.unmodifiableMap(copy);
    }

    /** The rule numbers of a stored reason id / warn-button id ({@code null} or unknown = none). */
    List<Integer> numbers(String reasonId) {
        if (reasonId == null) {
            return List.of();
        }
        return refs.getOrDefault(reasonId.toLowerCase(Locale.ROOT), List.of());
    }

    /** The rules of a reason id, in the order written (only rules that exist). */
    List<Rule> rulesFor(String reasonId) {
        List<Rule> out = new ArrayList<>();
        for (int n : numbers(reasonId)) {
            Rule r = rules.get(n);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    /** {@code "3"} or {@code "1, 3"}; empty when the reason has no rule. */
    String numberText(String reasonId) {
        StringJoiner j = new StringJoiner(", ");
        numbers(reasonId).forEach(n -> j.add(String.valueOf(n)));
        return j.toString();
    }

    /** The link for a reason: its first rule's own link, else {@code fallback} (the general {@code extras.rules-link}). */
    String link(String reasonId, String fallback) {
        for (Rule r : rulesFor(reasonId)) {
            if (r.link() != null && !r.link().isBlank()) {
                return r.link();
            }
        }
        return fallback;
    }

    /**
     * The titles of a reason's rules joined with {@code " / "}. {@code title} resolves one rule's title (message, else the
     * legacy YAML title, else a "Rule n" text); empty when the reason has no rule.
     */
    String titleText(String reasonId, java.util.function.Function<Rule, String> title) {
        StringJoiner j = new StringJoiner(" / ");
        for (Rule r : rulesFor(reasonId)) {
            j.add(title.apply(r));
        }
        return j.toString();
    }

    /** Reason ids (and warn buttons) without any rule, for docs/tests. */
    List<String> withoutRule(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (numbers(id).isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }
}
