package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.sell.Compact;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 7 Nov 2026 (OWNER_PICKS_7NOV.md): Auto-Compactor, Top Tools. */
class OwnerPicksTest {

    @Test
    void theCompactorIsAOneLevelPerkOfTheHoeAndPickaxe() {
        IslandToolsConfig c = new IslandToolsConfig();
        List<String> problems = new ArrayList<>();
        Upgrades u = Upgrades.parse(c.perks, problems::add);
        assertEquals(List.of(), problems);
        for (Kind k : List.of(Kind.HOE, Kind.PICKAXE)) {
            Upgrades.Def d = u.byEffect(k, Upgrades.Effect.COMPACT).orElseThrow();
            assertEquals(1, d.max());
            assertEquals(15, d.unlock());
        }
        assertTrue(u.byEffect(Kind.ROD, Upgrades.Effect.COMPACT).isEmpty());
        assertEquals(Material.HAY_BLOCK, Compact.BLOCK_OF.get(Material.WHEAT));
        assertEquals(Material.IRON_BLOCK, Compact.BLOCK_OF.get(Material.IRON_INGOT));
        assertEquals(9, Compact.SIZE);
        assertEquals(50, c.rebirthBoardWeight);
        assertEquals(17, c.version());
    }

    @Test
    void skinsAreVanillaLooksBoughtWithCredits() {
        List<String> problems = new ArrayList<>();
        List<ToolSkins.Skin> skins = ToolSkins.parse(new IslandToolsConfig().skins, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(List.of("iron", "golden", "copper", "netherite"), skins.stream().map(ToolSkins.Skin::id).toList());
        assertEquals(Material.NETHERITE_HOE, skins.get(3).material(Kind.HOE));
        assertEquals(Material.GOLDEN_SWORD, skins.get(1).material(Kind.SWORD));
        assertEquals(1000, skins.get(3).price());
        assertEquals(1, ToolSkins.parse(List.of("iron:250", "rainbow:5", "golden"), problems::add).size());
        assertEquals(2, problems.size());
    }
}
