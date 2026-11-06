package net.skybornmc.skyblock.hud;

import net.skybornmc.skyblock.hud.PlaceRules.IslandHere;
import net.skybornmc.skyblock.hud.PlaceRules.Place;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaceRulesTest {

    private static final PlaceRules.Settings S = PlaceRules.Settings.of(new HudConfig(), p -> {
        throw new AssertionError(p);
    });
    private static final Set<String> ALL = Set.of("default", "island", "island-visit", "grind", "grind-farm", "grind-mine", "spawn", "warzone");


    @Test
    void placeFollowsTheWorld() {
        assertEquals(Place.SPAWN, PlaceRules.place(S, "spawn", false, IslandHere.NONE));
        assertEquals(Place.DEPTHS, PlaceRules.place(S, "grind", false, IslandHere.NONE));
        assertEquals(Place.WARZONE, PlaceRules.place(S, "warzone", false, IslandHere.NONE));
        assertEquals(Place.OTHER, PlaceRules.place(S, "outposts", false, IslandHere.NONE));
        assertEquals(Place.OTHER, PlaceRules.place(S, null, false, IslandHere.NONE));
    }

    @Test
    void ownIslandVersusVisiting() {
        assertEquals(Place.ISLAND, PlaceRules.place(S, "islands", true, IslandHere.OWN));
        assertEquals(Place.ISLAND_VISIT, PlaceRules.place(S, "islands", true, IslandHere.OTHER));
        assertEquals(Place.ISLAND, PlaceRules.place(S, "islands", true, IslandHere.NONE), "between islands counts as own");
        // The islands module's world counts even if hud.yml lists another name.
        assertEquals(Place.ISLAND_VISIT, PlaceRules.place(S, "skyworld", true, IslandHere.OTHER));
        // Without the islands module the configured world names are the fallback.
        assertEquals(Place.ISLAND, PlaceRules.place(S, "islands", false, IslandHere.NONE));
    }

    @Test
    void layoutPerPlace() {
        Predicate<String> all = ALL::contains;
        assertEquals("island", PlaceRules.layout(S, Place.ISLAND, "islands", null, all));
        assertEquals("island-visit", PlaceRules.layout(S, Place.ISLAND_VISIT, "islands", null, all));
        assertEquals("spawn", PlaceRules.layout(S, Place.SPAWN, "spawn", null, all));
        assertEquals("warzone", PlaceRules.layout(S, Place.WARZONE, "warzone", null, all));
        assertEquals("default", PlaceRules.layout(S, Place.OTHER, "outposts", null, all));
    }

    @Test
    void grindVariantsByArea() {
        // Owner 4 Nov 2026: the Depths place became /farm and /mine (world grind) with a board per area.
        Predicate<String> all = ALL::contains;
        assertEquals("grind-farm", PlaceRules.layout(S, Place.DEPTHS, "grind", "Farm", all));
        assertEquals("grind-mine", PlaceRules.layout(S, Place.DEPTHS, "grind", "Mine", all));
        assertEquals("grind", PlaceRules.layout(S, Place.DEPTHS, "grind", "", all), "between the areas: base layout");
        assertEquals("grind", PlaceRules.layout(S, Place.DEPTHS, "grind", null, all));
    }

    @Test
    void missingLayoutsFallBack() {
        Predicate<String> few = Set.of("default", "island", "grind")::contains;
        assertEquals("island", PlaceRules.layout(S, Place.ISLAND_VISIT, "islands", null, few), "visit falls back to own island");
        assertEquals("grind", PlaceRules.layout(S, Place.DEPTHS, "grind", "Farm", few), "variant falls back to the place layout");
        assertEquals("default", PlaceRules.layout(S, Place.SPAWN, "spawn", null, few));
        assertEquals("default", PlaceRules.layout(S, Place.WARZONE, "warzone", null, Set.of("default")::contains));
        // UX1 world-named layout still works for places without their own layout.
        assertEquals("spawn", PlaceRules.layout(S, Place.OTHER, "spawn", null, Set.of("default", "spawn")::contains));
    }

    @Test
    void variantsParseAndReportBrokenEntries() {
        List<String> problems = new ArrayList<>();
        var variants = PlaceRules.parseVariants(List.of("The Gate=depths-gate", "broken", "=x", "y=", " A = b "), problems::add);
        assertEquals(2, variants.size());
        assertEquals("the gate", variants.get(0).match());
        assertEquals("b", variants.get(1).layout());
        assertEquals(3, problems.size());
        assertNull(PlaceRules.variant(variants, ""));
        assertEquals(0, PlaceRules.parseVariants(null, problems::add).size());
    }
}
