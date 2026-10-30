package net.skybornmc.skyblock.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Pure rules of the per-place scoreboard (SB): which place a player is in and which layout that place uses.
 * Every layout name is a key {@code scoreboard.<layout>} in {@code messages/hud.yml}; a missing layout falls back to
 * the next candidate, so a server with fewer layouts still shows a board.
 *
 * <p>Fallback chain: place variant (Depths wing / The Gate) → place layout (visiting falls back to the own-island
 * layout) → layout named after the world (the UX1 rule) → {@code default}.</p>
 */
final class PlaceRules {

    static final String DEFAULT = "default";

    /** Where a player is, as far as the scoreboard cares. */
    enum Place { ISLAND, ISLAND_VISIT, DEPTHS, SPAWN, WARZONE, OTHER }

    /** The island slot a player stands in: none, one they are a stored member of, or someone else's. */
    enum IslandHere { NONE, OWN, OTHER }

    /** A Depths variant: when the plain text of the variant placeholder contains {@code match}, use {@code layout}. */
    record Variant(String match, String layout) {
    }

    /** The place settings of {@code hud.yml}, copied once per (re)load. World names are compared case-sensitively. */
    record Settings(Set<String> islandWorlds, String islandLayout, String visitLayout,
                    Set<String> depthsWorlds, String depthsLayout, List<Variant> depthsVariants,
                    Set<String> spawnWorlds, String spawnLayout,
                    Set<String> warzoneWorlds, String warzoneLayout) {

        Settings {
            islandWorlds = Set.copyOf(islandWorlds);
            depthsWorlds = Set.copyOf(depthsWorlds);
            depthsVariants = List.copyOf(depthsVariants);
            spawnWorlds = Set.copyOf(spawnWorlds);
            warzoneWorlds = Set.copyOf(warzoneWorlds);
        }

        static Settings of(HudConfig c, Consumer<String> problems) {
            return new Settings(Set.copyOf(c.islandWorlds), c.islandLayout, c.islandVisitLayout,
                    Set.copyOf(c.depthsWorlds), c.depthsLayout, parseVariants(c.depthsVariants, problems),
                    Set.copyOf(c.spawnWorlds), c.spawnLayout, Set.copyOf(c.warzoneWorlds), c.warzoneLayout);
        }
    }

    private PlaceRules() {
    }

    /**
     * Parses {@code "match=layout"} entries (e.g. {@code "The Gate=depths-gate"}). Broken entries are reported and
     * skipped; order is kept (the first matching variant wins).
     */
    static List<Variant> parseVariants(List<String> raw, Consumer<String> problems) {
        List<Variant> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String entry : raw) {
            int eq = entry == null ? -1 : entry.lastIndexOf('=');
            String match = eq < 0 ? "" : entry.substring(0, eq).trim();
            String layout = eq < 0 ? "" : entry.substring(eq + 1).trim();
            if (match.isEmpty() || layout.isEmpty()) {
                problems.accept("hud.yml places.depths.variants: '" + entry + "' is not 'text=layout', skipped");
                continue;
            }
            out.add(new Variant(match.toLowerCase(Locale.ROOT), layout));
        }
        return out;
    }

    /**
     * The place of a player in {@code world}.
     *
     * @param islandWorld true if the islands module says this is its world (the {@code islandWorlds} setting is
     *                    the fallback when the islands module does not run)
     * @param here        the island slot the player stands in (only read in island worlds)
     */
    static Place place(Settings s, String world, boolean islandWorld, IslandHere here) {
        if (world == null) {
            return Place.OTHER;
        }
        if (s.warzoneWorlds().contains(world)) {
            return Place.WARZONE;
        }
        if (s.spawnWorlds().contains(world)) {
            return Place.SPAWN;
        }
        if (s.depthsWorlds().contains(world)) {
            return Place.DEPTHS;
        }
        if (islandWorld || s.islandWorlds().contains(world)) {
            return here == IslandHere.OTHER ? Place.ISLAND_VISIT : Place.ISLAND;
        }
        return Place.OTHER;
    }

    /**
     * The layout to render. {@code variantText} is the plain value of the Depths variant placeholder (ignored
     * elsewhere, may be null). {@code exists} says whether {@code scoreboard.<layout>} exists in messages/hud.yml;
     * {@code default} is returned when nothing else exists.
     */
    static String layout(Settings s, Place place, String world, String variantText, Predicate<String> exists) {
        switch (place) {
            case DEPTHS -> {
                String variant = variant(s.depthsVariants(), variantText);
                if (ok(variant, exists)) {
                    return variant;
                }
                if (ok(s.depthsLayout(), exists)) {
                    return s.depthsLayout();
                }
            }
            case ISLAND_VISIT -> {
                if (ok(s.visitLayout(), exists)) {
                    return s.visitLayout();
                }
                if (ok(s.islandLayout(), exists)) {
                    return s.islandLayout();
                }
            }
            case ISLAND -> {
                if (ok(s.islandLayout(), exists)) {
                    return s.islandLayout();
                }
            }
            case SPAWN -> {
                if (ok(s.spawnLayout(), exists)) {
                    return s.spawnLayout();
                }
            }
            case WARZONE -> {
                if (ok(s.warzoneLayout(), exists)) {
                    return s.warzoneLayout();
                }
            }
            case OTHER -> {
                // world-named layout below
            }
        }
        return SidebarLines.layout(world, exists);
    }

    /** First variant whose match text is contained (ignoring case) in {@code text}, or null. */
    static String variant(List<Variant> variants, String text) {
        if (text == null || text.isBlank() || variants.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Variant v : variants) {
            if (lower.contains(v.match())) {
                return v.layout();
            }
        }
        return null;
    }

    private static boolean ok(String layout, Predicate<String> exists) {
        return layout != null && !layout.isBlank() && exists.test(layout);
    }
}
