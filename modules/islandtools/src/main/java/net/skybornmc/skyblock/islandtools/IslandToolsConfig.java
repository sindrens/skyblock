package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;
import java.util.Map;

/**
 * Settings of the island tools ({@code islandtools.yml}); texts in messages/islandtools.yml. Owner 4 Nov 2026: two tools
 * (harvester hoe and pickaxe), diamond from the start, owned by the island (level, XP, perks and prestige per island),
 * perks unlocking at tool levels, Farming and Mining Shards.
 */
public final class IslandToolsConfig extends ModuleConfig {

    @Setting(path = "enabled", desc = "Island tools on (every island member gets the island's hoe and pickaxe).")
    public boolean enabled = true;

    // ------------------------------------------------------------------ levels and prestige

    // ------------------------------------------------------------------ owner 6 Nov 2026: personal grind enchants

    @Setting(path = "enchants.enabled", desc = "Owner 6 Nov 2026: personal grind enchants (bought with your own shards, work only at /farm and /mine).")
    public boolean enchantsEnabled = true;

    @Setting(path = "enchants.list", desc = "Grind enchants (tools v6: pickaxe and hoe 26, rod 19, sword 15) as 'kind:id:max:cost:chance1:chanceMax:value1:valueMax:cooldownSeconds"
            + ":unlockLevel:unlockPrestige'. Chance (percent per block) and value grow linearly from level 1 to max; a level costs "
            + "cost x level^1.2 shards; the unlock is the tool level and prestige needed. Area ids (value = blocks, drops only, one per "
            + "break): blast, lightning, meteor, nuke / explode, row_reaper, tornado, meteor_shower. rich_vein/bountiful (extra items), "
            + "shard_burst/shard_jackpot (shards), shard_echo (percent more shards after a Burst), compound_interest (streak: up to "
            + "value percent more shards), combo (streak: up to value percent more tool XP), cash_vein/cash_crop/golden_crop (money = "
            + "sell price x value), quick_study/motherlode (extra unlock progress), scholar (percent more skill XP), geode/"
            + "harvest_basket (one of every unlocked ore/crop), lucky_streak (after value blocks without a rare find the rare chances "
            + "double), keyfinder (a crate key). Tools v6: shard_storm (seconds of a Shard Burst on every break), frenzy (seconds of "
            + "Haste III / Strength), golden_touch (the next N drops sell twice), duplicator (the next N drops doubled), xp_surge "
            + "(tool XP of N blocks), crystal_find (crystals), booster_find (seconds of x2 shards), chain_reaction (percent chance of "
            + "a second area enchant), shard_rain (shards for you and every island member online). Values compound from level 1 to max.",
            level = Setting.Level.OWNER)
    public List<String> enchantList = List.of(
            // tools v6 round 3 (owner 7 Nov 2026, plans/HOE_IMPROVEMENTS.md): fast-start growth to the listed max value, then
            // a milestone (+10%) every 4% of the track on the amount (or on the chance of area and timed enchants)
            "pickaxe:blast:2500:200:0.3:0.6:4:18:0:1:0",
            "pickaxe:shard_burst:2500:200:0.4:1.0:100:2500:0:1:0",
            "pickaxe:scholar:2500:200:0:0:5:25:0:1:0",
            "pickaxe:rich_vein:2500:200:1:5:1:5:0:1:0",
            "pickaxe:quick_study:2500:200:1:5:1:5:0:1:0",
            "pickaxe:combo:2500:200:0:0:5:25:0:5:0",
            "pickaxe:cash_vein:1000:300:0.5:2.0:2:10:0:9:0",
            "pickaxe:duplicator:1000:300:0.1:0.5:5:15:0:13:0",
            "pickaxe:compound_interest:1000:300:0:0:5:50:0:16:0",
            "pickaxe:shard_echo:1000:300:0:0:10:100:0:19:0",
            "pickaxe:lightning:1000:300:0.03:0.08:9:30:20:22:0",
            "pickaxe:golden_touch:1000:300:0.1:0.5:5:15:0:25:0",
            "pickaxe:motherlode:1000:300:0.2:0.6:10:50:0:28:0",
            "pickaxe:xp_surge:1000:300:0.2:0.8:20:100:0:30:0",
            "pickaxe:lucky_streak:1000:300:0:0:100:30:0:32:0",
            "pickaxe:frenzy:1000:300:0.05:0.2:5:20:30:34:0",
            "pickaxe:shard_storm:1000:400:0.02:0.1:3:10:60:36:0",
            "pickaxe:geode:500:400:0.02:0.1:1:1:0:38:0",
            "pickaxe:keyfinder:500:500:0.001:0.02:1:1:0:40:0",
            "pickaxe:shard_rain:1000:400:0.1:0.5:50:1250:0:42:0",
            "pickaxe:meteor:500:500:0.02:0.05:15:40:20:43:0",
            "pickaxe:crystal_find:500:500:0.005:0.05:1:3:0:44:0",
            "pickaxe:chain_reaction:1000:400:0:0:5:50:0:45:0",
            "pickaxe:booster_find:500:600:0.001:0.01:60:180:300:46:0",
            "pickaxe:shard_jackpot:500:600:0.01:0.04:2000:50000:0:47:0",
            "pickaxe:nuke:500:800:0.002:0.006:40:100:300:48:0",
            "hoe:explode:2500:200:0.3:0.6:4:18:0:1:0",
            "hoe:shard_burst:2500:200:0.4:1.0:100:2500:0:1:0",
            "hoe:scholar:2500:200:0:0:5:25:0:1:0",
            "hoe:bountiful:2500:200:1:5:1:5:0:1:0",
            "hoe:quick_study:2500:200:1:5:1:5:0:1:0",
            "hoe:combo:2500:200:0:0:5:25:0:5:0",
            "hoe:cash_crop:1000:300:0.5:2.0:2:10:0:9:0",
            "hoe:duplicator:1000:300:0.1:0.5:5:15:0:13:0",
            "hoe:compound_interest:1000:300:0:0:5:50:0:16:0",
            "hoe:shard_echo:1000:300:0:0:10:100:0:19:0",
            "hoe:row_reaper:1000:300:0.2:0.5:4:12:0:22:0",
            "hoe:golden_touch:1000:300:0.1:0.5:5:15:0:25:0",
            "hoe:golden_crop:1000:300:0.1:0.4:2:10:0:28:0",
            "hoe:xp_surge:1000:300:0.2:0.8:20:100:0:30:0",
            "hoe:lucky_streak:1000:300:0:0:100:30:0:32:0",
            "hoe:frenzy:1000:300:0.05:0.2:5:20:30:34:0",
            "hoe:shard_storm:1000:400:0.02:0.1:3:10:60:36:0",
            "hoe:harvest_basket:500:400:0.02:0.1:1:1:0:38:0",
            "hoe:keyfinder:500:500:0.001:0.02:1:1:0:40:0",
            "hoe:shard_rain:1000:400:0.1:0.5:50:1250:0:42:0",
            "hoe:tornado:500:500:0.02:0.05:20:40:20:43:0",
            "hoe:crystal_find:500:500:0.005:0.05:1:3:0:44:0",
            "hoe:chain_reaction:1000:400:0:0:5:50:0:45:0",
            "hoe:booster_find:500:600:0.001:0.01:60:180:300:46:0",
            "hoe:shard_jackpot:500:600:0.01:0.04:2000:50000:0:47:0",
            "hoe:meteor_shower:500:800:0.005:0.015:12:36:60:48:0",
            "rod:shard_burst:2500:20:4:10:400:10000:0:1:0",
            "rod:scholar:2500:20:0:0:5:25:0:1:0",
            "rod:big_catch:2500:20:10:40:1:5:0:1:0",
            "rod:quick_study:2500:20:5:20:1:5:0:1:0",
            "rod:combo:2500:20:0:0:5:25:0:5:0",
            "rod:golden_fish:1000:30:5:20:2:10:0:9:0",
            "rod:duplicator:1000:30:1:5:3:10:0:13:0",
            "rod:compound_interest:1000:30:0:0:5:50:0:16:0",
            "rod:shard_echo:1000:30:0:0:10:100:0:19:0",
            "rod:golden_touch:1000:30:1:5:3:10:0:25:0",
            "rod:xp_surge:1000:30:2:8:20:100:0:30:0",
            "rod:lucky_streak:1000:30:0:0:30:10:0:32:0",
            "rod:shard_storm:1000:40:0.2:1:10:30:60:36:0",
            "rod:treasure_chest:500:40:0.2:1:1:1:0:38:0",
            "rod:keyfinder:500:50:0.01:0.2:1:1:0:40:0",
            "rod:shard_rain:1000:40:1:5:100:2500:0:42:0",
            "rod:crystal_find:500:50:0.05:0.5:1:3:0:44:0",
            "rod:booster_find:500:60:0.01:0.1:60:180:300:46:0",
            "rod:shard_jackpot:500:60:0.1:0.4:5000:125000:0:47:0",
            "sword:shard_burst:2500:20:2:6:150:3750:0:1:0",
            "sword:scholar:2500:20:0:0:5:25:0:1:0",
            "sword:combo:2500:20:0:0:5:25:0:5:0",
            "sword:duplicator:1000:30:1:5:3:10:0:13:0",
            "sword:compound_interest:1000:30:0:0:5:50:0:16:0",
            "sword:shard_echo:1000:30:0:0:10:100:0:19:0",
            "sword:xp_surge:1000:30:1:4:20:100:0:30:0",
            "sword:lucky_streak:1000:30:0:0:50:15:0:32:0",
            "sword:frenzy:1000:30:0.2:1:5:20:30:34:0",
            "sword:shard_storm:1000:40:0.1:0.5:5:15:60:36:0",
            "sword:keyfinder:500:50:0.005:0.1:1:1:0:40:0",
            "sword:shard_rain:1000:40:0.5:2:50:1250:0:42:0",
            "sword:crystal_find:500:50:0.02:0.2:1:3:0:44:0",
            "sword:booster_find:500:60:0.005:0.05:60:180:300:46:0",
            "sword:shard_jackpot:500:60:0.05:0.2:3000:75000:0:47:0");

