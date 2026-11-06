package net.skybornmc.skyblock.common.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Plan T1: one clean action bar line; higher priority wins, equal priority joins, an old line gives way. */
class ActionBarsTest {

    @Test
    void priorityDecides() {
        ActionBars.Hold payout = new ActionBars.Hold(ActionBars.PAYOUT, 2_000);
        assertEquals(ActionBars.Decision.REPLACE, ActionBars.decide(null, ActionBars.XP, 1_000));
        assertEquals(ActionBars.Decision.DROP, ActionBars.decide(payout, ActionBars.XP, 1_000), "XP waits while a payout shows");
        assertEquals(ActionBars.Decision.MERGE, ActionBars.decide(payout, ActionBars.PAYOUT, 1_000), "shards and bonus share a line");
        assertEquals(ActionBars.Decision.REPLACE, ActionBars.decide(payout, ActionBars.WARNING, 1_000), "a warning wins");
        assertEquals(ActionBars.Decision.REPLACE, ActionBars.decide(payout, ActionBars.XP, 2_000), "an expired line gives way");
    }
}
