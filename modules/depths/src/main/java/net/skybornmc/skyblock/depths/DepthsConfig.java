package net.skybornmc.skyblock.depths;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

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

    // ------------------------------------------------------------------ tiers (M4.6)

    @Setting(path = "tiers.locks", desc = "Tier locks: zones need their skill level (zones.yml required-level). Off = every zone is open (gates stay open, nobody is pushed back).")
    public boolean tierLocks = true;

    @Setting(path = "tiers.season-id", desc = "Season the remembered tier unlocks / first visits belong to (matches skills.yml season-id).", min = 0, max = 10000, level = Setting.Level.OWNER)
    public int tierSeasonId = 0;

    @Setting(path = "tiers.gate-block", desc = "Block a player sees in the gate opening of a tier they have not unlocked (only for them; the real gate stays open). Bars/panes connect along the gate.")
    public String gateBlock = "minecraft:iron_bars";

    @Setting(path = "tiers.check-ticks", desc = "Ticks between the checks that open/close gates when levels change without a level-up (data loaded, staff set XP, reload) and remember unlocks.", min = 5, max = 1200)
    public int tierCheckTicks = 20;

    @Setting(path = "tiers.push-strength", desc = "Horizontal push away from a locked zone when a player tries to walk in.", min = 0.0, max = 3.0)
    public double pushStrength = 0.6;

    @Setting(path = "tiers.push-up", desc = "Upward part of that push.", min = 0.0, max = 2.0)
    public double pushUp = 0.25;

    @Setting(path = "tiers.message-cooldown-ms", desc = "Minimum milliseconds between two 'locked' messages/sounds for the same player.", min = 0, max = 60000)
    public int lockedMessageCooldownMs = 1500;

    @Setting(path = "tiers.locked-sound", desc = "Sound (sounds.yml) when a locked zone blocks a player (empty = none).")
    public String lockedSound = "error";

    @Setting(path = "tiers.first-visit-sound", desc = "Sound (sounds.yml) with the title on the first visit of a tier (empty = none).")
    public String firstVisitSound = "unlock";

    @Setting(path = "tiers.unlock-sound", desc = "Sound (sounds.yml) with the chat line when a level-up unlocks a tier (empty = none).")
    public String unlockSound = "unlock";

    // ------------------------------------------------------------------ /dungeon (M4.6)

    @Setting(path = "dungeon.warmup-seconds", desc = "/dungeon: seconds to stand still before the teleport (0 = instant; skyblock.depths.bypass skips it).", min = 0, max = 30)
    public int dungeonWarmupSeconds = 3;

    @Setting(path = "dungeon.cancel-on-move", desc = "/dungeon: moving to another block cancels the warmup.")
    public boolean dungeonCancelOnMove = true;

    @Setting(path = "dungeon.cancel-on-damage", desc = "/dungeon: taking damage cancels the warmup.")
    public boolean dungeonCancelOnDamage = true;

    @Setting(path = "dungeon.bar-length", desc = "/dungeon: segments of the progress bar on locked tiers.", min = 1, max = 20)
    public int dungeonBarLength = 10;

    @Setting(path = "dungeon.refresh-ticks", desc = "/dungeon: ticks between live refreshes of the open menu (levels, 'you are here'); 0 = never.", min = 0, max = 1200)
    public int dungeonRefreshTicks = 40;

    @Setting(path = "dungeon.locked-icon", desc = "/dungeon: item of a locked tier.")
    public String dungeonLockedIcon = "gray_dye";

    @Setting(path = "dungeon.tab-icons", desc = "/dungeon: tab items as wing=item (mine, forest, combat).")
    public List<String> dungeonTabIcons = List.of("mine=iron_pickaxe", "forest=oak_log", "combat=iron_sword");

    @Setting(path = "dungeon.fallback-icons", desc = "/dungeon: tier item when a zone has no ore/log field to show, as wing=item.")
    public List<String> dungeonFallbackIcons = List.of("mine=stone", "forest=oak_log", "combat=iron_sword");

    @Setting(path = "dungeon.combat-icons", desc = "/dungeon: unlocked combat tier items in tier order (tier 1 first); missing tiers use the fallback.")
    public List<String> dungeonCombatIcons = List.of("zombie_head", "skeleton_skull", "cobweb", "slime_ball", "trident",
            "potion", "crossbow", "blaze_rod", "wither_skeleton_skull", "totem_of_undying");

    // ------------------------------------------------------------------ satchel (M4.4)

    @Setting(path = "satchel.enabled", desc = "Depths drops go into the satchel (virtual storage per profile). Off = no drops are given at all.")
    public boolean satchelEnabled = true;

    @Setting(path = "satchel.base-capacity", desc = "Items every satchel holds before skills and upgrades.", min = 1, max = 1000000)
    public int satchelBaseCapacity = 256;

    @Setting(path = "satchel.per-mining-level", desc = "Extra satchel space per Mining level.", min = 0, max = 100000)
    public int satchelPerMiningLevel = 16;

    @Setting(path = "satchel.per-foraging-level", desc = "Extra satchel space per Foraging level.", min = 0, max = 100000)
    public int satchelPerForagingLevel = 16;

    @Setting(path = "satchel.max-capacity", desc = "Largest satchel possible, whatever skills, upgrades and bonuses add.", min = 1, max = 100000000)
    public int satchelMaxCapacity = 100000;

    @Setting(path = "satchel.upgrades", desc = "Satchel upgrades bought in the satchel menu, in order, as extra-space=price ($).")
    public List<String> satchelUpgrades = List.of("256=25000", "512=100000", "1024=400000", "2048=1600000", "4096=6400000");

    @Setting(path = "satchel.take-out", desc = "Players can take items out of the satchel into their inventory (to trade them). Selling and the Forge work without it.")
    public boolean satchelTakeOut = true;

    @Setting(path = "satchel.save-seconds", desc = "Seconds between batched satchel saves (new drops). Take-outs, sales and Forge use are saved at once.", min = 1, max = 60)
    public int satchelSaveSeconds = 5;

    @Setting(path = "satchel.audit-seconds", desc = "Seconds between the batched audit rows of Depths drops (one row per player with every item and crystal).", min = 10, max = 3600)
    public int satchelAuditSeconds = 60;

    @Setting(path = "satchel.full-warning-ms", desc = "Minimum milliseconds between two 'satchel full' action bar warnings (UI_STYLE: max one per 1.5 s).", min = 0, max = 60000)
    public int satchelFullWarningMs = 1500;

    @Setting(path = "satchel.full-sound", desc = "Sound (sounds.yml) with the 'satchel full' warning (empty = none).")
    public String satchelFullSound = "error";

    @Setting(path = "satchel.sell-sound", desc = "Sound (sounds.yml) after a satchel sale (empty = none).")
    public String satchelSellSound = "money";

    @Setting(path = "satchel.icon", desc = "Item of the Satchel button in /dungeon and the satchel menu header.")
    public String satchelIcon = "bundle";

    @Setting(path = "satchel.refresh-ticks", desc = "Ticks between live refreshes of the open satchel menu (new drops, prices); 0 = never.", min = 0, max = 1200)
    public int satchelRefreshTicks = 40;

    // ------------------------------------------------------------------ drops and crystals (M4.9)

    @Setting(path = "drops.max-per-block", desc = "Most drops one block/log can give with fortune (BonusFamily extra_drops: max 3 per action).", min = 1, max = 64)
    public int dropsMaxPerBlock = 3;

    @Setting(path = "drops.rare-sound", desc = "Sound (sounds.yml) when a rare drop is found (empty = none).")
    public String dropsRareSound = "unlock";

    @Setting(path = "crystals.batch-seconds", desc = "Seconds between crystal payouts: crystals found are added up per player and paid in one ledger row (reason depths_drop).", min = 1, max = 60)
    public int crystalsBatchSeconds = 3;

    @Setting(path = "crystals.action-bar", desc = "Show the crystals of each payout on the action bar.")
    public boolean crystalsActionBar = true;

    // ------------------------------------------------------------------ forest (M4.7)

    @Setting(path = "forest.max-trunk-height", desc = "Highest tree trunk (logs above the ground) a tree template may have; logs above it would be out of reach.", min = 1, max = 16)
    public int forestMaxTrunkHeight = 5;

    @Setting(path = "forest.no-leaf-decay", desc = "Leaves never decay in the Depths world (logs never really disappear there, but real builds may have far leaves).")
    public boolean forestNoLeafDecay = true;

    @Setting(path = "forest.protect-leaves", desc = "Leaves in forest zones are decoration: players can't break them (staff in creative with skyblock.depths.build can).")
    public boolean forestProtectLeaves = true;

    // ------------------------------------------------------------------ combat zones (M4.8)

    @Setting(path = "combat.enabled", desc = "Shared combat mobs spawn in the combat zones (mobs.yml).")
    public boolean combatEnabled = true;

    @Setting(path = "combat.cycle-ticks", desc = "Ticks between combat spawn cycles (all zones are handled in one batched pass).", min = 10, max = 1200)
    public int combatCycleTicks = 40;

    @Setting(path = "combat.max-total-mobs", desc = "Most combat mobs alive in all combat zones together (hard cap for performance).", min = 0, max = 5000)
    public int combatMaxTotalMobs = 300;

    @Setting(path = "combat.spawns-per-cycle", desc = "Most mobs one zone spawns per cycle (fills up gradually instead of all at once).", min = 1, max = 100)
    public int combatSpawnsPerCycle = 4;

    @Setting(path = "combat.spawn-attempts", desc = "Random spots tried per mob before giving up for this cycle.", min = 1, max = 64)
    public int combatSpawnAttempts = 8;

    @Setting(path = "combat.min-player-distance", desc = "Mobs never spawn closer than this many blocks to a player.", min = 0, max = 64)
    public int combatMinPlayerDistance = 6;

    @Setting(path = "combat.despawn-after-seconds", desc = "Seconds a combat zone may be empty of players before its mobs (and mini-boss) are removed.", min = 0, max = 3600)
    public int combatDespawnAfterSeconds = 30;

    @Setting(path = "combat.leash", desc = "Mobs that leave their zone are put back at a spawn spot in the arena.")
    public boolean combatLeash = true;

    @Setting(path = "combat.vanilla-xp", desc = "Kills give the mob's vanilla-xp (mobs.yml) straight to the killer (no orbs on the ground).")
    public boolean combatVanillaXp = true;

    @Setting(path = "combat.max-drop-multiplier", desc = "Most a kill's drops can be multiplied by looting/fortune bonuses.", min = 1.0, max = 10.0)
    public double combatMaxDropMultiplier = 3.0;

    @Setting(path = "combat.block-other-spawns", desc = "In the Depths world, mobs only appear when this plugin (or a command / spawn egg) spawns them: no natural spawns, reinforcements, vexes, jockeys or slime splits.")
    public boolean combatBlockOtherSpawns = true;

    @Setting(path = "combat.mini-boss.min-damage-share", desc = "Share of a mini-boss's health a player must deal to get its rewards (the killer always gets them).", min = 0.0, max = 1.0)
    public double miniBossMinDamageShare = 0.1;

    @Setting(path = "combat.mini-boss.boss-bar", desc = "Show a boss bar with the mini-boss's health to players in its zone.")
    public boolean miniBossBar = true;

    @Setting(path = "combat.mini-boss.spawn-sound", desc = "Sound (sounds.yml) for players in the zone when a mini-boss appears (empty = none).")
    public String miniBossSpawnSound = "unlock";

    @Setting(path = "combat.mini-boss.kill-sound", desc = "Sound (sounds.yml) for rewarded players when a mini-boss dies (empty = none).")
    public String miniBossKillSound = "money";

    // ------------------------------------------------------------------ ambient mobs (M4.8, REVIEW B6)

    @Setting(path = "ambient.enabled", desc = "Harmless per-player ambient mobs in mine and forest zones (only you see yours).")
    public boolean ambientEnabled = true;

    @Setting(path = "ambient.per-player", desc = "Ambient mobs around each player in a mine/forest zone.", min = 0, max = 10)
    public int ambientPerPlayer = 2;

    @Setting(path = "ambient.max-total", desc = "Most ambient mobs on the server at once (hard cap for performance).", min = 0, max = 5000)
    public int ambientMaxTotal = 300;

    @Setting(path = "ambient.cycle-ticks", desc = "Ticks between ambient cycles (spawn near players, remove far or left-behind ones).", min = 20, max = 2400)
    public int ambientCycleTicks = 100;

    @Setting(path = "ambient.radius-min", desc = "Ambient mobs spawn at least this many blocks from their player.", min = 1, max = 32)
    public int ambientRadiusMin = 5;

    @Setting(path = "ambient.radius-max", desc = "Ambient mobs spawn at most this many blocks from their player.", min = 2, max = 48)
    public int ambientRadiusMax = 12;

    @Setting(path = "ambient.despawn-distance", desc = "An ambient mob farther than this from its player is removed (a new one spawns nearby).", min = 8, max = 128)
    public int ambientDespawnDistance = 32;

    @Setting(path = "ambient.silent", desc = "Ambient mobs make no sounds (other players can't see them, so their sounds would come from nowhere).")
    public boolean ambientSilent = true;

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
                drop-items: the Depths drop items (custom item ids, also sell.yml keys and Forge materials).
                drops: drop table per wing and tier: item, amount, crystal-chance, crystal-amount, rare {item: chance}.
                  Blueprint chances stay in tools.yml (blueprints.<id>.drop), rolled by the tools module.
                trees: tree template per forest tier (shape, trunk, crown, spacing) for /depths generate (M4.7).
                Combat mobs, mini-bosses, combat drop items and ambient mobs are in mobs.yml (M4.8); their caps and
                timers are the combat.* and ambient.* settings here.
                Texts are in messages/depths.yml. Reload: /skyblock reload depths""";
    }
}
