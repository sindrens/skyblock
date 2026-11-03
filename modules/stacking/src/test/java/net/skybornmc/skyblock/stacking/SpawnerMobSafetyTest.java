package net.skybornmc.skyblock.stacking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerMobSafetyTest {

    @Test
    void spawnerMobsNeverHurtPlayersByDefault() {
        assertTrue(SpawnerMobSafety.blocks(true, true, false));
        assertFalse(SpawnerMobSafety.blocks(true, true, true), "the owner can turn damage back on");
        assertFalse(SpawnerMobSafety.blocks(false, true, false), "natural and Depths mobs are untouched");
        assertFalse(SpawnerMobSafety.blocks(true, false, false), "only players are protected");
    }

    @Test
    void theDefaultIsNoDamage() {
        assertFalse(new StackingConfig().spawnerMobsHurtPlayers);
    }
}