    /** The enchant list of update164 (version 18 moves an untouched list to the milestone values). */
    static final List<String> ENCHANTS_V17 = List.of(
            // tools v6 (owner 7 Nov 2026, plans/TOOLS_V6_COMPOUND.md): 2,500 / 1,000 / 500 levels by relevance, values compound
            // (x15 from level 1 to max), 9 new enchants, unlocks over the tool levels 1-48 (closer together near the top)
            "pickaxe:blast:2500:200:0.5:1.0:4:18:0:1:0",
            "pickaxe:shard_burst:2500:200:0.4:1.0:100:1500:0:1:0",
            "pickaxe:scholar:2500:200:0:0:5:75:0:1:0",
            "pickaxe:rich_vein:2500:200:1:5:1:15:0:1:0",
            "pickaxe:quick_study:2500:200:1:5:1:15:0:1:0",
            "pickaxe:combo:2500:200:0:0:5:75:0:5:0",
            "pickaxe:cash_vein:1000:300:0.5:2.0:5:75:0:9:0",
            "pickaxe:duplicator:1000:300:0.2:1.0:5:75:0:13:0",
            "pickaxe:compound_interest:1000:300:0:0:5:75:0:16:0",
            "pickaxe:shard_echo:1000:300:0:0:10:150:0:19:0",
            "pickaxe:lightning:1000:300:0.05:0.15:9:30:20:22:0",
            "pickaxe:golden_touch:1000:300:0.2:1.0:10:150:0:25:0",
            "pickaxe:motherlode:1000:300:0.2:0.6:10:150:0:28:0",
            "pickaxe:xp_surge:1000:300:0.2:0.8:20:300:0:30:0",
            "pickaxe:lucky_streak:1000:300:0:0:100:30:0:32:0",
            "pickaxe:frenzy:1000:300:0.1:0.5:5:20:30:34:0",
            "pickaxe:shard_storm:1000:400:0.05:0.2:3:10:60:36:0",
            "pickaxe:geode:500:400:0.05:0.2:1:1:0:38:0",
            "pickaxe:keyfinder:500:500:0.001:0.02:1:1:0:40:0",
            "pickaxe:shard_rain:1000:400:0.1:0.5:50:750:0:42:0",
            "pickaxe:meteor:500:500:0.03:0.08:15:40:20:43:0",
            "pickaxe:crystal_find:500:500:0.005:0.05:1:5:0:44:0",
            "pickaxe:chain_reaction:1000:400:0:0:5:50:0:45:0",
            "pickaxe:booster_find:500:600:0.002:0.02:60:180:300:46:0",
            "pickaxe:shard_jackpot:500:600:0.01:0.04:2000:30000:0:47:0",
            "pickaxe:nuke:500:800:0.003:0.01:40:100:300:48:0",
            "hoe:explode:2500:200:0.5:1.0:4:18:0:1:0",
            "hoe:shard_burst:2500:200:0.4:1.0:100:1500:0:1:0",
            "hoe:scholar:2500:200:0:0:5:75:0:1:0",
            "hoe:bountiful:2500:200:1:5:1:15:0:1:0",
            "hoe:quick_study:2500:200:1:5:1:15:0:1:0",
            "hoe:combo:2500:200:0:0:5:75:0:5:0",
            "hoe:cash_crop:1000:300:0.5:2.0:5:75:0:9:0",
            "hoe:duplicator:1000:300:0.2:1.0:5:75:0:13:0",
            "hoe:compound_interest:1000:300:0:0:5:75:0:16:0",
            "hoe:shard_echo:1000:300:0:0:10:150:0:19:0",
            "hoe:row_reaper:1000:300:0.3:0.7:4:12:0:22:0",
            "hoe:golden_touch:1000:300:0.2:1.0:10:150:0:25:0",
            "hoe:golden_crop:1000:300:0.1:0.4:5:75:0:28:0",
            "hoe:xp_surge:1000:300:0.2:0.8:20:300:0:30:0",
            "hoe:lucky_streak:1000:300:0:0:100:30:0:32:0",
            "hoe:frenzy:1000:300:0.1:0.5:5:20:30:34:0",
            "hoe:shard_storm:1000:400:0.05:0.2:3:10:60:36:0",
            "hoe:harvest_basket:500:400:0.05:0.2:1:1:0:38:0",
            "hoe:keyfinder:500:500:0.001:0.02:1:1:0:40:0",
            "hoe:shard_rain:1000:400:0.1:0.5:50:750:0:42:0",
            "hoe:tornado:500:500:0.03:0.08:20:40:20:43:0",
            "hoe:crystal_find:500:500:0.005:0.05:1:5:0:44:0",
            "hoe:chain_reaction:1000:400:0:0:5:50:0:45:0",
            "hoe:booster_find:500:600:0.002:0.02:60:180:300:46:0",
            "hoe:shard_jackpot:500:600:0.01:0.04:2000:30000:0:47:0",
            "hoe:meteor_shower:500:800:0.01:0.03:12:36:60:48:0",
            "rod:shard_burst:2500:20:4:10:400:6000:0:1:0",
            "rod:scholar:2500:20:0:0:5:75:0:1:0",
            "rod:big_catch:2500:20:10:40:1:15:0:1:0",
            "rod:quick_study:2500:20:5:20:1:15:0:1:0",
            "rod:combo:2500:20:0:0:5:75:0:5:0",
            "rod:golden_fish:1000:30:5:20:5:75:0:9:0",
            "rod:duplicator:1000:30:2:10:3:45:0:13:0",
            "rod:compound_interest:1000:30:0:0:5:75:0:16:0",
            "rod:shard_echo:1000:30:0:0:10:150:0:19:0",
            "rod:golden_touch:1000:30:2:10:5:75:0:25:0",
            "rod:xp_surge:1000:30:2:8:20:300:0:30:0",
            "rod:lucky_streak:1000:30:0:0:30:10:0:32:0",
            "rod:shard_storm:1000:40:0.5:2:10:30:60:36:0",
            "rod:treasure_chest:500:40:0.5:2:1:1:0:38:0",
            "rod:keyfinder:500:50:0.01:0.2:1:1:0:40:0",
            "rod:shard_rain:1000:40:1:5:100:1500:0:42:0",
            "rod:crystal_find:500:50:0.05:0.5:1:5:0:44:0",
            "rod:booster_find:500:60:0.02:0.2:60:180:300:46:0",
            "rod:shard_jackpot:500:60:0.1:0.4:5000:75000:0:47:0",
            "sword:shard_burst:2500:20:2:6:150:2250:0:1:0",
            "sword:scholar:2500:20:0:0:5:75:0:1:0",
            "sword:combo:2500:20:0:0:5:75:0:5:0",
            "sword:duplicator:1000:30:1:5:3:45:0:13:0",
            "sword:compound_interest:1000:30:0:0:5:75:0:16:0",
            "sword:shard_echo:1000:30:0:0:10:150:0:19:0",
            "sword:xp_surge:1000:30:1:4:20:300:0:30:0",
            "sword:lucky_streak:1000:30:0:0:50:15:0:32:0",
            "sword:frenzy:1000:30:0.5:2:5:20:30:34:0",
            "sword:shard_storm:1000:40:0.2:1:5:15:60:36:0",
            "sword:keyfinder:500:50:0.005:0.1:1:1:0:40:0",
            "sword:shard_rain:1000:40:0.5:2:50:750:0:42:0",
            "sword:crystal_find:500:50:0.02:0.2:1:5:0:44:0",
            "sword:booster_find:500:60:0.01:0.1:60:180:300:46:0",
            "sword:shard_jackpot:500:60:0.05:0.2:3000:45000:0:47:0");

    /** The enchant list of 7 Nov 2026 before tools v6 (version 17 moves an untouched list to the new one). */
    static final List<String> ENCHANTS_V16 = List.of(
            // owner 7 Nov 2026: max 500 (everyday), 300 (mid), 100 (rare); shards x10 (prices and Shard Burst/Jackpot x10)
            "pickaxe:blast:500:200:0.5:1.0:4:18:0:1:0",
            "pickaxe:shard_burst:500:200:0.4:1.0:30:60:0:1:0",
            "pickaxe:scholar:500:200:0:0:5:25:0:1:0",
            "pickaxe:rich_vein:500:200:1:5:1:2:0:3:0",
            "pickaxe:quick_study:500:200:1:5:1:1:0:5:0",
            "pickaxe:cash_vein:300:300:0.5:2.0:5:5:0:8:0",
            "pickaxe:combo:500:200:0:0:5:20:0:10:0",
            "pickaxe:compound_interest:300:300:0:0:5:15:0:13:0",
            "pickaxe:shard_echo:300:300:0:0:10:25:0:16:0",
            "pickaxe:lightning:300:300:0.05:0.15:9:18:20:20:0",
            "pickaxe:motherlode:300:300:0.2:0.6:10:10:0:24:0",
            "pickaxe:lucky_streak:300:300:0:0:100:50:0:28:0",
            "pickaxe:geode:100:400:0.05:0.2:1:1:0:32:0",
            "pickaxe:keyfinder:100:500:0.001:0.02:1:1:0:36:0",
            "pickaxe:meteor:100:500:0.03:0.08:15:25:20:1:1",
            "pickaxe:nuke:100:800:0.003:0.01:40:100:300:1:5",
            "pickaxe:shard_jackpot:100:600:0.01:0.04:400:750:0:1:10",
            "hoe:explode:500:200:0.5:1.0:4:18:0:1:0",
            "hoe:shard_burst:500:200:0.4:1.0:30:60:0:1:0",
            "hoe:scholar:500:200:0:0:5:25:0:1:0",
            "hoe:bountiful:500:200:1:5:1:2:0:3:0",
            "hoe:quick_study:500:200:1:5:1:1:0:5:0",
            "hoe:cash_crop:300:300:0.5:2.0:5:5:0:8:0",
            "hoe:combo:500:200:0:0:5:20:0:10:0",
            "hoe:compound_interest:300:300:0:0:5:15:0:13:0",
            "hoe:shard_echo:300:300:0:0:10:25:0:16:0",
            "hoe:row_reaper:300:300:0.3:0.7:4:7:0:20:0",
            "hoe:golden_crop:300:300:0.1:0.4:5:5:0:24:0",
            "hoe:lucky_streak:300:300:0:0:100:50:0:28:0",
            "hoe:harvest_basket:100:400:0.05:0.2:1:1:0:32:0",
            "hoe:keyfinder:100:500:0.001:0.02:1:1:0:36:0",
            "hoe:tornado:100:500:0.03:0.08:20:28:20:1:1",
            "hoe:meteor_shower:100:800:0.01:0.03:12:36:60:1:5",
            "hoe:shard_jackpot:100:600:0.01:0.04:400:1000:0:1:10",
            // owner 6 Nov 2026: the island rod at /fish (a catch is about 10x rarer than a block, so the chances are higher)
            "rod:shard_burst:1000:20:4:10:40:80:0:1:0",
            "rod:scholar:1000:20:0:0:5:25:0:1:0",
            "rod:big_catch:1000:20:10:40:1:2:0:1:0",
            "rod:quick_study:1000:20:5:20:1:2:0:5:0",
            "rod:golden_fish:1000:30:5:20:5:5:0:8:0",
            "rod:combo:1000:20:0:0:5:20:0:10:0",
            "rod:compound_interest:1000:30:0:0:5:15:0:13:0",
            "rod:shard_echo:1000:30:0:0:10:25:0:16:0",
            "rod:lucky_streak:1000:30:0:0:30:15:0:28:0",
            "rod:treasure_chest:1000:40:0.5:2:1:1:0:32:0",
            "rod:keyfinder:1000:50:0.01:0.2:1:1:0:36:0",
            "rod:shard_jackpot:1000:60:0.1:0.4:400:1000:0:1:10",
            // owner 6 Nov 2026: the island sword at /pve (no area, drops and progress enchants: kills go to the bag)
            "sword:shard_burst:1000:20:2:6:30:60:0:1:0",
            "sword:scholar:1000:20:0:0:5:25:0:1:0",
            "sword:combo:1000:20:0:0:5:20:0:10:0",
            "sword:compound_interest:1000:30:0:0:5:15:0:13:0",
            "sword:shard_echo:1000:30:0:0:10:25:0:16:0",
            "sword:lucky_streak:1000:30:0:0:50:25:0:28:0",
            "sword:keyfinder:1000:50:0.005:0.1:1:1:0:36:0",
            "sword:shard_jackpot:1000:60:0.05:0.2:400:1000:0:1:10");

