package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.worlds.WorldsConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PasteOrderTest {

    @Test
    void spawnBaseFirstThenDistrictsThenTheGateThenZones() {
        List<String> ids = List.of("depths_mine_t02_copper_v1", "spawn_market_square_v1", "depths_combat_t01_graveyard_v1",
                "depths_gate_v1", "spawn_base_v1", "zzz_other_v1", "depths_mine_t01_coal_v1", "spawn_forge_quarter_v1",
                "depths_forest_t01_oak_v1");
        assertEquals(List.of("spawn_base_v1", "spawn_forge_quarter_v1", "spawn_market_square_v1", "depths_gate_v1",
                "depths_mine_t01_coal_v1", "depths_mine_t02_copper_v1", "depths_forest_t01_oak_v1", "depths_combat_t01_graveyard_v1",
                "zzz_other_v1"), PasteOrder.sort(ids, new WorldsConfig().pasteOrder));
    }
}
