package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the crates module ({@code crates-settings.yml}). The crates and reward tables live in
 * {@code crates.yml}; texts in {@code messages/crates.yml}. Every number is a default the owner can change
 * (PHASE-10-REWARDS.md M10.1).
 */
public final class CratesSettings extends ModuleConfig {

    @Setting(path = "open.bulk-amount", desc = "Opens of the 'Open 10' button and shift-click (PHASE-10: 10).", min = 2, max = 64)
    public int bulkAmount = 10;

    @Setting(path = "open.max-per-click", desc = "Most opens one click of 'Open all' does (PHASE-10: up to 64 per click).",
            min = 1, max = 256)
    public int maxPerClick = 64;

    @Setting(path = "open.summary-lines", desc = "Reward lines in the chat summary of a bulk open (the rest is counted in "
            + "one 'and N more' line; the summary screen shows everything).", min = 1, max = 30)
    public int summaryLines = 8;

    @Setting(path = "open.retries", desc = "How often an open is tried again when the keys or pity changed while it ran "
            + "(another open, a key arriving). Nothing is taken until it succeeds.", min = 0, max = 5)
    public int retries = 2;

    @Setting(path = "open.save-player-data", desc = "Save the player's data right after reward items are handed over, so a "
            + "crash can't roll the inventory back to before the open (SECFIX1 PlayerSaves).")
    public boolean savePlayerData = true;

    @Setting(path = "open.sound", desc = "Sound (sounds.yml key) after an open.")
    public String openSound = "success";

    @Setting(path = "open.top-sound", desc = "Sound (sounds.yml key) when an open wins a top-band reward.")
    public String topSound = "unlock";

    @Setting(path = "keys.bound-sources", desc = "Key sources whose keys are BOUND (never withdrawn or traded; REVIEW3 "
            + "R3-2 and PHASE-10 RX6): votes, the AFK pool, the daily calendar, playtime, rank claims, the tutorial. A source "
            + "in neither list is treated as bound (safest).", level = Setting.Level.OWNER)
    public List<String> boundSources = List.of("afk_pool", "vote", "vote_streak", "vote_party", "daily", "playtime", "rank",
            "tutorial");

    @Setting(path = "keys.tradeable-sources", desc = "Key sources whose keys are TRADEABLE (withdraw as items in M10.4): "
            + "quests, the battle pass, events, weekly top, key fragments, crate chains.", level = Setting.Level.OWNER)
    public List<String> tradeableSources = List.of("quest", "quest_daily", "quest_weekly", "pass", "event", "weekly_top",
            "fragment", "koth");

    @Setting(path = "tier.stage-skills", desc = "Skills whose highest level decides the opener's tier for money and crystal "
            + "pouches (the same rule the AFK pool uses).")
    public List<String> stageSkills = List.of("mining", "foraging", "combat");

    @Setting(path = "tier.tier-levels", desc = "Skill level that unlocks tiers 1-12 (the Depths unlock levels, "
            + "ECONOMY_BALANCE §3.4).", level = Setting.Level.OWNER)
    public List<String> tierLevels = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58", "66");

    @Setting(path = "pouch.ref-per-minute", desc = "Reference income per minute (REF(t)/60, ECONOMY_BALANCE §1.2) for tiers "
            + "1-12; a Money Pouch of N minutes pays N x this at the opener's tier. The last value is used above.",
            level = Setting.Level.OWNER)
    public List<String> refPerMinute = List.of("827", "1467", "2560", "4507", "7920", "13867", "24347", "42667", "122133",
            "349333", "1000000", "2853333");

    @Setting(path = "pouch.crystal-growth", desc = "Crystal Pouch growth per tier: base x growth^(tier-1) (crystals grow "
            + "x1.30 per tier, ECONOMY_BALANCE §9.1). Also scales the crystal value of coming-soon rewards.",
            min = 1, max = 3, level = Setting.Level.OWNER)
    public double crystalGrowth = 1.3;

    @Setting(path = "pity.season-id", desc = "Current season number for pity counters: raise it at the season reset and "
            + "every pity counter starts at 0 again (PHASE-10: counters reset at the season reset).", min = 0,
            level = Setting.Level.OWNER)
    public int seasonId = 1;

    @Setting(path = "physical.enabled", desc = "Clicking the crate blocks on the crate yard pedestals (build points "
            + "'crates'): right-click opens 1, left-click shows the rewards.")
    public boolean physicalEnabled = true;

    @Setting(path = "physical.points", desc = "Crate yard build point id -> crate id ('point:crate'). The yard has 6 "
            + "pedestals (no Conqueror pedestal yet).")
    public List<String> physicalPoints = List.of("common:ember", "uncommon:cinder", "rare:blaze", "epic:inferno",
            "legendary:phoenix", "monthly:eclipse");

    @Setting(path = "physical.y-offsets", desc = "Blocks above a crate build point that count as the crate (0 = the point "
            + "itself, 1 = the block on top of the pedestal).")
    public List<String> physicalOffsets = List.of("0", "1");

    @Setting(path = "delivery.join-delay-ticks", desc = "Ticks after joining before the key summary is refreshed (the "
            + "profile module loads the active profile first).", min = 1, max = 1_200)
    public int joinDelayTicks = 40;

    @Setting(path = "admin.max-amount", desc = "Most keys one /crates admin give or take can move (P10-41).", min = 1,
            max = CrateKeysService.MAX_PER_CALL, level = Setting.Level.OWNER)
    public int adminMaxAmount = 1_000;

    @Setting(path = "admin.confirm-above", desc = "A /crates admin give or take of more keys than this asks to confirm first "
            + "(click or type it again with 'confirm').", min = 0, max = CrateKeysService.MAX_PER_CALL, level = Setting.Level.OWNER)
    public int adminConfirmAbove = 64;

    @Setting(path = "admin.confirm-seconds", desc = "Seconds a /crates admin confirmation stays valid (read at server start).",
            min = 5, max = 120, restart = true)
    public int adminConfirmSeconds = 15;

    @Setting(path = "admin.default-tradeable", desc = "Kind of keys /crates admin give adds when the type is left out: false = "
            + "bound (safest: never traded), true = tradeable.", level = Setting.Level.OWNER)
    public boolean adminDefaultTradeable = false;

    @Setting(path = "admin.notify-player", desc = "Tell an online player on that profile when staff give them keys.")
    public boolean adminNotify = true;

    @Override
    public String fileName() {
        return "crates-settings.yml";
    }

    @Override
    public String header() {
        return "Crates settings (PHASE-10 M10.1). Crates and reward tables: crates.yml. Texts: messages/crates.yml.\n"
                + "Reload: /skyblock reload crates";
    }

    /** Whether keys from {@code reason} are bound (anything not listed as tradeable is bound: the safe default). */
    public boolean bound(String reason) {
        String r = reason == null ? "" : reason.trim().toLowerCase(java.util.Locale.ROOT);
        if (boundSources.contains(r)) {
            return true;
        }
        return !tradeableSources.contains(r);
    }
}
