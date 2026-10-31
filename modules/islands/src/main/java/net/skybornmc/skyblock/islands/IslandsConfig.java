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

    @Setting(path = "size.max-chunks", desc = "Largest border width in chunks any island can ever have (odd; 15 = 240x240): the cap "
            + "of the paid sizes above size.normal-max-chunks (gems or black market, owner answers 157/167/171) and of /isadmin setsize.",
            min = 1, max = 31, level = Setting.Level.OWNER)
    public int maxChunks = 15;

    @Setting(path = "size.normal-max-chunks", desc = "Largest border width the normal Island Size upgrades reach (odd; owner answer "
            + "157: 3x3 up to 9x9). Bigger sizes only with gems or from the black market (hook for Phase 5 upgrades).", min = 1,
            max = 31, level = Setting.Level.OWNER)
    public int normalMaxChunks = 9;

    // Owner answer 169: island upgrades are paid from the island bank only (progression module, always). The old switch
    // upgrades.island-bank-only was read by nothing and is removed on load (removedKeys).

    @Setting(path = "creation.surface-y", desc = "Y of the starter island's top surface (the home block).", min = 1, max = 250)
    public int surfaceY = 100;

    // Owner answers 121/161/258/259 (round 1): no creation cooldown at all (old key creation.cooldown-hours is ignored).

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

    @Setting(path = "permissions.defaults.admin", desc = "Default permissions of island admins (ISL2; between leader and mod). "
            + "Same keys as the mod list; * = all of them. hurt-monsters and settings have their own role lists below.")
    public List<String> defaultsAdmin = List.of("*");

    @Setting(path = "permissions.defaults.mod", desc = "Default permissions of island mods (keys: break, place, interact, containers, "
            + "redstone, animals, villagers, pickup, drop, armor-stands, vehicles, fire, ender-pearls, spawners; * = all of these). "
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

    @Setting(path = "permissions.role-defaults.hurt-monsters", desc = "Roles that may hurt hostile mobs by default (owner #25; "
            + "roles: admin, mod, member, coop, visitor). Islands change it in the permission menu.")
    public List<String> hurtMonstersRoles = List.of("admin", "mod", "member", "coop", "visitor");

    @Setting(path = "permissions.role-defaults.settings", desc = "Roles that may change /is settings by default (the leader always can; "
            + "roles: admin, mod, member; coop players and visitors never).")
    public List<String> settingsRoles = List.of("admin");

    @Setting(path = "permissions.role-defaults.notes-read", desc = "Roles that may read /is notes by default (owner N1: members and "
            + "coop players; roles: admin, mod, member, coop, visitor). The leader always can.")
    public List<String> notesReadRoles = List.of("admin", "mod", "member", "coop");

    @Setting(path = "permissions.role-defaults.notes-write", desc = "Roles that may add notes, tick them done and change/pin/delete "
            + "their own by default (roles: admin, mod, member; coop players and visitors never).")
    public List<String> notesWriteRoles = List.of("admin", "mod", "member");

    @Setting(path = "permissions.role-defaults.notes-manage", desc = "Roles that may change, pin and delete anyone's notes and clear "
            + "done ones by default (roles: admin, mod, member).")
    public List<String> notesManageRoles = List.of("admin", "mod");

    @Setting(path = "permissions.role-defaults.bank-deposit", desc = "Roles that may put money into the island bank by default "
            + "(M3.3; roles: admin, mod, member; coop players and visitors never). The leader always can.")
    public List<String> bankDepositRoles = List.of("admin", "mod", "member");

    @Setting(path = "permissions.role-defaults.bank-withdraw", desc = "Roles that may take money out of the island bank by "
            + "default (M3.3; roles: admin, mod, member). The leader always can. Daily limits per role: bank.yml.")
    public List<String> bankWithdrawRoles = List.of("admin");

    @Setting(path = "permissions.role-defaults.bank-history", desc = "Roles that may see the island bank history by default "
            + "(M3.3; roles: admin, mod, member). The leader always can.")
    public List<String> bankHistoryRoles = List.of("admin", "mod");

    @Setting(path = "permissions.role-defaults.upgrades", desc = "Roles that may buy island upgrades with the island bank by default "
            + "(PHASE-5 M5.4, owner 170 / P5-10; roles: admin, mod, member). The leader always can; everyone can view and hand in.")
    public List<String> upgradesRoles = List.of("admin");

    // ------------------------------------------------------------------ NOTES (/is notes, owner N1-N9)

    @Setting(path = "notes.max-notes", desc = "Most notes an island can have, open and done together (owner N6: 50). Deleted notes "
            + "don't count.", min = 1, max = 500)
    public int notesMax = 50;

    @Setting(path = "notes.max-length", desc = "Most characters in one note (owner N6: 200).", min = 10, max = 250)
    public int notesMaxLength = 200;

    @Setting(path = "notes.max-holograms", desc = "Most notes an island can pin as a hologram at once (N9).", min = 0, max = 30)
    public int notesMaxHolograms = 5;

    @Setting(path = "notes.deleted-keep-days", desc = "Days deleted notes (also of disbanded islands) are kept for staff before "
            + "they are removed for good (owner N7: 7).", min = 1, max = 90)
    public int notesKeepDays = 7;

    @Setting(path = "notes.book-seconds", desc = "Seconds the temporary notes book stays in the inventory before it is taken back "
            + "unsaved (N3).", min = 30, max = 3600)
    public int notesBookSeconds = 300;

    @Setting(path = "notes.hologram.y-offset", desc = "Blocks above the pinned spot (the player's feet) the hologram floats.",
            min = 0, max = 4)
    public double notesHologramYOffset = 1.6;

    @Setting(path = "notes.hologram.line-width", desc = "Hologram text width in pixels before it wraps.", min = 60, max = 400)
    public int notesHologramLineWidth = 160;

    @Setting(path = "notes.hologram.view-range", desc = "Hologram view range multiplier (1 = about 64 blocks).", min = 0.1, max = 4)
    public double notesHologramViewRange = 0.5;

    @Setting(path = "notes.lore-width", desc = "Characters per lore line when a note is shown in the menu.", min = 20, max = 60)
    public int notesLoreWidth = 32;

    @Setting(path = "notes.scoreboard-length", desc = "Characters of the newest open note shown by the island_next_note placeholder "
            + "(longer ones end with ...).", min = 8, max = 64)
    public int notesScoreboardLength = 24;

    @Setting(path = "notes.add-cooldown-ms", desc = "Minimum time between two notes added by the same player (anti-spam).",
            min = 0, max = 60_000)
    public int notesAddCooldownMs = 2000;

    @Setting(path = "members.start-slots", desc = "Members an island can have at the start, leader included (owner answer 242: "
            + "4 members plus the leader = 5). The Member Slots upgrade (Phase 5) raises an island's own value.", min = 1, max = 32,
            level = Setting.Level.OWNER)
    public int startSlots = 5;

    @Setting(path = "members.max-slots", desc = "Most members any island can have, leader included (Member Slots upgrade cap: "
            + "8 members plus the leader = 9).", min = 1, max = 32, level = Setting.Level.OWNER)
    public int maxSlots = 9;

    @Setting(path = "members.max-joins-per-season", desc = "How many islands a player can join per season by accepting invites "
            + "(owner N2-1: 0 = unlimited; REVIEW A5 had 3).", min = 0, max = 100, level = Setting.Level.OWNER)
    public int maxJoinsPerSeason = 0;

    // Owner answers 121/161/258/259 (round 1) and principle 1 (REVIEW3): no cooldown on joining at all; the old key
    // members.join-cooldown-hours is deleted on load (removedKeys). The island side is the F10 slot lock below.

    @Setting(path = "members.slot-lock-hours", desc = "Owner F10: after a member leaves or is kicked, that member slot stays "
            + "locked this many hours before the island can invite someone into it (0 = off). Staff removals never lock.",
            min = 0, max = 720, level = Setting.Level.OWNER)
    public int slotLockHours = 48;

    @Setting(path = "members.slot-lock-inactive-days", desc = "Owner F10 / brief 4.2: kicking a member who was not online for "
            + "this many days frees the slot at once (no lock). 0 = every kick locks.", min = 0, max = 365, level = Setting.Level.OWNER)
    public int slotLockInactiveDays = 21;

    @Setting(path = "members.portal-invite-choice", desc = "TUTORIAL TQ19: a player without an island who has pending invites gets a choice at the island portal (create my own / join that island) instead of an island right away.")
    public boolean portalInviteChoice = true;

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

    // Owner answer 261 (round 1): /is disband confirms with a button, no typed word (old key lifecycle.disband-prompt-length
    // is ignored).

    @Setting(path = "lifecycle.profile-reset.on-disband", desc = "Owner answer 591: /is disband resets the profiles of everyone on "
            + "the island (money, inventory and ender chest; staff keep a backup, /isadmin restore).")
    public boolean profileResetOnDisband = true;

    @Setting(path = "lifecycle.profile-reset.on-reset", desc = "Owner answer 591: /isadmin reset resets the profiles of everyone on "
            + "the island too (backups kept), and deletes the island bank money (its amount is kept in the leader's backup).")
    public boolean profileResetOnReset = true;

    @Setting(path = "lifecycle.profile-reset.on-delete", desc = "/isadmin delete also resets the members' profiles (off: a staff "
            + "delete keeps profiles and the closed bank for staff to decide).")
    public boolean profileResetOnDelete = false;

    @Setting(path = "lifecycle.profile-reset.money", desc = "A profile reset takes the profile's money (restorable from the backup).")
    public boolean profileResetMoney = true;

    @Setting(path = "lifecycle.profile-reset.items", desc = "A profile reset empties the inventory and ender chest of members who "
            + "are online on that profile (restorable from the backup; offline members keep their items, see OPEN_QUESTIONS).")
    public boolean profileResetItems = true;

    /** Owner answer 591: does removing an island for {@code reason} reset its members' profiles? */
    public boolean resetsProfiles(String reason) {
        return switch (reason) {
            case IslandStore.REASON_DISBAND -> profileResetOnDisband;
            case IslandStore.REASON_RESET -> profileResetOnReset;
            case IslandStore.REASON_DELETE -> profileResetOnDelete;
            default -> false;
        };
    }

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

    // ------------------------------------------------------------------ M2.4 visiting, warps, showcase

    @Setting(path = "visiting.close-banned-leader-islands", desc = "Owner answer 522: while an island's leader is banned the island "
            + "is closed to visitors (hidden from the browser, visitors sent to spawn); members and coop players keep playing.")
    public boolean closeBannedLeaderIslands = true;

    @Setting(path = "visiting.banned-leader-refresh-seconds", desc = "Seconds between reads of the ban list (ended or lifted "
            + "bans reopen the island after at most this long; new bans close it at once).", min = 10, max = 3600, restart = true)
    public int bannedLeaderRefreshSeconds = 120;

    @Setting(path = "visiting.max-bans", desc = "Most players one island can ban.", min = 1, max = 1000)
    public int maxBans = 100;

    @Setting(path = "visiting.require-visit", desc = "Players must have visited an island before they can rate or like it.")
    public boolean requireVisit = true;

    @Setting(path = "visiting.vote-min-playtime-minutes", desc = "Owner answer 292: minutes of playtime before a player can rate or "
            + "like islands (0 = off).", min = 0, max = 10_080)
    public int voteMinPlaytimeMinutes = 60;

    @Setting(path = "visiting.vote-max-accounts-per-ip", desc = "Owner answer 292: most accounts from the same address that may "
            + "rate (and, separately, like) the same island this season (0 = no limit). Only a hash of the address is stored.",
            min = 0, max = 100)
    public int voteMaxAccountsPerIp = 3;

    @Setting(path = "visiting.timezone", desc = "Time zone of \"once per day\" (likes, unique visitors per day). Resets at local midnight.")
    public String timezone = "Europe/Oslo";

    @Setting(path = "visiting.notify-cooldown-seconds", desc = "The same visitor arriving or leaving again within this time is not "
            + "announced to the members again.", min = 0, max = 3600)
    public int notifyCooldownSeconds = 30;

    @Setting(path = "visiting.attempt-cooldown-seconds", desc = "Members hear about the same player trying to visit their locked "
            + "island at most once per this many seconds.", min = 0, max = 3600)
    public int attemptCooldownSeconds = 60;

    @Setting(path = "warps.start-slots", desc = "Warps an island can have at the start (owner: 3; sign warps count too). "
            + "A later upgrade raises an island's own value.", min = 0, max = 54, level = Setting.Level.OWNER)
    public int warpStartSlots = 3;

    @Setting(path = "warps.max-slots", desc = "Most warps any island can have (cap of the later warp upgrade).", min = 0, max = 54,
            level = Setting.Level.OWNER)
    public int warpMaxSlots = 10;

    @Setting(path = "warps.name-max-length", desc = "Longest warp name (letters, digits, _ and -).", min = 3, max = 32)
    public int warpNameMaxLength = 16;

    @Setting(path = "warps.sign-tag", desc = "First line of a sign that creates a warp (second line = name, third = category).")
    public String warpSignTag = "[warp]";

    @Setting(path = "warps.category-icons.shops", desc = "Icon of new warps in the shops category (item id).")
    public String iconShops = "emerald";

    @Setting(path = "warps.category-icons.farms", desc = "Icon of new warps in the farms category.")
    public String iconFarms = "wheat";

    @Setting(path = "warps.category-icons.parkour", desc = "Icon of new warps in the parkour category.")
    public String iconParkour = "feather";

    @Setting(path = "warps.category-icons.builds", desc = "Icon of new warps in the builds category.")
    public String iconBuilds = "bricks";

    @Setting(path = "warps.category-icons.other", desc = "Icon of new warps in the other category.")
    public String iconOther = "ender_pearl";

    @Setting(path = "warps.icons", desc = "Icons players can pick for their warps (item ids; one menu page holds 28).")
    public List<String> warpIcons = List.of(
            "ender_pearl", "emerald", "diamond", "gold_ingot", "iron_ingot", "chest", "barrel", "wheat", "carrot", "potato",
            "sugar_cane", "melon_slice", "pumpkin", "cactus", "oak_sapling", "feather", "slime_ball", "ladder", "bricks",
            "oak_planks", "stone_bricks", "quartz_block", "beacon", "nether_star", "spawner", "bone", "fishing_rod", "lantern");

    @Setting(path = "browser.visits-days", desc = "\"Most visited\" counts unique visitors per day over this many days.", min = 1,
            max = 365)
    public int browserVisitsDays = 7;

    @Setting(path = "browser.min-ratings", desc = "Islands need at least this many ratings to show under \"Top rated\".", min = 1,
            max = 100)
    public int browserMinRatings = 3;

    @Setting(path = "browser.top-rated-min-ips", desc = "Owner R3-33: islands need ratings from at least this many different "
            + "addresses (IPs) to show under \"Top rated\" (one address can't push an island up with alt accounts).", min = 1,
            max = 100, level = Setting.Level.OWNER)
    public int browserTopRatedMinIps = 5;

    @Setting(path = "browser.top-rated-prior-weight", desc = "Owner R3-33: \"Top rated\" ranks by a smoothed (Bayesian) average: "
            + "every island counts as if it also had this many ratings at the server-wide average, so 5 stars from 5 players "
            + "don't beat 4.8 stars from 100. 0 = plain average.", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double browserTopRatedPriorWeight = 5.0;

    @Setting(path = "browser.discover-playtime-weight", desc = "Owner answer 290: the Discover tab lists every island in random "
            + "order; islands with more playtime are more likely near the top (weight 1 + this x ln(1 + hours); 0 = plain random).",
            min = 0, max = 10)
    public double browserDiscoverPlaytimeWeight = 1.0;

    @Setting(path = "browser.refresh-seconds", desc = "How often visit, rating and like totals are reloaded from the database.",
            min = 10, max = 3600)
    public int browserRefreshSeconds = 120;

    // ------------------------------------------------------------------ ISL2 /is settings, island fly, personal homes

    @Setting(path = "flags.defaults.mob-spawning", desc = "Default of /is settings: hostile mobs spawn naturally (spawners always work).")
    public boolean flagMobSpawning = false;

    @Setting(path = "flags.defaults.animal-spawning", desc = "Default of /is settings: animals spawn naturally.")
    public boolean flagAnimalSpawning = true;

    @Setting(path = "flags.defaults.fire-spread", desc = "Default of /is settings: fire spreads and burns blocks.")
    public boolean flagFireSpread = false;

    @Setting(path = "flags.defaults.explosions", desc = "Default of /is settings: explosions break blocks.")
    public boolean flagExplosions = false;

    @Setting(path = "flags.defaults.leaf-decay", desc = "Default of /is settings: leaves decay.")
    public boolean flagLeafDecay = true;

    @Setting(path = "flags.defaults.crop-trampling", desc = "Default of /is settings: players and mobs trample farmland.")
    public boolean flagCropTrampling = false;

    @Setting(path = "flags.defaults.notifications", desc = "Default of /is settings: members hear about visitors (each member can "
            + "still mute them in /settings). Visitors picking up items is the visitor row of permissions.defaults.")
    public boolean flagNotifications = true;

    @Setting(path = "flags.defaults.visitor-fly", desc = "Default of /is settings: visitors with island fly may fly here.")
    public boolean flagVisitorFly = true;

    @Setting(path = "flags.defaults.weather", desc = "Default of /is settings: weather everyone on the island sees (sunny, rain, storm).")
    public String flagWeather = "sunny";

    @Setting(path = "flags.defaults.time", desc = "Default of /is settings: time everyone on the island sees (day, noon, sunset, night, "
            + "real = the server's local clock in visiting.timezone).")
    public String flagTime = "day";

    @Setting(path = "flags.time.day-ticks", desc = "Time of day (ticks, 0-23999) shown for \"day\".", min = 0, max = 23_999)
    public int timeDayTicks = 1000;

    @Setting(path = "flags.time.noon-ticks", desc = "Time of day (ticks) shown for \"noon\".", min = 0, max = 23_999)
    public int timeNoonTicks = 6000;

    @Setting(path = "flags.time.sunset-ticks", desc = "Time of day (ticks) shown for \"sunset\".", min = 0, max = 23_999)
    public int timeSunsetTicks = 12_000;

    @Setting(path = "flags.time.night-ticks", desc = "Time of day (ticks) shown for \"night\".", min = 0, max = 23_999)
    public int timeNightTicks = 18_000;

    @Setting(path = "flags.time.real-refresh-seconds", desc = "How often the \"real\" time is updated for players on such islands.",
            min = 5, max = 600)
    public int timeRealRefreshSeconds = 30;

    @Setting(path = "flags.weather.thunder-seconds", desc = "\"storm\": seconds between thunder sounds for players on the island "
            + "(only they hear it; 0 = rain without thunder).", min = 0, max = 600)
    public int stormThunderSeconds = 25;

    @Setting(path = "flags.weather.thunder-sound", desc = "Sound key of the storm thunder (e.g. entity.lightning_bolt.thunder).")
    public String stormThunderSound = "entity.lightning_bolt.thunder";

    @Setting(path = "flags.weather.thunder-volume", desc = "Volume of the storm thunder (0.1-2).", min = 0.1, max = 2)
    public double stormThunderVolume = 0.6;

    @Setting(path = "auto-pickup.enabled", desc = "QUICK1 (owner answer O15): blocks a member breaks on their own island go "
            + "straight into their inventory. What does not fit drops on the ground as usual (never to /box, so a full "
            + "inventory can't flood the box). Players switch it in /settings.")
    public boolean autoPickupEnabled = true;

    @Setting(path = "auto-pickup.default", desc = "Default of the /settings switch \"Island auto-pickup\" for players who never changed it.")
    public boolean autoPickupDefault = true;

    @Setting(path = "auto-pickup.full-hint-seconds", desc = "At most one \"inventory full\" action bar per this many seconds "
            + "while auto-pickup drops items on the ground. 0 = never.", min = 0, max = 600)
    public int autoPickupFullHintSeconds = 10;

    @Setting(path = "fly.check-interval-ticks", desc = "How often island fly is re-checked for players in the islands world "
            + "(permission, role, settings changes). Crossing the border is checked right away.", min = 5, max = 200)
    public int flyCheckTicks = 20;

    @Setting(path = "fly.landing-protection-seconds", desc = "After island fly is turned off (border, settings) or the player stops "
            + "flying, the next fall within this time does no damage.", min = 0, max = 60)
    public int flyLandingSeconds = 10;

    // Owner answer 247 (round 1): no personal member homes; the old homes.* keys are ignored.

    // ---------------------------------------------------------------- M2.5 island chat (/is chat)

    @Setting(path = "chat.palette", desc = "Colors the leader can pick for each role in island chat (owner I11: 8 colors). "
            + "Named colors (gold, red, aqua, green, yellow, light_purple, blue, white, ...) or #RRGGBB. Named colors look the same on Bedrock.")
    public List<String> chatPalette = List.of("gold", "red", "aqua", "green", "yellow", "light_purple", "blue", "white");

    @Setting(path = "chat.role-colors.leader", desc = "Default island chat color of the leader (until the leader picks another).")
    public String chatColorLeader = "gold";

    @Setting(path = "chat.role-colors.admin", desc = "Default island chat color of admins.")
    public String chatColorAdmin = "red";

    @Setting(path = "chat.role-colors.mod", desc = "Default island chat color of mods.")
    public String chatColorMod = "aqua";

    @Setting(path = "chat.role-colors.member", desc = "Default island chat color of members.")
    public String chatColorMember = "green";

    @Setting(path = "chat.console", desc = "Also write island chat to the server console (it is always in the audit log).")
    public boolean chatConsole = true;

    @Setting(path = "chat.max-length", desc = "Longest island chat message in characters (vanilla chat allows 256).", min = 16, max = 256)
    public int chatMaxLength = 256;

    /** The default chat color of a role ({@code chat.role-colors.*}). */
    String chatColor(net.skybornmc.skyblock.common.island.IslandService.Role role) {
        return switch (role) {
            case LEADER -> chatColorLeader;
            case ADMIN -> chatColorAdmin;
            case MOD -> chatColorMod;
            default -> chatColorMember;
        };
    }

    /** The {@code /is settings} defaults parsed from {@code flags.defaults.*} (problems = invalid values). */
    public FlagDefaults.Result flagDefaults() {
        java.util.Map<IslandFlag, String> map = new java.util.EnumMap<>(IslandFlag.class);
        map.put(IslandFlag.MOB_SPAWNING, onOff(flagMobSpawning));
        map.put(IslandFlag.ANIMAL_SPAWNING, onOff(flagAnimalSpawning));
        map.put(IslandFlag.FIRE_SPREAD, onOff(flagFireSpread));
        map.put(IslandFlag.EXPLOSIONS, onOff(flagExplosions));
        map.put(IslandFlag.LEAF_DECAY, onOff(flagLeafDecay));
        map.put(IslandFlag.CROP_TRAMPLING, onOff(flagCropTrampling));
        map.put(IslandFlag.NOTIFICATIONS, onOff(flagNotifications));
        map.put(IslandFlag.VISITOR_FLY, onOff(flagVisitorFly));
        map.put(IslandFlag.WEATHER, flagWeather);
        map.put(IslandFlag.TIME, flagTime);
        return FlagDefaults.parse(map);
    }

    private static String onOff(boolean value) {
        return value ? IslandFlag.ON : IslandFlag.OFF;
    }

    /** Ticks shown for a fixed {@code time} choice ({@code real} is computed by {@link IslandClock}). */
    int timeTicks(String choice) {
        return switch (choice) {
            case "noon" -> timeNoonTicks;
            case "sunset" -> timeSunsetTicks;
            case "night" -> timeNightTicks;
            default -> timeDayTicks;
        };
    }

    /** The default icon of a warp category (falls back to ender_pearl when the setting is empty). */
    String categoryIcon(WarpCategory category) {
        String icon = switch (category) {
            case SHOPS -> iconShops;
            case FARMS -> iconFarms;
            case PARKOUR -> iconParkour;
            case BUILDS -> iconBuilds;
            case OTHER -> iconOther;
        };
        return icon == null || icon.isBlank() ? "ender_pearl" : icon.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Warp slot limits for {@link VisitStore#saveWarp}. */
    VisitStore.WarpLimits warpLimits() {
        return new VisitStore.WarpLimits(Math.min(warpStartSlots, warpMaxSlots), warpMaxSlots);
    }

    /** The role defaults parsed from {@code permissions.defaults.*} (problems = unknown keys). */
    public RolePermissions.Result rolePermissions() {
        java.util.Map<IslandPerm, List<String>> perPermission = new java.util.EnumMap<>(IslandPerm.class);
        perPermission.put(IslandPerm.HURT_MONSTERS, hurtMonstersRoles);
        perPermission.put(IslandPerm.SETTINGS, settingsRoles);
        perPermission.put(IslandPerm.NOTES_READ, notesReadRoles);
        perPermission.put(IslandPerm.NOTES_WRITE, notesWriteRoles);
        perPermission.put(IslandPerm.NOTES_MANAGE, notesManageRoles);
        perPermission.put(IslandPerm.BANK_DEPOSIT, bankDepositRoles);
        perPermission.put(IslandPerm.BANK_WITHDRAW, bankWithdrawRoles);
        perPermission.put(IslandPerm.BANK_HISTORY, bankHistoryRoles);
        perPermission.put(IslandPerm.UPGRADES, upgradesRoles);
        return RolePermissions.parse(defaultsAdmin, defaultsMod, defaultsMember, defaultsCoop, defaultsVisitor, perPermission);
    }

    /** The join limits for invites and accepts (REVIEW A5, B1). */
    MemberStore.Limits memberLimits() {
        return new MemberStore.Limits(seasonId, maxJoinsPerSeason, Math.min(startSlots, maxSlots), maxSlots);
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

    /** The cap of the normal Island Size upgrades (owner answer 157: 9x9), odd and never above {@link #maxSize()}. */
    public int normalMaxSize() {
        return Math.min(odd(normalMaxChunks), maxSize());
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
        if (normalMaxChunks % 2 == 0) {
            problems.add("islands.yml: size.normal-max-chunks must be odd (" + normalMaxChunks + "); using " + normalMaxSize() + ".");
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
        try {
            java.time.ZoneId.of(timezone);
        } catch (RuntimeException e) {
            problems.add("islands.yml: visiting.timezone '" + timezone + "' is not a valid time zone; using UTC.");
        }
        if (warpStartSlots > warpMaxSlots) {
            problems.add("islands.yml: warps.start-slots is bigger than warps.max-slots; using " + warpMaxSlots + ".");
        }
        return problems;
    }

    static int odd(int value) {
        int v = Math.max(1, Math.min(IslandGrid.MAX_POSSIBLE_CHUNKS, value));
        return v % 2 == 0 ? v - 1 : v;
    }

    /**
     * Version 2 (owner answers round 1): member slots count the leader on top of the team (4 + leader = 5, cap 8 + leader
     * = 9); see {@link #upgradeValues}. Sizes need no value change: the new size.normal-max-chunks (9) caps normal upgrades.
     */
    @Override
    public int version() {
        return 2;
    }

    /** Old defaults that version 2 changes (only untouched old defaults are changed; owner-chosen values stay). */
    static final int OLD_START_SLOTS = 4;
    static final int OLD_MAX_SLOTS = 8;

    /**
     * Value changes a key rename can't do, applied to the raw file before it is loaded. Returns the changed keys (empty
     * when nothing changed). A new file (no keys) gets the new defaults anyway.
     */
    static List<String> upgradeValues(org.bukkit.configuration.file.YamlConfiguration yaml) {
        List<String> changed = new java.util.ArrayList<>();
        if (yaml.getKeys(false).isEmpty() || yaml.getInt("config-version", 1) >= 2) {
            return changed;
        }
        if (yaml.isInt("members.start-slots") && yaml.getInt("members.start-slots") == OLD_START_SLOTS) {
            yaml.set("members.start-slots", 5);
            changed.add("members.start-slots 4 -> 5");
        }
        if (yaml.isInt("members.max-slots") && yaml.getInt("members.max-slots") == OLD_MAX_SLOTS) {
            yaml.set("members.max-slots", 9);
            changed.add("members.max-slots 8 -> 9");
        }
        return changed;
    }

    /** REVIEW3 §3.6: keys no code reads any more, deleted on load (with a log line, ConfigManager#removeDeadKeys). */
    @Override
    public java.util.Map<String, String> removedKeys() {
        return java.util.Map.of(
                "lifecycle.disband-prompt-length", "owner answer 261: /is disband confirms with a button, no typed word",
                "homes", "owner answer 247: no personal member homes (shared homes come with P5-21)",
                "upgrades.island-bank-only", "owner answer 169: upgrades always pay from the island bank (progression), no switch",
                "members.join-cooldown-hours", "owner principle 1: no cooldowns on joining; the F10 48 h slot lock covers the island side");
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
