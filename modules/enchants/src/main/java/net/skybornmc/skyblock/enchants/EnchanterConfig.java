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
    @Setting(path = "caps.conquest", desc = "Cap of each conquest stat in percent (envoy luck, capture speed, Conquest XP, warzone death-loss reduction).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public double capConquest = 60;

    // ------------------------------------------------------------------ rules and feel

    @Setting(path = "rules.two-slot-in-prestige-slot", desc = "May a 2-slot enchant use the prestige slot as its second slot.")
    public boolean twoSlotInPrestige = false;

    @Setting(path = "operation-timeout-seconds", desc = "A stuck enchant operation unlocks the player's inventory after this many seconds.", min = 2, max = 120)
    public int operationTimeoutSeconds = 15;

    @Setting(path = "sounds.apply", desc = "Sound (sounds.yml key) after an enchant was applied, upgraded or combined.")
    public String soundApply = "enchant";
    @Setting(path = "sounds.roll", desc = "Sound after a roll gave a book.")
    public String soundRoll = "unlock";
    @Setting(path = "sounds.error", desc = "Sound when something is not possible.")
    public String soundError = "error";

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