    /** The enchant list of 6-7 Nov 2026 (version 13 moves an untouched list to the new prices and max levels). */
    static final List<String> ENCHANTS_V12 = List.of(
            "pickaxe:blast:1000:2:0.5:1.0:4:18:0:1:0",
            "pickaxe:shard_burst:1000:2:0.4:1.0:3:6:0:1:0",
            "pickaxe:scholar:1000:2:0:0:5:25:0:1:0",
            "pickaxe:rich_vein:1000:2:1:5:1:2:0:3:0",
            "pickaxe:quick_study:1000:2:1:5:1:1:0:5:0",
            "pickaxe:cash_vein:1000:3:0.5:2.0:5:5:0:8:0",
            "pickaxe:combo:1000:2:0:0:5:20:0:10:0",
            "pickaxe:compound_interest:1000:3:0:0:5:15:0:13:0",
            "pickaxe:shard_echo:1000:3:0:0:10:25:0:16:0",
            "pickaxe:lightning:1000:3:0.05:0.15:9:18:20:20:0",
            "pickaxe:motherlode:1000:3:0.2:0.6:10:10:0:24:0",
            "pickaxe:lucky_streak:1000:3:0:0:100:50:0:28:0",
            "pickaxe:geode:1000:4:0.05:0.2:1:1:0:32:0",
            "pickaxe:keyfinder:1000:5:0.001:0.02:1:1:0:36:0",
            "pickaxe:meteor:1000:5:0.03:0.08:15:25:20:1:1",
            "pickaxe:nuke:1000:8:0.003:0.01:40:100:300:1:5",
            "pickaxe:shard_jackpot:1000:6:0.01:0.04:40:75:0:1:10",
            "hoe:explode:1000:2:0.5:1.0:4:18:0:1:0",
            "hoe:shard_burst:1000:2:0.4:1.0:3:6:0:1:0",
            "hoe:scholar:1000:2:0:0:5:25:0:1:0",
            "hoe:bountiful:1000:2:1:5:1:2:0:3:0",
            "hoe:quick_study:1000:2:1:5:1:1:0:5:0",
            "hoe:cash_crop:1000:3:0.5:2.0:5:5:0:8:0",
            "hoe:combo:1000:2:0:0:5:20:0:10:0",
            "hoe:compound_interest:1000:3:0:0:5:15:0:13:0",
            "hoe:shard_echo:1000:3:0:0:10:25:0:16:0",
            "hoe:row_reaper:1000:3:0.3:0.7:4:7:0:20:0",
            "hoe:golden_crop:1000:3:0.1:0.4:5:5:0:24:0",
            "hoe:lucky_streak:1000:3:0:0:100:50:0:28:0",
            "hoe:harvest_basket:1000:4:0.05:0.2:1:1:0:32:0",
            "hoe:keyfinder:1000:5:0.001:0.02:1:1:0:36:0",
            "hoe:tornado:1000:5:0.03:0.08:20:28:20:1:1",
            "hoe:meteor_shower:1000:8:0.01:0.03:12:36:60:1:5",
            "hoe:shard_jackpot:1000:6:0.01:0.04:40:100:0:1:10",
            // owner 6 Nov 2026: the island rod at /fish (a catch is about 10x rarer than a block, so the chances are higher)
            "rod:shard_burst:1000:2:4:10:4:8:0:1:0",
            "rod:scholar:1000:2:0:0:5:25:0:1:0",
            "rod:big_catch:1000:2:10:40:1:2:0:1:0",
            "rod:quick_study:1000:2:5:20:1:2:0:5:0",
            "rod:golden_fish:1000:3:5:20:5:5:0:8:0",
            "rod:combo:1000:2:0:0:5:20:0:10:0",
            "rod:compound_interest:1000:3:0:0:5:15:0:13:0",
            "rod:shard_echo:1000:3:0:0:10:25:0:16:0",
            "rod:lucky_streak:1000:3:0:0:30:15:0:28:0",
            "rod:treasure_chest:1000:4:0.5:2:1:1:0:32:0",
            "rod:keyfinder:1000:5:0.01:0.2:1:1:0:36:0",
            "rod:shard_jackpot:1000:6:0.1:0.4:40:100:0:1:10",
            // owner 6 Nov 2026: the island sword at /pve (no area, drops and progress enchants: kills go to the bag)
            "sword:shard_burst:1000:2:2:6:3:6:0:1:0",
            "sword:scholar:1000:2:0:0:5:25:0:1:0",
            "sword:combo:1000:2:0:0:5:20:0:10:0",
            "sword:compound_interest:1000:3:0:0:5:15:0:13:0",
            "sword:shard_echo:1000:3:0:0:10:25:0:16:0",
            "sword:lucky_streak:1000:3:0:0:50:25:0:28:0",
            "sword:keyfinder:1000:5:0.005:0.1:1:1:0:36:0",
            "sword:shard_jackpot:1000:6:0.05:0.2:40:100:0:1:10");

    @Setting(path = "enchants.growth-shape", desc = "Tools v6 (owner 7 Nov 2026): how an enchant grows between level 1 and its max: 1 = even "
            + "steps, below 1 = the most per level at the start and still growing to the end (0.7: level 2 +10%, level 100 about 4x for Shard Burst).",
            min = 0.05, max = 3, level = Setting.Level.OWNER)
    public double enchantGrowthShape = 0.7;

    @Setting(path = "enchants.milestone-every-percent", desc = "Tools v6: a milestone every this many percent of an enchant's track "
            + "(4 = 25 milestones: every 100 levels of 2,500, 40 of 1,000, 20 of 500).", min = 0.5, max = 100, level = Setting.Level.OWNER)
    public double enchantMilestoneEvery = 4;

    @Setting(path = "enchants.milestone-bonus-percent", desc = "Tools v6: the extra boost of every milestone, in percent, compounding "
            + "(10 = x1.1 per milestone, about x11 after 25). It raises the amount, or the chance of area and timed enchants.",
            min = 0, max = 100, level = Setting.Level.OWNER)
    public double enchantMilestoneBonus = 10;

    @Setting(path = "enchants.cap-base", desc = "Lowest grind enchant level cap (a new tool).", min = 1, max = 100_000)
    public int enchantCapBase = 20;

    @Setting(path = "enchants.cap-full-levels", desc = "Tools v6: lifetime tool levels (prestige x max level + level) that open an "
            + "enchant's whole track. The cap is max x (lifetime / this)^cap-exponent: it grows faster the higher you get (100 = "
            + "prestige 2; level 50 at prestige 0 opens about a third).", min = 1, max = 100_000)
    public int enchantCapFullLevels = 100;

    @Setting(path = "enchants.cap-exponent", desc = "Tools v6: see cap-full-levels (1 = even steps, 1.5 = slow at first, fast near the top).", min = 0.1, max = 5)
    public double enchantCapExponent = 1.5;

    @Setting(path = "enchants.streak-step-percent", desc = "Compound Interest and Combo: percent bonus per block in a streak (up to the enchant's value).", min = 0, max = 100)
    public double enchantStreakStep = 0.1;

    @Setting(path = "enchants.streak-gap-shards", desc = "Compound Interest: seconds without a block that end the streak.", min = 1, max = 60)
    public int enchantStreakGapShards = 3;

    @Setting(path = "enchants.streak-gap-xp", desc = "Combo: seconds without a block that end the streak.", min = 1, max = 60)
    public int enchantStreakGapXp = 2;

    @Setting(path = "enchants.echo-seconds", desc = "Shard Echo: seconds of more shards after a Shard Burst.", min = 1, max = 120)
    public int enchantEchoSeconds = 10;

    @Setting(path = "enchants.shard-bonus-cap", desc = "Shard Echo and Compound Interest together give at most this many percent more shards (events come on top).", min = 0, max = 1000)
    public double enchantShardCap = 1000; // owner 7 Nov 2026: "more than 300%, we will have a high economy"

    @Setting(path = "enchants.area-per-tick", desc = "Area enchants break at most this many blocks per tick (the rest follow next ticks).", min = 1, max = 500)
    public int enchantAreaPerTick = 30;

    @Setting(path = "enchants.key-crate", desc = "Crate whose key Keyfinder finds.", level = Setting.Level.OWNER)
    public String enchantKeyCrate = "ember";

    @Setting(path = "levels.max", desc = "Highest tool level; at this level the player can prestige their own tool (owner 6 Nov 2026: personal prestige).", min = 2, max = 1000)
    public int maxLevel = 50;

    @Setting(path = "levels.xp-base", desc = "XP for level L to L+1 = xp-base x L^exponent x (1 + prestige-factor x prestige).", min = 0.01, max = 1_000_000)
    public double xpBase = 0.8; // owner 6-7 Nov 2026: personal prestige, about 15 min for the first

    @Setting(path = "levels.exponent", desc = "See levels.xp-base: how much slower every next level is.", min = 0, max = 5)
    public double xpExponent = 1.2;

    @Setting(path = "levels.prestige-factor", desc = "See levels.xp-base: every prestige makes the levels this much slower (0.15 = +15%).", min = 0, max = 10)
    public double xpPrestigeFactor = 0.15;

    @Setting(path = "levels.xp-per-block", desc = "Tool XP per relevant block (a ripe crop for the hoe, a natural ore or stone for the pickaxe), before the bonuses.", min = 0, max = 1000)
    public double xpPerBlock = 1;

    @Setting(path = "levels.hoe-xp-divisor", desc = "Owner 7 Nov 2026: the hoe gets its tool XP per crop divided by this (15 = 15x "
            + "slower levels and prestiges than before, so every level is felt and brings 15x more shards). 1 = like the other tools.",
            min = 1, max = 1000, level = Setting.Level.OWNER)
    public double hoeXpDivisor = 15;

