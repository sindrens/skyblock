package net.skybornmc.skyblock.crates;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * PERF10: everything the crate yard needs that does not touch a world, built off the main thread: the pedestals (from
 * the build points), the click and chunk lookups, each crate's block data and hologram text, the key board lines and
 * the log lines. The main thread only swaps it in ({@code crates:yard-apply}) and sets the blocks and spawns the
 * holograms over the next ticks ({@code crates:yard-place}). The same values the old one-tick start computed, in the
 * same order. Pure (texts and the block parser are injected), unit-tested.
 *
 * @param inputs      what the plan was built from (its catalog decides whether a look is still current)
 * @param pedestals   the crates on the pedestals, in build point order
 * @param byChunk     world → chunk key → pedestals in that chunk
 * @param byBlock     world → block key → pedestal
 * @param blocks      {@code physical.blocks} as crate → block data text
 * @param offsets     {@code physical.y-offsets}
 * @param looks       crate id → its block data and hologram text
 * @param keyBoardId  build hologram id of the key board, or null when the key board is off (texts are cleared)
 * @param keyBoard    the key board lines, or null when they could not be built here (built on the main thread then)
 * @param generation  {@code Messages.generation()} read before any text was built
 * @param cacheTexts  false when a crates text uses a player placeholder (then texts are always built at spawn, as before)
 * @param problems    warnings for the log, in order
 * @param summary     the info line for the log
 */
record YardPlan(Inputs inputs, List<Pedestals.Pedestal> pedestals, Map<String, Map<Long, List<Pedestals.Pedestal>>> byChunk,
                Map<String, Map<Long, Pedestals.Pedestal>> byBlock, Map<String, String> blocks, List<Integer> offsets,
                Map<String, Look> looks, String keyBoardId, List<Component> keyBoard, long generation, boolean cacheTexts,
                List<String> problems, String summary) {

    /** Log line when the builds have no crate yard pedestals. */
    static final String NO_PEDESTALS = "Crates: no crate yard pedestals in the applied builds (crates open from /crates only).";

    /** The settings and catalog a plan is built from (copied, so a later reload can't change them under the plan). */
    record Inputs(List<String> points, List<String> blocks, List<String> offsets, boolean keyBoardEnabled, String keyBoardHologram,
                  CrateCatalog catalog) {

        static Inputs of(CratesSettings s, CrateCatalog catalog) {
            return new Inputs(copy(s.physicalPoints), copy(s.physicalBlocks), copy(s.physicalOffsets), s.keyBoardEnabled,
                    s.keyBoardHologram, catalog);
        }

        private static List<String> copy(List<String> list) {
            List<String> out = new ArrayList<>();
            if (list != null) {
                list.forEach(out::add); // null entries are kept (the parsers skip them), List.copyOf would throw
            }
            return out;
        }
    }

    /**
     * What a crate looks like on its pedestal.
     *
     * @param spec     the block data text ({@code physical.blocks} line or the crates.yml icon)
     * @param data     the parsed block, or null when it was not parsed here (parsed at placing then)
     * @param invalid  true when {@code spec} is not a block (reported once when placing, nothing placed)
     * @param hologram the hologram text, or null when it was not built here (built at spawn then)
     */
    record Look(String spec, BlockData data, boolean invalid, Component hologram) {
    }

    /** Text builders (message parsing only, safe off the main thread). */
    interface Texts {
        /** The hologram above a crate's block. */
        Component hologram(CrateCatalog.Crate crate);

        /** The key board lines for this catalog. */
        List<Component> keyBoard(CrateCatalog catalog);
    }

    /**
     * Builds the plan.
     *
     * @param in         settings and catalog
     * @param points     the crate yard build points, or null when there is no build points service
     * @param parser     block data parser: throws {@link IllegalArgumentException} for a text that is not a block; any other
     *                   failure leaves the block to be parsed at placing (on the main thread)
     * @param texts      text builders; a failing text is built at spawn instead
     * @param generation messages generation, read before calling this
     * @param cacheTexts false when crates texts use player placeholders
     */
    static YardPlan compute(Inputs in, List<BuildPoint> points, Function<String, BlockData> parser, Texts texts, long generation,
                            boolean cacheTexts) {
        Map<String, String> blocks = PhysicalCrates.mapping(in.blocks());
        List<Integer> offsets = PhysicalCrates.offsets(in.offsets());
        List<Pedestals.Pedestal> pedestals = List.of();
        List<String> problems = List.of();
        if (points != null) {
            Pedestals.Result result = Pedestals.plan(points, PhysicalCrates.mapping(in.points()), id -> in.catalog().crate(id).isPresent());
            problems = result.problems();
            pedestals = result.pedestals();
        }
        Map<String, Map<Long, List<Pedestals.Pedestal>>> byChunk = new HashMap<>();
        Map<String, Map<Long, Pedestals.Pedestal>> byBlock = new HashMap<>();
        Map<String, Look> looks = new HashMap<>();
        for (Pedestals.Pedestal p : pedestals) {
            byChunk.computeIfAbsent(p.world(), w -> new HashMap<>()).computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
            byBlock.computeIfAbsent(p.world(), w -> new HashMap<>()).put(p.blockKey(), p);
            Optional<CrateCatalog.Crate> crate = in.catalog().crate(p.crateId());
            if (crate.isPresent() && !looks.containsKey(p.crateId())) {
                looks.put(p.crateId(), look(crate.get(), blocks, parser, texts));
            }
        }
        String summary = pedestals.isEmpty() ? NO_PEDESTALS : "Crates: " + pedestals.size() + " crates on the crate yard pedestals.";
        String id = in.keyBoardHologram();
        String keyBoardId = !in.keyBoardEnabled() || id == null || id.isBlank() ? null : id.trim();
        List<Component> keyBoard = null;
        if (keyBoardId != null) {
            try {
                keyBoard = List.copyOf(texts.keyBoard(in.catalog()));
            } catch (RuntimeException e) {
                keyBoard = null; // built on the main thread at apply, as before
            }
        }
        return new YardPlan(in, List.copyOf(pedestals), byChunk, byBlock, blocks, List.copyOf(offsets), looks, keyBoardId, keyBoard,
                generation, cacheTexts, problems, summary);
    }

    private static Look look(CrateCatalog.Crate crate, Map<String, String> blocks, Function<String, BlockData> parser, Texts texts) {
        String spec = Pedestals.blockData(crate.id(), blocks, crate.icon());
        BlockData data = null;
        boolean invalid = false;
        try {
            data = parser.apply(spec);
        } catch (IllegalArgumentException e) {
            invalid = true;
        } catch (RuntimeException e) {
            data = null; // parsed on the main thread when placing
        }
        Component hologram;
        try {
            hologram = texts.hologram(crate);
        } catch (RuntimeException e) {
            hologram = null; // built at spawn
        }
        return new Look(spec, data, invalid, hologram);
    }

    /** An empty plan (before the first one is applied). */
    static YardPlan empty() {
        return new YardPlan(new Inputs(List.of(), List.of(), List.of("0"), false, null, null), List.of(), Map.of(), Map.of(), Map.of(),
                List.of(0), Map.of(), null, null, 0L, false, List.of(), NO_PEDESTALS);
    }
}
