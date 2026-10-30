package net.skybornmc.skyblock.npcs;

import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotsTest {

    /** Build points in memory, like build-points.yml after applying builds. */
    record FakePoints(List<BuildPoint> all) implements BuildPoints {
        @Override
        public List<BuildPoint> points(String category) {
            return all.stream().filter(p -> p.category().equals(category)).toList();
        }

        @Override
        public List<BuildPoint> points(String build, String category) {
            return points(category).stream().filter(p -> p.build().equals(build)).toList();
        }

        @Override
        public Optional<BuildPoint> point(String category, String id) {
            return points(category).stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public List<BuildRegion> regions(String category) {
            return List.of();
        }

        @Override
        public Optional<BuildRegion> region(String category, String id) {
            return Optional.empty();
        }

        @Override
        public Set<String> categories() {
            return all.stream().map(BuildPoint::category).collect(Collectors.toSet());
        }

        @Override
        public Set<String> builds() {
            return all.stream().map(BuildPoint::build).collect(Collectors.toSet());
        }
    }

    static BuildPoint point(String build, String category, String id, double x, double y, double z, float yaw, Map<String, String> extra) {
        return new BuildPoint(build, category, id, "spawn", x, y, z, yaw, 0f, extra);
    }

    private static RoleRules roles() {
        NpcsConfig c = new NpcsConfig();
        return RoleRules.parse(c.npcRoles, c.roleActions);
    }

    @Test
    void readsNpcsHologramsAndBoards() {
        FakePoints points = new FakePoints(List.of(
                point("spawn_market_square_v1", "npcs", "bank", 0, 99, 81, 180, Map.of()),
                point("spawn_forge_quarter_v1", "npcs", "Blacksmith", 74, 99, 10, 90, Map.of()),
                point("spawn_forge_quarter_v1", "npcs", "enchanter", 62, 99, 24, 180, Map.of("role", "wardrobe")),
                point("spawn_base_v1", "holograms", "info_board", 10, 103, -11, 0, Map.of("facing", "south", "size", "[5, 4]")),
                point("spawn_crate_yard_v1", "holograms", "key_board", 94, 104, -39, 0,
                        Map.of("facing", "west", "size", "[7, 5]", "text", "How to get keys")),
                point("spawn_hall_of_heroes_v1", "leaderboards", "mining", -61, 108, -62, 0, Map.of("facing", "south", "size", "[5, 7]")),
                point("spawn_market_square_v1", "particles", "fountain_spray", 0, 101, 57, 0, Map.of())));
        Spots spots = Spots.from(points, roles(), Set.of());
        assertEquals(List.of(), spots.problems());
        assertEquals(3, spots.npcs().size());
        Spots.Npc bank = spots.npcs().get(0);
        assertEquals("bank", bank.ref());
        assertEquals("bank", bank.role());
        assertEquals(180f, bank.yaw());
        assertEquals("forge", spots.npcs().get(1).role(), "ids are lower-cased and mapped");
        assertEquals("blacksmith", spots.npcs().get(1).id());
        assertEquals("wardrobe", spots.npcs().get(2).role(), "metadata role wins");

        assertEquals(2, spots.holograms().size());
        Spots.Text info = spots.holograms().get(0);
        assertEquals(5, info.width());
        assertEquals(4, info.height());
        assertEquals(0f, info.yaw());
        assertTrue(info.faced());
        assertEquals("", info.fallbackText());
        assertEquals("How to get keys", spots.holograms().get(1).fallbackText());
        assertEquals(90f, spots.holograms().get(1).yaw());

        assertEquals(1, spots.boards().size());
        assertEquals(7, spots.boards().get(0).height());
        assertEquals(6, spots.total());
    }

    @Test
    void sameIdInSeveralBuildsGetsUniqueRefs() {
        FakePoints points = new FakePoints(List.of(
                point("depths_mine_t01_coal_v1", "npcs", "mine_foreman", 1, 100, 1, 0, Map.of()),
                point("depths_mine_t02_copper_v1", "npcs", "mine_foreman", 200, 100, 1, 0, Map.of()),
                point("depths_mine_t01_coal_v1", "holograms", "tier_sign", 1, 105, 1, 0, Map.of("text", "Tier 1")),
                point("depths_mine_t02_copper_v1", "holograms", "tier_sign", 200, 105, 1, 0, Map.of("text", "Tier 2")),
                point("spawn_base_v1", "holograms", "info_board", 10, 103, -11, 0, Map.of())));
        Spots spots = Spots.from(points, roles(), Set.of());
        assertEquals(List.of("mine_foreman@depths_mine_t01_coal_v1", "mine_foreman@depths_mine_t02_copper_v1"),
                spots.npcs().stream().map(Spots.Npc::ref).toList());
        assertEquals("foreman", spots.npcs().get(0).role());
        assertEquals(List.of("tier_sign@depths_mine_t01_coal_v1", "tier_sign@depths_mine_t02_copper_v1", "info_board"),
                spots.holograms().stream().map(Spots.Text::ref).toList());
        assertEquals("tier_sign", spots.holograms().get(0).id(), "texts are still looked up by the builder's id");
    }

    @Test
    void skipsDoublesInOneBuildSkippedIdsAndMissingWorlds() {
        FakePoints points = new FakePoints(List.of(
                point("b", "npcs", "bank", 0, 99, 81, 0, Map.of()),
                point("b", "npcs", "bank", 5, 99, 81, 0, Map.of()),
                new BuildPoint("c", "npcs", "shop", "", 0, 0, 0, 0, 0, Map.of()),
                point("b", "holograms", "miniboss_timer", 0, 0, 0, 0, Map.of("facing", "up"))));
        Spots spots = Spots.from(points, roles(), Set.of("miniboss_timer"));
        assertEquals(1, spots.npcs().size());
        assertEquals(0, spots.holograms().size());
        assertEquals(2, spots.problems().size(), spots.problems().toString());
    }

    @Test
    void noFacingMeansTurnToViewerAndDefaultSize() {
        FakePoints points = new FakePoints(List.of(point("b", "holograms", "x", 0, 100, 0, 0, Map.of())));
        Spots.Text t = Spots.from(points, roles(), Set.of()).holograms().getFirst();
        assertTrue(!t.faced());
        assertEquals(5, t.width());
        assertEquals(3, t.height());
    }
}
