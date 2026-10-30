package net.skybornmc.skyblock.worlds.portals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The target rules of {@code worlds.yml → portals.*} as plain data (pure, unit-tested).
 *
 * @param islandPortal    portal id ({@code to_island} or {@code build:id}) that is the island portal; blank = from the
 *                        metadata (target {@code island}, or an id containing {@code island} / {@code start})
 * @param forceOpen       portal ids opened although the builder wrote {@code status: closed}
 * @param disabled        portal ids that never teleport
 * @param targetOverrides portal id → target, replacing the metadata target
 * @param worldTargets    target name → world ({@code spawn → spawn}); a target of {@code world:<name>} works too
 * @param needBuild       worlds that only open once a build of that world is applied ({@code warzone})
 */
public record PortalSettings(String islandPortal, Set<String> forceOpen, Set<String> disabled, Map<String, String> targetOverrides,
                             Map<String, String> worldTargets, Set<String> needBuild) {

    public static final String ISLAND = "island";

    public PortalSettings {
        islandPortal = islandPortal == null ? "" : islandPortal.trim();
        forceOpen = Set.copyOf(forceOpen);
        disabled = Set.copyOf(disabled);
        targetOverrides = Map.copyOf(targetOverrides);
        worldTargets = Map.copyOf(worldTargets);
        needBuild = Set.copyOf(needBuild);
    }

    /** Defaults of worlds.yml. */
    public static final PortalSettings DEFAULTS = of("", List.of(), List.of(), List.of(),
            List.of("spawn=spawn", "depths=depths", "warzone=warzone"), List.of("warzone"));

    /** From the config lists ({@code key=value} entries; broken entries are ignored). */
    public static PortalSettings of(String islandPortal, List<String> forceOpen, List<String> disabled, List<String> targetOverrides,
                                    List<String> worldTargets, List<String> needBuild) {
        return new PortalSettings(islandPortal, lower(forceOpen), lower(disabled), pairs(targetOverrides), pairs(worldTargets),
                lower(needBuild));
    }

    /** True if {@code ids} names this portal by id or {@code build:id}. */
    static boolean names(Set<String> ids, Portal p) {
        return ids.contains(p.id().toLowerCase(Locale.ROOT)) || ids.contains(p.key().toLowerCase(Locale.ROOT));
    }

    /** The target after overrides. */
    public String target(Portal p) {
        String byKey = targetOverrides.get(p.key().toLowerCase(Locale.ROOT));
        if (byKey != null) {
            return byKey;
        }
        return targetOverrides.getOrDefault(p.id().toLowerCase(Locale.ROOT), p.target());
    }

    /** The world a target name stands for, or {@code null} (island, point:, unknown). */
    public String worldOf(String target) {
        if (target == null) {
            return null;
        }
        if (target.startsWith("world:")) {
            String w = target.substring("world:".length()).trim();
            return w.isEmpty() ? null : w;
        }
        return worldTargets.get(target);
    }

    /** True if this portal is the island portal (owner answer 159). */
    public boolean isIslandPortal(Portal p) {
        if (ISLAND.equals(target(p))) {
            return true;
        }
        if (!islandPortal.isEmpty()) {
            String want = islandPortal.toLowerCase(Locale.ROOT);
            return p.id().equalsIgnoreCase(want) || p.key().equalsIgnoreCase(want);
        }
        if (targetOverrides.containsKey(p.id().toLowerCase(Locale.ROOT)) || targetOverrides.containsKey(p.key().toLowerCase(Locale.ROOT))) {
            return false; // an explicit target wins over the id guess
        }
        String id = p.id().toLowerCase(Locale.ROOT);
        return p.target().isEmpty() && (id.contains("island") || id.contains("start"));
    }

    private static Set<String> lower(List<String> list) {
        return list.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private static Map<String, String> pairs(List<String> list) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String entry : list) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                continue;
            }
            out.put(entry.substring(0, eq).trim().toLowerCase(Locale.ROOT), entry.substring(eq + 1).trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
