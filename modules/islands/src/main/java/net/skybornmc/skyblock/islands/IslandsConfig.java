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
    public int creationCooldownHours = 0;   // owner 2026-10-30: no cooldown after disband

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

    @Setting(path = "protection.pvp", desc = "Players can hurt each other in the islands world (owner decision: off).")
    public boolean pvp = false;

    @Setting(path = "protection.disable-portals", desc = "Nether and end portals can't be built or used in the islands world.")
    public boolean disablePortals = true;

    @Setting(path = "protection.keep-players-inside", desc = "A player inside an island border can't be pushed or walk out of it "
            + "(backup for clients that ignore the border, e.g. Bedrock).")
    public boolean keepPlayersInside = true;

    @Setting(path = "protection.return-entities.enabled", desc = "Mobs, animals, items and vehicles pushed out of an island border "
            + "are put back inside.")
    public boolean returnEntities = true;

    @Setting(path = "protection.return-entities.interval-ticks", desc = "How often (ticks) the chunks around occupied islands are "
            + "checked for entities outside the border.", min = 5, max = 1200)
    public int returnEntitiesIntervalTicks = 40;

    @Setting(path = "protection.return-entities.ring-chunks", desc = "How many chunk rings outside the border are checked.", min = 1, max = 4)
    public int returnEntitiesRingChunks = 1;

    @Setting(path = "protection.return-entities.margin-blocks", desc = "How far inside the border a returned entity is put.", min = 0.5,
            max = 8)
    public double returnEntitiesMargin = 1.5;

    @Setting(path = "permissions.defaults.mod", desc = "Default permissions of island mods (keys: break, place, interact, containers, "
            + "redstone, animals, villagers, pickup, drop, armor-stands, vehicles, fire, ender-pearls, spawners; * = all). "
            + "The leader always has everything. Islands can change these (M2.3 permission menu).")
    public List<String> defaultsMod = List.of("*");

    @Setting(path = "permissions.defaults.member", desc = "Default permissions of island members.")
    public List<String> defaultsMember = List.of("break", "place", "interact", "containers", "redstone", "animals", "villagers",
            "pickup", "drop", "armor-stands", "vehicles", "fire", "ender-pearls");

    @Setting(path = "permissions.defaults.coop", desc = "Default permissions of coop players (temporary trust while the leader is online).")
    public List<String> defaultsCoop = List.of("break", "place", "interact", "containers", "redstone", "animals", "villagers",
            "pickup", "drop", "armor-stands", "vehicles", "ender-pearls");

    @Setting(path = "permissions.defaults.visitor", desc = "Default permissions of visitors (empty = walk and look only). "
            + "Islands can change this in their settings (M2.5).")
    public List<String> defaultsVisitor = List.of();

    @Setting(path = "members.start-slots", desc = "Members an island can have at the start, leader included (REVIEW B1: 4). "
            + "The Member Slots upgrade (Phase 5) raises an island's own value.", min = 1, max = 32, level = Setting.Level.OWNER)
    public int startSlots = 4;

    @Setting(path = "members.max-slots", desc = "Most members any island can have, leader included (Member Slots upgrade cap: 8).",
            min = 1, max = 32, level = Setting.Level.OWNER)
    public int maxSlots = 8;

    @Setting(path = "members.max-joins-per-season", desc = "How many islands a player can join per season by accepting invites "
            + "(REVIEW A5: 3; 0 = unlimited).", min = 0, max = 100, level = Setting.Level.OWNER)
    public int maxJoinsPerSeason = 3;

    @Setting(path = "members.join-cooldown-hours", desc = "Hours a player waits after joining, leaving or being kicked from an island "
            + "before joining another one (REVIEW A5: 24; 0 = off).", min = 0, max = 720, level = Setting.Level.OWNER)
    public int joinCooldownHours = 24;

    @Setting(path = "members.invite-expiry-seconds", desc = "Seconds an island invite stays valid (5 min).", min = 30, max = 3600)
    public int inviteExpirySeconds = 300;

    @Setting(path = "members.max-pending-invites", desc = "Open invites an island can have at the same time (spam guard).", min = 1, max = 32)
    public int maxPendingInvites = 8;

    @Setting(path = "members.invite-sweep-seconds", desc = "How often expired invites are removed and both players told.", min = 1, max = 60)
    public int inviteSweepSeconds = 5;

    @Setting(path = "members.max-coops", desc = "Coop players an island can have at the same time.", min = 0, max = 32)
    public int maxCoops = 4;

    @Setting(path = "members.date-format", desc = "How join dates are shown in /is members (Java DateTimeFormatter pattern).")
    public String dateFormat = "yyyy-MM-dd";

    @Setting(path = "lifecycle.disband-prompt-length", desc = "Longest text accepted when /is disband asks the leader to type the "
            + "confirm word in chat (the word itself is in messages/islands.yml: disband.confirm-word).", min = 8, max = 64)
    public int disbandPromptLength = 32;

    @Setting(path = "lifecycle.clear.use-fawe", desc = "Empty removed islands with FastAsyncWorldEdit when it is installed "
            + "(off = always the built-in throttled clearer).")
    public boolean clearUseFawe = true;

    @Setting(path = "lifecycle.clear.extra-ring-chunks", desc = "Chunk rings emptied outside the max island size when an island is "
            + "removed (catches anything that ended up just outside a border).", min = 0, max = 8)
    public int clearExtraRings = 1;

    @Setting(path = "lifecycle.clear.chunks-per-slice", desc = "Chunks emptied per step (one strip of a chunk row). Smaller = "
            + "less work per step, slower clearing.", min = 1, max = 32)
    public int clearChunksPerSlice = 4;

    @Setting(path = "lifecycle.clear.ticks-between-slices", desc = "Ticks to wait between two steps of the clearer (throttle).",
            min = 1, max = 200)
    public int clearTicksBetweenSlices = 2;

    @Setting(path = "lifecycle.clear.fallback-blocks-per-tick", desc = "Without FAWE: most non-air block positions checked "
            + "and emptied per tick (empty 16x16x16 sections are skipped for free).", min = 1024, max = 262_144)
    public int clearFallbackBlocksPerTick = 16_384;

    @Setting(path = "lifecycle.clear.fawe-timeout-seconds", desc = "A FAWE step that takes longer than this is redone by the "
            + "built-in clearer.", min = 5, max = 300)
    public int clearFaweTimeoutSeconds = 30;

    @Setting(path = "lifecycle.clear.start-delay-seconds", desc = "Seconds after startup before unfinished clearings are resumed.",
            min = 0, max = 600)
    public int clearStartDelaySeconds = 10;

    /** The role defaults parsed from {@code permissions.defaults.*} (problems = unknown keys). */
    public RolePermissions.Result rolePermissions() {
        return RolePermissions.parse(defaultsMod, defaultsMember, defaultsCoop, defaultsVisitor);
    }

    /** The join limits for invites and accepts (REVIEW A5, B1). */
    MemberStore.Limits memberLimits() {
        return new MemberStore.Limits(seasonId, maxJoinsPerSeason, java.time.Duration.ofHours(joinCooldownHours).toMillis(),
                Math.min(startSlots, maxSlots), maxSlots);
    }

    /** {@code members.date-format}, or {@code yyyy-MM-dd} when invalid. */
    public java.time.format.DateTimeFormatter dateFormatter() {
        try {
            return java.time.format.DateTimeFormatter.ofPattern(dateFormat).withZone(java.time.ZoneId.systemDefault());
        } catch (IllegalArgumentException e) {
            return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(java.time.ZoneId.systemDefault());
        }
    }

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
        if (startSlots > maxSlots) {
            problems.add("islands.yml: members.start-slots is bigger than members.max-slots; using " + maxSlots + ".");
        }
        try {
            java.time.format.DateTimeFormatter.ofPattern(dateFormat);
        } catch (IllegalArgumentException e) {
            problems.add("islands.yml: members.date-format '" + dateFormat + "' is not a valid date pattern; using yyyy-MM-dd.");
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
