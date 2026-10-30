package net.skybornmc.skyblock.npcs;

import net.skybornmc.skyblock.common.builds.BuildPoints;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * PERF3: everything {@code place()} reads before spawning (role rules, skins, held items, the points of the builds,
 * leaderboard sources, problems), parsed off the main thread and then applied on it in one step. No world access; the
 * inputs are immutable snapshots. Pure, unit-tested.
 *
 * @param pointsAvailable false when the worlds module's build points service is missing
 */
record NpcPlan(RoleRules roles, Map<String, String> skins, Map<String, Material> held, Spots spots,
               Map<String, BoardSource> sources, List<String> problems, boolean pointsAvailable) {

    NpcPlan {
        skins = Map.copyOf(skins);
        held = Map.copyOf(held);
        sources = Map.copyOf(sources);
        problems = List.copyOf(problems);
    }

    /** The settings a plan is made from, copied on the main thread (the config object may be reloaded meanwhile). */
    record Inputs(List<String> npcRoles, List<String> roleActions, List<String> skins, List<String> heldItems,
                  List<String> hologramSkip, List<String> sources) {

        Inputs {
            npcRoles = List.copyOf(npcRoles);
            roleActions = List.copyOf(roleActions);
            skins = List.copyOf(skins);
            heldItems = List.copyOf(heldItems);
            hologramSkip = List.copyOf(hologramSkip);
            sources = List.copyOf(sources);
        }

        static Inputs of(NpcsConfig c) {
            return new Inputs(c.npcRoles, c.roleActions, c.skins, c.heldItems, c.hologramSkip, c.sources);
        }
    }

    /**
     * Parses a plan; problems are collected in the same order the old main-thread code logged them.
     *
     * @param points  the build points, or null when the worlds module's service is missing
     * @param item    an item name to a held item, or null when it is not an item
     * @param hasText whether a hologram has a text to show ({@code messages/npcs.yml → holograms.<id>} or its metadata)
     */
    static NpcPlan compute(Inputs in, BuildPoints points, Function<String, Material> item, Predicate<Spots.Text> hasText) {
        List<String> problems = new ArrayList<>();
        RoleRules roles = RoleRules.parse(in.npcRoles(), in.roleActions());
        problems.addAll(roles.problems());
        Map<String, String> skins = RoleRules.pairs("npcs.skins", in.skins(), problems);
        Map<String, Material> held = materials(RoleRules.pairs("npcs.held-items", in.heldItems(), problems), item, problems);
        Spots spots = points == null ? Spots.empty() : Spots.from(points, roles, lower(in.hologramSkip()));
        problems.addAll(spots.problems());
        Map<String, BoardSource> sources = BoardSource.parseAll(in.sources(), problems);
        List<String> noText = new ArrayList<>();
        spots.holograms().stream().filter(t -> !hasText.test(t)).forEach(t -> noText.add(t.id()));
        if (!noText.isEmpty()) {
            problems.add("messages/npcs.yml -> holograms: no text for " + String.join(", ", noText) + " (not shown)");
        }
        return new NpcPlan(roles, skins, held, spots, sources, problems, points != null);
    }

    private static Set<String> lower(List<String> ids) {
        Set<String> out = new HashSet<>();
        ids.forEach(id -> out.add(id.trim().toLowerCase(Locale.ROOT)));
        return out;
    }

    private static Map<String, Material> materials(Map<String, String> raw, Function<String, Material> item, List<String> problems) {
        Map<String, Material> out = new LinkedHashMap<>();
        raw.forEach((id, name) -> {
            Material m = item.apply(name);
            if (m == null) {
                problems.add("npcs.yml -> npcs.held-items: '" + name + "' is not an item (skipped)");
            } else {
                out.put(id, m);
            }
        });
        return out;
    }
}
