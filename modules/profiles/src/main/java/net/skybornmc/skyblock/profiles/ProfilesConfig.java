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

    @Setting(path = "switch.cooldown-seconds", desc = "Seconds between two switches (owner 3 Nov 2026: 30; was 10 after owner P5). "
            + "Bypass: skyblock.cooldown.bypass.", min = 0, max = 3600)
    public int switchCooldownSeconds = 30;

    @Setting(path = "switch.blocked-worlds", desc = "Worlds where switching is not allowed (e.g. the boss arena). Combat and freeze are checked by their modules.")
    public List<String> blockedWorlds = List.of("depths");

    /**
     * Version 2 (owner 3 Nov 2026): the Depths has its own inventory, so switching waits until you are out of it (untouched
     * lists only). Version 3 (feedback 31 Oct): 30 s between switches (untouched values only).
     */
    @Override
    public int version() {
        return 3;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of("switch.blocked-worlds",
                        ValueChange.of(List.of(), List.of("depths"))),
                3, java.util.Map.of("switch.cooldown-seconds", ValueChange.of(10, 30)));
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
