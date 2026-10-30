package net.skybornmc.skyblock.npcs;

import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildPoints;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What to place, read from the applied builds ({@link BuildPoints}, pure apart from the input, unit-tested). Point
 * categories: {@code npcs} (pad + yaw, optional {@code role}), {@code holograms} ({@code facing}, {@code size},
 * optional {@code text}), {@code leaderboards} ({@code facing}, {@code size}).
 */
record Spots(List<Npc> npcs, List<Text> holograms, List<Text> boards, List<String> problems) {

    static final String NPCS = "npcs";
    static final String HOLOGRAMS = "holograms";
    static final String LEADERBOARDS = "leaderboards";

    /**
     * One NPC: build-point id, role, world, block/feet position and facing. {@code ref} is unique among NPCs: the id, or
     * {@code id@build} when several builds use the same id (e.g. a foreman in every mine tier).
     */
    record Npc(String ref, String id, String role, String build, String world, double x, double y, double z, float yaw) {
    }

    /**
     * One text board.
     *
     * @param ref         unique among texts of its kind: the id, or {@code id@build} when several builds use it
     * @param yaw         facing yaw, {@code NaN} = turns to the viewer (no facing given)
     * @param fallbackText the metadata's {@code text} (used when messages/npcs.yml has no text for the id), may be empty
     */
    record Text(String ref, String id, String build, String world, double x, double y, double z, float yaw, int width, int height,
                String fallbackText) {

        boolean faced() {
            return !Float.isNaN(yaw);
        }
    }

    Spots {
        npcs = List.copyOf(npcs);
        holograms = List.copyOf(holograms);
        boards = List.copyOf(boards);
        problems = List.copyOf(problems);
    }

    static Spots empty() {
        return new Spots(List.of(), List.of(), List.of(), List.of());
    }

    static Spots from(BuildPoints points, RoleRules roles, Set<String> skipHolograms) {
        List<String> problems = new ArrayList<>();
        List<Npc> npcs = new ArrayList<>();
        List<BuildPoint> npcPoints = unique(points.points(NPCS), Set.of(), problems, "NPC");
        Set<String> shared = shared(npcPoints);
        for (BuildPoint p : npcPoints) {
            String id = p.id().toLowerCase(Locale.ROOT);
            npcs.add(new Npc(ref(id, p.build(), shared), id, roles.roleOf(id, p.extra("role", null)), p.build(), p.world(),
                    p.x(), p.y(), p.z(), p.yaw()));
        }
        List<Text> holograms = texts(unique(points.points(HOLOGRAMS), skipHolograms, problems, "hologram"), 5, 3);
        List<Text> boards = texts(unique(points.points(LEADERBOARDS), Set.of(), problems, "leaderboard"), 5, 7);
        return new Spots(npcs, holograms, boards, problems);
    }

    /** Points without a world, skipped ids, and second uses of an id inside one build are left out. */
    private static List<BuildPoint> unique(List<BuildPoint> list, Set<String> skip, List<String> problems, String kind) {
        List<BuildPoint> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (BuildPoint p : list) {
            String id = p.id().toLowerCase(Locale.ROOT);
            if (skip.contains(id)) {
                continue;
            }
            if (p.world().isBlank()) {
                problems.add("build-points.yml: " + kind + " '" + id + "' (" + p.build() + ") has no world (skipped)");
                continue;
            }
            if (!seen.add(p.build() + "/" + id)) {
                problems.add("build-points.yml: " + kind + " id '" + id + "' is used twice in " + p.build() + "; the second one is skipped");
                continue;
            }
            out.add(p);
        }
        return out;
    }

    /** Ids used by more than one build. */
    private static Set<String> shared(List<BuildPoint> list) {
        Set<String> seen = new HashSet<>();
        Set<String> shared = new HashSet<>();
        for (BuildPoint p : list) {
            String id = p.id().toLowerCase(Locale.ROOT);
            if (!seen.add(id)) {
                shared.add(id);
            }
        }
        return shared;
    }

    private static String ref(String id, String build, Set<String> shared) {
        return shared.contains(id) ? id + "@" + build : id;
    }

    private static List<Text> texts(List<BuildPoint> list, int w, int h) {
        Set<String> shared = shared(list);
        List<Text> out = new ArrayList<>();
        for (BuildPoint p : list) {
            String id = p.id().toLowerCase(Locale.ROOT);
            Map<String, String> extra = p.extra();
            int[] size = Layout.size(extra.get("size"), w, h);
            float yaw = Layout.yawOf(extra.get("facing"));
            out.add(new Text(ref(id, p.build(), shared), id, p.build(), p.world(), p.x(), p.y(), p.z(), yaw, size[0], size[1],
                    extra.getOrDefault("text", "")));
        }
        return out;
    }

    int total() {
        return npcs.size() + holograms.size() + boards.size();
    }
}
