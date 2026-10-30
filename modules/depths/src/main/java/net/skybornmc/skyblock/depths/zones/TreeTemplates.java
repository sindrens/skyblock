package net.skybornmc.skyblock.depths.zones;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tree templates per forest tier (M4.7) from {@code depths.yml → trees}:
 * <pre>
 * trees:
 *   1: {shape: round, trunk-min: 4, trunk-max: 5, trunk-width: 1, crown-radius: 2, crown-height: 3, spacing: 8}
 * </pre>
 * Missing keys take {@link TreeTemplate#DEFAULT}'s value; a broken entry is reported and the tier uses the default.
 * Immutable.
 */
public final class TreeTemplates {

    public static final String SECTION = "trees";
    public static final TreeTemplates DEFAULTS = new TreeTemplates(Map.of());

    private final Map<Integer, TreeTemplate> byTier;

    private TreeTemplates(Map<Integer, TreeTemplate> byTier) {
        this.byTier = Collections.unmodifiableMap(new LinkedHashMap<>(byTier));
    }

    public static TreeTemplates of(Map<Integer, TreeTemplate> byTier) {
        return new TreeTemplates(byTier);
    }

    /** The template of a forest tier ({@link TreeTemplate#DEFAULT} when none is configured). */
    public TreeTemplate get(int tier) {
        return byTier.getOrDefault(tier, TreeTemplate.DEFAULT);
    }

    /** Configured tiers. */
    public Map<Integer, TreeTemplate> all() {
        return byTier;
    }

    /**
     * Reads the section. Broken entries are reported and skipped (that tier uses the default).
     *
     * @param maxTrunk highest trunk allowed ({@code forest.max-trunk-height})
     */
    public static TreeTemplates parse(ConfigurationSection section, int maxTrunk, Consumer<String> problem) {
        if (section == null) {
            return DEFAULTS;
        }
        Map<Integer, TreeTemplate> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String where = SECTION + "." + key;
            int tier;
            try {
                tier = Integer.parseInt(key.trim());
            } catch (NumberFormatException e) {
                problem.accept(where + ": the key must be a forest tier number, skipped");
                continue;
            }
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null || tier < 1) {
                problem.accept(where + ": must be a section {shape, trunk-min, ...}, skipped");
                continue;
            }
            TreeTemplate d = TreeTemplate.DEFAULT;
            String shapeId = s.getString("shape", d.shape().id());
            TreeTemplate.Shape shape = TreeTemplate.Shape.byId(shapeId).orElse(null);
            if (shape == null) {
                problem.accept(where + ".shape: '" + shapeId + "' is unknown (round, cone, flat, cap), tier uses the default tree");
                continue;
            }
            TreeTemplate t = new TreeTemplate(shape,
                    s.getInt("trunk-min", d.trunkMin()),
                    s.getInt("trunk-max", s.getInt("trunk-min", d.trunkMax())),
                    s.getInt("trunk-width", d.trunkWidth()),
                    s.getInt("crown-radius", d.crownRadius()),
                    s.getInt("crown-height", d.crownHeight()),
                    s.getInt("crown-overlap", d.crownOverlap()),
                    s.getInt("spacing", d.spacing()));
            var problems = t.problems(maxTrunk);
            if (!problems.isEmpty()) {
                problem.accept(where + ": " + String.join("; ", problems) + ", tier uses the default tree");
                continue;
            }
            if (t.hidesLogs()) {
                problem.accept(where + ".crown-overlap: " + t.crownOverlap() + " top log(s) sit inside the crown and can't be chopped (leaves can't be broken)");
            }
            out.put(tier, t);
        }
        return new TreeTemplates(out);
    }
}
