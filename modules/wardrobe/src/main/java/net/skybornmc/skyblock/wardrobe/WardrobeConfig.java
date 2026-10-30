package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wardrobe settings ({@code wardrobe.yml}, WARDROBE §8.3). Every number is a default the owner can change. Set definitions
 * live in {@code sets.yml}, texts in {@code messages/wardrobe.yml}.
 */
final class WardrobeConfig extends ModuleConfig {

    @Setting(path = "season-id", desc = "Current season (gem-set claims are once per profile per season). Stays 0 until the season system exists.",
            min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int seasonId = 0;

    // ------------------------------------------------------------------ levels (W2)

    @Setting(path = "levels.enabled", desc = "Set levels (W2). While off every owned set gives its bonuses at levels.fixed-multiplier.",
            level = Setting.Level.OWNER)
    public boolean levelsEnabled = false;

    @Setting(path = "levels.fixed-multiplier", desc = "Bonus multiplier of every set while set levels are off (1.0 = the values in sets.yml).",
            min = 0, max = 10, level = Setting.Level.OWNER)
    public double fixedMultiplier = 1.0;

    @Setting(path = "levels.max", desc = "Highest set level.", min = 1, max = 1000, level = Setting.Level.OWNER)
    public int maxLevel = 50;

    @Setting(path = "levels.max-multiplier", desc = "Bonus multiplier at the highest set level (linear from 0x at level 0).",
            min = 0, max = 100, level = Setting.Level.OWNER)
    public double maxMultiplier = 2.0;

    @Setting(path = "enchants.second-slot-level", desc = "Set level that opens a 2nd armor enchant slot on every piece (EN12, used by E5).",
            min = 0, max = 1000, level = Setting.Level.OWNER)
    public int secondSlotLevel = 25;

    // ------------------------------------------------------------------ equipping

    @Setting(path = "equip.cooldown-seconds", desc = "Seconds between two wear/take-off actions (shared with loadouts later).", min = 0, max = 60)
    public int equipCooldownSeconds = 2;

    @Setting(path = "equip.blocked-worlds", desc = "Worlds where sets can't be put on or taken off (the warzone).")
    public List<String> blockedWorlds = List.of("warzone");

    @Setting(path = "equip.hint-cooldown-seconds", desc = "Seconds between two \"Use /wardrobe to take this off\" hints.", min = 0, max = 300)
    public int hintCooldownSeconds = 3;

    @Setting(path = "features.available", desc = "Features that exist on this server; sets with enabled-from a feature not listed show \"Arrives with ...\" (conquest, fishing).",
            level = Setting.Level.OWNER)
    public List<String> availableFeatures = List.of();

    // ------------------------------------------------------------------ armor points per tier (attribute modifiers)

    @Setting(path = "armor.tier-1.armor", desc = "Armor points per piece (helm, cuirass, greaves, boots) for tier I sets (iron-like).", level = Setting.Level.OWNER)
    public List<String> armorTier1 = List.of("2", "6", "5", "2");
    @Setting(path = "armor.tier-1.toughness", desc = "Armor toughness per piece for tier I sets.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double toughnessTier1 = 0;
    @Setting(path = "armor.tier-1.knockback-resistance", desc = "Knockback resistance per piece (0.025 = 2.5%) for tier I sets.", min = 0, max = 1, level = Setting.Level.OWNER)
    public double knockbackTier1 = 0;

    @Setting(path = "armor.tier-2.armor", desc = "Armor points per piece for tier II sets (diamond-like).", level = Setting.Level.OWNER)
    public List<String> armorTier2 = List.of("3", "8", "6", "3");
    @Setting(path = "armor.tier-2.toughness", desc = "Armor toughness per piece for tier II sets.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double toughnessTier2 = 2;
    @Setting(path = "armor.tier-2.knockback-resistance", desc = "Knockback resistance per piece for tier II sets.", min = 0, max = 1, level = Setting.Level.OWNER)
    public double knockbackTier2 = 0;

    @Setting(path = "armor.tier-3.armor", desc = "Armor points per piece for tier III sets (diamond-like).", level = Setting.Level.OWNER)
    public List<String> armorTier3 = List.of("3", "8", "6", "3");
    @Setting(path = "armor.tier-3.toughness", desc = "Armor toughness per piece for tier III sets.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double toughnessTier3 = 2;
    @Setting(path = "armor.tier-3.knockback-resistance", desc = "Knockback resistance per piece for tier III sets.", min = 0, max = 1, level = Setting.Level.OWNER)
    public double knockbackTier3 = 0;

    @Setting(path = "armor.tier-4.armor", desc = "Armor points per piece for tier IV sets (netherite-like).", level = Setting.Level.OWNER)
    public List<String> armorTier4 = List.of("3", "8", "6", "3");
    @Setting(path = "armor.tier-4.toughness", desc = "Armor toughness per piece for tier IV sets.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double toughnessTier4 = 3;
    @Setting(path = "armor.tier-4.knockback-resistance", desc = "Knockback resistance per piece for tier IV sets (4 x 0.025 = 10%).", min = 0, max = 1, level = Setting.Level.OWNER)
    public double knockbackTier4 = 0.025;

    // ------------------------------------------------------------------ Wardrobe Chest (storage)

    @Setting(path = "storage.size", desc = "Items the Wardrobe Chest holds per profile.", min = 0, max = 270)
    public int storageSize = 27;

    @Setting(path = "storage.per-grant", desc = "Extra items per storage rank grant (skyblock.wardrobe.storage.1-3; the highest counts).", min = 0, max = 270)
    public int storagePerGrant = 9;

    @Setting(path = "storage.item-types", desc = "Item types that may go in the Wardrobe Chest (unique, tracked items only).")
    public List<String> storageTypes = List.of("tool", "enchant_book", "scroll");

    @Setting(path = "storage.item-ids", desc = "Extra custom item ids that may go in the Wardrobe Chest (unique items only).")
    public List<String> storageIds = List.of("set_token", "wardrobe_tome");

    // ------------------------------------------------------------------ tokens

    @Setting(path = "tokens.material", desc = "Vanilla item a Set Token looks like (right-click to redeem; never placeable or craftable).")
    public String tokenMaterial = "ARMOR_STAND";

    // ------------------------------------------------------------------ gates (Depths tiers from skill levels)

    @Setting(path = "tiers.mining", desc = "Mining skill level that opens each Mine tier (tier 1 first); keep in line with The Depths.")
    public List<String> tiersMining = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58", "66");
    @Setting(path = "tiers.foraging", desc = "Foraging skill level that opens each Forest tier.")
    public List<String> tiersForaging = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58");
    @Setting(path = "tiers.combat", desc = "Combat skill level that opens each Combat tier.")
    public List<String> tiersCombat = List.of("0", "6", "12", "18", "24", "30", "38", "46", "55", "66");
    @Setting(path = "tiers.farming", desc = "Farming skill level per crop tier (Phase 7).")
    public List<String> tiersFarming = List.of("0");
    @Setting(path = "tiers.fishing", desc = "Fishing skill level per fishing band.")
    public List<String> tiersFishing = List.of("0", "20", "40", "60");

    // ------------------------------------------------------------------ safety

    @Setting(path = "reconcile.alert-staff", desc = "Tell staff (skyblock.staff.notify) when a copied set piece is found outside its owner's armor slots.")
    public boolean alertStaff = true;

    @Override
    public String fileName() {
        return "wardrobe.yml";
    }

    @Override
    public String header() {
        return """
                The wardrobe (/wardrobe): set levels switch, equip rules, armor points per tier, the Wardrobe Chest,
                Set Tokens and tier gates. The sets themselves are in sets.yml, texts in messages/wardrobe.yml.
                Reload: /skyblock reload wardrobe""";
    }

    // ------------------------------------------------------------------ typed views

    /** Armor points per piece for a tier (head, chest, legs, feet). */
    List<Double> armor(int tier) {
        return numbers(switch (tier) {
            case 1 -> armorTier1;
            case 2 -> armorTier2;
            case 3 -> armorTier3;
            default -> armorTier4;
        }, 4);
    }

    double toughness(int tier) {
        return switch (tier) {
            case 1 -> toughnessTier1;
            case 2 -> toughnessTier2;
            case 3 -> toughnessTier3;
            default -> toughnessTier4;
        };
    }

    double knockback(int tier) {
        return switch (tier) {
            case 1 -> knockbackTier1;
            case 2 -> knockbackTier2;
            case 3 -> knockbackTier3;
            default -> knockbackTier4;
        };
    }

    /** Skill levels that open tiers 1, 2, 3 … in a wing (mining, foraging, combat, farming, fishing). */
    List<Integer> tiers(String wing) {
        List<String> raw = switch (wing) {
            case "mining" -> tiersMining;
            case "foraging" -> tiersForaging;
            case "combat" -> tiersCombat;
            case "farming" -> tiersFarming;
            case "fishing" -> tiersFishing;
            default -> List.of();
        };
        List<Integer> out = new ArrayList<>();
        for (String s : raw) {
            try {
                out.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                // reported by validate()
            }
        }
        return out;
    }

    boolean featureAvailable(String feature) {
        return feature == null || feature.isEmpty()
                || availableFeatures.stream().anyMatch(f -> f.trim().equalsIgnoreCase(feature));
    }

    boolean blockedWorld(String world) {
        return blockedWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    boolean storable(String itemType, String itemId) {
        String type = itemType == null ? "" : itemType.toLowerCase(Locale.ROOT).replace('-', '_');
        return storageTypes.stream().anyMatch(t -> t.trim().toLowerCase(Locale.ROOT).replace('-', '_').equals(type))
                || (itemId != null && storageIds.stream().anyMatch(i -> i.trim().equalsIgnoreCase(itemId)));
    }

    /** Problems with list values (numbers that are not numbers). */
    List<String> validate() {
        List<String> out = new ArrayList<>();
        for (int tier = 1; tier <= 4; tier++) {
            if (armor(tier).size() != 4) {
                out.add("wardrobe.yml → armor.tier-" + tier + ".armor needs 4 numbers (helm, cuirass, greaves, boots); using 0 for the rest");
            }
        }
        for (String wing : List.of("mining", "foraging", "combat", "farming", "fishing")) {
            if (tiers(wing).isEmpty()) {
                out.add("wardrobe.yml → tiers." + wing + " has no numbers; nobody passes a " + wing + " tier gate");
            }
        }
        return out;
    }

    private static List<Double> numbers(List<String> raw, int size) {
        List<Double> out = new ArrayList<>();
        for (String s : raw) {
            try {
                double v = Double.parseDouble(s.trim());
                out.add(Double.isFinite(v) && v >= 0 ? v : 0);
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        return out.size() > size ? out.subList(0, size) : out;
    }
}
