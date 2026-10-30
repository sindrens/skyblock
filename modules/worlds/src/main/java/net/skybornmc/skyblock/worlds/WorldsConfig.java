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

    // ------------------------------------------------------------------ portals (PORTALS)

    @Setting(path = "portals.enabled", desc = "Build portals teleport players who step into them (portals of the applied builds).")
    public boolean portalsEnabled = true;

    @Setting(path = "portals.island-portal", desc = "Portal id (or build:id) that creates/teleports to the player's island. "
            + "Empty = from the build metadata (target island, or an id containing island/start).")
    public String islandPortal = "";

    @Setting(path = "portals.force-open", desc = "Portal ids that teleport although the build marks them closed (open the gate first).")
    public List<String> portalsForceOpen = List.of();

    @Setting(path = "portals.disabled", desc = "Portal ids that never teleport.")
    public List<String> portalsDisabled = List.of();

    @Setting(path = "portals.target-overrides", desc = "portal=target entries replacing the metadata target "
            + "(targets: island, spawn, depths, warzone, world:<name>, point:<category>:<id>).")
    public List<String> portalTargetOverrides = List.of();

    @Setting(path = "portals.world-targets", desc = "target=world entries: which world a target name leads to.")
    public List<String> portalWorldTargets = List.of("spawn=spawn", "depths=depths", "warzone=warzone");

    @Setting(path = "portals.need-build", desc = "Worlds whose portals open only once a build of that world is applied.")
    public List<String> portalsNeedBuild = List.of("warzone");

    @Setting(path = "portals.grace-millis", desc = "After a portal teleport, portals ignore the player this long (no bouncing back).",
            min = 0, max = 10000)
    public int portalGraceMillis = 1000;

    @Setting(path = "portals.refusal-cooldown-millis", desc = "After a refused portal (closed, frozen...), the next message comes at most this often.",
            min = 0, max = 30000)
    public int portalRefusalMillis = 2000;

    @Setting(path = "portals.island-wait-millis", desc = "While the island portal creates/loads the island, portals ignore the player up to this long.",
            min = 1000, max = 120000)
    public int portalIslandWaitMillis = 15000;

    @Setting(path = "portals.combat-delay-seconds", desc = "Seconds a combat-tagged player (or anyone in the warzone) waits in the portal (owner rule: instant except in combat).",
            min = 0, max = 30)
    public int portalCombatDelaySeconds = 3;

    @Setting(path = "portals.push-back", desc = "Speed a refused player is pushed out of the portal (0 = no push).", min = 0, max = 3)
    public double portalPushBack = 0.6;

    @Setting(path = "portals.sound", desc = "Sound key (sounds.yml) played on arrival through a portal.")
    public String portalSound = "teleport";

    @Setting(path = "portals.particle-ticks", desc = "Ticks between portal particle puffs (0 = no particles).", min = 0, max = 200)
    public int portalParticleTicks = 10;

    @Setting(path = "portals.particle-count", desc = "Particles per puff, spread over the portal opening.", min = 1, max = 50)
    public int portalParticleCount = 6;

    @Setting(path = "portals.warzone-world", desc = "The warzone world (reachable only through its portal).")
    public String warzoneWorld = "warzone";

    @Setting(path = "portals.warzone-portal-only", desc = "Teleports into the warzone from other worlds are refused except through its portal "
            + "(staff teleports with the teleport bypass still work).")
    public boolean warzonePortalOnly = true;

    @Override
    public String fileName() {
        return "worlds.yml";
    }
}
