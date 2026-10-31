package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.builds.BuildPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which crate stands on which crate yard pedestal (pure, unit-tested): the build points of category {@code crates}
 * ({@code common ... monthly}) mapped through {@code crates-settings.yml → physical.points}. A point without a line is
 * not a crate (the Conqueror pedestal does not exist yet), a line naming an unknown crate is reported and skipped, and
 * a crate is placed only once (the first point wins).
 */
final class Pedestals {

    /** Build point category of the crate yard pedestals. */
    static final String CATEGORY = "crates";

    /** One crate on a pedestal; {@code x/y/z} = the crate block. */
    record Pedestal(String pointId, String crateId, String world, int x, int y, int z) {

        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }

        /** Chunk key of the pedestal's chunk in its world. */
        long chunkKey() {
            return chunkKey(chunkX(), chunkZ());
        }

        static long chunkKey(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        }

        /** Block key in its world (clicks look pedestals up by it). */
        long blockKey() {
            return blockKey(x, y, z);
        }

        static long blockKey(int x, int y, int z) {
            return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        }
    }

    /** The pedestals and what was wrong. */
    record Result(List<Pedestal> pedestals, List<String> problems) {
    }

    private Pedestals() {
    }

    /**
     * Maps the build points to pedestals. {@code known} tells whether a crate id exists in {@code crates.yml}.
     */
    static Result plan(List<BuildPoint> points, Map<String, String> mapping, Predicate<String> known) {
        List<Pedestal> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (BuildPoint p : points == null ? List.<BuildPoint>of() : points) {
            String crate = mapping.get(p.id().toLowerCase(Locale.ROOT));
            if (crate == null) {
                continue;
            }
            if (!known.test(crate)) {
                problems.add("crates-settings.yml → physical.points: pedestal '" + p.id() + "' names crate '" + crate
                        + "', which is not in crates.yml (skipped)");
                continue;
            }
            if (!placed.add(crate)) {
                problems.add("crates-settings.yml → physical.points: crate '" + crate + "' is on more than one pedestal; only the "
                        + "first one (" + out.stream().filter(x -> x.crateId().equals(crate)).findFirst().map(Pedestal::pointId)
                        .orElse("?") + ") is used");
                continue;
            }
            out.add(new Pedestal(p.id(), crate, p.world(), p.blockX(), p.blockY(), p.blockZ()));
        }
        Set<String> ids = new HashSet<>();
        for (BuildPoint p : points == null ? List.<BuildPoint>of() : points) {
            ids.add(p.id().toLowerCase(Locale.ROOT));
        }
        for (String point : mapping.keySet()) {
            // No points at all = the crate yard build is not applied yet: one log line from the caller, not one per crate.
            if (!ids.isEmpty() && !ids.contains(point)) {
                problems.add("crates-settings.yml → physical.points: no crate yard pedestal '" + point + "' in the applied builds "
                        + "(skipped until the builder adds it)");
            }
        }
        return new Result(List.copyOf(out), List.copyOf(problems));
    }

    /** The block data of a crate: its {@code physical.blocks} line, else its crates.yml icon. Pure. */
    static String blockData(String crateId, Map<String, String> blocks, String icon) {
        String data = blocks.get(crateId);
        if (data != null && !data.isBlank()) {
            return data.contains(":") ? data : "minecraft:" + data;
        }
        String fallback = icon == null || icon.isBlank() ? "chest" : icon.toLowerCase(Locale.ROOT);
        return fallback.contains(":") ? fallback : "minecraft:" + fallback;
    }
}
