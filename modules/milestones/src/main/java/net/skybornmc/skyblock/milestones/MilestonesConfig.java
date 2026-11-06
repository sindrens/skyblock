package net.skybornmc.skyblock.milestones;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Settings of the milestones module ({@code milestones-settings.yml}); the chains live in {@code milestones.yml}. */
public final class MilestonesConfig extends ModuleConfig {

    @Setting(path = "grind-world", desc = "Owner 5 Nov 2026: the /farm and /mine world; its harvests and ores count for the Farm and Mine milestones.")
    public String grindWorld = "grind";

    @Setting(path = "enabled", desc = "Milestones on: player actions count toward island and personal chains (Complete Developer "
            + "Spec section 7). Off by default until the release (golden rule 10).", level = Setting.Level.OWNER)
    public boolean enabled = true;

    @Setting(path = "flush-seconds", desc = "How often counted actions are saved (spec 7.11: 10). Also saved on quit, profile "
            + "switch and stop; a crash loses at most this much progress.", min = 2, max = 300)
    public int flushSeconds = 10;

    @Setting(path = "notify.batch-seconds", desc = "Tier-ready notices inside this window merge into one title and one chat "
            + "line (spec 7.4: 30), so many chains completing early on don't spam titles.", min = 0, max = 300)
    public int batchSeconds = 30;

    @Setting(path = "notify.discovery", desc = "One chat line when a chain appears for the first time (\"New milestone: Wheat "
            + "Reaper\", spec 7.2).")
    public boolean announceDiscovery = true;

    @Setting(path = "show-next-tiers", desc = "Tiers shown after the current one in a chain; the rest show as ??? (owner HID: "
            + "current plus the next 2).", min = 0, max = 20)
    public int showNextTiers = 2;

    @Setting(path = "placed.ttl-minutes", desc = "Blocks a player placed don't count when broken for this long (spec 7.3: "
            + "placed blocks never count). Remembered in memory.", min = 1, max = 100_000)
    public int placedTtlMinutes = 1_440;

    @Setting(path = "placed.max-per-world", desc = "Most placed blocks remembered per world (oldest forgotten first).",
            min = 1_000, max = 5_000_000)
    public int placedMaxPerWorld = 200_000;

    @Setting(path = "actionbar.enabled", desc = "Progress in the action bar after a counted action (chain, total, next target). "
            + "Players can turn it off in /settings.")
    public boolean actionBarEnabled = true;

    @Setting(path = "actionbar.actions", desc = "Which counted actions show progress: harvest_crop, break_block, kill_mob "
            + "(build plan: crops and ores on, mobs off).")
    public java.util.List<String> actionBarActions = java.util.List.of("harvest_crop", "break_block");

    @Setting(path = "actionbar.gap-ms", desc = "Shortest time between two progress lines for one player.", min = 250, max = 60_000)
    public int actionBarGapMs = 1_500;

    @Setting(path = "level.points-per-island-tier", desc = "Synergy (owner 2 Nov 2026: island level without pillars): island "
            + "level points for every completed ISLAND milestone tier (earned, never go down in a season; source 'collections'). "
            + "0 = milestones give no island level.", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double levelPointsPerIslandTier = 2;

    /** Version 2 (owner 2 Nov 2026: "please enable milestones"): on, unless the owner switched it off by hand. */
    @Override
    public int version() {
        return 2;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of("enabled", ValueChange.of(false, true)));
    }

    @Override
    public String fileName() {
        return "milestones-settings.yml";
    }

    @Override
    public String header() {
        return "Milestones settings (Complete Developer Spec section 7). Chains: milestones.yml. Texts: messages/milestones.yml.\n"
                + "Reload: /skyblock reload milestones";
    }
}
