package net.skybornmc.skyblock.logs;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Settings of the staff log viewer ({@code logs.yml}). */
public final class LogsConfig extends ModuleConfig {

    @Setting(desc = "Time range used when none is given (e.g. 1h, 24h, 7d, 30d or all).")
    public String defaultRange = "7d";

    @Setting(desc = "Time ranges the menu cycles through (durations like 1h, 7d, or 'all').")
    public List<String> ranges = List.of("1h", "24h", "7d", "30d", "all");

    @Setting(desc = "Entries per page in chat output (console, /logs page).", min = 3, max = 30)
    public int chatPageSize = 10;

    @Setting(path = "near.default-radius", desc = "Radius in blocks for /logs near without a radius.", min = 1, max = 1000)
    public int nearDefaultRadius = 30;

    @Setting(path = "near.max-radius", desc = "Largest radius /logs near accepts.", min = 1, max = 5000)
    public int nearMaxRadius = 250;

    @Setting(path = "near.radii", desc = "Radii the menu cycles through for 'Near me'.")
    public List<String> nearRadii = List.of("10", "30", "50", "100", "250");

    @Setting(path = "export.max-rows", desc = "Most rows one CSV export may contain.", min = 100, max = 1_000_000,
            level = Setting.Level.OWNER)
    public int exportMaxRows = 50_000;

    @Setting(path = "export.folder", desc = "Folder (inside plugins/PhoenixSkyblock) for CSV exports.", level = Setting.Level.OWNER)
    public String exportFolder = "exports";

    @Setting(desc = "Bedrock players get chat output instead of the menu (the menu works on Bedrock too).")
    public boolean bedrockChatOutput = false;

    @Setting(path = "lore.detail-lines", desc = "Detail lines shown in an entry's lore (the rest is in chat/hover).",
            min = 0, max = 30)
    public int loreDetailLines = 8;

    @Setting(path = "lore.width", desc = "Characters per lore/hover line before it is shortened or wrapped.", min = 20, max = 80)
    public int loreWidth = 42;

    @Setting(desc = "Menu icon per category: category:MATERIAL.")
    public List<String> icons = List.of(
            "economy:GOLD_INGOT", "trade:EMERALD", "auction:GOLDEN_HORSE_ARMOR", "island:GRASS_BLOCK",
            "punishment:IRON_BARS", "staff:NETHER_STAR", "config:COMPARATOR", "command:COMMAND_BLOCK",
            "chat:OAK_SIGN", "item:CHEST", "auth:OAK_DOOR", "world:FILLED_MAP");

    @Override
    public String fileName() {
        return "logs.yml";
    }

    @Override
    public String header() {
        return """
                Staff log viewer (/logs). Which categories a staff member may see is set by permissions:
                skyblock.staff.logs.category.<category> (see server/permissions/logs.txt).
                Reload: /skyblock reload logs""";
    }
}
