package net.skybornmc.skyblock.shardblock;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Typed settings of {@code shardblock.yml} (ECON-SHARDS section 6). Numbers that move the economy are owner level. */
public final class ShardBlockConfig extends ModuleConfig {

    @Setting(path = "enabled", desc = "Shard blocks pay shards. Off = placed blocks stay as they are and pay nothing (rollback switch).",
            level = Setting.Level.OWNER)
    public boolean enabled = true;

    @Setting(path = "worlds", desc = "Worlds where shard blocks can be placed (island worlds only).")
    public List<String> worlds = List.of("islands");

    @Setting(path = "block.active-material", desc = "Block (and item) of a ready shard block. Pick one players cannot get otherwise.")
    public String activeMaterial = "INFESTED_STONE";

    @Setting(path = "block.depleted-material", desc = "Block shown while a shard block regrows (must not be breakable or pushable).")
    public String depletedMaterial = "BEDROCK";

    @Setting(path = "block.max-per-island", desc = "Shard blocks one island can have.", min = 1, max = 64, level = Setting.Level.OWNER)
    public int maxPerIsland = 4;

    @Setting(path = "shards.per-break", desc = "Shards paid per break (a double roll pays twice this).", min = 1, max = 1000,
            level = Setting.Level.OWNER)
    public int perBreak = 10;

    @Setting(path = "shards.daily-cap-per-profile", desc = "Most shards one profile can get from shard blocks per day (0 = no cap). "
            + "Checked inside the payout transaction.", min = 0, max = 1000000, level = Setting.Level.OWNER)
    public int dailyCap = 1000;

    @Setting(path = "shards.cap-zone", desc = "Time zone whose midnight starts a new day for the daily cap.")
    public String capZone = "Europe/Oslo";

    @Setting(path = "levels.respawn-seconds", desc = "Regrow time in seconds per respawn level (level 0 first).", level = Setting.Level.OWNER)
    public List<String> respawnSeconds = List.of("300", "240", "180", "120", "90", "60");

    /** Before owner 3 Nov 2026 ("faster, upgraded with something else than shards"). */
    static final List<String> RESPAWN_SECONDS_V1 = List.of("1800", "1500", "1200", "900", "600", "420");
    static final List<String> RESPAWN_COSTS_V1 = List.of("crystals:100", "crystals:300", "crystals:900", "crystals:2700", "crystals:8100");
    static final List<String> DOUBLE_COSTS_V1 = List.of("shards:10", "shards:25", "shards:50", "shards:100", "shards:200");

    @Setting(path = "levels.double-percent", desc = "Chance in % to get double shards per double level (level 0 first).",
            level = Setting.Level.OWNER)
    public List<String> doublePercent = List.of("0", "5", "10", "15", "20", "25");

    @Setting(path = "upgrades.respawn", desc = "Cost of each respawn level after 0, as currency:amount (crystals, shards, money). "
            + "One entry per level above 0.", level = Setting.Level.OWNER)
    public List<String> respawnCosts = List.of("money:200000", "money:800000", "money:2500000", "money:8000000", "money:25000000");

    @Setting(path = "upgrades.double", desc = "Cost of each double-chance level after 0, as currency:amount. One entry per level above 0.",
            level = Setting.Level.OWNER)
    public List<String> doubleCosts = List.of("money:150000", "money:500000", "money:1500000", "money:5000000", "money:15000000");

    @Setting(path = "interaction.break-gap-ms", desc = "Shortest time between two shard block breaks of one player (anti auto-click).",
            min = 0, max = 10000)
    public int breakGapMs = 400;

    @Setting(path = "interaction.message-gap-ms", desc = "Shortest time between two 'Regrows in' action bar lines for one player.",
            min = 0, max = 10000)
    public int messageGapMs = 1000;

    @Setting(path = "interaction.look-range", desc = "Blocks a staff member can be away from the shard block they look at (/shardblock info).",
            min = 1, max = 32)
    public int lookRange = 6;

    @Setting(path = "regrow.check-seconds", desc = "How often regrown blocks are switched back.", min = 1, max = 60, restart = true)
    public int regrowCheckSeconds = 1;

