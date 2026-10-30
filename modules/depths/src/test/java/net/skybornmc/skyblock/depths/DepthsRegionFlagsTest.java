package net.skybornmc.skyblock.depths;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.region.FlagRegion;
import net.skybornmc.skyblock.depths.engine.DepthsRegion;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneLayout;
import net.skybornmc.skyblock.depths.zones.ZoneSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepthsRegionFlagsTest {

    private final ZoneSet set = new ZoneLayout(ZoneLayout.Params.defaults()).build("depths");

    @Test
    void fieldsZonesGateAndArenaGetTheirKinds() {
        DepthsRegion field = DepthsRegion.of("mine_t01_ore", "depths", -150, 99, -20, -95, 104, 20, Map.of(), "zones");
        List<FlagRegion> out = DepthsRegionFlags.of("depths", List.of(field), set.zones(), set.hub(), set.arena());
        FlagRegion f = out.getFirst();
        assertEquals("field", f.kind());
        assertEquals(new BuildBox(-150, 99, -20, -95, 104, 20), f.box());
        assertEquals(1 + set.zones().size() + (set.hub() == null ? 0 : 1) + (set.arena() == null ? 0 : 1), out.size());
        Zone combat = set.zones(Aspect.COMBAT).getFirst();
        FlagRegion zone = out.stream().filter(r -> r.id().equals(combat.id())).findFirst().orElseThrow();
        assertEquals("combat_zone", zone.kind());
        assertEquals("depths", zone.world());
        assertEquals(combat.region().minX(), zone.box().minX());
        assertEquals(combat.region().maxY(), zone.box().maxY());
        assertTrue(out.stream().anyMatch(r -> r.kind().equals("mine_zone")));
        assertTrue(out.stream().anyMatch(r -> r.kind().equals("forest_zone")));
        if (set.hub() != null) {
            assertTrue(out.stream().anyMatch(r -> r.kind().equals(DepthsRegionFlags.HUB)));
        }
        if (set.arena() != null) {
            assertTrue(out.stream().anyMatch(r -> r.kind().equals(DepthsRegionFlags.BOSS_ARENA)));
        }
    }

    @Test
    void nothingLoadedMeansNoRegions() {
        assertEquals(List.of(), DepthsRegionFlags.of("depths", List.of(), List.of(), null, null));
    }
}
