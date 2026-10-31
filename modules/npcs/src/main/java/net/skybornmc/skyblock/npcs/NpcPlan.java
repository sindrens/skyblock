package net.skybornmc.skyblock.npcs;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
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
 * <p>PERF9: it also builds everything the main thread used to build in {@code npcs:apply} and the spawn ticks: the
 * tracked sets with their per-chunk map ({@link PlacementIndex}), the hologram texts and the NPC name texts, and the
 * log lines. The apply only swaps them in.
 *
 * @param index           the tracked sets of this placement (used once, by the apply)
 * @param hologramTexts   static hologram texts by {@link Spots.Text#ref()} (live texts are looked up at spawn)
 * @param labels          NPC name texts by {@link Spots.Npc#ref()}
 * @param pointsAvailable false when the worlds module's build points service is missing
 */
record NpcPlan(RoleRules roles, Map<String, String> skins, Map<String, Material> held, Spots spots,
               Map<String, BoardSource> sources, List<String> problems, boolean pointsAvailable, PlacementIndex index,
               Map<String, NpcsModule.TextContent> hologramTexts, Map<String, Component> labels) {

    NpcPlan {
        skins = Map.copyOf(skins);
        held = Map.copyOf(held);
        sources = Map.copyOf(sources);
        problems = List.copyOf(problems);
        hologramTexts = Map.copyOf(hologramTexts);
        labels = Map.copyOf(labels);
    }

    /**
     * PERF9: builds the texts of a plan off the main thread (MiniMessage parsing of {@code messages/npcs.yml}, which is
     * thread-safe; no world access). A null result or an exception leaves the text to be built at spawn, as before.
     */
    interface Texts {

        /** A hologram's own text ({@code holograms.<id>} or its metadata text), null when it has none. */
        NpcsModule.TextContent hologram(Spots.Text text);

        /** The name text above an NPC with the plan's role rules. */
        Component label(RoleRules roles, Spots.Npc npc);

        /** Builds nothing (everything is built at spawn). */
        Texts NONE = new Texts() {
            @Override
            public NpcsModule.TextContent hologram(Spots.Text text) {
                return null;
            }

            @Override
            public Component label(RoleRules roles, Spots.Npc npc) {
                return null;
            }
        };
    }

    /**
     * The info lines of this plan, in the order the old apply logged them: the problems, then the summary.
     */
    List<String> logLines() {
        List<String> out = new ArrayList<>(problems.size() + 1);
        problems.forEach(p -> out.add("[npcs] " + p));
        out.add("[npcs] " + spots.npcs().size() + " NPCs, " + spots.holograms().size() + " holograms, "
                + spots.boards().size() + " leaderboards from the builds"
                + (pointsAvailable ? "" : " (worlds module's build points not available)")
                + (index.isEmpty() ? "" : "; spawning those in loaded chunks over the next ticks"));
        return out;
    }

    /** The settings a plan is made from, copied on the main thread (the config object may be reloaded meanwhile). */
    record Inputs(List<String> npcRoles, List<String> roleActions, List<String> skins, List<String> heldItems,
                  List<String> hologramSkip, List<String> sources, boolean npcsEnabled, boolean hologramsEnabled,
                  boolean leaderboardsEnabled) {

        Inputs {
            npcRoles = List.copyOf(npcRoles);
            roleActions = List.copyOf(roleActions);
            skins = List.copyOf(skins);
            heldItems = List.copyOf(heldItems);
            hologramSkip = List.copyOf(hologramSkip);
            sources = List.copyOf(sources);
        }

        /** Every kind enabled. */
        Inputs(List<String> npcRoles, List<String> roleActions, List<String> skins, List<String> heldItems,
               List<String> hologramSkip, List<String> sources) {
            this(npcRoles, roleActions, skins, heldItems, hologramSkip, sources, true, true, true);
        }

        static Inputs of(NpcsConfig c) {
            return new Inputs(c.npcRoles, c.roleActions, c.skins, c.heldItems, c.hologramSkip, c.sources, c.npcsEnabled,
                    c.hologramsEnabled, c.leaderboardsEnabled);
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
        return compute(in, points, item, hasText, Texts.NONE);
    }

    /** As above, and builds the texts with {@code texts} (PERF9). */
    static NpcPlan compute(Inputs in, BuildPoints points, Function<String, Material> item, Predicate<Spots.Text> hasText,
                           Texts texts) {
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
        PlacementIndex index = PlacementIndex.build(spots, in.npcsEnabled(), in.hologramsEnabled(), in.leaderboardsEnabled());
        Map<String, NpcsModule.TextContent> hologramTexts = new HashMap<>();
        if (in.hologramsEnabled()) {
            for (Spots.Text t : spots.holograms()) {
                NpcsModule.TextContent content = safely(() -> texts.hologram(t));
                if (content != null) {
                    hologramTexts.put(t.ref(), content);
                }
            }
        }
        Map<String, Component> labels = new HashMap<>();
        if (in.npcsEnabled()) {
            for (Spots.Npc n : spots.npcs()) {
                Component label = safely(() -> texts.label(roles, n));
                if (label != null) {
                    labels.put(n.ref(), label);
                }
            }
        }
        return new NpcPlan(roles, skins, held, spots, sources, problems, points != null, index, hologramTexts, labels);
    }

    /** A text, or null when building it failed (it is built again at spawn, where a failure is logged as before). */
    private static <T> T safely(java.util.function.Supplier<T> build) {
        try {
            return build.get();
        } catch (RuntimeException e) {
            return null;
        }
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
