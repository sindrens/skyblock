package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.skills.SkillType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSkillsTest {

    private final SkillCurve curve = new SkillCurve(600, 1.2, 1.13, 35, 66);
    private final UUID id = UUID.randomUUID();

    @Test
    void gainsLevelUpAndBecomeDirty() {
        PlayerSkills p = PlayerSkills.loaded(id, Map.of(), curve);
        assertFalse(p.hasDirty());
        assertNull(p.add(SkillType.MINING, 599, curve));
        PlayerSkills.LevelChange change = p.add(SkillType.MINING, 1.5, curve);
        assertEquals(new PlayerSkills.LevelChange(SkillType.MINING, 0, 1), change);
        assertEquals(600.5, p.xp(SkillType.MINING), 1e-9);
        assertTrue(p.hasDirty());
        List<PlayerSkills.Row> rows = p.drainDirty();
        assertEquals(List.of(new PlayerSkills.Row(id, SkillType.MINING, 600.5, 1)), rows);
        assertFalse(p.hasDirty());
    }

    @Test
    void oneGainCanJumpSeveralLevels() {
        PlayerSkills p = PlayerSkills.loaded(id, Map.of(SkillType.COMBAT, 100.0), curve);
        PlayerSkills.LevelChange change = p.add(SkillType.COMBAT, curve.totalFor(10), curve);
        assertEquals(0, change.from());
        assertEquals(10, change.to());
    }

    @Test
    void ignoresInvalidAmounts() {
        PlayerSkills p = PlayerSkills.loaded(id, Map.of(), curve);
        assertNull(p.add(SkillType.MINING, 0, curve));
        assertNull(p.add(SkillType.MINING, -10, curve));
        assertNull(p.add(SkillType.MINING, Double.NaN, curve));
        assertNull(p.add(SkillType.MINING, Double.POSITIVE_INFINITY, curve));
        assertEquals(0, p.xp(SkillType.MINING));
        assertFalse(p.hasDirty());
    }

    @Test
    void gainsWhileNotLoadedAreMergedOntoStoredXp() {
        PlayerSkills p = new PlayerSkills(id);
        assertNull(p.add(SkillType.MINING, 200, curve), "no levels before loading");
        assertEquals(0, p.level(SkillType.MINING));
        assertTrue(p.drainDirty().isEmpty(), "nothing absolute to save while not loaded");
        assertEquals(Map.of(SkillType.MINING, 200.0), p.deltas());

        List<PlayerSkills.LevelChange> changes = p.merge(Map.of(SkillType.MINING, 500.0, SkillType.COMBAT, 50.0), curve);
        assertTrue(p.isLoaded());
        assertEquals(700, p.xp(SkillType.MINING), 1e-9);
        assertEquals(50, p.xp(SkillType.COMBAT), 1e-9);
        assertEquals(List.of(new PlayerSkills.LevelChange(SkillType.MINING, 0, 1)), changes);
        List<PlayerSkills.Row> rows = p.drainDirty();
        assertEquals(1, rows.size(), "only the skill with a delta is written");
        assertEquals(700, rows.getFirst().xp(), 1e-9);
        assertTrue(p.merge(Map.of(SkillType.MINING, 1.0), curve).isEmpty(), "a second merge does nothing");
        assertEquals(700, p.xp(SkillType.MINING), 1e-9);
    }

    @Test
    void failedSaveMarksRowsDirtyAgain() {
        PlayerSkills p = PlayerSkills.loaded(id, Map.of(), curve);
        p.add(SkillType.FORAGING, 10, curve);
        List<PlayerSkills.Row> rows = p.drainDirty();
        assertFalse(p.hasDirty());
        p.markDirty(rows);
        assertEquals(rows, p.drainDirty());
    }

    @Test
    void staffSetCanLowerLevels() {
        PlayerSkills p = PlayerSkills.loaded(id, Map.of(SkillType.MINING, curve.totalFor(12)), curve);
        assertEquals(12, p.level(SkillType.MINING));
        PlayerSkills.LevelChange change = p.set(SkillType.MINING, 0, curve);
        assertEquals(new PlayerSkills.LevelChange(SkillType.MINING, 12, 0), change);
        assertNull(p.set(SkillType.MINING, -1, curve));
        assertNull(new PlayerSkills(id).set(SkillType.MINING, 10, curve), "not before loading");
    }

    @Test
    void curveChangeRecomputesLevels() {
        PlayerSkills p = PlayerSkills.loaded(id, Map.of(SkillType.MINING, 1_000.0), curve);
        assertEquals(1, p.level(SkillType.MINING));
        p.recompute(new SkillCurve(100, 1.0, 1.0, 0, 66));
        assertEquals(10, p.level(SkillType.MINING));
        assertTrue(p.hasDirty(), "stored level follows");
    }
}
