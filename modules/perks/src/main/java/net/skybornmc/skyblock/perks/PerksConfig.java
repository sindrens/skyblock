package net.skybornmc.skyblock.perks;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Settings of the perks module ({@code perks.yml}). */
public final class PerksConfig extends ModuleConfig {

    @Setting(path = "nick.min-length", desc = "Shortest nickname (visible letters, without colors).", min = 2, max = 16)
    public int nickMinLength = 3;

    @Setting(path = "nick.max-length", desc = "Longest nickname (visible letters, without colors). Java names are max 16.", min = 3, max = 24)
    public int nickMaxLength = 16;

    @Setting(path = "nick.cooldown-seconds", desc = "Seconds between nickname changes for players (bypass: skyblock.cooldown.bypass).",
            min = 0, max = 86400)
    public int nickCooldownSeconds = 60;

    @Setting(path = "nick.marker", desc = "Shown in front of every nickname so players know it isn't a real name (MiniMessage). Empty = none.")
    public String nickMarker = "~";

    @Setting(path = "nick.reserved", desc = "Words a nickname may not contain (case-insensitive). Bypass: skyblock.perks.nick.bypass.")
    public List<String> nickReserved = List.of("admin", "owner", "staff", "moderator", "helper", "console", "server",
            "skyborn", "phoenix", "system", "official", "support", "builder", "ashborn", "hatchling", "fledgling", "firewing",
            "flamecrest", "sunfeather", "pyrelord", "immortal", "torchbearer", "flameheart", "firecrown");

    /** {@code nick.reserved} before v2 (Ranks v2, batch 10: the rank names can't be worn as a nickname). */
    static final List<String> NICK_RESERVED_V1 = List.of("admin", "owner", "staff", "moderator", "helper", "console", "server",
            "skyborn", "phoenix", "system", "official", "support");

    @Setting(path = "hat.blocked", desc = "Materials that can't be worn with /hat, e.g. [tnt, lava_bucket].")
    public List<String> hatBlocked = List.of();

    @Setting(path = "blocked.worlds", desc = "Owner answer 377: worlds where /ec and /craft don't work (the warzone).")
    public List<String> blockedWorlds = List.of("warzone");

    @Setting(path = "blocked.boss-arena", desc = "Owner answer 377: /ec and /craft don't work inside a boss arena.")
    public boolean blockedBossArena = true;

    @Setting(path = "blocked.combat-seconds", desc = "Owner answer 377: /ec and /craft wait this many seconds after combat "
            + "(hitting or being hit by a player or hostile mob). 0 = no combat block.", min = 0, max = 120)
    public int blockedCombatSeconds = 10;

    @Setting(path = "trash.rows", desc = "Rows of the /trash menu, including the bottom bar.", min = 2, max = 6)
    public int trashRows = 4;

    @Setting(path = "trash.warn-custom-items", desc = "Tell the player in chat when custom items (tools, notes, keys) were destroyed.")
    public boolean trashWarnCustom = true;

    @Setting(path = "sudo.blocked-commands", desc = "Commands /sudo may never run (first word, without /).")
    public List<String> sudoBlocked = List.of("sudo", "op", "deop", "lp", "luckperms", "perm", "perms", "permissions",
            "stop", "restart", "reload", "rl", "pl", "plugins", "skyblock", "sb", "eco", "pay", "trade", "ah", "withdraw");

    @Setting(path = "freerank.enabled", desc = "/freerank: the free rank Ashborn (given automatically after the tutorial and the playtime) and the one-time tag pick.")
    public boolean freerankEnabled = true;

    @Setting(path = "freerank.min-playtime-minutes", desc = "Total playtime needed before /freerank works.", min = 0, max = 10_000)
    public int freerankMinPlaytimeMinutes = 30;

    @Setting(path = "freerank.group", desc = "LuckPerms group /freerank gives (server/permissions/base.txt creates 'ember'). Kept forever.",
            level = Setting.Level.OWNER)
    public String freerankGroup = "ember";

    @Setting(path = "freerank.auto.enabled", desc = "Ranks V2 (owner EP-Q15): Ashborn is given automatically once the tutorial is "
            + "complete and the playtime is reached (never removes a rank).")
    public boolean freerankAutoEnabled = true;

    @Setting(path = "freerank.auto.check-seconds", desc = "Seconds between the automatic Ashborn checks.", min = 5, max = 3_600)
    public int freerankAutoCheckSeconds = 30;

    @Setting(path = "freerank.auto.batch", desc = "Online players checked per run (at most this many LuckPerms writes per run).",
            min = 1, max = 100)
    public int freerankAutoBatch = 5;

    @Setting(path = "freerank.auto.max-attempts", desc = "Tries per session after a LuckPerms failure, then one console warning.",
            min = 1, max = 100)
    public int freerankAutoMaxAttempts = 5;

    @Setting(path = "rank-rewards.enabled", desc = "Ranks V2: /rankrewards (weekly kit per profile, monthly tag per account).")
    public boolean rankRewardsEnabled = true;

    @Setting(path = "rank-rewards.weekly.enabled", desc = "The weekly rank kit (Monday 00:00 Oslo, per profile, bound items).")
    public boolean rankWeeklyEnabled = true;

    @Setting(path = "rank-rewards.kits.S", desc = "Kit S (skyblock.rank.kit.size.1): 'item amount' lines. Food and blocks only; "
            + "tools, weapons, armour, spawners and custom items are refused.")
    public List<String> rankKitS = List.of("cooked_beef 16", "cobblestone 64");

    @Setting(path = "rank-rewards.kits.M", desc = "Kit M (skyblock.rank.kit.size.2).")
    public List<String> rankKitM = List.of("cooked_beef 32", "cobblestone 128", "oak_log 32");

    @Setting(path = "rank-rewards.kits.L", desc = "Kit L (skyblock.rank.kit.size.3).")
    public List<String> rankKitL = List.of("cooked_beef 48", "stone_bricks 128", "oak_log 64");

    @Setting(path = "rank-rewards.kits.XL", desc = "Kit XL (skyblock.rank.kit.size.4).")
    public List<String> rankKitXL = List.of("golden_carrot 48", "cooked_beef 48", "stone_bricks 256", "oak_log 128");

    @Setting(path = "rank-rewards.monthly.enabled", desc = "The monthly rank tag (per account, skyblock.rank.monthly).")
    public boolean rankMonthlyEnabled = true;

    @Setting(path = "rank-rewards.monthly.pool", desc = "Tag ids (tags-titles.yml) handed out one per month, the first one the "
            + "player does not own yet. Empty = the tile says 'Nothing new this month' (owner supplies the list, EP-Q17).")
    public List<String> rankMonthlyPool = List.of();

    @Setting(path = "freerank.tags", desc = "The 3 tags the player picks 1 from (ids from tags-titles.yml). Kept forever.")
    public List<String> freerankTags = List.of("ember", "spark", "ash");

    @Override
    public int version() {
        return 2;
    }

    /** v2 (Ranks v2 leftovers, 7 Nov 2026): the rank names join the reserved nickname words. An edited list is kept. */
    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of("nick.reserved", ValueChange.of(NICK_RESERVED_V1, new PerksConfig().nickReserved)));
    }

    @Override
    public String fileName() {
        return "perks.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock perks: /craft, /ec, /hat, /nick (rank perks), /freerank and /trash, /repair, /sudo, /realname (staff).
                Edit, then run /skyblock reload perks. Wrong values are reported in the console and the old value stays active.""";
    }
}
