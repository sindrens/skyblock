package net.skybornmc.skyblock.islands;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QUICK1 island auto-pickup: who it applies to and the rate-limited "inventory full" hint. */
class IslandAutoPickupTest {

    @Test
    void onlyMembersInSurvivalOrAdventure() {
        assertTrue(IslandAutoPickup.eligible(GameMode.SURVIVAL, true, false));
        assertTrue(IslandAutoPickup.eligible(GameMode.ADVENTURE, true, false));
        assertFalse(IslandAutoPickup.eligible(GameMode.SURVIVAL, false, false), "visitors and coops keep normal drops");
        assertFalse(IslandAutoPickup.eligible(GameMode.CREATIVE, true, false), "staff building in creative");
        assertFalse(IslandAutoPickup.eligible(GameMode.SPECTATOR, true, false));
        assertFalse(IslandAutoPickup.eligible(GameMode.SURVIVAL, true, true), "never while the profile switches");
    }

    @Test
    void nothingLeftWhenEverythingFit() {
        assertEquals(0, IslandAutoPickup.leftover(Map.of()));
    }

    @Test
    void fullHintIsRateLimited() {
        assertTrue(IslandAutoPickup.hintDue(null, 1_000, 10_000));
        assertFalse(IslandAutoPickup.hintDue(1_000L, 10_999, 10_000));
        assertTrue(IslandAutoPickup.hintDue(1_000L, 11_000, 10_000));
        assertFalse(IslandAutoPickup.hintDue(null, 1_000, 0), "0 = never");
    }
}