    @Setting(path = "levels.title", desc = "A title on screen for the player whose block levelled the tool up (the other members get an action bar line).")
    public boolean levelTitle = true;

    @Setting(path = "prestige.bonus-percent", desc = "Owner 4 Nov 2026: the island's permanent bonus per prestige on that tool: this much more tool XP and shards of its type.", min = 0, max = 100)
    public double prestigeBonus = 5;

    @Setting(path = "prestige.max", desc = "Owner 6 Nov 2026: highest prestige of a player's own tool; after it come rebirths (no end).", min = 1, max = 1000)
    public int prestigeMax = 50;

    @Setting(path = "rebirth.bonus-percent", desc = "Owner 6 Nov 2026: lasting bonus per rebirth: this much more tool XP and shards of its type.", min = 0, max = 1000)
    public double rebirthBonus = 10;

    @Setting(path = "rebirth.xp-percent", desc = "Owner 6 Nov 2026: every rebirth needs this many percent more tool XP per level.", min = 0, max = 1000)
    public double rebirthXpPercent = 10;

    @Setting(path = "skins.list", desc = "Owner 7 Nov 2026: tool skins (hoe, pickaxe, sword) as 'id:credits'; the id is the vanilla "
            + "item set (iron, golden, copper, netherite, stone, wooden). Diamond is the free default. Only the look changes.", level = Setting.Level.OWNER)
    public List<String> skins = List.of("iron:250", "golden:500", "copper:500", "netherite:1000");

    @Setting(path = "rebirth.board-weight", desc = "Owner 7 Nov 2026: on the Top Tools board a rebirth counts as this many prestiges.", min = 1, max = 1000)
    public int rebirthBoardWeight = 50;

    @Setting(path = "prestige.discount-percent", desc = "Perks of a prestiged tool are this much cheaper per prestige.", min = 0, max = 100)
    public double prestigeDiscount = 5;

    @Setting(path = "prestige.price-floor-percent", desc = "Perk prices never drop under this share of the full price, however many prestiges.", min = 0, max = 100)
    public double priceFloor = 50;

    @Setting(path = "prestige.max-stars", desc = "Stars in the tool name up to this prestige (★★★★★); after it '★6', '★7' ...", min = 1, max = 10)
    public int maxStars = 5;


    // ------------------------------------------------------------------ shards

    @Setting(path = "shards.chance-pickaxe", desc = "Percent chance of 1 Mining Shard per relevant block mined with the island pickaxe (over 100 = more than one).", min = 0, max = 10_000)
    public double shardPickaxe = 1000; // tools v6: 10 a block (was 6)

    @Setting(path = "shards.chance-rod", desc = "Owner 6 Nov 2026: percent chance of 1 Fishing Shard per catch at /fish with the island rod (over 100 = more than one; a catch is much slower than a block).", min = 0, max = 1_000_000)
    public double shardRod = 30000; // tools v6: ~300 shards a catch (was 200)

    @Setting(path = "shards.chance-sword", desc = "Owner 6 Nov 2026: percent chance of 1 Combat Shard per /pve kill with the island sword (over 100 = more than one).", min = 0, max = 1_000_000)
    public double shardSword = 8000; // tools v6: ~80 shards a /pve kill (was 50)

    @Setting(path = "shards.chance-hoe", desc = "Percent chance of 1 Farming Shard per ripe crop harvested with the island hoe (over 100 = more than one).", min = 0, max = 10_000)
    public double shardHoe = 1000; // tools v6: 10 a crop (was 6)

    @Setting(path = "shards.finder-per-level", desc = "Shard Finder: percent more shards per level, COMPOUND (tools v6, owner 7 Nov 2026: 3% "
            + "a level on top of the last, 250 levels = about 1,600x shards; shards are always rounded up; the lore shows only the level).", min = 0, max = 20)
    public double shardFinderPerLevel = 3.0;

    @Setting(path = "shards.finder-milestone-every", desc = "Tools v6: Shard Finder gets an extra boost every this many levels (25 = ten up to 250).", min = 1, max = 10_000)
    public int shardFinderMilestoneEvery = 25;

    @Setting(path = "shards.finder-milestone-bonus", desc = "Tools v6: the extra boost of a Shard Finder milestone, in percent, compounding.", min = 0, max = 100)
    public double shardFinderMilestoneBonus = 10;

    @Setting(path = "prestige.key-crate", desc = "Owner 7 Nov 2026: crate key every prestige (and rebirth) of a player's own tool (empty = none).")
    public String prestigeKeyCrate = "blaze";

    @Setting(path = "prestige.key-crate-tenth", desc = "Owner 7 Nov 2026: crate key instead at every 10th prestige and every rebirth.")
    public String prestigeKeyCrateTenth = "phoenix";

    @Setting(path = "shards.payout-seconds", desc = "Found shards are paid in one batch this often.", min = 1, max = 60)
    public int shardPayoutSeconds = 3;

    @Setting(path = "shards.popups", desc = "Owner 7 Nov 2026: every shard find shows as a small floating text on the block or mob "
            + "that gave it (gone after 0.5 s, only you see it) instead of an action bar line.")
    public boolean shardPopups = true;

    @Setting(path = "shards.popup-every-ms", desc = "Owner 7 Nov 2026 (\"spammy\"): shard popups are added up and shown at most this often (one number, not one per block).", min = 0, max = 10000)
    public int shardPopupEveryMillis = 1000;

    @Setting(path = "enchants.quiet", desc = "Owner 7 Nov 2026 (\"spammy\"): enchants that proc often show no action bar line (you see their effect anyway). "
            + "The others share one line with the combo counter.")
    public List<String> enchantQuiet = List.of("explode", "blast", "row_reaper", "lightning", "bountiful", "rich_vein", "big_catch",
            "cash_crop", "cash_vein", "golden_fish", "quick_study", "scholar", "xp_surge", "duplicator", "golden_touch");

    @Setting(path = "auto-sell.price-percent", desc = "Auto-Sell sells drops as you break them at this percent of the sell price (the other selling bonuses apply too).", min = 1, max = 100)
    public double autoSellPercent = 90;

    @Setting(path = "auto-sell.batch-seconds", desc = "Auto-sold drops are paid in one sale this often.", min = 1, max = 60)
    public int autoSellSeconds = 3;

    // ------------------------------------------------------------------ perks

    @Setting(path = "relevant.pickaxe", desc = "Blocks where the island pickaxe earns XP, shards and its perks (block ids, * wildcard at the start or end). Blocks a player placed never count.")
    public List<String> relevantPickaxe = List.of("*_ore", "stone", "cobblestone", "deepslate", "cobbled_deepslate", "andesite",
            "diorite", "granite", "tuff", "calcite", "netherrack", "basalt", "blackstone", "end_stone", "obsidian", "ancient_debris",
            "amethyst_cluster", "raw_*_block");

    @Setting(path = "relevant.hoe", desc = "Blocks where the island hoe earns XP, shards and its perks (ripe crops only, always).")
    public List<String> relevantHoe = List.of("wheat", "carrots", "potatoes", "beetroots", "nether_wart", "cocoa", "melon", "pumpkin",
            "sweet_berry_bush", "sugar_cane", "cactus", "bamboo");

    @Setting(path = "placed.memory-hours", desc = "How long a block a player placed is remembered (so breaking it gives no bonus).", min = 1, max = 168)
    public int placedHours = 12;

    @Setting(path = "perks", desc = "Owner 4 Nov 2026: perks per tool, 'kind:id:effect:max:unlock-level:costs[:prestige]'. costs 'base+step' = a long "
            + "track (price of level L = base + step x L^prices.exponent, nice-rounded) or 'a/b/c' = the price of each level. Effects: "
            + "enchant=<vanilla id>, haste, smelt, drops (Extra Drops), storage (hoe), shards (Shard Finder), autosell (Auto-Sell), growth "
            + "(island crop growth, hoe), sweep (hoe Sweep / pickaxe Vein), wisdom (more tool XP), sell (Sell Bonus), lucky (Shard Pouch finds), compact (Auto-Compactor: 9 items become a block). "
            + "Magnet is built in (drops go straight to the inventory). Prices are paid in the tool's own shards.")
    public List<String> perks = List.of( // tools v6: Shard Finder 250 levels, +3% compound each, price +3.5% a level (tools v5: personal long tracks)
            "hoe:shard_finder:shards:250:1:150*3.5", "hoe:efficiency:enchant=efficiency:5:1:300/1200/4000/12000/30000",
            "hoe:extra_drops:drops:1000:3:120+16", "hoe:fortune:enchant=fortune:3:5:3000/16000/60000",
            "hoe:wisdom:wisdom:500:5:160+20", "hoe:growth:growth:500:10:200+20", "hoe:storage:storage:500:10:120+12",
            "hoe:haste:haste:2:15:10000/60000", "hoe:auto_sell:autosell:1:20:40000", "hoe:sell_bonus:sell:500:25:400+40",
            "hoe:lucky:lucky:1000:30:600+50", "hoe:sweep:sweep:100:30:6000+200", "hoe:compactor:compact:1:15:40000",
            "pickaxe:shard_finder:shards:250:1:150*3.5", "pickaxe:efficiency:enchant=efficiency:5:1:300/1200/4000/12000/30000",
            "pickaxe:extra_drops:drops:1000:3:120+16", "pickaxe:fortune:enchant=fortune:3:5:3000/16000/60000",
            "pickaxe:wisdom:wisdom:500:5:160+20", "pickaxe:smelter:smelt:100:10:200+20", "pickaxe:storage:storage:500:10:120+12",
            "pickaxe:haste:haste:2:15:10000/60000", "pickaxe:auto_sell:autosell:1:20:40000", "pickaxe:sell_bonus:sell:500:25:400+40",
            "pickaxe:lucky:lucky:1000:30:600+50", "pickaxe:vein:sweep:100:30:6000+200", "pickaxe:compactor:compact:1:15:40000",
            "rod:shard_finder:shards:250:1:150*3.5", "rod:lure:enchant=lure:3:1:600/4000/18000",
            "rod:extra_drops:drops:1000:3:120+16", "rod:luck:enchant=luck_of_the_sea:3:5:3000/16000/60000",
            "rod:wisdom:wisdom:500:5:160+20", "rod:storage:storage:500:10:120+12", "rod:auto_sell:autosell:1:20:40000",
            "rod:sell_bonus:sell:500:25:400+40", "rod:lucky:lucky:1000:30:600+50",
            "sword:shard_finder:shards:250:1:150*3.5", "sword:sharpness:enchant=sharpness:5:1:300/1200/4000/12000/30000",
            "sword:looting:enchant=looting:3:5:3000/16000/60000", "sword:wisdom:wisdom:500:5:160+20",
            "sword:sweeping:enchant=sweeping_edge:3:10:2000/10000/40000", "sword:lucky:lucky:1000:30:600+50");

