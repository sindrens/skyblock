package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.Pillar;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One island's pillars in memory, the write-behind buffer and the placed-log memory (PHASE-5 M5.1). */
class IslandPillarsTest {

    private final PillarCurve curve = new PillarCurve(100, 1.12);

    @Test
    void gainsLevelUpAndJumpSeveralLevels() {
        IslandPillars s = new IslandPillars();
        assertNull(s.add(Pillar.MINING, 99, curve));
        IslandPillars.LevelChange one = s.add(Pillar.MINING, 1, curve);
        assertEquals(new IslandPillars.LevelChange(Pillar.MINING, 0, 1), one);
        IslandPillars.LevelChange jump = s.add(Pillar.MINING, curve.totalFor(5) - curve.totalFor(1), curve);
        assertEquals(new IslandPillars.LevelChange(Pillar.MINING, 1, 5), jump);
        assertEquals(0, s.level(Pillar.FARMING));
        assertNull(s.add(Pillar.FARMING, -5, curve));
        assertNull(s.add(Pillar.FARMING, Double.POSITIVE_INFINITY, curve));
        assertEquals(0, s.xp(Pillar.FARMING));
    }

    @Test
    void levelsNeverGoDown() {
        IslandPillars s = new IslandPillars();
        s.addStored(Pillar.COMBAT, curve.totalFor(10), 12, curve);
        assertEquals(12, s.level(Pillar.COMBAT), "stored level above the XP (curve was cheaper) is kept");
        s.relevel(new PillarCurve(1000, 1.12));
        assertEquals(12, s.level(Pillar.COMBAT), "a more expensive curve never lowers a level");
        s.relevel(new PillarCurve(10, 1.12));
        assertTrue(s.level(Pillar.COMBAT) > 12, "a cheaper curve raises it");
    }

    @Test
    void storedXpAddsToGainsMadeDuringTheLoad() {
        IslandPillars s = new IslandPillars();
        s.add(Pillar.FORAGING, 50, curve);
        s.addStored(Pillar.FORAGING, 150, 1, curve);
        assertEquals(200, s.xp(Pillar.FORAGING), 1e-9);
        assertEquals(curve.levelFor(200), s.level(Pillar.FORAGING));
    }

    @Test
    void bufferDrainsAndMergesBack() {
        PillarBuffer buffer = new PillarBuffer();
        UUID island = UUID.randomUUID();
        buffer.add(island, Pillar.MINING, 100, 10, 0);
        buffer.add(island, Pillar.MINING, 100, 5, 1);
        buffer.add(island, Pillar.MINING, 107, 1, 0);
        buffer.add(island, Pillar.COMBAT, 100, 0, 0);
        buffer.add(island, Pillar.COMBAT, 100, -3, 2);
        PillarBuffer.Batch batch = buffer.drain(k -> 7);
        assertTrue(buffer.isEmpty());
        assertEquals(16, batch.xp().get(new PillarBuffer.Key(island, Pillar.MINING)), 1e-9);
        assertEquals(7, batch.levels().get(new PillarBuffer.Key(island, Pillar.MINING)));
        assertEquals(new PillarBuffer.Week(15, 1), batch.weekly().get(new PillarBuffer.WeekKey(island, Pillar.MINING, 100)));
        assertEquals(new PillarBuffer.Week(1, 0), batch.weekly().get(new PillarBuffer.WeekKey(island, Pillar.MINING, 107)));
        assertEquals(1, batch.xp().size(), "zero and negative gains are ignored");

        buffer.add(island, Pillar.MINING, 100, 4, 0);
        buffer.mergeBack(batch);
        PillarBuffer.Batch again = buffer.drain(k -> 0);
        assertEquals(20, again.xp().get(new PillarBuffer.Key(island, Pillar.MINING)), 1e-9);
        assertEquals(new PillarBuffer.Week(19, 1), again.weekly().get(new PillarBuffer.WeekKey(island, Pillar.MINING, 100)));
    }

    @Test
    void placedLogsGiveNothingWhenBrokenAgain() {
        PlacedBlocks placed = new PlacedBlocks(2);
        placed.placed(1, 64, 1);
        placed.placed(2, 64, 2);
        placed.placed(3, -10, -3);
        assertEquals(2, placed.size());
        assertTrue(!placed.brokePlaced(1, 64, 1), "oldest position was forgotten");
        assertTrue(placed.brokePlaced(3, -10, -3));
        assertTrue(!placed.brokePlaced(3, -10, -3), "forgotten after the break");
        PlacedBlocks off = new PlacedBlocks(0);
        off.placed(1, 1, 1);
        assertTrue(!off.brokePlaced(1, 1, 1));
    }
}
