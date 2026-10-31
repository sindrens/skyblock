package net.skybornmc.skyblock.production;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PHASE-7 M7.2: lag guard, Asleep islands, generator-made memory and the pillar XP rule (PD8). */
class GeneratorRulesTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    // ------------------------------------------------------------------ lag guard

    @Test
    void lagGuardAllowsFortyRollsPerIslandPerSecond() {
        RollLimiter limiter = new RollLimiter(new GeneratorSettings().maxRollsPerSecond);
        long t = 1_000_000L;
        for (int i = 0; i < 40; i++) {
            assertTrue(limiter.tryRoll(A, t + i), "roll " + (i + 1));
        }
        assertFalse(limiter.tryRoll(A, t + 999), "41st roll in the same second is plain");
        assertTrue(limiter.tryRoll(B, t + 999), "other islands have their own budget");
        assertTrue(limiter.tryRoll(A, t + 1_000), "the next second starts fresh");
    }

    @Test
    void lagGuardPrunesOldIslandsAndFollowsANewMax() {
        RollLimiter limiter = new RollLimiter(2);
        limiter.tryRoll(A, 5_000);
        limiter.tryRoll(B, 6_000);
        limiter.prune(6_500);
        assertEquals(1, limiter.tracked(), "A's second is over");
        limiter.max(0);
        assertFalse(limiter.tryRoll(B, 6_600));
        limiter.forget(B);
        assertEquals(0, limiter.tracked());
    }

    @Test
    void formDecision() {
        AtomicInteger asked = new AtomicInteger();
        assertEquals(GeneratorRules.Form.VANILLA, GeneratorRules.form(false, false, () -> { asked.incrementAndGet(); return true; }));
        assertEquals(GeneratorRules.Form.PLAIN, GeneratorRules.form(true, true, () -> { asked.incrementAndGet(); return true; }),
                "an Asleep island forms plain cobble");
        assertEquals(0, asked.get(), "the lag guard budget is not spent on vanilla or asleep forms");
        assertEquals(GeneratorRules.Form.PLAIN, GeneratorRules.form(true, false, () -> false), "over the lag guard");
        assertEquals(GeneratorRules.Form.ROLL, GeneratorRules.form(true, false, () -> true));
    }

    // ------------------------------------------------------------------ generator-made memory

    @Test
    void memoryRemembersPerIslandAndEachBlockCountsOnce() {
        GeneratorMemory memory = new GeneratorMemory(16);
        memory.remember(A, 10, 64, -20, "coal");
        assertNull(memory.take(B, 10, 64, -20), "another island's block");
        assertNull(memory.take(A, 10, 65, -20), "another position");
        assertEquals("coal", memory.take(A, 10, 64, -20));
        assertNull(memory.take(A, 10, 64, -20), "taken once");
    }

    @Test
    void placedOrReformedPositionsAreForgottenOrReplaced() {
        GeneratorMemory memory = new GeneratorMemory(16);
        memory.remember(A, 1, 2, 3, "iron");
        memory.forget(A, 1, 2, 3); // a player placed a block there
        assertNull(memory.take(A, 1, 2, 3));
        memory.remember(A, 1, 2, 3, "iron");
        memory.remember(A, 1, 2, 3, "diamond"); // the generator formed again
        assertEquals("diamond", memory.take(A, 1, 2, 3));
        assertEquals(0, memory.size(A));
    }

    @Test
    void memoryIsBoundedPerIslandAndClearedWithTheIsland() {
        GeneratorMemory memory = new GeneratorMemory(3);
        for (int i = 0; i < 5; i++) {
            memory.remember(A, i, 0, 0, "coal");
        }
        assertEquals(3, memory.size(A));
        assertNull(memory.take(A, 0, 0, 0), "oldest forgotten first");
        assertEquals("coal", memory.take(A, 4, 0, 0));
        memory.remember(B, 0, 0, 0, "coal");
        memory.clear(B);
        assertEquals(0, memory.size(B));
        new GeneratorMemory(0).remember(A, 0, 0, 0, "coal"); // capacity 0 = off, no error
    }

    @Test
    void negativeAndLargeCoordinatesDontCollide() {
        GeneratorMemory memory = new GeneratorMemory(16);
        memory.remember(A, -1, 64, 1, "coal");
        memory.remember(A, 1, 64, -1, "iron");
        memory.remember(A, 30_000_000, -64, -30_000_000, "gold");
        assertEquals("coal", memory.take(A, -1, 64, 1));
        assertEquals("iron", memory.take(A, 1, 64, -1));
        assertEquals("gold", memory.take(A, 30_000_000, -64, -30_000_000));
    }

    // ------------------------------------------------------------------ pillar XP (PD8)

    @Test
    void pillarXpIsAQuarterOfTheDepthsOreOnlyForGeneratorMadeOresOfCountingMembers() {
        GeneratorSettings s = new GeneratorSettings();
        GeneratorTable t = GeneratorTable.parse(s.ores, s.levels, p -> { });
        double pct = s.pillarXpPercent;
        assertEquals(25, pct, 1e-9);
        assertEquals(0.25, GeneratorRules.pillarXp(t.ores().get("coal"), true, true, pct), 1e-9);
        assertEquals(3.25, GeneratorRules.pillarXp(t.ores().get("diamond"), true, true, pct), 1e-9);
        assertEquals(7.0, GeneratorRules.pillarXp(t.ores().get("ashstone"), true, true, pct), 1e-9);
        assertEquals(10.0, GeneratorRules.pillarXp(t.ores().get("emberite"), true, true, pct), 1e-9);
        assertEquals(0, GeneratorRules.pillarXp(t.ores().get("coal"), false, true, pct), "placed or moved ore");
        assertEquals(0, GeneratorRules.pillarXp(t.ores().get("coal"), true, false, pct), "coop player or visitor");
        assertEquals(0, GeneratorRules.pillarXp(t.ores().get("cobble"), true, true, pct), "cobblestone is no ore");
        assertEquals(0, GeneratorRules.pillarXp(t.ores().get("coal"), true, true, 0), "switched off");
        assertEquals(0, GeneratorRules.pillarXp(null, true, true, pct));
    }

    @Test
    void depthsXpMatchesTheDepthsMiningTable() {
        // Same numbers as the Depths ore XP (SkillTables / WORLD §3): coal 1 … quartz 40, Ashstone = amethyst, Emberite = quartz.
        GeneratorSettings s = new GeneratorSettings();
        GeneratorTable t = GeneratorTable.parse(s.ores, s.levels, new ArrayList<String>()::add);
        double[] xp = {1, 1.5, 2.5, 4, 6, 9, 13, 19, 28, 40};
        String[] ids = {"coal", "copper", "iron", "gold", "redstone", "lapis", "diamond", "emerald", "ashstone", "emberite"};
        for (int i = 0; i < ids.length; i++) {
            assertEquals(xp[i], t.ores().get(ids[i]).depthsXp(), 1e-9, ids[i]);
            assertEquals(i + 1, t.ores().get(ids[i]).tier(), ids[i] + " tier");
        }
    }
}
