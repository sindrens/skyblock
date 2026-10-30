package net.skybornmc.skyblock.worlds;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the worlds module that live in {@code worlds.yml} next to the world list: the builds catalog
 * ({@code /skyblock builds}). The {@code worlds:} and {@code defaults:} sections are read by {@link WorldSettings}.
 */
public final class WorldsConfig extends ModuleConfig {

    @Setting(path = "builds.folder", desc = "Folder with the finished builds (.schem + .yml per build, depths_layout.yml). "
            + "Relative to the server folder, or an absolute path.")
    public String buildsFolder = "builds";

    @Setting(path = "builds.points-file", desc = "Store of applied build spots (NPC pads, holograms, crates...) in the plugin folder.")
    public String pointsFile = "build-points.yml";

    @Setting(path = "builds.state-file", desc = "Which builds were pasted/applied, when and by whom (plugin folder).")
    public String stateFile = "builds-state.yml";

    @Setting(path = "builds.confirm-seconds", desc = "Seconds a paste preview stays valid for its confirm.", min = 10, max = 600)
    public int confirmSeconds = 60;

    @Setting(path = "builds.chunks-per-tick", desc = "Chunks loaded per tick before a paste (lower = smoother, slower).", min = 1, max = 64)
    public int chunksPerTick = 4;

    @Setting(path = "builds.progress-seconds", desc = "Seconds between progress messages while a paste runs.", min = 1, max = 120)
    public int progressSeconds = 5;

    @Setting(path = "builds.paste-timeout-minutes", desc = "A paste running longer than this is reported as failed (FAWE may still finish).",
            min = 1, max = 240)
    public int pasteTimeoutMinutes = 30;

    @Setting(path = "builds.template-worlds", desc = "Worlds whose builds are templates (the island schematic): never pasted, only applied.")
    public List<String> templateWorlds = List.of("islands", "outposts");

    @Setting(path = "builds.paste-order", desc = "paste-all order: builds whose id starts with an earlier prefix go first.")
    public List<String> pasteOrder = List.of("spawn_base", "spawn_", "depths_gate", "depths_mine_", "depths_forest_", "depths_combat_",
            "depths_", "warzone_");

    @Setting(path = "builds.islands-schematics-folder", desc = "Folder (plugin folder) the islands module reads its starter schematic from.")
    public String islandsSchematicsFolder = "schematics";

    @Override
    public String fileName() {
        return "worlds.yml";
    }
}
