package net.skybornmc.skyblock.depths.combat;

import net.skybornmc.skyblock.common.mob.MobDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobRewardsTest {

    private static MobDef mob(double crystalChance, MobDef.Drop... drops) {
        return new MobDef("m", "zombie", 20, 2, 0, 1, 0, 5, 5, crystalChance, 1, List.of(drops), false, false);
    }

    @Test
    void averagesMatchTheDefinition() {
        MobDef m = mob(0.26, new MobDef.Drop("flesh", 3, 5, 1.0), new MobDef.Drop("bone", 1, 1, 0.1));
        SplittableRandom rng = new SplittableRandom(42);
        long flesh = 0;
        long bone = 0;
        long crystals = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            MobRewards.Roll r = MobRewards.roll(m, 1.0, 1.0, 3.0, rng);
            flesh += r.items().getOrDefault("flesh", 0);
            bone += r.items().getOrDefault("bone", 0);
            crystals += r.crystals();
            assertEquals(5, r.xp());
        }
        assertEquals(4.0, flesh / (double) n, 0.03);
        assertEquals(0.1, bone / (double) n, 0.01);
        assertEquals(0.26, crystals / (double) n, 0.01);
    }

    @Test
    void bonusesMultiplyAndAreCapped() {
        MobDef m = mob(2.5, new MobDef.Drop("flesh", 4, 4, 1.0));
        SplittableRandom rng = new SplittableRandom(1);
        MobRewards.Roll doubled = MobRewards.roll(m, 2.0, 1.0, 3.0, rng);
        assertEquals(8, doubled.items().get("flesh"));
        MobRewards.Roll capped = MobRewards.roll(m, 50.0, 1.0, 3.0, rng);
        assertEquals(12, capped.items().get("flesh"), "looting capped at x3");
        MobRewards.Roll noBonus = MobRewards.roll(m, 0.2, 1.0, 3.0, rng);
        assertEquals(4, noBonus.items().get("flesh"), "below 1 counts as none");
        long crystals = 0;
        for (int i = 0; i < 10_000; i++) {
            crystals += MobRewards.roll(m, 1.0, 2.0, 3.0, rng).crystals();
        }
        assertEquals(5.0, crystals / 10_000.0, 0.05, "chance above 1 = several hits, crystal bonus doubles");
        assertEquals(0, MobRewards.roll(m, 1.0, 0.0, 3.0, rng).crystals());
    }

    @Test
    void damageSharesPickWhoFought() {
        // R3-10 (BossFight replaced DamageShares): the killer needs the 10% share like everyone else.
        UUID killer = UUID.randomUUID();
        UUID helper = UUID.randomUUID();
        UUID tourist = UUID.randomUUID();
        BossFight s = new BossFight(300);
        s.hit(helper, "1", 120, 0);
        s.hit(tourist, "2", 10, 0);
        s.hit(killer, "3", 5, 0);
        s.hit(tourist, "2", -50, 0);
        s.hit(null, "4", 99, 0);
        assertEquals(135, s.total(), 1e-9);
        assertEquals(List.of(helper), s.eligible(0, 300, 0.1, 90_000, 3, id -> false), "killer and tourist below 10% of 300");
        assertFalse(s.eligible(0, 300, 0.1, 90_000, 3, id -> false).contains(tourist));
        assertTrue(s.eligible(0, 300, 0.0, 90_000, 3, id -> false).contains(tourist), "0 = everyone who hit it");
    }

    /** M8.1: a loot factor of 1 gives exactly the old roll (same random numbers, same result). */
    @Test
    void lootFactorOneIsTheOldRoll() {
        MobDef m = mob(0.7, new MobDef.Drop("flesh", 3, 5, 1.0), new MobDef.Drop("bone", 1, 2, 0.3));
        for (long seed = 0; seed < 200; seed++) {
            MobRewards.Roll old = MobRewards.roll(m, 1.4, 1.2, 3.0, new java.util.SplittableRandom(seed));
            MobRewards.Roll same = MobRewards.roll(m, 1.4, 1.2, 3.0, 1.0, new java.util.SplittableRandom(seed));
            assertEquals(old, same);
        }
    }

    /** M8.1: an elite factor scales items and crystals on average. */
    @Test
    void lootFactorScalesTheAverage() {
        MobDef m = mob(2.0, new MobDef.Drop("flesh", 4, 4, 1.0));
        var rng = new java.util.SplittableRandom(3);
        long items = 0;
        long crystals = 0;
        int n = 20_000;
        for (int i = 0; i < n; i++) {
            MobRewards.Roll r = MobRewards.roll(m, 1.0, 1.0, 3.0, 0.5, rng);
            items += r.items().getOrDefault("flesh", 0);
            crystals += r.crystals();
        }
        assertEquals(2.0, items / (double) n, 0.05);
        assertEquals(1.0, crystals / (double) n, 0.05);
    }
}
