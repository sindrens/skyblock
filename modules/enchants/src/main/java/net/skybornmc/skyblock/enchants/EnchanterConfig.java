package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.item.Rarity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Enchanter settings ({@code enchanter.yml}): crystal costs (ENCHANTS §5, EN14), fees (EN10, EN11, EN13), the Book shop
 * rolls (EN9), tier thresholds, family caps (EN16) and rules. Every number is a default the owner can change.
 */
final class EnchanterConfig extends ModuleConfig {

    /** Setting names used in perf log lines. */
    static final String PERF_STACKS = "stacks.perf-warn-ms-per-second";
    static final String PERF_EFFECTS = "combat.perf-warn-ms-per-second";

    @Setting(path = "season-id", desc = "Current season (books and the enchant collection are per season). Stays 0 until the season system exists.",
            min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int seasonId = 0;

    // ------------------------------------------------------------------ costs (§5)

    @Setting(path = "costs.step-base", desc = "Crystal cost of step 1 (level I of every enchant before the rarity factor).", min = 1, max = 1_000_000, level = Setting.Level.OWNER)
    public double stepBase = 100;

    @Setting(path = "costs.step-growth", desc = "Each step costs this much more than the one before (x1.8).", min = 1, max = 10, level = Setting.Level.OWNER)
    public double stepGrowth = 1.8;

    @Setting(path = "costs.steps", desc = "Number of steps; the max level of every enchant is the last step.", min = 2, max = 20, level = Setting.Level.OWNER)
    public int steps = 10;

    @Setting(path = "costs.single-level-step", desc = "Step used for enchants with only one level (non-Mythic).", min = 1, max = 20, level = Setting.Level.OWNER)
    public int singleLevelStep = 6;

    @Setting(path = "costs.mythic-step", desc = "Step used for Mythic enchants (one level).", min = 1, max = 20, level = Setting.Level.OWNER)
    public int mythicStep = 10;

    @Setting(path = "costs.rarity-factor.common", desc = "Cost factor for Common enchants.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double factorCommon = 0.5;
    @Setting(path = "costs.rarity-factor.uncommon", desc = "Cost factor for Uncommon enchants.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double factorUncommon = 0.75;
    @Setting(path = "costs.rarity-factor.rare", desc = "Cost factor for Rare enchants.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double factorRare = 1.0;
    @Setting(path = "costs.rarity-factor.epic", desc = "Cost factor for Epic enchants.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double factorEpic = 1.5;
    @Setting(path = "costs.rarity-factor.legendary", desc = "Cost factor for Legendary enchants.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double factorLegendary = 2.5;
    @Setting(path = "costs.rarity-factor.mythic", desc = "Cost factor for Mythic enchants.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double factorMythic = 4.0;

    // ------------------------------------------------------------------ fees (§4)

    @Setting(path = "fees.apply-book-percent", desc = "Apply fee: level I cost, or this percent of the book level's cost if higher.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double applyBookPercent = 25;

    @Setting(path = "fees.combine-percent", desc = "Combine fee: this percent of the new level's cost.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double combinePercent = 25;

    @Setting(path = "fees.shatter-refund-percent", desc = "Shatter (remove without a scroll): percent of the crystals put into the enchant you get back.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double shatterRefundPercent = 20;

    @Setting(path = "scroll.price", desc = "Crystals for one Disenchant Scroll in the Book shop.", min = 1, max = 1_000_000_000, level = Setting.Level.OWNER)
    public long scrollPrice = 2_500;

    // ------------------------------------------------------------------ rolls (§3.5)

    @Setting(path = "rolls.pools", desc = "Pools offered in the Book shop (farming and fishing come with their phase).")
    public List<String> rollPools = List.of("mining", "foraging", "combat");

    @Setting(path = "rolls.book-level", desc = "Level of books from rolls.", min = 1, max = 10, level = Setting.Level.OWNER)
    public int rollBookLevel = 1;

    @Setting(path = "rolls.apprentice.price", desc = "Crystals for an Apprentice Roll.", min = 1, max = 1_000_000_000, level = Setting.Level.OWNER)
    public long apprenticePrice = 500;
    @Setting(path = "rolls.apprentice.odds", desc = "Apprentice Roll odds as rarity:weight.", level = Setting.Level.OWNER)
    public List<String> apprenticeOdds = List.of("common:70", "uncommon:30");
    @Setting(path = "rolls.apprentice.min-tier", desc = "Tier needed in that pool's wing.", min = 1, max = 20)
    public int apprenticeTier = 1;
    @Setting(path = "rolls.apprentice.daily", desc = "Apprentice Rolls per player per day (Europe/Oslo day).", min = 0, max = 1000)
    public int apprenticeDaily = 5;

    @Setting(path = "rolls.adept.price", desc = "Crystals for an Adept Roll.", min = 1, max = 1_000_000_000, level = Setting.Level.OWNER)
    public long adeptPrice = 4_000;
    @Setting(path = "rolls.adept.odds", desc = "Adept Roll odds as rarity:weight.", level = Setting.Level.OWNER)
    public List<String> adeptOdds = List.of("uncommon:40", "rare:50", "epic:10");
    @Setting(path = "rolls.adept.min-tier", desc = "Tier needed in that pool's wing.", min = 1, max = 20)
    public int adeptTier = 4;
    @Setting(path = "rolls.adept.daily", desc = "Adept Rolls per player per day.", min = 0, max = 1000)
    public int adeptDaily = 5;

    @Setting(path = "rolls.master.price", desc = "Crystals for a Master Roll.", min = 1, max = 1_000_000_000, level = Setting.Level.OWNER)
    public long masterPrice = 25_000;
    @Setting(path = "rolls.master.odds", desc = "Master Roll odds as rarity:weight.", level = Setting.Level.OWNER)
    public List<String> masterOdds = List.of("rare:40", "epic:50", "legendary:10");
    @Setting(path = "rolls.master.min-tier", desc = "Tier needed in that pool's wing.", min = 1, max = 20)
    public int masterTier = 7;
    @Setting(path = "rolls.master.daily", desc = "Master Rolls per player per day.", min = 0, max = 1000)
    public int masterDaily = 3;

    // ------------------------------------------------------------------ tiers

    @Setting(path = "tiers.mining", desc = "Mining skill level that opens each Mine tier (tier 1 first).")
    public List<String> tiersMining = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58", "66");
    @Setting(path = "tiers.foraging", desc = "Foraging skill level that opens each Forest tier.")
    public List<String> tiersForaging = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58");
    @Setting(path = "tiers.combat", desc = "Combat skill level that opens each Combat tier.")
    public List<String> tiersCombat = List.of("0", "6", "12", "18", "24", "30", "38", "46", "55", "66");
    @Setting(path = "tiers.farming", desc = "Farming skill level per crop tier (Phase 7).")
    public List<String> tiersFarming = List.of("0");
    @Setting(path = "tiers.fishing", desc = "Fishing skill level per fishing band A-D.")
    public List<String> tiersFishing = List.of("0", "20", "40", "60");

    // ------------------------------------------------------------------ caps (§8.1)

    @Setting(path = "caps.crystals", desc = "Cap of the crystals family in percent (sell uses economy.yml).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capCrystals = 200;
    @Setting(path = "caps.extra-drops", desc = "Cap of the extra drops family in percent.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capExtraDrops = 200;
    @Setting(path = "caps.speed", desc = "Cap of the speed family in percent (the 6 blocks/s limit is in the block engine).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capSpeed = 200;
    @Setting(path = "caps.skill-xp", desc = "Cap of the skill XP family in percent.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capSkillXp = 300;
    @Setting(path = "caps.tool-xp", desc = "Cap of the tool XP family in percent.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capToolXp = 100;
    @Setting(path = "caps.rare-finds", desc = "Cap of the rare finds family in percent.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capRareFinds = 150;
    @Setting(path = "caps.area", desc = "Cap of the area family in percent.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capArea = 100;
    @Setting(path = "caps.mob-damage", desc = "Cap of the mob damage family in percent.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capMobDamage = 200;
    @Setting(path = "caps.damage-reduction", desc = "Cap of damage reduction from custom sources in percent.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double capDamageReduction = 60;
    @Setting(path = "caps.health", desc = "Cap of extra hearts from custom sources.", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double capHealth = 10;
    @Setting(path = "caps.crop-growth", desc = "Cap of the crop growth family in percent (WARDROBE §2.5: Cropwarden Robes, growth upgrades, events).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capCropGrowth = 100;
    @Setting(path = "caps.fuel-efficiency", desc = "Cap of the Island Fuel efficiency family in percent (PHASE-7 M7.1: Fuel Efficiency mastery + island achievements; fuel lasts at most this much longer).", min = 0, max = 1_000, level = Setting.Level.OWNER)
    public double capFuelEfficiency = 40;
    @Setting(path = "caps.conquest", desc = "Cap of each conquest stat in percent (envoy luck, capture speed, Conquest XP, warzone death-loss reduction).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capConquest = 60;

    // ------------------------------------------------------------------ stacked mobs (§7, E3)

    @Setting(path = "stacks.max-kills-per-hit", desc = "Most mobs one hit (or one area effect per stack) kills from a stack: Slaughter + Bloodlust are clamped to this.",
            min = 1, max = 1024, level = Setting.Level.OWNER)
    public int stackMaxKillsPerHit = 96;

    @Setting(path = "stacks.bloodlust-step-percent", desc = "Bloodlust: each killing stack hit in the window adds this percent to the Slaughter count (up to the level's max).",
            min = 0, max = 1000, level = Setting.Level.OWNER)
    public double bloodlustStepPercent = 10;

    @Setting(path = "stacks.wrath-max-stacks", desc = "Phoenix Wrath: most stacks one nova hits.", min = 0, max = 64, level = Setting.Level.OWNER)
    public int wrathMaxStacks = 16;

    @Setting(path = "stacks.book-chance", desc = "Island stack books: 1 in this many stacked mobs killed with a custom sword drops a book (0 = off).",
            min = 0, max = 100_000_000, level = Setting.Level.OWNER)
    public int stackBookChance = 25_000;

    @Setting(path = "stacks.book-daily-cap", desc = "Island stack books: most books per profile per day (Europe/Oslo day).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public int stackBookDailyCap = 5;

    @Setting(path = "stacks.book-odds", desc = "Island stack books: rarity odds as rarity:weight (the pool has Uncommon and Rare enchants).", level = Setting.Level.OWNER)
    public List<String> stackBookOdds = List.of("uncommon:60", "rare:40");

    @Setting(path = "stacks.book-level-odds", desc = "Island stack books: book level odds as level:weight.", level = Setting.Level.OWNER)
    public List<String> stackBookLevelOdds = List.of("1:85", "2:12", "3:3");

    @Setting(path = "stacks.proc-message-interval-ms", desc = "Shortest time between two action bar lines for Reaper's Sweep / Phoenix Wrath.", min = 0, max = 60_000)
    public int procMessageIntervalMs = 1000;

    @Setting(path = "stacks.perf-warn-ms-per-second", desc = "Log a warning when the stacked-mob enchants use more main-thread time than this (ms per second, checked every minute).",
            min = 0.1, max = 1000)
    public double stackPerfWarnMs = 5;

    // ------------------------------------------------------------------ book drops (§3.2–§3.4, E2)

    @Setting(path = "drops.enabled", desc = "Book drops from Depths blocks, logs and combat kills (first finds, pity, chance).")
    public boolean dropsEnabled = true;

    @Setting(path = "drops.band-tiers", desc = "First tier of each band A, B, C, D (rarity odds, drop chance and pity go by band).")
    public List<String> dropBandTiers = List.of("1", "4", "7", "10");

    @Setting(path = "drops.mining.one-in", desc = "Depths ore blocks: 1 in this many drops a book, per band A-D.", level = Setting.Level.OWNER)
    public List<String> dropMiningOneIn = List.of("2000", "3000", "4000", "5000");
    @Setting(path = "drops.mining.pity", desc = "Depths ore blocks without a book that guarantee the next one, per band A-D (0 = no pity).", level = Setting.Level.OWNER)
    public List<String> dropMiningPity = List.of("5000", "7500", "10000", "12500");
    @Setting(path = "drops.foraging.one-in", desc = "Depths logs: 1 in this many drops a book, per band A-D.", level = Setting.Level.OWNER)
    public List<String> dropForagingOneIn = List.of("2000", "3000", "4000", "5000");
    @Setting(path = "drops.foraging.pity", desc = "Depths logs without a book that guarantee the next one, per band A-D.", level = Setting.Level.OWNER)
    public List<String> dropForagingPity = List.of("5000", "7500", "10000", "12500");
    @Setting(path = "drops.combat.one-in", desc = "Depths combat kills: 1 in this many drops a book, per band A-D (TUNE: ~400-500 kills per played hour -> ~1.2-1.4 books/h like the mine).", level = Setting.Level.OWNER)
    public List<String> dropCombatOneIn = List.of("300", "350", "400", "450");
    @Setting(path = "drops.combat.pity", desc = "Depths combat kills without a book that guarantee the next one, per band A-D (2.5x the 1-in-N gap).", level = Setting.Level.OWNER)
    public List<String> dropCombatPity = List.of("750", "875", "1000", "1125");

    @Setting(path = "drops.rarity-odds.a", desc = "Book rarity odds in band A (tier 1-3) as rarity:weight.", level = Setting.Level.OWNER)
    public List<String> dropOddsA = List.of("common:70", "uncommon:26", "rare:4");
    @Setting(path = "drops.rarity-odds.b", desc = "Book rarity odds in band B (tier 4-6).", level = Setting.Level.OWNER)
    public List<String> dropOddsB = List.of("common:45", "uncommon:35", "rare:16", "epic:4");
    @Setting(path = "drops.rarity-odds.c", desc = "Book rarity odds in band C (tier 7-9).", level = Setting.Level.OWNER)
    public List<String> dropOddsC = List.of("common:25", "uncommon:33", "rare:27", "epic:12", "legendary:3");
    @Setting(path = "drops.rarity-odds.d", desc = "Book rarity odds in band D (tier 10-12).", level = Setting.Level.OWNER)
    public List<String> dropOddsD = List.of("common:12", "uncommon:26", "rare:32", "epic:22", "legendary:8");

    @Setting(path = "drops.level-odds.a", desc = "Book level odds in band A as level:weight.", level = Setting.Level.OWNER)
    public List<String> dropLevelsA = List.of("1:85", "2:12", "3:3");
    @Setting(path = "drops.level-odds.b", desc = "Book level odds in band B.", level = Setting.Level.OWNER)
    public List<String> dropLevelsB = List.of("1:85", "2:12", "3:3");
    @Setting(path = "drops.level-odds.c", desc = "Book level odds in band C.", level = Setting.Level.OWNER)
    public List<String> dropLevelsC = List.of("1:70", "2:22", "3:8");
    @Setting(path = "drops.level-odds.d", desc = "Book level odds in band D.", level = Setting.Level.OWNER)
    public List<String> dropLevelsD = List.of("1:70", "2:22", "3:8");

    @Setting(path = "drops.first-finds", desc = "Guaranteed first books per activity, once per account and season, as action:rarity.", level = Setting.Level.OWNER)
    public List<String> dropFirstFinds = List.of("100:common", "1000:uncommon");

    @Setting(path = "drops.mini-boss-chance", desc = "Percent chance of a Combat book for every player a mini-boss rewards.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double dropMiniBossChance = 30;
    @Setting(path = "drops.mini-boss-band-bonus", desc = "Mini-boss books use the rarity odds this many bands above the tier (band D stays D).", min = 0, max = 5, level = Setting.Level.OWNER)
    public int dropMiniBossBands = 1;

    @Setting(path = "drops.save-seconds", desc = "Pity and first-find counters are saved this often (and on quit / profile switch).", min = 5, max = 600)
    public int dropSaveSeconds = 30;

    // ------------------------------------------------------------------ gathering + area effects (§6.1–§6.3, E2)

    @Setting(path = "gathering.max-blocks-per-second", desc = "Hard cap of the speed family: no speed burst (Momentum, Haste) applies while a player breaks this many blocks a second.",
            min = 1, max = 40, level = Setting.Level.OWNER)
    public int maxBlocksPerSecond = 6;
    @Setting(path = "gathering.geode-min-crystals", desc = "Least crystals a Crystal Geode / Amber Resin gives (when the tier gives few crystals).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public long geodeMinCrystals = 5;
    @Setting(path = "gathering.proc-message-interval-ms", desc = "Shortest time between two action bar lines for gathering and sword procs.", min = 0, max = 60_000)
    public int gatherMessageIntervalMs = 1000;

    @Setting(path = "area.max-blocks-per-proc", desc = "Most blocks one area proc (Vein Rend, Dragonfire Blast, Treefeller, Wildfire, Worldbreaker, Ashen Grove) breaks.",
            min = 1, max = 256, level = Setting.Level.OWNER)
    public int areaMaxBlocks = 80;
    @Setting(path = "area.max-blocks-per-second", desc = "Most extra area blocks per player per second (refilling budget, up to max-blocks-per-proc at once).",
            min = 1, max = 1000, level = Setting.Level.OWNER)
    public int areaMaxPerSecond = 40;
    @Setting(path = "area.tree-max-logs", desc = "Most logs that count as one tree (Treefeller, Wildfire, Ashen Grove).", min = 1, max = 256)
    public int areaTreeMaxLogs = 64;
    @Setting(path = "area.tree-scan-down", desc = "Wildfire / Ashen Grove look for other trees this many blocks below the cut log.", min = 0, max = 16)
    public int areaScanDown = 4;
    @Setting(path = "area.tree-scan-up", desc = "Wildfire / Ashen Grove look for other trees this many blocks above the cut log.", min = 0, max = 32)
    public int areaScanUp = 12;

    // ------------------------------------------------------------------ Depths sword effects (§6.5, E2)

    @Setting(path = "combat.cleave-max-targets", desc = "Most other mobs one Cleave hit reaches.", min = 0, max = 64, level = Setting.Level.OWNER)
    public int cleaveMaxTargets = 8;
    @Setting(path = "combat.nova-max-targets", desc = "Most mobs one Phoenix Wrath fire nova hits (mobs that are not stacks).", min = 0, max = 64, level = Setting.Level.OWNER)
    public int novaMaxTargets = 16;
    @Setting(path = "combat.perf-warn-ms-per-second", desc = "Log a warning when the E2 gathering and sword effects use more main-thread time than this (ms per second, checked every minute).",
            min = 0.1, max = 1000)
    public double effectsPerfWarnMs = 5;

    // ------------------------------------------------------------------ armor enchants on wardrobe set pieces (E5)

    @Setting(path = "armor.effects-enabled", desc = "Armor enchant effects on worn set pieces (Vitality, Fortitude, Phoenix Rebirth ...). Off = they stay on the pieces and do nothing.")
    public boolean armorEffects = true;

    @Setting(path = "armor.refresh-ticks", desc = "How often (ticks) max health, walk speed, Night Eye and Second Wind are updated.", min = 5, max = 200)
    public int armorRefreshTicks = 20;

    @Setting(path = "armor.night-vision-ticks", desc = "Length of the Night Eye effect, refreshed before it runs out (above 200 avoids the vanilla flicker).",
            min = 220, max = 2400)
    public int nightVisionTicks = 400;

    @Setting(path = "armor.thorns-max-percent", desc = "Phoenix Thorns on chestplate and leggings add up to at most this share of the melee damage.",
            min = 0, max = 100, level = Setting.Level.OWNER)
    public double thornsMaxPercent = 30;

    @Setting(path = "armor.mythic-slot-mythic-only", desc = "The chestplate's mythic slot takes Mythic enchants only (false: any enchant).", level = Setting.Level.OWNER)
    public boolean mythicSlotMythicOnly = true;

    @Setting(path = "sounds.rebirth", desc = "Sound when Phoenix Rebirth saves a player.")
    public String soundRebirth = "unlock";

    // ------------------------------------------------------------------ rules and feel

    @Setting(path = "rules.two-slot-in-prestige-slot", desc = "May a 2-slot enchant use the prestige slot as its second slot.")
    public boolean twoSlotInPrestige = false;

    @Setting(path = "operation-timeout-seconds", desc = "A stuck enchant operation unlocks the player's inventory after this many seconds.", min = 2, max = 120)
    public int operationTimeoutSeconds = 15;

    @Setting(path = "sounds.apply", desc = "Sound (sounds.yml key) after an enchant was applied, upgraded or combined.")
    public String soundApply = "enchant";
    @Setting(path = "sounds.roll", desc = "Sound after a roll gave a book.")
    public String soundRoll = "unlock";
    @Setting(path = "sounds.stack-proc", desc = "Sound when Reaper's Sweep or Phoenix Wrath kills mobs of other stacks.")
    public String soundStackProc = "notify";
    @Setting(path = "sounds.stack-book", desc = "Sound when a book drops from stacked mobs.")
    public String soundStackBook = "unlock";
    @Setting(path = "sounds.error", desc = "Sound when something is not possible.")
    public String soundError = "error";
    @Setting(path = "sounds.drop-book", desc = "Sound when a book drops in The Depths.")
    public String soundDropBook = "unlock";
    @Setting(path = "sounds.area-proc", desc = "Sound when an area enchant breaks extra blocks.")
    public String soundAreaProc = "notify";
    @Setting(path = "sounds.gather-proc", desc = "Sound for a Crystal Geode, Amber Resin, Gilded drop or a speed burst.")
    public String soundGatherProc = "notify";
    @Setting(path = "sounds.nova", desc = "Sound when Phoenix Wrath's fire nova goes off.")
    public String soundNova = "notify";

    @Override
    public String fileName() {
        return "enchanter.yml";
    }

    @Override
    public String header() {
        return """
                The Enchanter (/enchanter): crystal costs, fees, Book shop rolls, tiers and bonus family caps.
                Every number is a default from docs/plans/ENCHANTS.md that the owner can change.
                Enchant definitions are in enchants.yml, texts in messages/enchants.yml.
                Reload: /skyblock reload enchants""";
    }

    // ------------------------------------------------------------------ typed views

    Map<Rarity, Double> rarityFactors() {
        Map<Rarity, Double> out = new EnumMap<>(Rarity.class);
        out.put(Rarity.COMMON, factorCommon);
        out.put(Rarity.UNCOMMON, factorUncommon);
        out.put(Rarity.RARE, factorRare);
        out.put(Rarity.EPIC, factorEpic);
        out.put(Rarity.LEGENDARY, factorLegendary);
        out.put(Rarity.MYTHIC, factorMythic);
        return out;
    }

    EnchantCosts costs() {
        return new EnchantCosts(stepBase, stepGrowth, steps, singleLevelStep, mythicStep, rarityFactors(), applyBookPercent,
                combinePercent, shatterRefundPercent);
    }

    Map<BonusFamily, Double> caps() {
        Map<BonusFamily, Double> out = new EnumMap<>(BonusFamily.class);
        out.put(BonusFamily.CRYSTALS, capCrystals);
        out.put(BonusFamily.EXTRA_DROPS, capExtraDrops);
        out.put(BonusFamily.SPEED, capSpeed);
        out.put(BonusFamily.SKILL_XP, capSkillXp);
        out.put(BonusFamily.TOOL_XP, capToolXp);
        out.put(BonusFamily.RARE_FINDS, capRareFinds);
        out.put(BonusFamily.AREA, capArea);
        out.put(BonusFamily.MOB_DAMAGE, capMobDamage);
        out.put(BonusFamily.DAMAGE_REDUCTION, capDamageReduction);
        out.put(BonusFamily.HEALTH, capHealth);
        out.put(BonusFamily.CROP_GROWTH, capCropGrowth);
        out.put(BonusFamily.FUEL_EFFICIENCY, capFuelEfficiency);
        out.put(BonusFamily.CONQUEST, capConquest);
        return out;
    }

    RollType.Settings roll(RollType type) {
        return switch (type) {
            case APPRENTICE -> new RollType.Settings(apprenticePrice, Rolls.parseOdds(apprenticeOdds), apprenticeTier, apprenticeDaily);
            case ADEPT -> new RollType.Settings(adeptPrice, Rolls.parseOdds(adeptOdds), adeptTier, adeptDaily);
            case MASTER -> new RollType.Settings(masterPrice, Rolls.parseOdds(masterOdds), masterTier, masterDaily);
        };
    }

    List<Pool> rollPools() {
        List<Pool> out = new ArrayList<>();
        for (String raw : rollPools) {
            Pool.byId(raw).filter(p -> !out.contains(p)).ifPresent(out::add);
        }
        return out;
    }

    /** Skill levels that open tiers 1, 2, 3 … of a pool's wing (bad entries skipped, sorted). */
    List<Integer> tierLevels(Pool pool) {
        List<String> raw = switch (pool) {
            case MINING -> tiersMining;
            case FORAGING -> tiersForaging;
            case COMBAT, STACKS -> tiersCombat;
            case FARMING -> tiersFarming;
            case FISHING -> tiersFishing;
        };
        List<Integer> out = new ArrayList<>();
        for (String s : raw) {
            try {
                out.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                // skipped; validate() reports it
            }
        }
        out.sort(Integer::compare);
        return out;
    }

    /** The book drop settings (§3.2–§3.4) as typed values; broken entries are skipped (validate() reports them). */
    DropMath.Rules dropRules() {
        Map<Pool, List<Integer>> oneIn = new EnumMap<>(Pool.class);
        oneIn.put(Pool.MINING, DropMath.parseInts(dropMiningOneIn));
        oneIn.put(Pool.FORAGING, DropMath.parseInts(dropForagingOneIn));
        oneIn.put(Pool.COMBAT, DropMath.parseInts(dropCombatOneIn));
        Map<Pool, List<Integer>> pity = new EnumMap<>(Pool.class);
        pity.put(Pool.MINING, DropMath.parseInts(dropMiningPity));
        pity.put(Pool.FORAGING, DropMath.parseInts(dropForagingPity));
        pity.put(Pool.COMBAT, DropMath.parseInts(dropCombatPity));
        List<Map<Rarity, Double>> odds = List.of(Rolls.parseOdds(dropOddsA), Rolls.parseOdds(dropOddsB), Rolls.parseOdds(dropOddsC),
                Rolls.parseOdds(dropOddsD));
        List<List<String>> levels = List.of(dropLevelsA, dropLevelsB, dropLevelsC, dropLevelsD);
        List<Integer> bands = DropMath.parseInts(dropBandTiers);
        bands.sort(Integer::compare);
        return new DropMath.Rules(dropsEnabled, bands, oneIn, pity, odds, levels, DropMath.parseMilestones(dropFirstFinds),
                dropMiniBossChance, dropMiniBossBands);
    }

    /** Readable problems with list settings (number formats), empty when all is fine. */
    List<String> validate() {
        List<String> problems = new ArrayList<>();
        Map<String, List<String>> odds = new LinkedHashMap<>();
        odds.put("rolls.apprentice.odds", apprenticeOdds);
        odds.put("rolls.adept.odds", adeptOdds);
        odds.put("rolls.master.odds", masterOdds);
        odds.forEach((path, list) -> {
            for (String entry : list) {
                if (Rolls.parseOdd(entry) == null) {
                    problems.add("enchanter.yml → " + path + ": '" + entry + "' is not rarity:weight (e.g. rare:50); skipped");
                }
            }
            if (Rolls.parseOdds(list).isEmpty()) {
                problems.add("enchanter.yml → " + path + ": no valid odds; this roll is off");
            }
        });
        for (String entry : stackBookOdds) {
            if (Rolls.parseOdd(entry) == null) {
                problems.add("enchanter.yml → stacks.book-odds: '" + entry + "' is not rarity:weight (e.g. rare:40); skipped");
            }
        }
        for (String entry : stackBookLevelOdds) {
            if (StackKillMath.parseLevelOdd(entry) == null) {
                problems.add("enchanter.yml → stacks.book-level-odds: '" + entry + "' is not level:weight (e.g. 1:85); skipped");
            }
        }
        Map<String, List<String>> dropOdds = new LinkedHashMap<>();
        dropOdds.put("drops.rarity-odds.a", dropOddsA);
        dropOdds.put("drops.rarity-odds.b", dropOddsB);
        dropOdds.put("drops.rarity-odds.c", dropOddsC);
        dropOdds.put("drops.rarity-odds.d", dropOddsD);
        dropOdds.forEach((path, list) -> {
            for (String entry : list) {
                if (Rolls.parseOdd(entry) == null) {
                    problems.add("enchanter.yml → " + path + ": '" + entry + "' is not rarity:weight (e.g. rare:16); skipped");
                }
            }
        });
        Map<String, List<String>> dropLevels = new LinkedHashMap<>();
        dropLevels.put("drops.level-odds.a", dropLevelsA);
        dropLevels.put("drops.level-odds.b", dropLevelsB);
        dropLevels.put("drops.level-odds.c", dropLevelsC);
        dropLevels.put("drops.level-odds.d", dropLevelsD);
        dropLevels.forEach((path, list) -> {
            for (String entry : list) {
                if (StackKillMath.parseLevelOdd(entry) == null) {
                    problems.add("enchanter.yml → " + path + ": '" + entry + "' is not level:weight (e.g. 1:85); skipped");
                }
            }
        });
        Map<String, List<String>> numbers = new LinkedHashMap<>();
        numbers.put("drops.band-tiers", dropBandTiers);
        numbers.put("drops.mining.one-in", dropMiningOneIn);
        numbers.put("drops.mining.pity", dropMiningPity);
        numbers.put("drops.foraging.one-in", dropForagingOneIn);
        numbers.put("drops.foraging.pity", dropForagingPity);
        numbers.put("drops.combat.one-in", dropCombatOneIn);
        numbers.put("drops.combat.pity", dropCombatPity);
        numbers.forEach((path, list) -> {
            if (DropMath.parseInts(list).size() != list.size()) {
                problems.add("enchanter.yml → " + path + ": every entry must be a whole number ≥ 0; broken ones skipped");
            }
        });
        for (String entry : dropFirstFinds) {
            if (DropMath.parseMilestone(entry) == null) {
                problems.add("enchanter.yml → drops.first-finds: '" + entry + "' is not actions:rarity (e.g. 100:common); skipped");
            }
        }
        for (String raw : rollPools) {
            if (Pool.byId(raw).isEmpty()) {
                problems.add("enchanter.yml → rolls.pools: unknown pool '" + raw + "'");
            }
        }
        for (Pool pool : Pool.values()) {
            List<String> raw = switch (pool) {
                case MINING -> tiersMining;
                case FORAGING -> tiersForaging;
                case COMBAT, STACKS -> tiersCombat;
                case FARMING -> tiersFarming;
                case FISHING -> tiersFishing;
            };
            for (String s : raw) {
                try {
                    Integer.parseInt(s.trim());
                } catch (NumberFormatException e) {
                    problems.add("enchanter.yml → tiers." + pool.id() + ": '" + s + "' is not a number; skipped");
                }
            }
        }
        return problems.stream().distinct().toList();
    }

    static String rarityKey(Rarity rarity) {
        return rarity.name().toLowerCase(Locale.ROOT);
    }
}
