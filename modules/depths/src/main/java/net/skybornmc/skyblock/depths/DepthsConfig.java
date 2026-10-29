package net.skybornmc.skyblock.depths;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/**
 * Typed settings of {@code depths.yml}. The {@code regions} section of the same file (boxes with their resource
 * blocks) is read by {@link RegionLoader}; the layout (zones, fields, gates) lives in {@code zones.yml} (M4.1).
 */
public final class DepthsConfig extends ModuleConfig {

    // ------------------------------------------------------------------ engine

    @Setting(path = "engine.default-respawn-seconds", desc = "Seconds a broken resource block stays depleted for the player when a region entry has no respawn-seconds.", min = 1, max = 3600)
    public int defaultRespawnSeconds = 10;

    @Setting(path = "engine.default-depleted", desc = "Block shown while depleted when a region entry has no 'depleted' (block id or block state).")
    public String defaultDepleted = "minecraft:tuff";

    @Setting(path = "engine.timer-slots", desc = "Size of the respawn timer wheel in ticks (power of two; respawns longer than this still work, just a bit less efficiently).", min = 64, max = 65536, restart = true)
    public int timerSlots = 1024;

    @Setting(path = "engine.protect-regions", desc = "Inside regions, players can't break other blocks or place blocks (only resource blocks can be mined).")
    public boolean protectRegions = true;

    @Setting(path = "engine.builder-bypass", desc = "Staff in creative mode with skyblock.depths.build change blocks inside regions for real (to build and edit).")
    public boolean builderBypass = true;

    // ------------------------------------------------------------------ anti fast-break

    @Setting(path = "anti-fast-break.enabled", desc = "Ignore breaks that are faster than possible with the held tool (Grim still handles movement and reach).")
    public boolean fastBreakEnabled = true;

    @Setting(path = "anti-fast-break.min-ratio", desc = "Fastest accepted break as a share of the vanilla break time (vanilla itself accepts 0.7).", min = 0.1, max = 1.0)
    public double fastBreakMinRatio = 0.7;

    @Setting(path = "anti-fast-break.grace-ms", desc = "Extra milliseconds allowed for ping jitter and server lag.", min = 0, max = 2000)
    public int fastBreakGraceMs = 100;

    @Setting(path = "anti-fast-break.alert-threshold", desc = "Flagged breaks within the window before staff get an alert (and an audit row is written).", min = 1, max = 1000)
    public int fastBreakAlertThreshold = 3;

    @Setting(path = "anti-fast-break.window-seconds", desc = "Window in which flagged breaks are counted.", min = 1, max = 3600)
    public int fastBreakWindowSeconds = 10;

    @Setting(path = "anti-fast-break.alert-cooldown-seconds", desc = "Minimum seconds between two alerts about the same player.", min = 0, max = 3600)
    public int fastBreakAlertCooldownSeconds = 30;

    // ------------------------------------------------------------------ zones (M4.1)

    @Setting(path = "zones.register-fields", desc = "Register the ore/log fields of zones.yml with the per-player blocks engine.")
    public boolean registerZoneFields = true;

    @Setting(path = "zones.portals", desc = "The boss portal in the last combat tier and the return portal in the boss arena teleport players.")
    public boolean portalsEnabled = true;

    @Setting(path = "generate.blocks-per-tick", desc = "/depths generate: most blocks changed per tick (keeps the server smooth).", min = 100, max = 50000)
    public int generateBlocksPerTick = 3000;

    @Setting(path = "generate.positions-per-tick", desc = "/depths generate: most positions checked per tick (unchanged blocks are only read).", min = 1000, max = 500000)
    public int generatePositionsPerTick = 20000;

    @Setting(path = "generate.max-millis-per-tick", desc = "/depths generate: most milliseconds of a tick spent building.", min = 1, max = 40)
    public int generateMaxMillisPerTick = 8;

    @Setting(path = "generate.progress-seconds", desc = "/depths generate: seconds between progress updates to whoever started it.", min = 1, max = 300)
    public int generateProgressSeconds = 3;

    @Override
    public String fileName() {
        return "depths.yml";
    }

    @Override
    public String header() {
        return """
                The Depths: per-player resource blocks (M4.3).
                regions: boxes where resource blocks are personal. Each region:
                  world: depths
                  corner-1: "0, 60, 0"        (x, y, z; any two opposite corners)
                  corner-2: "50, 90, 50"
                  resources:
                    - { block: iron_ore, depleted: tuff, respawn-seconds: 10 }
                The layout (The Gate, wings, tier zones, ore/log fields, boss arena) is in zones.yml.
                Texts are in messages/depths.yml. Reload: /skyblock reload depths""";
    }
}
