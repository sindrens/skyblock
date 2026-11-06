package net.skybornmc.skyblock.essentials;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 6 Nov 2026: the /fish waters, catches and anti-AFK guard. */
class FishRulesTest {

    private static List<FishRules.Water> defaults() {
        List<String> problems = new ArrayList<>();
        List<FishRules.Water> w = FishRules.parse(new EssentialsConfig().fishWaters, problems::add);
        assertEquals(List.of(), problems);
        return w;
    }

    @Test
    void sixWatersUnlockByCatches() {
        List<FishRules.Water> w = defaults();
        assertEquals(List.of("shallows", "river", "lake", "reef", "deep", "abyss"), w.stream().map(FishRules.Water::id).toList());
        assertEquals(List.of(0L, 500L, 2000L, 6000L, 15000L, 35000L), w.stream().map(FishRules.Water::unlockAt).toList());
        assertEquals("shallows", FishRules.waterFor(w, 0).id());
        assertEquals("river", FishRules.waterFor(w, 500).id());
        assertEquals("abyss", FishRules.waterFor(w, 1_000_000).id());
        assertNull(FishRules.next(w, FishRules.waterFor(w, 1_000_000)));
        for (int i = 1; i < w.size(); i++) {
            assertTrue(w.get(i).pay() > w.get(i - 1).pay(), "every water pays more");
        }
    }

    @Test
    void catchesAreWeighted() {
        FishRules.Water shallows = defaults().getFirst();
        assertEquals(Material.COD, FishRules.roll(shallows, 0.0).item());
        assertEquals(Material.INK_SAC, FishRules.roll(shallows, 0.999).item());
        FishRules.Water reef = defaults().get(3);
        assertEquals(2, FishRules.roll(reef, 0.99).amount(), "prismarine crystals come two at a time");
    }

    @Test
    void brokenLinesAreSkipped() {
        List<String> problems = new ArrayList<>();
        List<FishRules.Water> w = FishRules.parse(List.of("a:10:5:cod", "b:x:5:cod", "c:0:5:not_an_item", "d:0:5"), problems::add);
        assertEquals(1, w.size());
        assertEquals(0, w.getFirst().unlockAt(), "the first water is always open");
        assertEquals(3, problems.size());
    }

    @Test
    void autoClickersAndFloodsGetNothing() {
        FishRules.Guard g = new FishRules.Guard();
        for (int i = 0; i < 8; i++) {
            g.cast(10.2, 64, 3.7, 90f, 10f, 8);
        }
        assertTrue(g.frozen(8), "eight casts from one spot with the same look");
        g.cast(10.2, 64, 3.7, 95f, 10f, 8);
        assertFalse(g.frozen(8), "looking around is enough");
        FishRules.Guard flood = new FishRules.Guard();
        for (int i = 0; i < 20; i++) {
            assertTrue(flood.allow(1000L + i, 20));
        }
        assertFalse(flood.allow(1100L, 20), "at most 20 a minute");
        assertTrue(flood.allow(62_000L, 20), "the minute moves on");
    }
}
