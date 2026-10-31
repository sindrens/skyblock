package net.skybornmc.skyblock.enchants;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R3-24: the live enchants.yml loses Soul Reaper's Combat XP values once and gets the sword XP values. */
class SoulReaperUpgradeTest {

    private static final String BASE = "enchants.soul_reaper";

    private static YamlConfiguration old() {
        YamlConfiguration y = new YamlConfiguration();
        y.set(BASE + ".family", "skill_xp");
        y.set(BASE + ".values.xp", List.of(10, 20, 30, 40, 50));
        y.set(BASE + ".values.skill", List.of(6, 12, 18, 24, 30));
        return y;
    }

    @Test
    void oldLiveFileIsUpgradedOnce() {
        YamlConfiguration live = old();
        YamlConfiguration bundled = Fixtures.resource("enchants.yml");
        assertTrue(EnchantsModule.upgradeSoulReaper(live, bundled));
        assertEquals("tool_xp", live.getString(BASE + ".family"));
        assertEquals(List.of(10, 20, 30, 40, 50), live.getIntegerList(BASE + ".values.tool"));
        assertFalse(live.contains(BASE + ".values.skill"));
        assertEquals(List.of(10, 20, 30, 40, 50), live.getIntegerList(BASE + ".values.xp"), "vanilla XP values stay");
        assertFalse(EnchantsModule.upgradeSoulReaper(live, bundled), "second start: nothing to do");
    }

    @Test
    void ownersToolNumbersAreKept() {
        YamlConfiguration live = old();
        live.set(BASE + ".values.tool", List.of(1, 2, 3, 4, 5));
        assertFalse(EnchantsModule.upgradeSoulReaper(live, Fixtures.resource("enchants.yml")));
        assertEquals(List.of(1, 2, 3, 4, 5), live.getIntegerList(BASE + ".values.tool"));
    }
}
