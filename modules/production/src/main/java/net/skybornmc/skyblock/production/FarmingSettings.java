package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of island farming ({@code crops.yml}, PHASE-7-PRODUCTION.md M7.3). Texts live in
 * {@code messages/production.yml → farming.*}. Every number is a default the owner can change.
 */
public final class FarmingSettings extends ModuleConfig {

    @Setting(path = "enabled", desc = "Farming on/off (master switch, OFF until the owner enables production). On: planting needs the "
            + "crop's Farming level, ripe crops broken with a hoe on your own island give Farming XP (and through it Farming "
            + "pillar XP), /skills shows Farming, the Crop Growth and Harvest Yield upgrades can be bought, harvest quests are "
            + "offered. Off: crops are plain vanilla. Apply with /skyblock reload production.", level = Setting.Level.OWNER)
    public boolean enabled = false;

    @Setting(path = "crops", desc = "Crop tiers, one crop per line: '<id>: tier=<n> level=<Farming level> xp=<Farming XP per ripe "
            + "crop> block=<harvested block> item=<crop item> [plant=<block the seed places>] [kind=age|stack|fruit]'. kind=age "
            + "(default): ripe at full age, the seed places the same block. kind=stack (sugar cane, cactus): only blocks that "
            + "grew count, the whole grown column above the broken block too. kind=fruit (melon, pumpkin, torchflower): only "
            + "blocks that grew from their plant count. Names: messages/production.yml farming.crops.<id>.",
            level = Setting.Level.OWNER)
    public List<String> crops = List.of(
            "wheat: tier=1 level=0 xp=1 block=wheat item=wheat",
            "carrot: tier=2 level=5 xp=1.5 block=carrots item=carrot",
            "potato: tier=2 level=5 xp=1.5 block=potatoes item=potato",
            "beetroot: tier=3 level=10 xp=2.5 block=beetroots item=beetroot",
            "sugar_cane: tier=4 level=15 xp=4 block=sugar_cane item=sugar_cane kind=stack",
            "melon: tier=5 level=20 xp=6 block=melon plant=melon_stem item=melon_slice kind=fruit",
            "pumpkin: tier=5 level=20 xp=6 block=pumpkin plant=pumpkin_stem item=pumpkin kind=fruit",
            "cocoa: tier=6 level=25 xp=9 block=cocoa item=cocoa_beans",
            "cactus: tier=7 level=30 xp=13 block=cactus item=cactus kind=stack",
            "sweet_berries: tier=8 level=35 xp=19 block=sweet_berry_bush item=sweet_berries",
            "nether_wart: tier=9 level=42 xp=28 block=nether_wart item=nether_wart",
            "torchflower: tier=10 level=50 xp=40 block=torchflower plant=torchflower_crop item=torchflower kind=fruit",
            "pitcher_plant: tier=11 level=58 xp=58 block=pitcher_crop item=pitcher_plant");

    @Setting(path = "planting.gate", desc = "Planting a crop needs its Farming level (PD10); a locked seed is refused with 'Farming 15 "
            + "needed: 9/15'. Only on islands; creative mode skips it.", level = Setting.Level.OWNER)
    public boolean plantingGate = true;

    @Setting(path = "planting.message-cooldown-ms", desc = "Shortest time between two 'Farming N needed' messages for one player "
            + "(right-click spam).", min = 0, max = 60_000)
    public int lockedMessageCooldownMs = 1_500;

    @Setting(path = "xp.need-hoe", desc = "Farming XP only for ripe crops broken (or sweet berries picked) with a hoe, any hoe (PD11).",
            level = Setting.Level.OWNER)
    public boolean xpNeedsHoe = true;

    @Setting(path = "growth.upgrade-effect", desc = "Island upgrade effect that speeds up crop growth (upgrades.yml effect, value = +%).")
    public String growthUpgradeEffect = "crop-growth";

    @Setting(path = "growth.family-activity", desc = "Activity asked from the CROP_GROWTH bonus family (Cropwarden Robes and other "
            + "'nearby crop growth' bonuses of members standing on the island; capped by enchanter.yml caps.crop-growth).")
    public String growthFamilyActivity = "crop_growth";

    @Setting(path = "growth.refresh-seconds", desc = "How often the growth bonus of each island (upgrade + members' nearby bonuses) "
            + "is worked out again.", min = 1, max = 600)
    public int growthRefreshSeconds = 10;

    @Setting(path = "yield.upgrade-effect", desc = "Island upgrade effect for extra crops (upgrades.yml effect, value = % chance of "
            + "one extra copy of the crop items per ripe harvest; above 100% one extra is sure and the rest is the chance of a "
            + "second).")
    public String yieldUpgradeEffect = "harvest-yield";

    @Setting(path = "memory.per-island", desc = "Grown and bone-mealed block positions remembered per island (oldest forgotten "
            + "first; a restart forgets all, so at worst a few crops give no XP, never extra).", min = 0, max = 100_000)
    public int memoryPerIsland = 4_096;

    @Override
    public String fileName() {
        return "crops.yml";
    }

    @Override
    public String header() {
        return "Island farming (PHASE-7 M7.3): crop tiers, planting gates, Farming XP, Crop Growth and Harvest Yield.\n"
                + "OFF by default: set enabled: true, then /skyblock reload production.\n"
                + "Texts: messages/production.yml (farming.*). Reload: /skyblock reload production";
    }
}
