package net.skybornmc.skyblock.depths.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingWheelTest {

    private record Fired(long tick, int owner, long key, int token) {
    }

    private static final class Recorder implements TimingWheel.Sink {
        final List<Fired> fired = new ArrayList<>();
        TimingWheel wheel;

        @Override
        public void expire(int owner, long key, int token) {
            fired.add(new Fired(wheel.tick(), owner, key, token));
        }
    }

    private static Recorder recorder(TimingWheel wheel) {
        Recorder r = new Recorder();
        r.wheel = wheel;
        return r;
    }

    @Test
    void firesExactlyOnItsTick() {
        TimingWheel wheel = new TimingWheel(64, 16);
        Recorder r = recorder(wheel);
        wheel.schedule(200, 7, 42L, 3);
        wheel.advanceTo(199, r);
        assertTrue(r.fired.isEmpty());
        wheel.advance(r);
        assertEquals(List.of(new Fired(200, 7, 42L, 3)), r.fired);
        assertEquals(0, wheel.size());
    }

    @Test
    void delaysLongerThanTheWheelWrapAround() {
        TimingWheel wheel = new TimingWheel(16, 16);
        Recorder r = recorder(wheel);
        wheel.schedule(5, 1, 1L, 1);
        wheel.schedule(21, 2, 2L, 2);   // same slot as 5, one round later
        wheel.schedule(1000, 3, 3L, 3);
        wheel.advanceTo(2000, r);
        assertEquals(List.of(new Fired(5, 1, 1L, 1), new Fired(21, 2, 2L, 2), new Fired(1000, 3, 3L, 3)), r.fired);
    }

    @Test
    void zeroOrNegativeDelayFiresNextTick() {
        TimingWheel wheel = new TimingWheel(16, 16);
        Recorder r = recorder(wheel);
        wheel.schedule(0, 1, 1L, 1);
        wheel.schedule(-5, 2, 2L, 2);
        wheel.advance(r);
        assertEquals(2, r.fired.size());
        assertEquals(1, r.fired.getFirst().tick());
    }

    @Test
    void slotsRoundUpToPowerOfTwo() {
        assertEquals(1024, new TimingWheel(1000, 16).slots());
        assertEquals(64, new TimingWheel(64, 16).slots());
    }

    @Test
    void poolGrowsAndNodesAreReused() {
        TimingWheel wheel = new TimingWheel(256, 16);
        Recorder r = recorder(wheel);
        for (int i = 0; i < 1000; i++) {
            wheel.schedule(1 + (i % 200), i, i, i);
        }
        assertEquals(1000, wheel.size());
        int capacity = wheel.capacity();
        assertTrue(capacity >= 1000);
        wheel.advanceTo(200, r);
        assertEquals(1000, r.fired.size());
        assertEquals(0, wheel.size());
        // Another round of the same size fits in the freed nodes: no growth.
        for (int i = 0; i < 1000; i++) {
            wheel.schedule(1 + (i % 200), i, i, i);
        }
        assertEquals(capacity, wheel.capacity());
    }

    @Test
    void sinkMayScheduleIntoTheSameSlot() {
        TimingWheel wheel = new TimingWheel(16, 16);
        List<Long> fired = new ArrayList<>();
        TimingWheel.Sink sink = new TimingWheel.Sink() {
            @Override
            public void expire(int owner, long key, int token) {
                fired.add(wheel.tick());
                if (fired.size() < 3) {
                    wheel.schedule(16, owner, key, token); // lands in the slot being processed
                }
            }
        };
        wheel.schedule(16, 1, 1L, 1);
        wheel.advanceTo(100, sink);
        assertEquals(List.of(16L, 32L, 48L), fired);
    }

    @Test
    void manyTimersInOneSlotAllFire() {
        TimingWheel wheel = new TimingWheel(64, 16);
        Recorder r = recorder(wheel);
        // 300 players x 1 break per second, 10 s respawn: 3000 timers in flight, 15 per tick.
        for (int tick = 0; tick < 200; tick++) {
            for (int p = 0; p < 15; p++) {
                wheel.schedule(200, p, tick, tick);
            }
            wheel.advance(r);
        }
        assertEquals(3000, wheel.size() + r.fired.size());
        wheel.advanceTo(wheel.tick() + 200, r);
        assertEquals(3000, r.fired.size());
        assertEquals(0, wheel.size());
        for (Fired f : r.fired) {
            assertEquals(f.key() + 200, f.tick(), "timer scheduled at tick " + f.key() + " must fire 200 ticks later");
        }
    }

    @Test
    void clearDropsEverything() {
        TimingWheel wheel = new TimingWheel(16, 16);
        Recorder r = recorder(wheel);
        wheel.schedule(3, 1, 1L, 1);
        wheel.schedule(30, 2, 2L, 2);
        wheel.clear();
        assertEquals(0, wheel.size());
        wheel.advanceTo(100, r);
        assertTrue(r.fired.isEmpty());
        wheel.schedule(1, 3, 3L, 3);
        wheel.advance(r);
        assertEquals(1, r.fired.size());
    }

    @Test
    void idleAdvanceOnlyCountsTicks() {
        TimingWheel wheel = new TimingWheel(16, 16);
        Recorder r = recorder(wheel);
        assertEquals(0, wheel.advanceTo(10_000, r));
        assertEquals(10_000, wheel.tick());
    }
}