    /** {@code perks} v17 (before Sweep and Vein lost their prestige gate). */
    static final List<String> PERKS_V17 = List.of( // tools v6: Shard Finder 250 levels, +3% compound each, price +3.5% a level (tools v5: personal long tracks)
            "hoe:shard_finder:shards:250:1:150*3.5", "hoe:efficiency:enchant=efficiency:5:1:300/1200/4000/12000/30000",
            "hoe:extra_drops:drops:1000:3:120+16", "hoe:fortune:enchant=fortune:3:5:3000/16000/60000",
            "hoe:wisdom:wisdom:500:5:160+20", "hoe:growth:growth:500:10:200+20", "hoe:storage:storage:500:10:120+12",
            "hoe:haste:haste:2:15:10000/60000", "hoe:auto_sell:autosell:1:20:40000", "hoe:sell_bonus:sell:500:25:400+40",
            "hoe:lucky:lucky:1000:30:600+50", "hoe:sweep:sweep:100:45:6000+200:1", "hoe:compactor:compact:1:15:40000",
            "pickaxe:shard_finder:shards:250:1:150*3.5", "pickaxe:efficiency:enchant=efficiency:5:1:300/1200/4000/12000/30000",
            "pickaxe:extra_drops:drops:1000:3:120+16", "pickaxe:fortune:enchant=fortune:3:5:3000/16000/60000",
            "pickaxe:wisdom:wisdom:500:5:160+20", "pickaxe:smelter:smelt:100:10:200+20", "pickaxe:storage:storage:500:10:120+12",
            "pickaxe:haste:haste:2:15:10000/60000", "pickaxe:auto_sell:autosell:1:20:40000", "pickaxe:sell_bonus:sell:500:25:400+40",
            "pickaxe:lucky:lucky:1000:30:600+50", "pickaxe:vein:sweep:100:45:6000+200:1", "pickaxe:compactor:compact:1:15:40000",
            "rod:shard_finder:shards:250:1:150*3.5", "rod:lure:enchant=lure:3:1:600/4000/18000",
            "rod:extra_drops:drops:1000:3:120+16", "rod:luck:enchant=luck_of_the_sea:3:5:3000/16000/60000",
            "rod:wisdom:wisdom:500:5:160+20", "rod:storage:storage:500:10:120+12", "rod:auto_sell:autosell:1:20:40000",
            "rod:sell_bonus:sell:500:25:400+40", "rod:lucky:lucky:1000:30:600+50",
            "sword:shard_finder:shards:250:1:150*3.5", "sword:sharpness:enchant=sharpness:5:1:300/1200/4000/12000/30000",
            "sword:looting:enchant=looting:3:5:3000/16000/60000", "sword:wisdom:wisdom:500:5:160+20",
            "sword:sweeping:enchant=sweeping_edge:3:10:2000/10000/40000", "sword:lucky:lucky:1000:30:600+50");

    /** {@code perks} v16 (before tools v6: Shard Finder 2,500 levels at +1% each). */
    static final List<String> PERKS_V16 = List.of( // owner 7 Nov 2026 (tools v5): personal, long tracks, priced for ~13 blocks a second
            "hoe:shard_finder:shards:2500:1:80+12", "hoe:efficiency:enchant=efficiency:5:1:300/1200/4000/12000/30000",
            "hoe:extra_drops:drops:1000:3:120+16", "hoe:fortune:enchant=fortune:3:5:3000/16000/60000",
            "hoe:wisdom:wisdom:500:5:160+20", "hoe:growth:growth:500:10:200+20", "hoe:storage:storage:500:10:120+12",
            "hoe:haste:haste:2:15:10000/60000", "hoe:auto_sell:autosell:1:20:40000", "hoe:sell_bonus:sell:500:25:400+40",
            "hoe:lucky:lucky:1000:30:600+50", "hoe:sweep:sweep:100:45:6000+200:1", "hoe:compactor:compact:1:15:40000",
            "pickaxe:shard_finder:shards:2500:1:80+12", "pickaxe:efficiency:enchant=efficiency:5:1:300/1200/4000/12000/30000",
            "pickaxe:extra_drops:drops:1000:3:120+16", "pickaxe:fortune:enchant=fortune:3:5:3000/16000/60000",
            "pickaxe:wisdom:wisdom:500:5:160+20", "pickaxe:smelter:smelt:100:10:200+20", "pickaxe:storage:storage:500:10:120+12",
            "pickaxe:haste:haste:2:15:10000/60000", "pickaxe:auto_sell:autosell:1:20:40000", "pickaxe:sell_bonus:sell:500:25:400+40",
            "pickaxe:lucky:lucky:1000:30:600+50", "pickaxe:vein:sweep:100:45:6000+200:1", "pickaxe:compactor:compact:1:15:40000",
            "rod:shard_finder:shards:2500:1:80+12", "rod:lure:enchant=lure:3:1:600/4000/18000",
            "rod:extra_drops:drops:1000:3:120+16", "rod:luck:enchant=luck_of_the_sea:3:5:3000/16000/60000",
            "rod:wisdom:wisdom:500:5:160+20", "rod:storage:storage:500:10:120+12", "rod:auto_sell:autosell:1:20:40000",
            "rod:sell_bonus:sell:500:25:400+40", "rod:lucky:lucky:1000:30:600+50",
            "sword:shard_finder:shards:2500:1:80+12", "sword:sharpness:enchant=sharpness:5:1:300/1200/4000/12000/30000",
            "sword:looting:enchant=looting:3:5:3000/16000/60000", "sword:wisdom:wisdom:500:5:160+20",
            "sword:sweeping:enchant=sweeping_edge:3:10:2000/10000/40000", "sword:lucky:lucky:1000:30:600+50");

    /** {@code perks} v15 (before the Auto-Compactor, owner 7 Nov 2026). */
    static final List<String> PERKS_V15 = List.of(
            "hoe:shard_finder:shards:2500:1:80+12", "hoe:efficiency:enchant=efficiency:5:1:300/1200/4000/12000/30000",
            "hoe:extra_drops:drops:1000:3:120+16", "hoe:fortune:enchant=fortune:3:5:3000/16000/60000",
            "hoe:wisdom:wisdom:500:5:160+20", "hoe:growth:growth:500:10:200+20", "hoe:storage:storage:500:10:120+12",
            "hoe:haste:haste:2:15:10000/60000", "hoe:auto_sell:autosell:1:20:40000", "hoe:sell_bonus:sell:500:25:400+40",
            "hoe:lucky:lucky:1000:30:600+50", "hoe:sweep:sweep:100:45:6000+200:1",
            "pickaxe:shard_finder:shards:2500:1:80+12", "pickaxe:efficiency:enchant=efficiency:5:1:300/1200/4000/12000/30000",
            "pickaxe:extra_drops:drops:1000:3:120+16", "pickaxe:fortune:enchant=fortune:3:5:3000/16000/60000",
            "pickaxe:wisdom:wisdom:500:5:160+20", "pickaxe:smelter:smelt:100:10:200+20", "pickaxe:storage:storage:500:10:120+12",
            "pickaxe:haste:haste:2:15:10000/60000", "pickaxe:auto_sell:autosell:1:20:40000", "pickaxe:sell_bonus:sell:500:25:400+40",
            "pickaxe:lucky:lucky:1000:30:600+50", "pickaxe:vein:sweep:100:45:6000+200:1",
            "rod:shard_finder:shards:2500:1:80+12", "rod:lure:enchant=lure:3:1:600/4000/18000",
            "rod:extra_drops:drops:1000:3:120+16", "rod:luck:enchant=luck_of_the_sea:3:5:3000/16000/60000",
            "rod:wisdom:wisdom:500:5:160+20", "rod:storage:storage:500:10:120+12", "rod:auto_sell:autosell:1:20:40000",
            "rod:sell_bonus:sell:500:25:400+40", "rod:lucky:lucky:1000:30:600+50",
            "sword:shard_finder:shards:2500:1:80+12", "sword:sharpness:enchant=sharpness:5:1:300/1200/4000/12000/30000",
            "sword:looting:enchant=looting:3:5:3000/16000/60000", "sword:wisdom:wisdom:500:5:160+20",
            "sword:sweeping:enchant=sweeping_edge:3:10:2000/10000/40000", "sword:lucky:lucky:1000:30:600+50");

    /** {@code perks} v14 (shared island perks, before tools v5). */
    static final List<String> PERKS_V14 = List.of( // owner 7 Nov 2026: shards x10 (prices x10 too)
            "hoe:shard_finder:shards:100:1:400+60", "hoe:efficiency:enchant=efficiency:5:1:1500/6000/20000/60000/150000",
            "hoe:extra_drops:drops:100:3:600+80", "hoe:fortune:enchant=fortune:3:5:15000/80000/300000",
            "hoe:wisdom:wisdom:50:5:800+100", "hoe:growth:growth:100:10:1000+100", "hoe:storage:storage:100:10:600+60",
            "hoe:haste:haste:2:15:50000/300000", "hoe:auto_sell:autosell:1:20:200000", "hoe:sell_bonus:sell:100:25:2000+200",
            "hoe:lucky:lucky:100:30:3000+250", "hoe:sweep:sweep:100:45:30000+1000:1",
            "pickaxe:shard_finder:shards:100:1:400+60", "pickaxe:efficiency:enchant=efficiency:5:1:1500/6000/20000/60000/150000",
            "pickaxe:extra_drops:drops:100:3:600+80", "pickaxe:fortune:enchant=fortune:3:5:15000/80000/300000",
            "pickaxe:wisdom:wisdom:50:5:800+100", "pickaxe:smelter:smelt:100:10:1000+100", "pickaxe:storage:storage:100:10:600+60",
            "pickaxe:haste:haste:2:15:50000/300000", "pickaxe:auto_sell:autosell:1:20:200000", "pickaxe:sell_bonus:sell:100:25:2000+200",
            "pickaxe:lucky:lucky:100:30:3000+250", "pickaxe:vein:sweep:100:45:30000+1000:1",
            // owner 6 Nov 2026: the island rod (/fish)
            "rod:shard_finder:shards:100:1:400+60", "rod:lure:enchant=lure:3:1:3000/20000/90000",
            "rod:extra_drops:drops:100:3:600+80", "rod:luck:enchant=luck_of_the_sea:3:5:15000/80000/300000",
            "rod:wisdom:wisdom:50:5:800+100", "rod:storage:storage:100:10:600+60", "rod:auto_sell:autosell:1:20:200000",
            "rod:sell_bonus:sell:100:25:2000+200", "rod:lucky:lucky:100:30:3000+250",
            // owner 6 Nov 2026: the island sword (/pve)
            "sword:shard_finder:shards:100:1:400+60", "sword:sharpness:enchant=sharpness:5:1:1500/6000/20000/60000/150000",
            "sword:looting:enchant=looting:3:5:15000/80000/300000", "sword:wisdom:wisdom:50:5:800+100",
            "sword:sweeping:enchant=sweeping_edge:3:10:10000/50000/200000", "sword:lucky:lucky:100:30:3000+250");

