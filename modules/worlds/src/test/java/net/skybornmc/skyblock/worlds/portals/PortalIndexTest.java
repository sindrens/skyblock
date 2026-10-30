package net.skybornmc.skyblock.worlds.portals;

import net.skybornmc.skyblock.common.builds.BuildBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalIndexTest {

    @Test
    void readsEveryPortalOfTheRealBuilds() {
        List<Portal> all = PortalFixtures.realPortals();
        assertEquals(List.of("to_spawn", "to_depths", "to_events", "to_island", "to_outposts", "to_warzone"),
                all.stream().map(Portal::id).toList(), "sorted by build, then id");
        Portal island = PortalFixtures.portal(all, "to_island");
        assertEquals("spawn", island.world());
        assertEquals("island", island.target());
        assertTrue(island.open());
        assertEquals(new BuildBox(-1, 103, -97, 1, 106, -97), island.box());
        assertFalse(PortalFixtures.portal(all, "to_outposts").open(), "status: closed");
        assertFalse(PortalFixtures.portal(all, "to_events").open());
    }

    @Test
    void landingsUseLandingTpOrTheBlockCenterFacingAway() {
        List<Portal> all = PortalFixtures.realPortals();
        Portal depths = PortalFixtures.portal(all, "to_depths");
        assertEquals(new Portal.Pos(-10.5, 102.5, -93.5, -49f, 0f), depths.landing(), "landing_tp with yaw");
        Portal back = PortalFixtures.portal(all, "to_spawn");
        Portal.Pos l = back.landing();
        assertEquals(7.5, l.x());
        assertEquals(100.0, l.y());
        assertEquals(14.5, l.z());
        assertEquals(180f, Math.abs(l.yaw()), 0.01f, "the pad is north of the portal: face north");
    }

    @Test
    void particlesComeFromTheSameBuild() {
        List<Portal> all = PortalFixtures.realPortals();
        Portal.Particle warzone = PortalFixtures.portal(all, "to_warzone").particle();
        assertNotNull(warzone);
        assertEquals("dust", warzone.type());
        assertEquals("#C21F2B", warzone.color());
        assertTrue(warzone.enabled());
        assertEquals("happy_villager", PortalFixtures.portal(all, "to_island").particle().type());
        assertFalse(PortalFixtures.portal(all, "to_outposts").particle().enabled(), "enabled: false");
        Portal.Particle glow = PortalFixtures.portal(all, "to_spawn").particle();
        assertEquals("portal_glow", glow.id(), "matched by position inside the trigger");
        assertEquals("flame", glow.type());
    }

    @Test
    void looksUpTriggersByBlock() {
        PortalIndex index = new PortalIndex(PortalFixtures.realPortals());
        assertEquals("to_island", index.at("spawn", 0, 104, -97).orElseThrow().id());
        assertEquals("to_island", index.at("spawn", -1, 103, -97).orElseThrow().id(), "corner, chunk -1");
        assertEquals("to_island", index.at("spawn", 1, 106, -97).orElseThrow().id(), "corner, chunk 0");
        assertTrue(index.at("spawn", 0, 102, -97).isEmpty(), "threshold block below");
        assertTrue(index.at("spawn", 0, 104, -96).isEmpty(), "in front of the trigger");
        assertTrue(index.at("depths", 0, 104, -97).isEmpty(), "other world");
        assertEquals("to_spawn", index.at("depths", 7, 101, 18).orElseThrow().id());
        assertTrue(index.at("islands", 0, 100, 0).isEmpty());
        assertTrue(index.hasWorld("spawn"));
        assertFalse(index.hasWorld("islands"));
        assertTrue(PortalIndex.EMPTY.at("spawn", 0, 104, -97).isEmpty());
    }

    @Test
    void indexesBoxesAcrossChunks() {
        Portal wide = new Portal("b", "wide", "w", new BuildBox(-20, 0, -20, 20, 3, 20), "spawn", true, null, null);
        PortalIndex index = new PortalIndex(List.of(wide));
        for (int x = -20; x <= 20; x += 5) {
            for (int z = -20; z <= 20; z += 5) {
                assertTrue(index.at("w", x, 1, z).isPresent(), x + "," + z);
            }
        }
        assertTrue(index.at("w", 21, 1, 0).isEmpty());
        assertTrue(index.at("w", -21, 1, 0).isEmpty());
    }

    @Test
    void parsesFlatNumbers() {
        assertEquals(-21.5, Portals.numbers("{x: -21.5, y: 102.5, z: -93.5, yaw: -67, pitch: 0}").get("x"));
        assertEquals(-67.0, Portals.numbers("{x: -21.5, y: 102.5, z: -93.5, yaw: -67, pitch: 0}").get("yaw"));
        assertTrue(Portals.numbers(null).isEmpty());
        assertNull(Portals.numbers("{x: a}").get("x"));
    }
}
