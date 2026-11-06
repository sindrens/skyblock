package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.grind.GrindEvents.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 6 Nov 2026: the six grind-area events. */
class GrindEventsTest {

    @Test
    void allSixAreOnByDefault() {
        EssentialsConfig c = new EssentialsConfig();
        assertEquals(List.of(Kind.values()), GrindEventsImpl.enabled(c.grindEventKinds));
        assertEquals(List.of(Kind.DOUBLE_XP), GrindEventsImpl.enabled(List.of("Double_XP", "nope", "double_xp")), "unknown and doubles skipped");
        assertEquals(30, c.grindEventSeconds);
        assertEquals(2, c.grindEventActiveMin);
        assertEquals(4, c.grindEventActiveMax);
        assertEquals(30, c.grindEventBreakMin);
        assertEquals(60, c.grindEventBreakMax);
    }

    @Test
    void aRoundPicksDifferentEvents() {
        Random r = new Random(7);
        for (int n = 1; n <= 6; n++) {
            Set<Kind> picked = GrindEventsImpl.pick(List.of(Kind.values()), n, r);
            assertEquals(n, picked.size());
        }
        assertEquals(2, GrindEventsImpl.pick(List.of(Kind.DOUBLE_XP, Kind.GOLDEN_BLOCKS), 4, r).size(), "never more than enabled");
    }

    @Test
    void multipliersComeFromTheConfig() {
        EssentialsConfig c = new EssentialsConfig();
        assertEquals(2.0, GrindEventsImpl.value(c, Kind.DOUBLE_XP));
        assertEquals(2.0, GrindEventsImpl.value(c, Kind.DOUBLE_DROPS));
        assertEquals(2.0, GrindEventsImpl.value(c, Kind.DOUBLE_SHARDS));
        assertEquals(2.0, GrindEventsImpl.value(c, Kind.PROC_FRENZY));
        assertEquals(5.0, GrindEventsImpl.value(c, Kind.GOLDEN_BLOCKS));
        assertEquals(1.0, GrindEventsImpl.value(c, Kind.INSTANT_REGROW), "a switch, not a multiplier");
        assertTrue(Kind.GOLDEN_BLOCKS.id().equals("golden_blocks"));
    }
}
