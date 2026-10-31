package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/**
 * Typed settings of the island masteries and Ascension (PHASE-5 M5.5): {@code masteries-settings.yml}. The masteries
 * themselves are the catalog {@code masteries.yml} (one section, every key a mastery, no plain settings there).
 */
public final class MasteriesConfig extends ModuleConfig {

    @Override
    public String fileName() {
        return "masteries-settings.yml";
    }

    @Override
    public String header() {
        return """
                Island mastery and Ascension settings (Phase 5, M5.5). The masteries, their prices and effects are in masteries.yml.
                Masteries are paid from the island bank only (owner 169), like upgrades: no withdrawal fee.
                Who may buy: the island permission "Upgrades" (/is permissions; leader always, admin by default).
                Island level points per rank: upgrades-settings.yml -> level-points (same rule as upgrades, P5-17).
                Texts: messages/progression.yml. Reload: /skyblock reload progression""";
    }

    @Setting(path = "enabled", desc = "Show /is masteries and the Masteries tab and allow buying (off = closed; bought ranks keep working).")
    public boolean enabled = true;

    @Setting(path = "ascension.enabled", desc = "Endless Ascension levels once every shown upgrade and mastery is maxed (owner P5-24).")
    public boolean ascensionEnabled = true;

    @Setting(path = "ascension.base-cost", desc = "Price of level 1 of the endless curve: 4 hours of REF(12) (ECONOMY_BALANCE 5.4). Paid from the island bank.",
            min = 0, max = 1_000_000_000_000L, level = Setting.Level.OWNER)
    public long ascensionBaseCost = 686_000_000L;

    @Setting(path = "ascension.growth", desc = "Each level of the endless curve costs this much more than the one before (1.12 = 12%).",
            min = 1.0, max = 3.0, level = Setting.Level.OWNER)
    public double ascensionGrowth = 1.12;

    @Setting(path = "ascension.start-level", desc = "Ascension 1 costs like this level of the endless curve (21 = after the 20 Market Contacts levels, which use levels 1-20).",
            min = 1, max = 1000, level = Setting.Level.OWNER)
    public int ascensionStartLevel = 21;

    @Setting(path = "ascension.level-points", desc = "Island level points per Ascension level (plan: 1.5).", min = 0, max = 100)
    public double ascensionLevelPoints = 1.5;

    @Setting(path = "ascension.max-levels", desc = "Most Ascension levels an island can buy per season (0 = endless).", min = 0, max = 100_000)
    public int ascensionMaxLevels = 0;

    @Setting(path = "ascension.icon", desc = "Item shown for Ascension in the Masteries tab.")
    public String ascensionIcon = "nether_star";

    @Setting(path = "notify.chat", desc = "Tell online members in chat when a mastery rank or Ascension level is bought.")
    public boolean notifyChat = true;

    @Setting(path = "notify.action-bar", desc = "Show online members a short action bar line when a mastery rank is bought.")
    public boolean notifyActionBar = true;

    @Setting(path = "notify.ready", desc = "Tell online members in chat when a mastery's next rank has every requirement met (ready to buy).")
    public boolean notifyReady = true;

    @Setting(path = "notify.sound", desc = "Sound key (sounds.yml) for online members when a mastery is bought.")
    public String buySound = "unlock";
}
