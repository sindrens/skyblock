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

    @Setting(path = "engine.remembered-per-player", desc = "SECFIX1 F-11: depleted blocks whose timer still runs are remembered when a player leaves or changes world (so relogging doesn't reset ores). Most remembered per player; above it the timers that end first are dropped.", min = 64, max = 100000)
    public int rememberedPerPlayer = 4096;

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

    // ------------------------------------------------------------------ Depths v2 tier palette (DV2-SPIKE)

    @Setting(path = "palette.enabled", desc = "DV2-SPIKE: the slot blocks of palette.zone show each player the block of their active tier (coal / copper / iron) with that tier's rewards. Off = the zone works as before. Spike only: staff turn it on to test.")
    public boolean paletteEnabled = false;

    @Setting(path = "palette.zone", desc = "DV2-SPIKE: the pilot zone (zones.yml id) whose ore/log fields become palette slots. Its field block must be tier 1's block.")
    public String paletteZone = "mine_t01";

    @Setting(path = "palette.tiers", desc = "DV2-SPIKE: the zones whose tiers the pilot zone shows, tier 1 first (zones.yml ids, same wing, mine or forest only). Each gives its field block, depleted look, respawn time, required-level and drop table tier.")
    public List<String> paletteTiers = List.of("mine_t01", "mine_t02", "mine_t03");

    @Setting(path = "palette.bedrock-blocks-per-tick", desc = "DV2-SPIKE: most slot blocks a Bedrock player gets per tick during a tier swap (Geyser sends one packet per block). 0 = no limit.", min = 0, max = 100000)
    public int paletteBedrockBlocksPerTick = 300;

    @Setting(path = "palette.java-blocks-per-tick", desc = "DV2-SPIKE: most slot blocks a Java player gets per tick during a tier swap. 0 = no limit (one multi-block change); set 300 to test the Bedrock throttle with Java.", min = 0, max = 100000)
    public int paletteJavaBlocksPerTick = 0;

    @Setting(path = "palette.log-swaps", desc = "DV2-SPIKE: write every tier swap to the console (player, tiers, block count, how many were queued, milliseconds; timers depths-palette-swap and depths-palette-drain).")
    public boolean paletteLogSwaps = true;

    @Setting(path = "palette.unlock-sound", desc = "DV2-SPIKE: sound (sounds.yml) with the 'Iron unlocked!' title (empty = none).")
    public String paletteUnlockSound = "unlock";

    @Setting(path = "palette.wave-particle", desc = "DV2-SPIKE: particle of the unlock wave over the swapped blocks (Bukkit particle name, empty = no wave).")
    public String paletteWaveParticle = "end_rod";

    @Setting(path = "palette.wave-speed", desc = "DV2-SPIKE: blocks the unlock wave travels per tick.", min = 0.5, max = 16.0)
    public double paletteWaveSpeed = 2.0;

    @Setting(path = "palette.wave-radius", desc = "DV2-SPIKE: how far (blocks) the unlock wave travels from the player.", min = 1, max = 128)
    public int paletteWaveRadius = 24;

    @Setting(path = "palette.wave-max-particles-per-tick", desc = "DV2-SPIKE: most wave particles per tick (keeps Bedrock smooth).", min = 1, max = 1000)
    public int paletteWaveMaxPerTick = 60;

    @Setting(path = "palette.effects-only-in-zone", desc = "DV2-SPIKE: the unlock title, sound and wave play only while the player stands in the pilot zone (elsewhere the tier still changes, quietly).")
    public boolean paletteEffectsOnlyInZone = true;

    // ------------------------------------------------------------------ /dungeon (M4.6)

    // Warmup and cancel rules of /dungeon moved to teleport.yml (F4: dungeon.*, cancel.*).

    @Setting(path = "teleport.combat-tiers-are-danger", desc = "Owner answer 345: the combat tiers count as a combat zone (the boss "
            + "arena always does), so teleports out of them wait teleport.yml danger.warmup-seconds.")
    public boolean teleportCombatTiersAreDanger = true;

    @Setting(path = "teleport.refuse-locked-tiers", desc = "Owner answer 346: /tpahere and other player teleports into a tier "
            + "the traveller has not unlocked are refused (the Depths bypass skips it).")
    public boolean teleportRefuseLockedTiers = true;

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

    @Setting(path = "satchel.full-warning-ms", desc = "Minimum milliseconds between two 'satchel full' (drops go to the inventory, then /box) or '/box full, mining stopped' warnings (R3-22: 3 s).", min = 0, max = 60000)
    public int satchelFullWarningMs = 3000;

    @Setting(path = "satchel.box-flush-seconds", desc = "R3-22: drops that fit neither the full satchel nor the inventory wait this long and then go to /box together (one box delivery per player, not one per block). Also sent on quit, profile switch and shutdown.", min = 1, max = 60)
    public int satchelBoxFlushSeconds = 5;

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

    // ------------------------------------------------------------------ key fragments (REVIEW3 R3-18)

    @Setting(path = "fragments.enabled", desc = "R3-18: mine and forest blocks of the high tiers can drop a key fragment (3 make a Phoenix key in /crates).")
    public boolean fragmentsEnabled = true;

    @Setting(path = "fragments.item", desc = "Drop item id of the fragment (drop-items in this file). It skips the satchel: inventory first, then /box.")
    public String fragmentsItem = "depths_key_fragment";

    @Setting(path = "fragments.min-tier", desc = "R3-18: lowest mine/forest tier that drops fragments.", min = 1, max = 99)
    public int fragmentsMinTier = 11;

    @Setting(path = "fragments.wings", desc = "Wings that drop fragments (mine, forest).")
    public List<String> fragmentsWings = List.of("mine", "forest");

    @Setting(path = "fragments.one-in", desc = "R3-18: chance per block/log is 1 in this many (rare-find bonuses like Treasure Hunter raise it). 18,000 = about 1 fragment per 3.3 h at tier 11-12 (~5,500-5,800 blocks/h, ECONOMY_BALANCE 3.3), so a key takes about 10 h (open question).", min = 1, max = 100_000_000)
    public int fragmentsOneIn = 18_000;

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

    @Setting(path = "combat.spawn-column-cache-seconds", desc = "Seconds a found standing height per arena column is remembered (the arena is protected, so it rarely changes). 0 = scan every time.", min = 0, max = 3600)
    public int combatSpawnColumnCacheSeconds = 300;

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

    @Setting(path = "combat.mini-boss.min-damage-share", desc = "R3-10: share of the damage a player must deal to get the full rewards (the killer too). Every eligible player gets the full reward, once.", min = 0.0, max = 1.0)
    public double miniBossMinDamageShare = 0.1;

    @Setting(path = "combat.mini-boss.recent-hit-seconds", desc = "R3-10: a player must have hit the mini-boss within this many seconds to get rewards and to count for its health scaling (0 = no limit).", min = 0, max = 3600)
    public int miniBossRecentHitSeconds = 90;

    @Setting(path = "combat.mini-boss.max-accounts-per-ip", desc = "R3-10: most accounts from one IP address that get rewards and count for scaling (most damage first; 0 = no limit).", min = 0, max = 100)
    public int miniBossMaxPerIp = 3;

    @Setting(path = "combat.mini-boss.health-per-extra-player", desc = "R3-10: extra mini-boss health in percent for each extra active fighter (hit recently, not AFK). The boss bar shows the scaled health.", min = 0.0, max = 500.0)
    public double miniBossHealthPerExtra = 25.0;

    @Setting(path = "combat.mini-boss.max-extra-players", desc = "R3-10: most extra fighters that raise the mini-boss's health (10 = up to +250% with 25% each).", min = 0, max = 100)
    public int miniBossMaxExtra = 10;

    @Setting(path = "combat.mini-boss.boss-bar", desc = "Show a boss bar with the mini-boss's health to players in its zone.")
    public boolean miniBossBar = true;

    @Setting(path = "combat.mini-boss.spawn-sound", desc = "Sound (sounds.yml) for players in the zone when a mini-boss appears (empty = none; players with 'Event sounds' off in /settings don't hear it).")
    public String miniBossSpawnSound = "unlock";

    @Setting(path = "combat.mini-boss.kill-sound", desc = "Sound (sounds.yml) for rewarded players when a mini-boss dies (empty = none).")
    public String miniBossKillSound = "money";

    // ------------------------------------------------------------------ mob abilities and elites (M8.1)

    @Setting(path = "combat.abilities.enabled", desc = "M8.1: mobs with abilities in mobs.yml use them. Off = abilities are ignored and mobs fight as before.")
    public boolean abilitiesEnabled = true;

    @Setting(path = "combat.abilities.min-tier", desc = "M8.1: lowest combat tier whose regular mobs use their abilities (WORLD 4.4: tiers 1-3 stay safe for new players). Mini-bosses always use theirs.", min = 1, max = 99)
    public int abilitiesMinTier = 4;

    @Setting(path = "combat.abilities.tick-ticks", desc = "M8.1: ticks between runs of the one shared ability scheduler (cooldown checks, telegraphs, hits).", min = 1, max = 20)
    public int abilitiesTickTicks = 2;

    @Setting(path = "combat.abilities.telegraph-ticks", desc = "M8.1: warning time before an ability hits (ring, sound, action bar). Never below 20 ticks (1 s) so a careful player can always dodge.", min = 20, max = 100)
    public int abilitiesTelegraphTicks = 20;

    @Setting(path = "combat.abilities.first-delay-ticks", desc = "M8.1: no ability starts earlier than this after a mob spawns (or its cooldown, if shorter).", min = 0, max = 1200)
    public int abilitiesFirstDelayTicks = 60;

    @Setting(path = "combat.abilities.retry-ticks", desc = "M8.1: after a failed chance roll an ability waits this long before it is rolled again.", min = 1, max = 200)
    public int abilitiesRetryTicks = 20;

    @Setting(path = "combat.abilities.active-range", desc = "M8.1: abilities only start while a player of the mob's zone is within this many blocks.", min = 4, max = 64)
    public int abilitiesActiveRange = 24;

    @Setting(path = "combat.abilities.effect-range", desc = "M8.1: players of the zone within this many blocks see the warning ring and get the warning line.", min = 8, max = 64)
    public int abilitiesEffectRange = 32;

    @Setting(path = "combat.abilities.max-active", desc = "M8.1: most ability effects running at once on the whole server (performance cap); mobs wait while it is full.", min = 1, max = 200)
    public int abilitiesMaxActive = 16;

    @Setting(path = "combat.abilities.max-ring-blocks", desc = "M8.1: most fake blocks one warning ring or lane shows.", min = 8, max = 512)
    public int abilitiesMaxRingBlocks = 128;

    @Setting(path = "combat.abilities.telegraph-block", desc = "M8.1: block of the warning ring/lane, shown only as a fake block for 1 s (Java and Bedrock).")
    public String abilitiesTelegraphBlock = "red_concrete";

    @Setting(path = "combat.abilities.fire-block", desc = "M8.1: fake block of a burning ring (ability ring, kind fire) while it burns.")
    public String abilitiesFireBlock = "magma_block";

    @Setting(path = "combat.abilities.soul-block", desc = "M8.1: fake block of a soul ring (ability ring, kind soul) while it burns.")
    public String abilitiesSoulBlock = "soul_soil";

    @Setting(path = "combat.abilities.telegraph-sound", desc = "M8.1: sound (sounds.yml) with every warning (empty = none). Not muted by 'Event sounds': it is a danger warning.")
    public String abilitiesTelegraphSound = "warning";

    @Setting(path = "combat.abilities.telegraph-particle", desc = "M8.1: extra particle over the warning ring (Bukkit particle name, empty = none). The fake blocks are the real warning.")
    public String abilitiesTelegraphParticle = "flame";

    @Setting(path = "combat.abilities.fire-ticks", desc = "M8.1: ticks a player burns after a pulse of a fire ring.", min = 0, max = 200)
    public int abilitiesFireTicks = 40;

    @Setting(path = "combat.abilities.leap-land-ticks", desc = "M8.1: ticks between a leap's jump and the landing hit in the marked spot.", min = 5, max = 40)
    public int abilitiesLeapLandTicks = 15;

    @Setting(path = "combat.abilities.arrow-life-ticks", desc = "M8.1: ticks a volley arrow stays on the ground before it disappears (it can never be picked up).", min = 1, max = 1200)
    public int abilitiesArrowLifeTicks = 60;

    @Setting(path = "combat.elite.health-multiplier", desc = "M8.1: max health of an elite (mobs.yml elite-chance above 0; 2 = twice).", min = 1.0, max = 20.0)
    public double eliteHealthMultiplier = 2.0;

    @Setting(path = "combat.elite.damage-multiplier", desc = "M8.1: damage per hit of an elite (1 = same as the normal mob).", min = 0.1, max = 10.0)
    public double eliteDamageMultiplier = 1.0;

    @Setting(path = "combat.elite.drop-multiplier", desc = "M8.1: drops and crystals of an elite kill (3 = three times).", min = 1.0, max = 20.0)
    public double eliteDropMultiplier = 3.0;

    @Setting(path = "combat.elite.xp-multiplier", desc = "M8.1: Combat XP of an elite kill (2 = twice, it takes twice as long).", min = 0.1, max = 20.0)
    public double eliteXpMultiplier = 2.0;

    @Setting(path = "combat.elite.min-tier", desc = "M8.1: lowest combat tier where elites can spawn (plan: tier 4+). Mini-bosses are never elites.", min = 1, max = 99)
    public int eliteMinTier = 4;

    @Setting(path = "combat.elite.budget-neutral", desc = "M8.1: normal kills of a mob that can be elite drop a little less, so the average money per kill (elites included) stays the same (plan: elites add excitement, not income).")
    public boolean eliteBudgetNeutral = true;

    @Setting(path = "combat.elite.extra-abilities", desc = "M8.1: an elite gets the first of these abilities it doesn't have yet (slam, leap, charge, volley, pull, summon, shield, ring, curse, teleport, enrage). Empty = no extra ability.")
    public List<String> eliteExtraAbilities = List.of("enrage", "shield", "slam");

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

    @Setting(path = "generate.causeways-of-built-zones", desc = "/depths generate also builds the causeway into a zone marked built "
            + "(the builders' zone schematics stop at the zone edge; set false once causeways are real builds).")
    public boolean causewaysOfBuiltZones = true;

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
