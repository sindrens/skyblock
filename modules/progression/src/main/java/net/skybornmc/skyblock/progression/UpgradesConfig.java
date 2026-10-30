package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/**
 * Typed settings of the island upgrades (PHASE-5 M5.4): {@code upgrades-settings.yml}. The upgrades themselves are the
 * catalog {@code upgrades.yml} (one section, every key an upgrade, no plain settings there).
 */
public final class UpgradesConfig extends ModuleConfig {

    @Override
    public String fileName() {
        return "upgrades-settings.yml";
    }

    @Override
    public String header() {
        return """
                Island upgrade settings (Phase 5, M5.4). The upgrades, their prices and effects are in upgrades.yml.
                Upgrades are paid from the island bank only (owner 169): no withdrawal fee, the period's interest is kept.
                Who may buy: the island permission "Upgrades" (/is permissions; leader always, admin by default).
                Texts: messages/progression.yml. Reload: /skyblock reload progression""";
    }

    @Setting(path = "enabled", desc = "Show /is upgrade and allow buying (off = the menu is closed; bought levels keep working).")
    public boolean enabled = true;

    @Setting(path = "level-points.tier-low", desc = "Island level points per bought level whose task tier is below level-points.mid-from-tier (owner P5-17: 0.5).",
            min = 0, max = 100)
    public double pointsLow = 0.5;

    @Setting(path = "level-points.tier-mid", desc = "Island level points per bought level with a task tier from mid-from-tier up to below high-from-tier (P5-17: 1).",
            min = 0, max = 100)
    public double pointsMid = 1.0;

    @Setting(path = "level-points.tier-high", desc = "Island level points per bought level with a task tier of high-from-tier or more (P5-17: 1.5).",
            min = 0, max = 100)
    public double pointsHigh = 1.5;

    @Setting(path = "level-points.mid-from-tier", desc = "First task tier that gives the mid points (P5-17: 4).", min = 1, max = 28)
    public int midFromTier = 4;

    @Setting(path = "level-points.high-from-tier", desc = "First task tier that gives the high points (P5-17: 7).", min = 1, max = 28)
    public int highFromTier = 7;

    @Setting(path = "notify.chat", desc = "Tell online members in chat when an upgrade is bought.")
    public boolean notifyChat = true;

    @Setting(path = "notify.action-bar", desc = "Show online members a short action bar line when an upgrade is bought.")
    public boolean notifyActionBar = true;

    @Setting(path = "notify.ready", desc = "Tell online members in chat when the tasks of the next level of an upgrade are all done (ready to buy).")
    public boolean notifyReady = true;

    @Setting(path = "notify.sound", desc = "Sound key (sounds.yml) for online members when an upgrade is bought.")
    public String buySound = "unlock";

    @Setting(path = "menu.refresh-ticks", desc = "How often an open upgrade menu refreshes (task progress, bank balance), in ticks.", min = 10, max = 400)
    public int refreshTicks = 40;

    UpgradeRules.Points points() {
        int mid = Math.max(1, midFromTier);
        return new UpgradeRules.Points(pointsLow, pointsMid, pointsHigh, mid, Math.max(mid, highFromTier));
    }
}
