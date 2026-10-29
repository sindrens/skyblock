package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.islands.SafeSpot.Kind;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeSpotTest {

    /** A tiny world: everything is air unless set. */
    private static final class FakeWorld implements SafeSpot.Column {
        private final Map<String, Kind> blocks = new HashMap<>();

        FakeWorld set(int x, int y, int z, Kind kind) {
            blocks.put(x + "," + y + "," + z, kind);
            return this;
        }

        @Override
        public Kind kind(int x, int y, int z) {
            return blocks.getOrDefault(x + "," + y + "," + z, Kind.PASSABLE);
        }
    }

    @Test
    void solidGroundWithTwoAirIsSafe() {
        FakeWorld w = new FakeWorld().set(0, 99, 0, Kind.SOLID);
        assertTrue(SafeSpot.isSafe(w, 0, 100, 0));
        assertFalse(SafeSpot.isSafe(w, 0, 101, 0), "floating");
        assertFalse(SafeSpot.isSafe(w, 0, 99, 0), "inside the block");
    }

    @Test
    void liquidsDangerAndLowCeilingsAreUnsafe() {
        assertFalse(SafeSpot.isSafe(new FakeWorld().set(0, 99, 0, Kind.LIQUID), 0, 100, 0));
        assertFalse(SafeSpot.isSafe(new FakeWorld().set(0, 99, 0, Kind.DANGER), 0, 100, 0));
        assertFalse(SafeSpot.isSafe(new FakeWorld().set(0, 99, 0, Kind.SOLID).set(0, 100, 0, Kind.LIQUID), 0, 100, 0));
        assertFalse(SafeSpot.isSafe(new FakeWorld().set(0, 99, 0, Kind.SOLID).set(0, 101, 0, Kind.SOLID), 0, 100, 0));
        assertFalse(SafeSpot.isSafe(new FakeWorld().set(0, 99, 0, Kind.SOLID).set(0, 100, 0, Kind.DANGER), 0, 100, 0));
    }

    @Test
    void standingOnASlabCounts() {
        FakeWorld w = new FakeWorld().set(0, 100, 0, Kind.SOLID); // slab at 100, feet at 100.5
        assertTrue(SafeSpot.isSafeAt(w, 0, 100.5, 0));
        assertFalse(SafeSpot.isSafeAt(w.set(0, 102, 0, Kind.SOLID), 0, 100.5, 0), "head would hit the block at 102");
        assertFalse(SafeSpot.isSafeAt(new FakeWorld(), 0, 100.5, 0), "jumping over the void");
        assertTrue(SafeSpot.isSafeAt(new FakeWorld().set(0, 99, 0, Kind.SOLID), 0, 100.0, 0));
    }

    @Test
    void nearestPrefersTheSameColumnThenRings() {
        FakeWorld w = new FakeWorld().set(0, 95, 0, Kind.SOLID).set(1, 99, 0, Kind.SOLID);
        Optional<SafeSpot.Spot> spot = SafeSpot.nearest(w, 0, 100, 0, 3, 0, 256, (x, z) -> true);
        assertEquals(Optional.of(new SafeSpot.Spot(0, 96, 0)), spot, "same column wins over a nearer column");
        FakeWorld onlyNeighbour = new FakeWorld().set(2, 99, 1, Kind.SOLID);
        assertEquals(Optional.of(new SafeSpot.Spot(2, 100, 1)),
                SafeSpot.nearest(onlyNeighbour, 0, 100, 0, 3, 0, 256, (x, z) -> true));
    }

    @Test
    void nearestRespectsTheAreaAndRadius() {
        FakeWorld w = new FakeWorld().set(2, 99, 0, Kind.SOLID);
        assertTrue(SafeSpot.nearest(w, 0, 100, 0, 3, 0, 256, (x, z) -> x < 2).isEmpty(), "outside the island");
        assertTrue(SafeSpot.nearest(w, 0, 100, 0, 1, 0, 256, (x, z) -> true).isEmpty(), "beyond the radius");
        assertTrue(SafeSpot.nearest(w, 0, 100, 0, 3, 101, 256, (x, z) -> true).isEmpty(), "below min Y");
    }

    @Test
    void highestInColumn() {
        FakeWorld w = new FakeWorld().set(5, 50, 5, Kind.SOLID).set(5, 80, 5, Kind.SOLID);
        assertEquals(Optional.of(new SafeSpot.Spot(5, 81, 5)), SafeSpot.highestIn(w, 5, 5, 0, 256));
        assertTrue(SafeSpot.highestIn(new FakeWorld(), 5, 5, 0, 256).isEmpty());
    }
}
