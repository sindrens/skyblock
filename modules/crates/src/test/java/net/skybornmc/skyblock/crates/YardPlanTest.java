package net.skybornmc.skyblock.crates;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF10: the crate yard plan built off the main thread gives what the old one-tick start computed. */
class YardPlanTest {

    private static BuildPoint point(String id, double x, double y, double z) {
        return new BuildPoint("spawn_crate_yard_v1", "crates", id, "spawn", x, y, z, 0f, 0f, Map.of());
    }

    /** The six pedestals of server/builds/spawn_crate_yard_v1.yml. */
    private static List<BuildPoint> yard() {
        return List.of(point("common", 80, 102, -56), point("uncommon", 84, 102, -50), point("rare", 86, 102, -43),
                point("epic", 86, 102, -35), point("legendary", 84, 102, -28), point("monthly", 80, 102, -22));
    }

    /** A stand-in block (only identity matters here). */
    private static BlockData block() {
        return (BlockData) Proxy.newProxyInstance(YardPlanTest.class.getClassLoader(), new Class<?>[]{BlockData.class},
                (proxy, method, args) -> method.getName().equals("equals") ? proxy == args[0] : null);
    }

    private static final class Texts implements YardPlan.Texts {
        final List<String> holograms = new ArrayList<>();
        int keyBoards;
        boolean failHologram;
        boolean failKeyBoard;

        @Override
        public Component hologram(CrateCatalog.Crate crate) {
            if (failHologram) {
                throw new IllegalStateException("broken text");
            }
            holograms.add(crate.id());
            return Component.text(crate.id() + (crate.hidden() ? " soon" : ""));
        }

        @Override
        public List<Component> keyBoard(CrateCatalog catalog) {
            keyBoards++;
            if (failKeyBoard) {
                throw new IllegalStateException("broken text");
            }
            return List.of(Component.text("title"), Component.text("footer"));
        }
    }

    private static YardPlan.Inputs inputs(CratesSettings s) {
        return YardPlan.Inputs.of(s, Fixtures.bundled());
    }

    @Test
    void sameValuesAsTheOldStart() {
        CratesSettings s = new CratesSettings();
        YardPlan.Inputs in = inputs(s);
        List<String> parsed = new ArrayList<>();
        BlockData data = block();
        Texts texts = new Texts();
        YardPlan plan = YardPlan.compute(in, yard(), spec -> {
            parsed.add(spec);
            return data;
        }, texts, 42L, true);

        Pedestals.Result old = Pedestals.plan(yard(), PhysicalCrates.mapping(s.physicalPoints), id -> in.catalog().crate(id).isPresent());
        assertEquals(old.pedestals(), plan.pedestals(), "same pedestals in the same order");
        assertEquals(old.problems(), plan.problems());
        assertEquals(PhysicalCrates.mapping(s.physicalBlocks), plan.blocks());
        assertEquals(PhysicalCrates.offsets(s.physicalOffsets), plan.offsets());
        assertEquals("Crates: 6 crates on the crate yard pedestals.", plan.summary());
        assertEquals(42L, plan.generation());
        assertTrue(plan.cacheTexts());
        assertSame(in, plan.inputs());

        for (Pedestals.Pedestal p : plan.pedestals()) {
            assertSame(p, plan.byBlock().get("spawn").get(p.blockKey()));
            assertTrue(plan.byChunk().get("spawn").get(p.chunkKey()).contains(p));
            YardPlan.Look look = plan.looks().get(p.crateId());
            String icon = in.catalog().crate(p.crateId()).orElseThrow().icon();
            assertEquals(Pedestals.blockData(p.crateId(), plan.blocks(), icon), look.spec());
            assertSame(data, look.data());
            assertFalse(look.invalid());
            assertEquals(Component.text(p.crateId() + (in.catalog().crate(p.crateId()).orElseThrow().hidden() ? " soon" : "")),
                    look.hologram());
        }
        assertEquals("minecraft:barrel[facing=up]", plan.looks().get("ember").spec());
        assertEquals(6, parsed.size(), "one parse per crate");
        assertEquals(List.of("ember", "cinder", "blaze", "inferno", "phoenix", "eclipse"), texts.holograms);
        int perChunk = plan.byChunk().get("spawn").values().stream().mapToInt(List::size).sum();
        assertEquals(6, perChunk, "every pedestal in exactly one chunk list");
        assertEquals("key_board", plan.keyBoardId());
        assertEquals(List.of(Component.text("title"), Component.text("footer")), plan.keyBoard());
    }

