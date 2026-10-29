package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.depths.zones.ZoneLayout;
import net.skybornmc.skyblock.depths.zones.ZoneSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DungeonIconsTest {

    private final ZoneSet set = new ZoneLayout(ZoneLayout.Params.defaults()).build("depths");

    @Test
    void wingMapParsesAndSkipsBadEntries() {
        Map<String, String> map = DungeonIcons.wingMap(Arrays.asList("mine=iron_pickaxe", " Forest = oak_log ", "broken", "=x", "y=", null));
        assertEquals(Map.of("mine", "iron_pickaxe", "forest", "oak_log"), map);
        assertEquals(Map.of(), DungeonIcons.wingMap(null));
    }

    @Test
    void zoneIcons() {
        assertEquals("coal_ore", DungeonIcons.zoneIconId(set.zone("mine_t01").orElseThrow(), List.of()));
        assertEquals("oak_log", DungeonIcons.zoneIconId(set.zone("forest_t01").orElseThrow(), List.of()));
        List<String> combat = List.of("zombie_head", "skeleton_skull");
        assertEquals("skeleton_skull", DungeonIcons.zoneIconId(set.zone("combat_t02").orElseThrow(), combat));
        assertNull(DungeonIcons.zoneIconId(set.zone("combat_t03").orElseThrow(), combat), "missing tier: fallback");
    }
}