    /** {@code perks} v13 (before shards x10, owner 7 Nov 2026). */
    static final List<String> PERKS_V13 = List.of( // feedback 5 Nov 2026: fewer, bigger steps; the first upgrade within a minute
            "hoe:shard_finder:shards:100:1:40+6", "hoe:efficiency:enchant=efficiency:5:1:150/600/2000/6000/15000",
            "hoe:extra_drops:drops:100:3:60+8", "hoe:fortune:enchant=fortune:3:5:1500/8000/30000",
            "hoe:wisdom:wisdom:50:5:80+10", "hoe:growth:growth:100:10:100+10", "hoe:storage:storage:100:10:60+6",
            "hoe:haste:haste:2:15:5000/30000", "hoe:auto_sell:autosell:1:20:20000", "hoe:sell_bonus:sell:100:25:200+20",
            "hoe:lucky:lucky:100:30:300+25", "hoe:sweep:sweep:100:45:3000+100:1",
            "pickaxe:shard_finder:shards:100:1:40+6", "pickaxe:efficiency:enchant=efficiency:5:1:150/600/2000/6000/15000",
            "pickaxe:extra_drops:drops:100:3:60+8", "pickaxe:fortune:enchant=fortune:3:5:1500/8000/30000",
            "pickaxe:wisdom:wisdom:50:5:80+10", "pickaxe:smelter:smelt:100:10:100+10", "pickaxe:storage:storage:100:10:60+6",
            "pickaxe:haste:haste:2:15:5000/30000", "pickaxe:auto_sell:autosell:1:20:20000", "pickaxe:sell_bonus:sell:100:25:200+20",
            "pickaxe:lucky:lucky:100:30:300+25", "pickaxe:vein:sweep:100:45:3000+100:1",
            // owner 6 Nov 2026: the island rod (/fish)
            "rod:shard_finder:shards:100:1:40+6", "rod:lure:enchant=lure:3:1:300/2000/9000",
            "rod:extra_drops:drops:100:3:60+8", "rod:luck:enchant=luck_of_the_sea:3:5:1500/8000/30000",
            "rod:wisdom:wisdom:50:5:80+10", "rod:storage:storage:100:10:60+6", "rod:auto_sell:autosell:1:20:20000",
            "rod:sell_bonus:sell:100:25:200+20", "rod:lucky:lucky:100:30:300+25",
            // owner 6 Nov 2026: the island sword (/pve)
            "sword:shard_finder:shards:100:1:40+6", "sword:sharpness:enchant=sharpness:5:1:150/600/2000/6000/15000",
            "sword:looting:enchant=looting:3:5:1500/8000/30000", "sword:wisdom:wisdom:50:5:80+10",
            "sword:sweeping:enchant=sweeping_edge:3:10:1000/5000/20000", "sword:lucky:lucky:100:30:300+25");

    /** {@code enchants.list} v13 (before shards x10, owner 7 Nov 2026). */
    static final List<String> ENCHANTS_V13 = List.of(
            // owner 7 Nov 2026: hoe and pickaxe enchants 10x the shards a level, max 500 (everyday), 300 (mid), 100 (rare)
            "pickaxe:blast:500:20:0.5:1.0:4:18:0:1:0",
            "pickaxe:shard_burst:500:20:0.4:1.0:3:6:0:1:0",
            "pickaxe:scholar:500:20:0:0:5:25:0:1:0",
            "pickaxe:rich_vein:500:20:1:5:1:2:0:3:0",
            "pickaxe:quick_study:500:20:1:5:1:1:0:5:0",
            "pickaxe:cash_vein:300:30:0.5:2.0:5:5:0:8:0",
            "pickaxe:combo:500:20:0:0:5:20:0:10:0",
            "pickaxe:compound_interest:300:30:0:0:5:15:0:13:0",
            "pickaxe:shard_echo:300:30:0:0:10:25:0:16:0",
            "pickaxe:lightning:300:30:0.05:0.15:9:18:20:20:0",
            "pickaxe:motherlode:300:30:0.2:0.6:10:10:0:24:0",
            "pickaxe:lucky_streak:300:30:0:0:100:50:0:28:0",
            "pickaxe:geode:100:40:0.05:0.2:1:1:0:32:0",
            "pickaxe:keyfinder:100:50:0.001:0.02:1:1:0:36:0",
            "pickaxe:meteor:100:50:0.03:0.08:15:25:20:1:1",
            "pickaxe:nuke:100:80:0.003:0.01:40:100:300:1:5",
            "pickaxe:shard_jackpot:100:60:0.01:0.04:40:75:0:1:10",
            "hoe:explode:500:20:0.5:1.0:4:18:0:1:0",
            "hoe:shard_burst:500:20:0.4:1.0:3:6:0:1:0",
            "hoe:scholar:500:20:0:0:5:25:0:1:0",
            "hoe:bountiful:500:20:1:5:1:2:0:3:0",
            "hoe:quick_study:500:20:1:5:1:1:0:5:0",
            "hoe:cash_crop:300:30:0.5:2.0:5:5:0:8:0",
            "hoe:combo:500:20:0:0:5:20:0:10:0",
            "hoe:compound_interest:300:30:0:0:5:15:0:13:0",
            "hoe:shard_echo:300:30:0:0:10:25:0:16:0",
            "hoe:row_reaper:300:30:0.3:0.7:4:7:0:20:0",
            "hoe:golden_crop:300:30:0.1:0.4:5:5:0:24:0",
            "hoe:lucky_streak:300:30:0:0:100:50:0:28:0",
            "hoe:harvest_basket:100:40:0.05:0.2:1:1:0:32:0",
            "hoe:keyfinder:100:50:0.001:0.02:1:1:0:36:0",
            "hoe:tornado:100:50:0.03:0.08:20:28:20:1:1",
            "hoe:meteor_shower:100:80:0.01:0.03:12:36:60:1:5",
            "hoe:shard_jackpot:100:60:0.01:0.04:40:100:0:1:10",
            // owner 6 Nov 2026: the island rod at /fish (a catch is about 10x rarer than a block, so the chances are higher)
            "rod:shard_burst:1000:2:4:10:4:8:0:1:0",
            "rod:scholar:1000:2:0:0:5:25:0:1:0",
            "rod:big_catch:1000:2:10:40:1:2:0:1:0",
            "rod:quick_study:1000:2:5:20:1:2:0:5:0",
            "rod:golden_fish:1000:3:5:20:5:5:0:8:0",
            "rod:combo:1000:2:0:0:5:20:0:10:0",
            "rod:compound_interest:1000:3:0:0:5:15:0:13:0",
            "rod:shard_echo:1000:3:0:0:10:25:0:16:0",
            "rod:lucky_streak:1000:3:0:0:30:15:0:28:0",
            "rod:treasure_chest:1000:4:0.5:2:1:1:0:32:0",
            "rod:keyfinder:1000:5:0.01:0.2:1:1:0:36:0",
            "rod:shard_jackpot:1000:6:0.1:0.4:40:100:0:1:10",
            // owner 6 Nov 2026: the island sword at /pve (no area, drops and progress enchants: kills go to the bag)
            "sword:shard_burst:1000:2:2:6:3:6:0:1:0",
            "sword:scholar:1000:2:0:0:5:25:0:1:0",
            "sword:combo:1000:2:0:0:5:20:0:10:0",
            "sword:compound_interest:1000:3:0:0:5:15:0:13:0",
            "sword:shard_echo:1000:3:0:0:10:25:0:16:0",
            "sword:lucky_streak:1000:3:0:0:50:25:0:28:0",
            "sword:keyfinder:1000:5:0.005:0.1:1:1:0:36:0",
            "sword:shard_jackpot:1000:6:0.05:0.2:40:100:0:1:10");

    /** {@code perks} v9 (feedback 5 Nov 2026); v11 (owner 6 Nov 2026) adds the island rod. */
    static final List<String> PERKS_V9 = List.of(
            "hoe:shard_finder:shards:100:1:40+6", "hoe:efficiency:enchant=efficiency:5:1:150/600/2000/6000/15000",
            "hoe:extra_drops:drops:100:3:60+8", "hoe:fortune:enchant=fortune:3:5:1500/8000/30000",
            "hoe:wisdom:wisdom:50:5:80+10", "hoe:growth:growth:100:10:100+10", "hoe:storage:storage:100:10:60+6",
            "hoe:haste:haste:2:15:5000/30000", "hoe:auto_sell:autosell:1:20:20000", "hoe:sell_bonus:sell:100:25:200+20",
            "hoe:lucky:lucky:100:30:300+25", "hoe:sweep:sweep:100:45:3000+100:1",
            "pickaxe:shard_finder:shards:100:1:40+6", "pickaxe:efficiency:enchant=efficiency:5:1:150/600/2000/6000/15000",
            "pickaxe:extra_drops:drops:100:3:60+8", "pickaxe:fortune:enchant=fortune:3:5:1500/8000/30000",
            "pickaxe:wisdom:wisdom:50:5:80+10", "pickaxe:smelter:smelt:100:10:100+10", "pickaxe:storage:storage:100:10:60+6",
            "pickaxe:haste:haste:2:15:5000/30000", "pickaxe:auto_sell:autosell:1:20:20000", "pickaxe:sell_bonus:sell:100:25:200+20",
            "pickaxe:lucky:lucky:100:30:300+25", "pickaxe:vein:sweep:100:45:3000+100:1");

    /** {@code perks} v8 (owner 4 Nov 2026 19:39): 500-1000 tiny levels; replaced on 5 Nov 2026 (feedback: too slow, +0.01% steps). */
    static final List<String> PERKS_V8 = List.of(
            "hoe:shard_finder:shards:1000:1:300+10", "hoe:efficiency:enchant=efficiency:5:1:1500/4000/10000/25000/60000",
            "hoe:extra_drops:drops:500:5:400+12", "hoe:fortune:enchant=fortune:3:5:5000/25000/100000",
            "hoe:haste:haste:2:10:15000/75000", "hoe:wisdom:wisdom:100:10:500+25", "hoe:growth:growth:250:15:600+20",
            "hoe:storage:storage:250:15:300+10", "hoe:auto_sell:autosell:1:25:50000", "hoe:sell_bonus:sell:500:30:800+20",
            "hoe:lucky:lucky:500:35:1000+25", "hoe:sweep:sweep:100:45:5000+150:1",
            "pickaxe:shard_finder:shards:1000:1:300+10", "pickaxe:efficiency:enchant=efficiency:5:1:1500/4000/10000/25000/60000",
            "pickaxe:extra_drops:drops:500:5:400+12", "pickaxe:fortune:enchant=fortune:3:5:5000/25000/100000",
            "pickaxe:haste:haste:2:10:15000/75000", "pickaxe:wisdom:wisdom:100:10:500+25", "pickaxe:smelter:smelt:100:15:600+20",
            "pickaxe:storage:storage:250:15:300+10", "pickaxe:auto_sell:autosell:1:25:50000", "pickaxe:sell_bonus:sell:500:30:800+20", "pickaxe:lucky:lucky:500:35:1000+25",
            "pickaxe:vein:sweep:100:45:5000+150:1");

