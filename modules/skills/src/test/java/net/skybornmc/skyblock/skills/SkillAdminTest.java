package net.skybornmc.skyblock.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillAdminTest {

    private final SkillCurve curve = new SkillCurve(600, 1.2, 1.13, 35, 1.02, 66, 150);

    @Test
    void setJumpsToTheStartOfTheLevel() {
        SkillAdmin.Result r = SkillAdmin.apply(SkillAdmin.Mode.SET, curve.totalFor(20) + 500, 10, curve);
        assertEquals(10, r.level());
        assertEquals(curve.totalFor(10), r.xp(), 1e-6);
        assertEquals(10, curve.levelFor(r.xp()));
    }

    @Test
    void setIsClampedToTheMaxLevel() {
        SkillAdmin.Result r = SkillAdmin.apply(SkillAdmin.Mode.SET, 0, 400, curve);
        assertEquals(150, r.level());
        assertEquals(curve.totalFor(150), r.xp(), 1e-3);
        assertEquals(0, SkillAdmin.apply(SkillAdmin.Mode.SET, 5_000, 0, curve).level());
    }

    @Test
    void addKeepsProgressInsideTheLevel() {
        double halfWay = curve.totalFor(5) + curve.step(5) / 2;
        SkillAdmin.Result r = SkillAdmin.apply(SkillAdmin.Mode.ADD, halfWay, 3, curve);
        assertEquals(8, r.level());
        assertEquals(0.5, curve.progress(r.xp()), 1e-9);
    }

    @Test
    void addClampsAndCanRemoveLevels() {
        assertEquals(150, SkillAdmin.targetLevel(SkillAdmin.Mode.ADD, 149, 5, 150));
        assertEquals(0, SkillAdmin.targetLevel(SkillAdmin.Mode.ADD, 3, -10, 150));
        assertEquals(150, SkillAdmin.targetLevel(SkillAdmin.Mode.ADD, 140, Integer.MAX_VALUE, 150));
        SkillAdmin.Result max = SkillAdmin.apply(SkillAdmin.Mode.ADD, curve.totalFor(149) + 1, 10, curve);
        assertEquals(150, max.level());
        assertEquals(curve.totalFor(150), max.xp(), 1e-3);
    }
}
