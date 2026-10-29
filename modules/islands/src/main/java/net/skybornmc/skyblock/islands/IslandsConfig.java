package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Settings of the islands module ({@code islands.yml}). Texts are in messages/islands.yml. */
public final class IslandsConfig extends ModuleConfig {

    @Setting(desc = "World that holds the island grid (created by worlds.yml).", restart = true, level = Setting.Level.OWNER)
    public String world = "islands";

    @Setting(path = "season-id", desc = "Season stored on new islands (until the season system exists).", min = 0, max = 10_000,
            level = Setting.Level.OWNER)
    public int seasonId = 0;

    @Setting(path = "size.start-chunks", desc = "Border width of a new island in chunks (odd: 3 = 48x48 blocks).", min = 1, max = 31,
            level = Setting.Level.OWNER)
    public int startChunks = 3;

    @Setting(path = "size.max-chunks", desc = "Largest border width in chunks (odd, Island Size upgrade cap; 15 = 240x240).", min = 1,
            max = 31, level = Setting.Level.OWNER)
    public int maxChunks = 15;

    @Setting(path = "creation.surface-y", desc = "Y of the starter island's top surface (the home block).", min = 1, max = 250)
    public int surfaceY = 100;

    @Setting(path = "creation.cooldown-hours", desc = "Hours after disbanding before the same player can create a new island.",
            min = 0, max = 720)
    public int creationCooldownHours = 24;

    @Setting(path = "creation.schematic", desc = "Starter schematic in plugins/PhoenixSkyblock/schematics/ (without .schem). "
            + "Missing file or no FastAsyncWorldEdit = the built-in placeholder islet.")
    public String schematic = "islands_starter_meadow";

    @Setting(path = "creation.paste-timeout-seconds", desc = "A schematic paste that takes longer than this falls back to the placeholder islet.",
            min = 5, max = 300)
    public int pasteTimeoutSeconds = 30;

    @Setting(path = "creation.home-yaw", desc = "Facing direction at the home spot when no schematic metadata says otherwise (180 = north).",
            min = -180, max = 360)
    public double homeYaw = 180;

    @Setting(path = "starter-chest.items", desc = "Starter chest contents as material:amount (plan open question 2).")
    public List<String> starterItems = List.of(
            "lava_bucket:1", "ice:1", "bone_meal:2", "bread:16", "oak_sapling:2", "sugar_cane:1", "cactus:1",
            "melon_seeds:1", "pumpkin_seeds:1");

    @Setting(path = "home.warmup-seconds", desc = "Seconds to stand still before /is home teleports (0 = instant).", min = 0, max = 30)
    public int homeWarmupSeconds = 0;

    @Setting(path = "home.search-radius", desc = "How far (blocks) to look for a safe spot when the home is blocked or over the void.",
            min = 0, max = 64)
    public int safeSearchRadius = 8;

    @Setting(path = "home.rescue-block", desc = "Block placed under the home if the island has no safe spot left at all.")
    public String rescueBlock = "dirt";

    @Setting(path = "build.min-y", desc = "Lowest Y a home can be set at (owner decision: islands are built in Y 0-256).", min = -64, max = 320)
    public int minBuildY = 0;

    @Setting(path = "build.max-y", desc = "Highest Y a home can be set at.", min = -64, max = 320)
    public int maxBuildY = 256;

    @Setting(path = "void.teleport-below-y", desc = "Falling below this Y in the islands world teleports to an island home (no damage).",
            min = -128, max = 64)
    public int voidY = -16;

    @Setting(path = "void.fallback-world", desc = "Where void falls go when there is no island to return to (its spawn point).")
    public String fallbackWorld = "spawn";

    @Setting(path = "protection.message-cooldown-ms", desc = "Minimum time between two \"you can't build here\" messages per player.",
            min = 0, max = 10_000)
    public int protectionMessageCooldownMs = 1500;

    /** The max size made valid: odd (an even value is lowered by one), at least 1. */
    public int maxSize() {
        return odd(maxChunks);
    }

    /** The start size made valid: odd and never above {@link #maxSize()}. */
    public int startSize() {
        return Math.min(odd(startChunks), maxSize());
    }

    /** Problems with the size settings (shown in the console on enable/reload); empty when fine. */
    public List<String> sizeProblems() {
        List<String> problems = new java.util.ArrayList<>();
        if (startChunks % 2 == 0 || maxChunks % 2 == 0) {
            problems.add("islands.yml: island sizes must be odd (start " + startChunks + ", max " + maxChunks + "); using "
                    + startSize() + " and " + maxSize() + ".");
        }
        if (odd(startChunks) > maxSize()) {
            problems.add("islands.yml: size.start-chunks is bigger than size.max-chunks; using " + startSize() + ".");
        }
        if (minBuildY >= maxBuildY) {
            problems.add("islands.yml: build.min-y must be below build.max-y.");
        }
        return problems;
    }

    static int odd(int value) {
        int v = Math.max(1, Math.min(IslandGrid.MAX_POSSIBLE_CHUNKS, value));
        return v % 2 == 0 ? v - 1 : v;
    }

    @Override
    public String fileName() {
        return "islands.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock islands (/is). Grid: 512x512 slots in the islands world, one island per slot, spiral from 0,0.
                Sizes are in chunks and must be odd (3, 5, ... 15). Texts: messages/islands.yml. Reload: /skyblock reload islands""";
    }
}
