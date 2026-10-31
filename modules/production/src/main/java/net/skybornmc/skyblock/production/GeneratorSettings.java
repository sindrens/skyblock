package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the custom island generators ({@code generators.yml}, PHASE-7-PRODUCTION.md M7.2). Texts live in
 * {@code messages/production.yml → generator.*}. Every number is a default the owner can change.
 */
public final class GeneratorSettings extends ModuleConfig {

    @Setting(path = "enabled", desc = "Custom generators on/off (master switch, OFF until the owner enables production). On: any lava + "
            + "water cobble generator on an island forms a block rolled from the island's Generator level (levels below), the "
            + "Generator upgrade in /is upgrade can be bought, Ashstone and Emberite drop. Off: every generator stays vanilla. "
            + "Apply with /skyblock reload production.", level = Setting.Level.OWNER)
    public boolean enabled = false;

    @Setting(path = "form-blocks", desc = "Blocks lava + water form that the generator replaces with its roll (basalt and obsidian "
            + "always stay vanilla). The 'cobble' share of a level keeps the block that formed.")
    public List<String> formBlocks = List.of("cobblestone", "stone");

    @Setting(path = "lag-guard.max-rolls-per-second", desc = "Most custom rolls per island per second; above it the generator forms "
            + "the plain block (cobblestone / stone). Lag-machine detection stays the main guard.", min = 1, max = 1_000)
    public int maxRollsPerSecond = 40;

    @Setting(path = "fuel.plain-when-asleep", desc = "While Island Fuel is on: an Asleep island (empty tank, no active member) forms "
            + "plain cobblestone. A member's first action wakes the island, so the next block rolls again.", level = Setting.Level.OWNER)
    public boolean plainWhenAsleep = true;

    @Setting(path = "pillar-xp.percent", desc = "Mining pillar XP for a generator-made ore, in percent of the matching Depths ore's XP "
            + "(depths-xp in the ores list; PD8: 25%). Only blocks the generator really made, only for members on their own "
            + "island. Never Mining skill XP.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double pillarXpPercent = 25;

    @Setting(path = "memory.per-island", desc = "Generator-made block positions remembered per island (oldest forgotten first; a "
            + "restart forgets all, so at worst a few blocks give no pillar XP). A generator re-forms at the same few positions.",
            min = 0, max = 100_000)
    public int memoryPerIsland = 512;

    @Setting(path = "test.level-override", desc = "Staff test switch: every island uses this Generator level (-1 = off, the "
            + "Generator upgrade decides). Turn it off again after testing.", min = -1, max = 100, level = Setting.Level.OWNER)
    public int levelOverride = -1;

    @Setting(path = "ores", desc = "Generator blocks, one per line: '<id>: block=<block|keep> [tier=<Depths mine tier>] "
            + "[depths-xp=<XP of that Depths ore>] [item=<item> rarity=<rarity>]'. block=keep keeps the block that formed "
            + "(cobblestone). Vanilla ore blocks drop their vanilla items; an entry with item= is a custom ore: its block always "
            + "drops one custom item custom:<id> (sell.yml prices it; names in messages/production.yml generator.items.<id>). "
            + "Custom ore blocks can't be placed on islands and survive explosions.", level = Setting.Level.OWNER)
    public List<String> ores = List.of(
            "cobble: block=keep",
            "coal: block=coal_ore tier=1 depths-xp=1",
            "copper: block=copper_ore tier=2 depths-xp=1.5",
            "iron: block=iron_ore tier=3 depths-xp=2.5",
            "gold: block=gold_ore tier=4 depths-xp=4",
            "redstone: block=redstone_ore tier=5 depths-xp=6",
            "lapis: block=lapis_ore tier=6 depths-xp=9",
            "diamond: block=diamond_ore tier=7 depths-xp=13",
            "emerald: block=emerald_ore tier=8 depths-xp=19",
            "ashstone: block=gilded_blackstone tier=9 depths-xp=28 item=flint rarity=rare",
            "emberite: block=nether_gold_ore tier=10 depths-xp=40 item=resin_brick rarity=epic");

    @Setting(path = "levels", desc = "Generator levels (the Generator upgrade's value), one per line: '<level>: <ore>=<weight> ...'. "
            + "Weights are shares (they don't have to add up to 100). An island uses the highest listed level at or below its "
            + "own; level 0 should be pure cobble.", level = Setting.Level.OWNER)
    public List<String> levels = List.of(
            "0: cobble=100",
            "1: cobble=90 coal=10",
            "2: cobble=82 coal=12 copper=6",
            "3: cobble=74 coal=12 copper=8 iron=6",
            "4: cobble=66 coal=12 copper=9 iron=8 gold=5",
            "5: cobble=60 coal=11 copper=9 iron=8 gold=7 redstone=5",
            "6: cobble=54 coal=10 copper=9 iron=8 gold=7 redstone=7 lapis=5",
            "7: cobble=49 coal=9 copper=8 iron=8 gold=7 redstone=7 lapis=7 diamond=5",
            "8: cobble=45 coal=8 copper=8 iron=7 gold=7 redstone=7 lapis=7 diamond=6 emerald=5",
            "9: cobble=42 coal=7 copper=7 iron=7 gold=7 redstone=7 lapis=6 diamond=6 emerald=6 ashstone=5",
            "10: cobble=40 coal=6 copper=6 iron=6 gold=6 redstone=6 lapis=6 diamond=6 emerald=6 ashstone=7 emberite=5");

    @Override
    public String fileName() {
        return "generators.yml";
    }

    @Override
    public String header() {
        return "Custom island generators (PHASE-7 M7.2): lava + water on an island forms ores by the island's Generator level.\n"
                + "OFF by default: set enabled: true, then /skyblock reload production.\n"
                + "Texts: messages/production.yml (generator.*). Prices: sell.yml. Reload: /skyblock reload production";
    }
}