    /** The v7 default (before the pickaxe storage, 4 Nov 2026 19:39). */
    static final List<String> PERKS_V7 = List.of(
            "hoe:shard_finder:shards:1000:1:300+10", "hoe:efficiency:enchant=efficiency:5:1:1500/4000/10000/25000/60000",
            "hoe:extra_drops:drops:500:5:400+12", "hoe:fortune:enchant=fortune:3:5:5000/25000/100000",
            "hoe:haste:haste:2:10:15000/75000", "hoe:wisdom:wisdom:100:10:500+25", "hoe:growth:growth:250:15:600+20",
            "hoe:storage:storage:250:15:300+10", "hoe:auto_sell:autosell:1:25:50000", "hoe:sell_bonus:sell:500:30:800+20",
            "hoe:lucky:lucky:500:35:1000+25", "hoe:sweep:sweep:100:45:5000+150:1",
            "pickaxe:shard_finder:shards:1000:1:300+10", "pickaxe:efficiency:enchant=efficiency:5:1:1500/4000/10000/25000/60000",
            "pickaxe:extra_drops:drops:500:5:400+12", "pickaxe:fortune:enchant=fortune:3:5:5000/25000/100000",
            "pickaxe:haste:haste:2:10:15000/75000", "pickaxe:wisdom:wisdom:100:10:500+25", "pickaxe:smelter:smelt:100:15:600+20",
            "pickaxe:auto_sell:autosell:1:25:50000", "pickaxe:sell_bonus:sell:500:30:800+20", "pickaxe:lucky:lucky:500:35:1000+25",
            "pickaxe:vein:sweep:100:45:5000+150:1");

    @Setting(path = "prices.exponent", desc = "Exponent of the long-track price curve (owner 4 Nov 2026: 1.35).", min = 0, max = 4)
    public double priceExponent = 1.35;

    @Setting(path = "effects.drops-per-level", desc = "Extra Drops: percent more drops per level. Owner 4 Nov 2026: the island's own farm/mine boosts "
            + "(island-boost.*) are much stronger than this.", min = 0, max = 100)
    public double dropsPerLevel = 0.15; // tools v5: 1,000 levels = +150%

    @Setting(path = "effects.smelt-per-level", desc = "Smelter: auto-smelt chance percent per level.", min = 0, max = 100)
    public double smeltPerLevel = 1.0;

    @Setting(path = "effects.growth-per-level", desc = "Growth (hoe): percent faster growth of the crops on the island per level.", min = 0, max = 100)
    public double growthPerLevel = 0.2; // tools v5: 500 levels = +100%

    @Setting(path = "effects.wisdom-per-level", desc = "Wisdom: percent more tool XP per level.", min = 0, max = 100)
    public double wisdomPerLevel = 1.0; // tools v5: 500 levels = +500%

    @Setting(path = "effects.sell-per-level", desc = "Sell Bonus: percent more money per level when Auto-Sell or the hoe storage sells.", min = 0, max = 10)
    public double sellPerLevel = 0.2; // tools v5: 500 levels = +100%

    @Setting(path = "effects.lucky-chance-per-level", desc = "Lucky: percent chance per relevant block and level of a Shard Pouch.", min = 0, max = 1)
    public double luckyPerLevel = 0.001; // tools v5: 1,000 levels = a pouch in 1% of the blocks

    @Setting(path = "effects.lucky-min", desc = "Lucky: fewest shards in a Shard Pouch (the prestige bonus applies on top).", min = 1, max = 1_000_000)
    public int luckyMin = 250;

    @Setting(path = "effects.lucky-max", desc = "Lucky: most shards in a Shard Pouch.", min = 1, max = 1_000_000)
    public int luckyMax = 2500;

    @Setting(path = "sweep.bands", desc = "Owner 4 Nov 2026: Sweep (hoe) and Vein (pickaxe) per prestige band, 'prestige:extra-blocks:chance-at-max-level'. "
            + "Locked below the first band; the 100 levels raise the chance inside the current band.")
    public List<String> sweepBands = List.of("0:2:10", "2:3:15", "3:4:20", "5:6:30"); // owner 7 Nov 2026: Sweep without a prestige

    @Setting(path = "sweep.radius", desc = "Sweep/Vein look this many blocks around the broken one for ripe crops / the same ore.", min = 1, max = 4)
    public int sweepRadius = 2;

    @Setting(path = "sweep.shards", desc = "Swept crops and veined ores also roll for shards and Shard Pouches (off: only normal drops and tool XP).")
    public boolean sweepShards = true; // tools v5: every block counts (the shard prices assume ~13 blocks a second)

    @Setting(path = "area.shards", desc = "Tools v5 (owner 7 Nov 2026): blocks broken by area enchants (Explode, Blast, Tornado ...) "
            + "roll for shards too (no tool XP). The shard prices assume about 13 blocks a second.")
    public boolean areaShards = true;

    @Setting(path = "hoe.speed-level", desc = "Owner 4 Nov 2026: Speed while holding the island hoe at /farm and on islands (0 = off).", min = 0, max = 5)
    public int speedLevel = 1;

    @Setting(path = "hoe.swiftness-prestige", desc = "'Swiftness': from this hoe prestige on the Speed is one level higher (0 = never).", min = 0, max = 100)
    public int swiftnessPrestige = 2;

    @Setting(path = "hoe.storage-base", desc = "Hoe and pickaxe storage at Storage level 0 (items; loot lands there when the bag is full).", min = 1, max = 1_000_000)
    public long storageBase = 2000;

    @Setting(path = "hoe.storage-per-level", desc = "More storage per Storage level (items), hoe and pickaxe.", min = 0, max = 100_000)
    public long storagePerLevel = 500;

    @Setting(path = "island-boost.hoe-effect", desc = "Owner 4 Nov 2026: the island upgrade (upgrades.yml effect) whose value is percent more crops for "
            + "everything the island hoe harvests (on islands and at /farm), from the island the player is a member of.")
    public String hoeBoostEffect = "harvest-yield";

    @Setting(path = "island-boost.pickaxe-effect", desc = "The island upgrade whose value is percent more ores for the island pickaxe (on islands and at /mine).")
    public String pickaxeBoostEffect = "mine-fortune";

    @Setting(path = "island-boost.rod-effect", desc = "The island upgrade whose value is percent more catches for the island rod at /fish (empty = none yet).")
    public String rodBoostEffect = "";

    @Setting(path = "island-boost.sword-effect", desc = "The island upgrade whose value is percent more for the island sword at /pve (empty = none yet).")
    public String swordBoostEffect = "";

    @Setting(path = "tool-worlds", desc = "Worlds besides the islands where the tools work exactly like on an island (level up, perks, shards): the /farm and /mine world.")
    public List<String> toolWorlds = List.of("grind");

    // ------------------------------------------------------------------ island crops

    @Setting(path = "growth.enabled", desc = "Owner 3 Nov 2026: every crop on an island grows on the island's clock, one stage every few seconds (the regrow time below split over the crop's stages, faster with the crop-growth upgrade), with a puff and a soft sound.")
    public boolean growthEnabled = true;

    @Setting(path = "growth.step-floor-ms", desc = "Shortest time between two stages of one crop (milliseconds).", min = 100, max = 60000)
    public long growthStepFloorMillis = 500;

    @Setting(path = "growth.stage-seconds", desc = "Feedback 3 Nov 2026: seconds per growth stage of an island crop without upgrades "
            + "(the crop-growth upgrade makes it faster, down to step-floor-ms).", min = 0.5, max = 600)
    public double growthStageSeconds = 3;

    @Setting(path = "growth.bonemeal-stages", desc = "Stages one bone meal adds to an island crop (1 to this, random).", min = 1, max = 7)
    public int growthBonemealStages = 3;

    @Setting(path = "growth.budget", desc = "Most crops grown per half second on all islands together (the rest wait for the next "
            + "run; keeps the tick time flat). Feedback 4 Nov 2026: 2000 (600 was exactly one 60x60 field, so two big fields "
            + "grew at half speed).", min = 10, max = 10000)
    public int growthBudget = 2000;

    @Setting(path = "growth.max-ms-per-run", desc = "Most milliseconds one half-second growth run may take (the rest waits for the "
            + "next run). Feedback 4 Nov 2026: 4 (was a fixed 2, too little for big fields).", min = 1, max = 20)
    public int growthMaxMillis = 4;

    @Setting(path = "growth.sound", desc = "A soft sound when a crop near you grows (at most every 2 s per player).")
    public boolean growthSound = true;

    @Setting(path = "growth.label", desc = "Looking at a crop on an island shows its stage and the time to the next one (only you see it).")
    public boolean growthLabel = false; // feedback 3 Nov 2026: no hologram over crops

    @Setting(path = "growth.label-range", desc = "How far you can look at a crop for its label (blocks).", min = 1, max = 10)
    public int growthLabelRange = 5;

    @Setting(path = "farmland.always-wet", desc = "Owner 3 Nov 2026: tilled dirt on islands is moist farmland at once and never dries, turns back to dirt or gets trampled (no water needed).")
    public boolean farmlandAlwaysWet = true;

    @Setting(path = "regrow.enabled", desc = "T11: crops harvested with the island hoe replant themselves and are ripe again after the regrow time.")
    public boolean regrowEnabled = true;

    @Setting(path = "regrow.base-seconds", desc = "T11: regrow time without upgrades.", min = 5, max = 3600)
    public double regrowBase = 60;

    @Setting(path = "regrow.top-seconds", desc = "T11: regrow time at the top island upgrade.", min = 1, max = 3600)
    public double regrowTop = 15;

    @Setting(path = "regrow.full-at-percent", desc = "Island upgrade value (percent) that gives the top regrow time.", min = 1, max = 10_000)
    public double regrowFullAt = 120;

    @Setting(path = "regrow.min-seconds", desc = "G11: floor after the CROP_GROWTH perks.", min = 1, max = 3600)
    public double regrowFloor = 10;

    @Setting(path = "regrow.upgrade-effect", desc = "Island upgrade effect (upgrades.yml) that speeds up regrowing.")
    public String regrowEffect = "crop-growth";

