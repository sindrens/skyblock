package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.skills.SkillType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** XP formatting, progress bars and the action bar rate limit. */
class XpMathTest {

    @Test
    void formatsXp() {
        assertEquals("1", XpFormat.xp(1));
        assertEquals("1.5", XpFormat.xp(1.5));
        assertEquals("2.5", XpFormat.xp(2.55));
        assertEquals("0", XpFormat.xp(0));
        assertEquals("999.9", XpFormat.xp(999.99));
        assertEquals("1.2k", XpFormat.xp(1_250));
        assertEquals("119.5M", XpFormat.xp(119_530_000));
        assertEquals("?", XpFormat.xp(Double.NaN));
    }

    @Test
    void percentAndBar() {
        assertEquals(0, XpFormat.percent(0));
        assertEquals(0, XpFormat.percent(Double.NaN));
        assertEquals(68, XpFormat.percent(0.689));
        assertEquals(99, XpFormat.percent(0.999));
        assertEquals(100, XpFormat.percent(1.0));
        assertEquals(29, XpFormat.percent(0.29), "no floating point drop to 28");
        assertEquals(0, XpFormat.filled(0.09, 10));
        assertEquals(6, XpFormat.filled(0.62, 10));
        assertEquals(3, XpFormat.filled(0.3, 10));
        assertEquals(10, XpFormat.filled(1.0, 10));
        assertEquals(10, XpFormat.filled(5.0, 10));
        assertEquals(0, XpFormat.filled(0.5, 0));
    }

    @Test
    void actionBarIsRateLimitedAndSumsGains() {
        GainBuffer buffer = new GainBuffer();
        UUID p = UUID.randomUUID();
        buffer.add(p, SkillType.MINING, 1.5);
        assertEquals(Map.of(SkillType.MINING, 1.5), buffer.poll(p, 10_000, 1_500), "first gain shows at once");
        buffer.add(p, SkillType.MINING, 2.5);
        buffer.add(p, SkillType.MINING, 2.5);
        buffer.add(p, SkillType.COMBAT, 5);
        assertTrue(buffer.poll(p, 11_000, 1_500).isEmpty(), "too soon");
        assertEquals(1, buffer.waiting().size());
        assertEquals(Map.of(SkillType.MINING, 5.0, SkillType.COMBAT, 5.0), buffer.poll(p, 11_500, 1_500));
        assertTrue(buffer.waiting().isEmpty());
        assertTrue(buffer.poll(p, 20_000, 1_500).isEmpty(), "nothing pending");
        buffer.add(p, SkillType.MINING, 0);
        buffer.add(p, SkillType.MINING, -3);
        assertTrue(buffer.waiting().isEmpty(), "no empty gains");
        buffer.add(p, SkillType.MINING, 1);
        buffer.remove(p);
        assertTrue(buffer.waiting().isEmpty());
    }
}
