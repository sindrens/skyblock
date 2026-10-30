package net.skybornmc.skyblock.common.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static net.skybornmc.skyblock.common.combat.DefaultCombatTagService.Tag;
import static net.skybornmc.skyblock.common.combat.DefaultCombatTagService.classify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTagsTest {

    private final UUID a = UUID.randomUUID();

    @Test
    void tagLastsItsSecondsAndCountsDown() {
        CombatTags tags = new CombatTags();
        assertEquals(0, tags.remaining(a, 1_000, 10), "never in combat");
        tags.tag(a, 10_000);
        assertEquals(10_000, tags.remaining(a, 10_000, 10));
        assertEquals(4_000, tags.remaining(a, 16_000, 10));
        assertTrue(tags.tagged(a, 19_999, 10));
        assertFalse(tags.tagged(a, 20_000, 10), "exactly N seconds later the tag is over");
        assertEquals(0, tags.remaining(a, 12_000, 0), "0 seconds = no tag");
    }

    @Test
    void newerHitsExtendOlderNeverShorten() {
        CombatTags tags = new CombatTags();
        tags.tag(a, 10_000);
        tags.tag(a, 15_000);
        assertEquals(15_000, tags.last(a));
        tags.tag(a, 12_000); // late event from another thread
        assertEquals(15_000, tags.last(a));
        assertEquals(10_000, tags.remaining(a, 5_000, 10), "a clock that went back never gives more than the full tag");
    }

    @Test
    void forgetAndPrune() {
        CombatTags tags = new CombatTags();
        UUID b = UUID.randomUUID();
        tags.tag(a, 1_000);
        tags.tag(b, 100_000);
        tags.prune(100_000, 60);
        assertEquals(0, tags.last(a));
        assertEquals(1, tags.size());
        tags.forget(b);
        assertEquals(0, tags.size());
    }

    @Test
    void pvpTagsBothPveTagsThePlayer() {
        assertEquals(Tag.BOTH, classify(true, false, true, false, false, true));
        assertEquals(Tag.BOTH, classify(true, false, true, false, false, false), "PvP counts even when mobs don't");
        assertEquals(Tag.VICTIM, classify(false, true, true, false, false, true), "hit by a zombie");
        assertEquals(Tag.ATTACKER, classify(true, false, false, true, false, true), "hit a zombie");
        assertEquals(Tag.NONE, classify(true, false, false, true, false, false), "mobs off");
        assertEquals(Tag.NONE, classify(true, false, false, false, false, true), "hitting a cow is not combat");
        assertEquals(Tag.NONE, classify(true, false, true, false, true, true), "own arrow");
        assertEquals(Tag.NONE, classify(false, true, false, false, false, true), "mob vs mob");
    }
}
