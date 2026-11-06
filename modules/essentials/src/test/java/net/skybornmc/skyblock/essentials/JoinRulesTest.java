package net.skybornmc.skyblock.essentials;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Owner 4 Nov 2026: returning players spawn on their island; new players start at spawn. */
class JoinRulesTest {

    @Test
    void returningPlayersFollowTheSetting() {
        assertEquals(JoinRules.Where.ISLAND, JoinRules.where("island", false));
        assertEquals(JoinRules.Where.LAST, JoinRules.where(" Last ", false));
        assertEquals(JoinRules.Where.SPAWN, JoinRules.where("SPAWN", false));
        assertEquals(JoinRules.Where.ISLAND, JoinRules.where("nonsense", false), "unknown = the default");
        assertEquals(JoinRules.Where.ISLAND, JoinRules.where(null, false));
        assertEquals(JoinRules.ISLAND, new EssentialsConfig().joinLocation);
    }

    @Test
    void newPlayersAlwaysStartAtSpawn() {
        for (String s : new String[]{"island", "last", "spawn"}) {
            assertEquals(JoinRules.Where.SPAWN, JoinRules.where(s, true));
        }
    }
}
