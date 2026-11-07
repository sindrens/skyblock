package net.skybornmc.skyblock.progression;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 7 Nov 2026: the Angler mastery asks for island levels instead of Farming pillar levels (there is no fishing pillar). */
class AnglerGatesTest {

    private static YamlConfiguration live(int firstGate) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("masteries.angler.pillar", "farming");
        y.set("masteries.angler.ranks", List.of(Map.of("tier", 3, "cost", 916000, "value", 5, "pillar-level", firstGate),
                Map.of("tier", 6, "cost", 16400000, "value", 10, "pillar-level", 30),
                Map.of("tier", 9, "cost", 477000000, "value", 15, "pillar-level", 45),
                Map.of("tier", 12, "cost", 36900000000L, "value", 20, "pillar-level", 60)));
        return y;
    }

    @Test
    void anUntouchedLiveFileMovesOnce() {
        YamlConfiguration y = live(15);
        assertTrue(MasteryManager.anglerGates(y));
        assertNull(y.getString("masteries.angler.pillar"));
        List<Map<?, ?>> ranks = y.getMapList("masteries.angler.ranks");
        assertEquals(List.of(5, 15, 30, 50), ranks.stream().map(r -> r.get("island-level")).toList());
        assertFalse(ranks.getFirst().containsKey("pillar-level"));
        assertEquals(36900000000L, ((Number) ranks.getLast().get("cost")).longValue(), "prices stay");
        assertFalse(MasteryManager.anglerGates(y), "a second run changes nothing");
    }

    @Test
    void anEditedFileStays() {
        YamlConfiguration y = live(20);
        assertFalse(MasteryManager.anglerGates(y));
        assertEquals("farming", y.getString("masteries.angler.pillar"));
    }
}
