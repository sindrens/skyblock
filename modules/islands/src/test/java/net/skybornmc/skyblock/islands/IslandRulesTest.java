package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandRulesTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void disbandOnlyByTheLeaderOfAnIslandWithoutOtherMembers() {
        // Owner answer 141: promote or hand over first; a solo leader may disband.
        assertTrue(IslandLifecycle.refusal(net.skybornmc.skyblock.common.island.IslandService.Role.LEADER, 1).isEmpty());
        assertEquals("disband.has-members",
                IslandLifecycle.refusal(net.skybornmc.skyblock.common.island.IslandService.Role.LEADER, 2).orElseThrow());
        assertEquals("disband.leader-only",
                IslandLifecycle.refusal(net.skybornmc.skyblock.common.island.IslandService.Role.ADMIN, 1).orElseThrow());
        assertEquals("disband.leader-only", IslandLifecycle.refusal(null, 3).orElseThrow());
    }

    @Test
    void teamIsFourMembersPlusTheLeaderAndNormalSizesStopAtNine() {
        IslandsConfig config = new IslandsConfig();
        assertEquals(5, config.startSlots, "owner answer 242: 4 members plus the leader");
        assertEquals(9, config.maxSlots);
        assertEquals(9, config.normalMaxSize(), "owner answer 157: normal upgrades reach 9x9");
        assertEquals(15, config.maxSize(), "bigger only with gems or the black market");
        assertTrue(config.removedKeys().containsKey("upgrades.island-bank-only"), "owner answer 169: always the island bank, no switch");
        config.normalMaxChunks = 21;
        assertEquals(15, config.normalMaxSize(), "never above the absolute cap");

        org.bukkit.configuration.file.YamlConfiguration old = new org.bukkit.configuration.file.YamlConfiguration();
        old.set("config-version", 1);
        old.set("members.start-slots", 4);
        old.set("members.max-slots", 8);
        assertEquals(2, IslandsConfig.upgradeValues(old).size());
        assertEquals(5, old.getInt("members.start-slots"));
        assertEquals(9, old.getInt("members.max-slots"));
        org.bukkit.configuration.file.YamlConfiguration chosen = new org.bukkit.configuration.file.YamlConfiguration();
        chosen.set("members.start-slots", 6);
        assertTrue(IslandsConfig.upgradeValues(chosen).isEmpty(), "an owner-chosen value stays");
        assertEquals(6, chosen.getInt("members.start-slots"));
        org.bukkit.configuration.file.YamlConfiguration current = new org.bukkit.configuration.file.YamlConfiguration();
        current.set("config-version", 2);
        current.set("members.start-slots", 4);
        assertTrue(IslandsConfig.upgradeValues(current).isEmpty(), "already version 2: 4 was chosen");
    }

    @Test
    void sizesAreForcedOddAndStartNeverExceedsMax() {
        IslandsConfig config = new IslandsConfig();
        assertEquals(3, config.startSize());
        assertEquals(15, config.maxSize());
        assertTrue(config.sizeProblems().isEmpty());
        config.startChunks = 4;
        config.maxChunks = 16;
        assertEquals(3, config.startSize());
        assertEquals(15, config.maxSize());
        assertEquals(1, config.sizeProblems().size());
        config.startChunks = 9;
        config.maxChunks = 5;
        assertEquals(5, config.startSize());
        assertEquals(1, config.sizeProblems().size());
        config.maxChunks = 99;
        assertEquals(31, config.maxSize());
    }
}