    @Setting(path = "regrow.max-tracked", desc = "Most regrowing crops remembered at once (oldest dropped: they grow normally).", min = 100, max = 1_000_000)
    public int regrowMax = 50_000;

    @Setting(path = "burst.percent-per-growth", desc = "T12: Growth Burst chance (percent) per percent of the island's crop-growth upgrade.", min = 0, max = 10)
    public double burstPerGrowth = 0.05;

    @Setting(path = "burst.max-percent", desc = "T12: highest Growth Burst chance.", min = 0, max = 100)
    public double burstMax = 10;

    @Setting(path = "cue.near-blocks", desc = "T13: players this close see a strong ripe cue.", min = 1, max = 64)
    public double cueNear = 6;

    @Setting(path = "cue.far-blocks", desc = "T13: players this close see a subtle cue (one particle).", min = 1, max = 128)
    public double cueFar = 16;

    @Override
    public int version() {
        return 18;
    }

    /** Changed defaults (a value the owner chose is kept). */
    @Override
    public Map<Integer, Map<String, ValueChange>> valueChanges() {
        IslandToolsConfig d = new IslandToolsConfig();
        Map<Integer, Map<String, ValueChange>> out = new java.util.HashMap<>(Map.of(4, Map.of("growth.label", ValueChange.of(true, false)),
                6, Map.of("growth.budget", ValueChange.of(600, d.growthBudget)),
                // v7 (owner 4 Nov 2026, island tools rework): more shards, tiny perk steps, linear hoe storage
                7, Map.of("shards.chance-hoe", ValueChange.of(3.0, d.shardHoe), "shards.chance-pickaxe", ValueChange.of(2.0, d.shardPickaxe),
                        "shards.finder-per-level", ValueChange.of(0.5, d.shardFinderPerLevel),
                        "effects.drops-per-level", ValueChange.of(8.0, d.dropsPerLevel),
                        "effects.smelt-per-level", ValueChange.of(20.0, d.smeltPerLevel),
                        "hoe.storage-base", ValueChange.of(256, d.storageBase)),
                // v8 (owner 4 Nov 2026 19:39): the pickaxe got a storage too
                8, Map.of("perks", ValueChange.of(PERKS_V7, PERKS_V8)),
                // v9 (feedback 5 Nov 2026): shards about 5x faster, Shard Finder 1% a level, 50-100 bigger levels per perk
                9, Map.ofEntries(Map.entry("perks", ValueChange.of(PERKS_V8, d.perks)),
                        Map.entry("shards.chance-hoe", ValueChange.of(8.0, d.shardHoe)), Map.entry("shards.chance-pickaxe", ValueChange.of(6.0, d.shardPickaxe)),
                        Map.entry("shards.finder-per-level", ValueChange.of(0.1, d.shardFinderPerLevel)),
                        Map.entry("effects.drops-per-level", ValueChange.of(0.05, d.dropsPerLevel)),
                        Map.entry("effects.smelt-per-level", ValueChange.of(0.5, d.smeltPerLevel)),
                        Map.entry("effects.growth-per-level", ValueChange.of(0.2, d.growthPerLevel)),
                        Map.entry("effects.wisdom-per-level", ValueChange.of(0.5, d.wisdomPerLevel)),
                        Map.entry("effects.sell-per-level", ValueChange.of(0.02, d.sellPerLevel)),
                        Map.entry("effects.lucky-chance-per-level", ValueChange.of(0.002, d.luckyPerLevel)),
                        Map.entry("hoe.storage-per-level", ValueChange.of(200L, d.storagePerLevel))),
                // v10 (owner 6-7 Nov 2026, plans/TOOLS_V3.md): personal prestige 50 + rebirths: short, fast levels
                10, Map.of("levels.xp-base", ValueChange.of(12.0, d.xpBase), "levels.exponent", ValueChange.of(1.6, d.xpExponent),
                        "levels.prestige-factor", ValueChange.of(0.35, d.xpPrestigeFactor)),
                // v11 (owner 6 Nov 2026): the island rod for /fish
                11, Map.of("perks", ValueChange.of(PERKS_V9, d.perks), "enchants.list",
                        ValueChange.of(ENCHANTS_V12.stream().filter(l -> !l.startsWith("rod:") && !l.startsWith("sword:")).toList(), ENCHANTS_V12)),
                // v12 (owner 7 Nov 2026, plans/HOE_SHARDS_SPAWNERS.md): more shards, a Shard Finder level that matters
                13, Map.of("enchants.list", ValueChange.of(ENCHANTS_V12, d.enchantList)),
                // v14 (owner 7 Nov 2026, plans/SHARDS_X10_BREAKTHROUGHS.md): every shard x10, found and paid
                14, Map.ofEntries(Map.entry("perks", ValueChange.of(PERKS_V13, d.perks)),
                        Map.entry("enchants.list", ValueChange.of(ENCHANTS_V13, d.enchantList)),
                        Map.entry("shards.chance-hoe", ValueChange.of(60.0, d.shardHoe)),
                        Map.entry("shards.chance-pickaxe", ValueChange.of(30.0, d.shardPickaxe)),
                        Map.entry("shards.chance-rod", ValueChange.of(300.0, d.shardRod)),
                        Map.entry("shards.chance-sword", ValueChange.of(150.0, d.shardSword)),
                        Map.entry("effects.lucky-min", ValueChange.of(25, d.luckyMin)),
                        Map.entry("effects.lucky-max", ValueChange.of(250, d.luckyMax))),
                12, Map.of("shards.chance-hoe", ValueChange.of(40.0, d.shardHoe),
                        "shards.finder-per-level", ValueChange.of(1.0, d.shardFinderPerLevel))));
        // v15 (owner 7 Nov 2026, plans/TOOLS_V5.md): personal perks on long tracks, priced for ~13 blocks a second
        out.put(15, Map.ofEntries(Map.entry("perks", ValueChange.of(PERKS_V14, d.perks)),
                        Map.entry("shards.chance-pickaxe", ValueChange.of(300.0, d.shardPickaxe)),
                        Map.entry("shards.chance-rod", ValueChange.of(3000.0, d.shardRod)),
                        Map.entry("shards.chance-sword", ValueChange.of(1500.0, d.shardSword)),
                        Map.entry("shards.finder-per-level", ValueChange.of(3.0, d.shardFinderPerLevel)),
                        Map.entry("effects.drops-per-level", ValueChange.of(0.5, d.dropsPerLevel)),
                        Map.entry("effects.growth-per-level", ValueChange.of(0.5, d.growthPerLevel)),
                        Map.entry("effects.wisdom-per-level", ValueChange.of(2.0, d.wisdomPerLevel)),
                        Map.entry("effects.sell-per-level", ValueChange.of(0.1, d.sellPerLevel)),
                        Map.entry("effects.lucky-chance-per-level", ValueChange.of(0.01, d.luckyPerLevel)),
                        Map.entry("sweep.shards", ValueChange.of(false, d.sweepShards))));
        out.put(16, Map.of("perks", ValueChange.of(PERKS_V15, d.perks))); // owner 7 Nov 2026: + Auto-Compactor
        // v17 (owner 7 Nov 2026, plans/TOOLS_V6_COMPOUND.md): compound Shard Finder, 10 shards a block, 2,500-level enchants
        out.put(17, Map.ofEntries(Map.entry("perks", ValueChange.of(PERKS_V16, d.perks)),
                Map.entry("enchants.list", ValueChange.of(ENCHANTS_V16, d.enchantList)),
                Map.entry("shards.finder-per-level", ValueChange.of(1.0, d.shardFinderPerLevel)),
                Map.entry("shards.chance-hoe", ValueChange.of(600.0, d.shardHoe)),
                Map.entry("shards.chance-pickaxe", ValueChange.of(600.0, d.shardPickaxe)),
                Map.entry("shards.chance-rod", ValueChange.of(20000.0, d.shardRod)),
                Map.entry("shards.chance-sword", ValueChange.of(5000.0, d.shardSword)),
                Map.entry("enchants.shard-bonus-cap", ValueChange.of(65.0, 300.0))));
        // v18 (owner 7 Nov 2026, plans/HOE_IMPROVEMENTS.md): milestones instead of breakthroughs, Sweep without a prestige
        out.put(18, Map.ofEntries(Map.entry("perks", ValueChange.of(PERKS_V17, d.perks)),
                Map.entry("enchants.list", ValueChange.of(ENCHANTS_V17, d.enchantList)),
                Map.entry("enchants.shard-bonus-cap", ValueChange.of(300.0, d.enchantShardCap)),
                Map.entry("sweep.bands", ValueChange.of(List.of("1:2:10", "2:3:15", "3:4:20", "5:6:30"), d.sweepBands))));
        return out;
    }

    /** Owner 4 Nov 2026: the old tier, task, axe/sword, row and put-away settings are gone. */
    @Override
    public Map<String, String> removedKeys() {
        String gone = "island tools rework 4 Nov 2026";
        return Map.ofEntries(Map.entry("prestige.roles", "owner 6-7 Nov 2026: every player prestiges their own tool"),
                Map.entry("enchants.cap-per-prestige", "tools v6: the cap grows with the tool's lifetime levels (enchants.cap-full-levels)"),
                Map.entry("enchants.breakthroughs", "owner 7 Nov 2026: milestones (enchants.milestone-*) replace the breakthroughs"),
                Map.entry("tier-costs", gone + ": the tools start at diamond"), Map.entry("tier-money", gone + ": no tiers"),
                Map.entry("tasks", gone + ": tool levels replace the island tasks"),
                Map.entry("shards.chance-axe", gone + ": no island axe"),
                Map.entry("relevant.axe", gone + ": no island axe"), Map.entry("confirm-above", gone + ": only prestige has a confirm screen"),
                Map.entry("only-on-island", gone + ": the Depths are gone"), Map.entry("own-inventory-worlds", gone + ": the Depths are gone"),
                Map.entry("put-away-worlds", gone + ": the Depths are gone"), Map.entry("upgrades", gone + ": replaced by 'perks'"),
                Map.entry("effects.timber-per-level", gone + ": no island axe"), Map.entry("effects.experience-per-level", gone + ": Wisdom gives tool XP"),
                Map.entry("effects.lifesteal-per-level", gone + ": no island sword"), Map.entry("hoe.row-lengths", gone + ": Row became Sweep"),
                Map.entry("hoe.storage-factor", gone + ": the storage grows by hoe.storage-per-level"));
    }

    @Override
    public String fileName() {
        return "islandtools.yml";
    }

    @Override
    public String header() {
        return "Island tools: the island's harvester hoe and pickaxe (owner 4 Nov 2026). Texts: messages/islandtools.yml. Reload: /skyblock reload islandtools";
    }
}
