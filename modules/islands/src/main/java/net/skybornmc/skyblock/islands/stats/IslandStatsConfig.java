package net.skybornmc.skyblock.islands.stats;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import net.skybornmc.skyblock.common.island.IslandStat;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Island stats settings (M2.6, {@code island-stats.yml}). Texts are in messages/islands.yml under {@code stats}. */
public final class IslandStatsConfig extends ModuleConfig {

    @Setting(desc = "Track island stats at all (off = nothing is counted; stored stats stay).")
    public boolean enabled = true;

    @Setting(desc = "Time zone of the day and week buckets (owner: Europe/Oslo). Days start at local midnight, weeks on Monday.",
            restart = true, level = Setting.Level.OWNER)
    public String timezone = "Europe/Oslo";

    @Setting(path = "flush-seconds", desc = "Counters are written to the database in one batch this often (and on shutdown). "
            + "A crash loses at most this many seconds of stats.", min = 5, max = 600)
    public int flushSeconds = 30;

    @Setting(path = "playtime-sample-seconds", desc = "How often members on their own island get playtime added (seconds per sample).",
            min = 10, max = 600)
    public int playtimeSampleSeconds = 60;

    @Setting(path = "retention.daily-days", desc = "Day buckets older than this many days are deleted (totals stay forever).",
            min = 8, max = 3650, level = Setting.Level.OWNER)
    public int dailyDays = 35;

    @Setting(path = "retention.weekly-weeks", desc = "Week buckets older than this many weeks are deleted (totals stay forever).",
            min = 2, max = 520, level = Setting.Level.OWNER)
    public int weeklyWeeks = 104;

    @Setting(path = "retention.purge-hours", desc = "How often old buckets are deleted (hours; also once shortly after start).",
            min = 1, max = 168)
    public int purgeHours = 6;

    @Setting(path = "anti-farm.placed-memory", desc = "Per island: the last this many placed block positions are remembered; "
            + "breaking one of them again doesn't count as mined, chopped or harvested (0 = off).", min = 0, max = 100_000)
    public int placedMemory = 512;

    @Setting(path = "crops.ripe-blocks", desc = "Crops that count as harvested when broken fully grown (max age).")
    public List<String> ripeBlocks = List.of("wheat", "carrots", "potatoes", "beetroots", "nether_wart", "cocoa",
            "sweet_berry_bush", "torchflower_crop", "pitcher_crop");

    @Setting(path = "crops.harvest-blocks", desc = "Blocks that always count as harvested when broken (unless placed there recently).")
    public List<String> harvestBlocks = List.of("sugar_cane", "cactus", "melon", "pumpkin", "bamboo", "kelp", "kelp_plant",
            "chorus_flower", "chorus_plant", "brown_mushroom", "red_mushroom");

    @Setting(path = "generator.blocks", desc = "Blocks made by lava + water that count as generator output.")
    public List<String> generatorBlocks = List.of("cobblestone", "stone", "basalt");

    @Setting(path = "menu.icons", desc = "Icon per category in /is stats as category:item.")
    public List<String> icons = List.of("blocks_mined:iron_pickaxe", "logs_chopped:iron_axe", "crops_harvested:wheat",
            "blocks_placed:bricks", "generator_output:cobblestone", "mobs_killed:iron_sword", "items_sold:chest",
            "money_earned:gold_ingot", "visitors:oak_door", "playtime:clock", "koth_time:beacon", "bosses_killed:wither_skeleton_skull");

    @Setting(path = "menu.max-members", desc = "Most members shown in a category's contribution screen (the rest add up in one line).",
            min = 1, max = 28)
    public int maxMembers = 21;

    /** {@link #timezone}, or Europe/Oslo when invalid. */
    public ZoneId zone() {
        try {
            return ZoneId.of(timezone.strip());
        } catch (RuntimeException e) {
            return ZoneId.of("Europe/Oslo");
        }
    }

    /** {@code menu.icons} parsed: category → item key (lower case); unknown categories are reported by {@link #problems}. */
    public Map<IslandStat, String> iconKeys() {
        Map<IslandStat, String> out = new EnumMap<>(IslandStat.class);
        for (String entry : icons) {
            int colon = entry.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            IslandStat.byKey(entry.substring(0, colon)).ifPresent(stat -> out.put(stat, entry.substring(colon + 1).strip().toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    /** A block list as lower-case keys without the {@code minecraft:} prefix. */
    public static Set<String> keys(List<String> list) {
        Set<String> out = new TreeSet<>();
        for (String s : list) {
            if (s != null && !s.isBlank()) {
                String k = s.strip().toLowerCase(Locale.ROOT);
                out.add(k.startsWith("minecraft:") ? k.substring("minecraft:".length()) : k);
            }
        }
        return out;
    }

    /** Problems shown in the console on enable/reload; empty when fine. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        try {
            ZoneId.of(timezone.strip());
        } catch (RuntimeException e) {
            problems.add("island-stats.yml: timezone '" + timezone + "' is not a valid time zone; using Europe/Oslo.");
        }
        for (String entry : icons) {
            int colon = entry.indexOf(':');
            if (colon <= 0 || IslandStat.byKey(entry.substring(0, colon)).isEmpty()) {
                problems.add("island-stats.yml: menu.icons entry '" + entry + "' is not category:item.");
            }
        }
        return problems;
    }

    @Override
    public String fileName() {
        return "island-stats.yml";
    }

    @Override
    public String header() {
        return """
                Island stats (M2.6, /is stats): counters per island and member in day and week buckets plus all-time totals.
                Recording is in memory; batches are written every flush-seconds and on shutdown. Texts: messages/islands.yml (stats).
                Reload: /skyblock reload islands""";
    }
}
