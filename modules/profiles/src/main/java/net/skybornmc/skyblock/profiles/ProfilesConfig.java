package net.skybornmc.skyblock.profiles;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Settings of the profiles module ({@code profiles.yml}). */
public final class ProfilesConfig extends ModuleConfig {

    @Setting(path = "slots.price-2", desc = "Credits for profile slot 2 (owner PR5: 500; earned credits are spent first). Rank Firewing+ gets it free (skyblock.profiles.slots.2). 0 = not for sale.",
            min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public long slot2Price = 500;

    @Setting(path = "slots.price-3", desc = "Credits for profile slot 3 (owner PR5: 1,500; earned credits are spent first). Rank Pyrelord+ gets it free (skyblock.profiles.slots.3). 0 = not for sale.",
            min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public long slot3Price = 1_500;

    @Setting(path = "switch.cooldown-seconds", desc = "Seconds between two switches (owner 3 Nov 2026: 5). "
            + "Bypass: skyblock.cooldown.bypass.", min = 0, max = 3600)
    public int switchCooldownSeconds = 5;

    @Setting(path = "switch.blocked-worlds", desc = "Worlds where switching is not allowed (e.g. the boss arena). Combat and freeze are checked by their modules.")
    public List<String> blockedWorlds = List.of("depths");

    /**
     * Version 2 (owner 3 Nov 2026): the Depths has its own inventory, so switching waits until you are out of it (untouched
     * lists only). Version 4 (owner 3 Nov 2026): 5 s between switches (untouched 10 s or 30 s values only).
     */
    @Override
    public int version() {
        return 4;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of("switch.blocked-worlds",
                        ValueChange.of(List.of(), List.of("depths"))),
                4, java.util.Map.of("switch.cooldown-seconds", ValueChange.of(10, 5)));
    }

    @Setting(path = "switch.timeout-seconds", desc = "A switch that waits longer than this for the database is cancelled (nothing changes).",
            min = 2, max = 60)
    public int switchTimeoutSeconds = 10;

    @Setting(path = "spawn-world", desc = "Where a switch to a profile without an island teleports to (that world's spawn point).")
    public String spawnWorld = "spawn";

    @Setting(path = "names.max-length", desc = "Longest profile name (letters, numbers, spaces, _ and -).", min = 3, max = 32)
    public int nameMaxLength = 16;

    @Setting(path = "icons.default", desc = "Default icon per slot (slot 1, 2, 3).")
    public List<String> defaultIcons = List.of("grass_block", "oak_sapling", "wheat");

    @Setting(path = "icons.choices", desc = "Icons players can pick for a profile (item ids).")
    public List<String> iconChoices = List.of("grass_block", "oak_sapling", "wheat", "diamond_pickaxe", "iron_sword", "fishing_rod",
            "cactus", "sugar_cane", "pumpkin", "melon", "cobblestone", "iron_ingot", "gold_ingot", "diamond", "emerald", "redstone",
            "lapis_lazuli", "amethyst_shard", "blaze_rod", "ender_pearl", "nether_star", "beacon", "heart_of_the_sea", "totem_of_undying");

    @Setting(path = "settings.profile-keys", desc = "Player settings that belong to a profile (key, or prefix ending with '.'). "
            + "Everything else (vanish, equipped titles/tags, staff homes) is shared by the account.")
    public List<String> profileSettingKeys = List.of("setting.", "scoreboard", "menu-item");

    @Setting(path = "state.persistent-keys", desc = "Owner 4 Nov 2026: player data values (whole numbers) that belong to the "
            + "profile, like the /farm and /mine tier counters. Saved and loaded with the profile; a new or wiped profile starts without them.")
    public List<String> persistentKeys = List.of("phoenixskyblock:grind_farmed", "phoenixskyblock:grind_mined");

    @Setting(path = "wipe.snapshot-days", desc = "Owner 4 Nov 2026 (island = profile): leaving an island wipes the profile. Days the "
            + "wiped profile is kept as a snapshot that staff can put back with /profiles restore. After that every module deletes it.",
            min = 1, max = 365, level = Setting.Level.OWNER)
    public int snapshotDays = 14;

    @Setting(path = "wipe.purge-interval-minutes", desc = "How often expired snapshots are deleted (in the background).", min = 1, max = 1440)
    public int purgeIntervalMinutes = 30;

    @Setting(path = "wipe.purge-batch", desc = "Expired snapshots deleted per run at most (the rest waits for the next run).", min = 1, max = 1000)
    public int purgeBatch = 50;

    @Setting(path = "wipe.intent-sweep-seconds", desc = "Crash safety: how often a wipe that was interrupted (server stop between the "
            + "island removal and the wipe) is finished.", min = 30, max = 3600)
    public int intentSweepSeconds = 120;

    @Setting(path = "wipe.intent-grace-seconds", desc = "An interrupted wipe counts as interrupted after this many seconds (a normal "
            + "removal and wipe take less than a second).", min = 10, max = 3600)
    public int intentGraceSeconds = 60;

    @Setting(path = "wipe.busy-retries", desc = "A wipe of a player who is switching profiles right now is tried again every 2 seconds, "
            + "this many times; after that the crash sweep finishes it.", min = 1, max = 120)
    public int wipeBusyRetries = 15;

    @Setting(path = "wipe.list-limit", desc = "Snapshots shown by /profiles snapshots <player> (newest first).", min = 1, max = 100)
    public int snapshotListLimit = 15;

    @Override
    public String fileName() {
        return "profiles.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock island profiles (/profiles): up to 3 profiles per player, each with its own island, money,
                crystals, shards, inventory, ender chest, XP, skills and settings. Credits, rank, titles, cosmetics are shared.
                Edit, then run /skyblock reload profiles. Wrong values are reported in the console and the old value stays active.""";
    }

    long price(int slot) {
        return switch (slot) {
            case 2 -> slot2Price;
            case 3 -> slot3Price;
            default -> 0;
        };
    }

    /** True if a player setting key belongs to the profile (not the account). */
    boolean profileSetting(String key) {
        for (String entry : profileSettingKeys) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String e = entry.trim();
            if (e.endsWith(".") ? key.startsWith(e) : key.equals(e)) {
                return true;
            }
        }
        return false;
    }
}
