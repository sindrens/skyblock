package net.skybornmc.skyblock.depths.drops;

import net.skybornmc.skyblock.depths.engine.PaletteMath;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DV2-SPIKE (D2): a slot of the coal field gives the drops of the tier the player sees, from the shipped tables. */
class ShownTierDropsTest {

    private static DropTable shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (var in = ShownTierDropsTest.class.getResourceAsStream("/depths-drops-defaults.yml")) {
            assertNotNull(in);
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> problems = new ArrayList<>();
        Set<String> ids = y.getConfigurationSection(DropSections.ITEMS).getKeys(false);
        DropTable t = DropTable.parse(y.getConfigurationSection(DropSections.DROPS), ids, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        return t;
    }

    @Test
    void eachShownTierHasItsOwnRewards() throws Exception {
        DropTable t = shipped();
        int zoneTier = 1; // the pilot coal field
        assertEquals("depths_coal", t.get("mine", PaletteMath.rewardTier(1, zoneTier)).item());
        assertEquals("depths_copper", t.get("mine", PaletteMath.rewardTier(2, zoneTier)).item());
        DropTable.TierDrops iron = t.get("mine", PaletteMath.rewardTier(3, zoneTier));
        assertEquals("depths_iron", iron.item());
        assertTrue(iron.crystalChance() > t.get("mine", 1).crystalChance());
        assertEquals("depths_coal", t.get("mine", PaletteMath.rewardTier(0, zoneTier)).item()); // palette off
    }
}