    @Test
    void noBuildPointsServiceMeansNoPedestalsAndOneLine() {
        YardPlan plan = YardPlan.compute(inputs(new CratesSettings()), null, spec -> block(), new Texts(), 1L, true);
        assertTrue(plan.pedestals().isEmpty());
        assertTrue(plan.problems().isEmpty());
        assertTrue(plan.byBlock().isEmpty());
        assertTrue(plan.looks().isEmpty());
        assertEquals(YardPlan.NO_PEDESTALS, plan.summary());
    }

    @Test
    void problemsAreKeptInOrder() {
        CratesSettings s = new CratesSettings();
        s.physicalPoints = List.of("common:ember", "uncommon:nope", "rare:ember", "missing:blaze");
        YardPlan plan = YardPlan.compute(inputs(s), yard(), spec -> block(), new Texts(), 1L, true);
        assertEquals(1, plan.pedestals().size());
        assertEquals(Pedestals.plan(yard(), PhysicalCrates.mapping(s.physicalPoints), id -> Fixtures.bundled().crate(id).isPresent())
                .problems(), plan.problems());
        assertEquals(3, plan.problems().size());
    }

    @Test
    void badBlockIsMarkedInvalidAndOtherParseFailuresAreLeftToTheMainThread() {
        CratesSettings s = new CratesSettings();
        Function<String, BlockData> parser = spec -> {
            if (spec.contains("barrel")) {
                throw new IllegalArgumentException("not a block");
            }
            if (spec.contains("vault")) {
                throw new IllegalStateException("registry not ready");
            }
            return block();
        };
        YardPlan plan = YardPlan.compute(inputs(s), yard(), parser, new Texts(), 1L, true);
        YardPlan.Look ember = plan.looks().get("ember");
        assertTrue(ember.invalid());
        assertNull(ember.data());
        YardPlan.Look phoenix = plan.looks().get("phoenix");
        assertFalse(phoenix.invalid(), "an unexpected failure is not reported as a bad block");
        assertNull(phoenix.data(), "parsed when placing instead");
        assertFalse(plan.looks().get("cinder").invalid());
    }

    @Test
    void failingTextsAreBuiltLater() {
        Texts texts = new Texts();
        texts.failHologram = true;
        texts.failKeyBoard = true;
        YardPlan plan = YardPlan.compute(inputs(new CratesSettings()), yard(), spec -> block(), texts, 1L, true);
        assertEquals(6, plan.pedestals().size());
        plan.looks().values().forEach(l -> assertNull(l.hologram()));
        assertEquals("key_board", plan.keyBoardId());
        assertNull(plan.keyBoard(), "built on the main thread at apply");
    }

    @Test
    void keyBoardIdFollowsTheSettings() {
        CratesSettings s = new CratesSettings();
        s.keyBoardHologram = "  board_2 ";
        Texts texts = new Texts();
        assertEquals("board_2", YardPlan.compute(inputs(s), yard(), spec -> block(), texts, 1L, true).keyBoardId());

        s.keyBoardHologram = " ";
        YardPlan blank = YardPlan.compute(inputs(s), yard(), spec -> block(), texts, 1L, true);
        assertNull(blank.keyBoardId());
        assertNull(blank.keyBoard());

        s.keyBoardHologram = "key_board";
        s.keyBoardEnabled = false;
        assertNull(YardPlan.compute(inputs(s), yard(), spec -> block(), texts, 1L, true).keyBoardId());
        assertEquals(1, texts.keyBoards, "lines are only built when the key board is on");
    }

    @Test
    void inputsAreCopiesAndKeepNullEntries() {
        CratesSettings s = new CratesSettings();
        List<String> offsets = new ArrayList<>(Arrays.asList("0", null, "2"));
        s.physicalOffsets = offsets;
        YardPlan.Inputs in = inputs(s);
        offsets.add("5");
        assertEquals(Arrays.asList("0", null, "2"), in.offsets());
        assertEquals(List.of(0, 2), YardPlan.compute(in, yard(), spec -> block(), new Texts(), 1L, true).offsets());
        s.physicalPoints = null;
        assertTrue(inputs(s).points().isEmpty());
    }

    @Test
    void emptyPlanHasNothing() {
        YardPlan empty = YardPlan.empty();
        assertTrue(empty.pedestals().isEmpty());
        assertTrue(empty.looks().isEmpty());
        assertEquals(List.of(0), empty.offsets());
        assertNull(empty.inputs().catalog());
        assertFalse(empty.cacheTexts());
    }
}