    @Setting(path = "regrow.per-tick", desc = "Most blocks switched back per check (the rest wait for the next one).", min = 1, max = 5000)
    public int regrowPerTick = 200;

    @Setting(path = "audit.batch-seconds", desc = "Breaks are audited in one row per profile every this many seconds.", min = 10,
            max = 3600, restart = true)
    public int auditBatchSeconds = 60;

    @Setting(path = "daily.keep-days", desc = "Days of daily-cap rows kept (older rows are purged weekly).", min = 2, max = 365)
    public int keepDays = 14;

    @Setting(path = "stats.top", desc = "/shardblock stats lists this many islands.", min = 1, max = 20)
    public int statsTop = 5;

    @Setting(path = "sounds.break", desc = "Sound key (sounds.yml) when a block pays.")
    public String soundBreak = "success";

    @Setting(path = "sounds.upgrade", desc = "Sound key when an upgrade is bought.")
    public String soundUpgrade = "success";

    @Setting(path = "sounds.regrow", desc = "Sound key when a block regrows near a player (empty = none).")
    public String soundRegrow = "";

    @Setting(path = "menu.icons", desc = "Menu icons: name:MATERIAL (wallet, respawn, double, pickup, maxed).")
    public List<String> icons = List.of("wallet:AMETHYST_CLUSTER", "respawn:CLOCK", "double:AMETHYST_SHARD", "pickup:HOPPER",
            "maxed:GRAY_DYE");

    /** The menu icon {@code name} from {@code menu.icons}, or {@code fallback} if missing/invalid. */
    org.bukkit.Material icon(String name, org.bukkit.Material fallback) {
        for (String entry : icons) {
            int colon = entry.indexOf(':');
            if (colon > 0 && entry.substring(0, colon).trim().equalsIgnoreCase(name)) {
                org.bukkit.Material m = org.bukkit.Material.matchMaterial(entry.substring(colon + 1).trim());
                return m == null || !m.isItem() ? fallback : m;
            }
        }
        return fallback;
    }

    @Override
    public int version() {
        return 3;
    }

    /** v2 (owner 3 Nov 2026): regrow in 5 min down to 1 min, upgrades paid with money (shards come from island tools). */
    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        ShardBlockConfig d = new ShardBlockConfig();
        return java.util.Map.of(3, java.util.Map.of( // owner 7 Nov 2026: shards x10
                "shards.per-break", ValueChange.of(1, d.perBreak), "shards.daily-cap-per-profile", ValueChange.of(100, d.dailyCap)),
                2, java.util.Map.of(
                "levels.respawn-seconds", ValueChange.of(RESPAWN_SECONDS_V1, d.respawnSeconds),
                "upgrades.respawn", ValueChange.of(RESPAWN_COSTS_V1, d.respawnCosts),
                "upgrades.double", ValueChange.of(DOUBLE_COSTS_V1, d.doubleCosts)));
    }

    /** Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): the money keys of this file, scaled once on a live server. */
    @Override
    public java.util.Map<String, net.skybornmc.skyblock.common.config.MoneyScale.Rule> moneyKeys() {
        return java.util.Map.ofEntries(
                java.util.Map.entry("upgrades.respawn", net.skybornmc.skyblock.common.config.MoneyScale.onlyLines(net.skybornmc.skyblock.common.config.MoneyScale.startsWith("money:"), net.skybornmc.skyblock.common.config.MoneyScale.field(1, ":"))),
                java.util.Map.entry("upgrades.double", net.skybornmc.skyblock.common.config.MoneyScale.onlyLines(net.skybornmc.skyblock.common.config.MoneyScale.startsWith("money:"), net.skybornmc.skyblock.common.config.MoneyScale.field(1, ":"))));
    }

    @Override
    public String fileName() {
        return "shardblock.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock shard blocks (ECON-SHARDS): the ONLY source of shards. Texts: messages/shardblock.yml.
                Reload: /skyblock reload shardblock. Staff: /shardblock give|info|remove|stats.""";
    }
}
