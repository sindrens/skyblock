package net.skybornmc.skyblock.worlds.portals;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalResolverTest {

    private final List<Portal> all = PortalFixtures.realPortals();
    private final PortalFixtures.FakeWorld world = PortalFixtures.FakeWorld.standard();

    private PortalResolver.Destination resolve(String id, PortalSettings settings, PortalResolver.World w) {
        return PortalResolver.resolve(PortalFixtures.portal(all, id), all, settings, w);
    }

    private PortalResolver.Destination resolve(String id) {
        return resolve(id, PortalSettings.DEFAULTS, world);
    }

    @Test
    void islandPortalIsTheGatehouseToIsland() {
        assertInstanceOf(PortalResolver.Island.class, resolve("to_island"));
        long islands = all.stream().filter(p -> PortalSettings.DEFAULTS.isIslandPortal(p)).count();
        assertEquals(1, islands, "exactly one island portal in the real builds");
    }

    @Test
    void depthsPortalLandsOnTheGateArrivalPad() {
        PortalResolver.Teleport t = assertInstanceOf(PortalResolver.Teleport.class, resolve("to_depths"));
        assertEquals("depths", t.world());
        assertEquals("depths", t.target());
        assertEquals(7.5, t.pos().x());
        assertEquals(100.0, t.pos().y());
        assertEquals(14.5, t.pos().z());
    }

    @Test
    void depthsReturnPortalLandsInFrontOfTheGatehouseDepthsPortal() {
        PortalResolver.Teleport t = assertInstanceOf(PortalResolver.Teleport.class, resolve("to_spawn"));
        assertEquals("spawn", t.world());
        assertEquals(new Portal.Pos(-10.5, 102.5, -93.5, -49f, 0f), t.pos());
    }

    @Test
    void warzoneWaitsForItsBuild() {
        PortalResolver.Refused r = assertInstanceOf(PortalResolver.Refused.class, resolve("to_warzone"));
        assertEquals(PortalResolver.Reason.NOT_BUILT, r.reason());
        PortalFixtures.FakeWorld built = new PortalFixtures.FakeWorld(world.loaded(), world.spawns(), Set.of("spawn", "depths", "warzone"),
                Map.of());
        PortalResolver.Teleport t = assertInstanceOf(PortalResolver.Teleport.class, resolve("to_warzone", PortalSettings.DEFAULTS, built));
        assertEquals("warzone", t.world());
        assertEquals(world.spawns().get("warzone"), t.pos(), "no return portal in the warzone yet: world spawn");
        PortalSettings noRule = PortalSettings.of("", List.of(), List.of(), List.of(), List.of("warzone=warzone", "spawn=spawn", "depths=depths"),
                List.of());
        assertInstanceOf(PortalResolver.Teleport.class, resolve("to_warzone", noRule, world));
    }

    @Test
    void closedAndUnknownTargets() {
        PortalResolver.Refused closed = assertInstanceOf(PortalResolver.Refused.class, resolve("to_outposts"));
        assertEquals(PortalResolver.Reason.CLOSED, closed.reason());
        assertEquals("outposts", closed.target());
        PortalSettings open = PortalSettings.of("", List.of("to_outposts"), List.of(), List.of(), List.of("spawn=spawn"), List.of());
        PortalResolver.Refused unknown = assertInstanceOf(PortalResolver.Refused.class, resolve("to_outposts", open, world));
        assertEquals(PortalResolver.Reason.UNKNOWN, unknown.reason(), "opened, but outposts aren't a target yet");
        PortalSettings disabled = PortalSettings.of("", List.of(), List.of("spawn_gatehouse_v1:to_depths"), List.of(), List.of("depths=depths"),
                List.of());
        assertEquals(PortalResolver.Reason.CLOSED,
                assertInstanceOf(PortalResolver.Refused.class, resolve("to_depths", disabled, world)).reason());
    }

    @Test
    void missingWorldIsRefused() {
        PortalFixtures.FakeWorld noDepths = new PortalFixtures.FakeWorld(Set.of("spawn"), world.spawns(), world.built(), Map.of());
        assertEquals(PortalResolver.Reason.NO_WORLD,
                assertInstanceOf(PortalResolver.Refused.class, resolve("to_depths", PortalSettings.DEFAULTS, noDepths)).reason());
    }

    @Test
    void overridesAndPointTargets() {
        PortalResolver.Located statue = new PortalResolver.Located("spawn", new Portal.Pos(3.5, 101, 4.5, 90, 0));
        PortalFixtures.FakeWorld withPoint = new PortalFixtures.FakeWorld(world.loaded(), world.spawns(), world.built(),
                Map.of("viewpoints:statue", statue));
        PortalSettings s = PortalSettings.of("", List.of(), List.of(), List.of("to_depths=point:viewpoints:statue", "to_island=spawn"),
                List.of("spawn=spawn", "depths=depths"), List.of());
        PortalResolver.Teleport t = assertInstanceOf(PortalResolver.Teleport.class, resolve("to_depths", s, withPoint));
        assertEquals(statue.pos(), t.pos());
        assertEquals("statue", t.target());
        PortalResolver.Teleport island = assertInstanceOf(PortalResolver.Teleport.class, resolve("to_island", s, withPoint));
        assertEquals("spawn", island.world(), "an override away from island wins");
        PortalSettings missing = PortalSettings.of("", List.of(), List.of(), List.of("to_depths=point:viewpoints:nope"), List.of(), List.of());
        assertEquals(PortalResolver.Reason.UNKNOWN,
                assertInstanceOf(PortalResolver.Refused.class, resolve("to_depths", missing, withPoint)).reason());
        PortalSettings worldTarget = PortalSettings.of("", List.of(), List.of(), List.of("to_depths=world:islands"), List.of(), List.of());
        PortalResolver.Teleport w = assertInstanceOf(PortalResolver.Teleport.class, resolve("to_depths", worldTarget,
                new PortalFixtures.FakeWorld(world.loaded(), Map.of("islands", new Portal.Pos(0.5, 100, 0.5, 0, 0)), world.built(), Map.of())));
        assertEquals("islands", w.world());
    }

    @Test
    void islandPortalCanBeConfigured() {
        PortalSettings s = PortalSettings.of("spawn_gatehouse_v1:to_depths", List.of(), List.of(), List.of(), List.of("depths=depths"), List.of());
        assertInstanceOf(PortalResolver.Island.class, resolve("to_depths", s, world));
        assertInstanceOf(PortalResolver.Island.class, resolve("to_island", s, world), "target island still goes to the island");
        Portal start = new Portal("b", "start_portal", "spawn", PortalFixtures.portal(all, "to_island").box(), "", true, null, null);
        assertTrue(PortalSettings.DEFAULTS.isIslandPortal(start), "auto: id containing start, no target");
        Portal startWithTarget = new Portal("b", "start_portal", "spawn", start.box(), "depths", true, null, null);
        assertFalse(PortalSettings.DEFAULTS.isIslandPortal(startWithTarget), "a metadata target wins over the id guess");
        assertFalse(s.isIslandPortal(start), "configured: only the named portal (and island targets)");
    }

    @Test
    void warzoneOnlyThroughItsPortal() {
        assertTrue(PortalResolver.deniesWarzone(true, "warzone", "warzone", "spawn", false), "/tpa, /spawn into the warzone");
        assertFalse(PortalResolver.deniesWarzone(true, "warzone", "warzone", "spawn", true), "the portal's own teleport");
        assertFalse(PortalResolver.deniesWarzone(true, "warzone", "warzone", "warzone", false), "moving inside the warzone");
        assertFalse(PortalResolver.deniesWarzone(true, "warzone", "spawn", "warzone", false), "leaving the warzone");
        assertFalse(PortalResolver.deniesWarzone(false, "warzone", "warzone", "spawn", false), "rule off");
        assertFalse(PortalResolver.deniesWarzone(true, "", "warzone", "spawn", false));
    }

    @Test
    void refusalKeysExistForEveryReason() {
        for (PortalResolver.Reason r : PortalResolver.Reason.values()) {
            assertTrue(PortalService.refusalKey(r).startsWith("portals."), r.name());
        }
    }

    @Test
    void parsesColors() {
        assertEquals(0xC21F2B, PortalService.color("#C21F2B").asRGB());
        assertEquals(0xFFFFFF, PortalService.color("nope").asRGB());
        assertEquals(0xFFFFFF, PortalService.color(null).asRGB());
    }
}
