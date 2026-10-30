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
            "skyborn", "phoenix", "system", "official", "support");

    @Setting(path = "hat.blocked", desc = "Materials that can't be worn with /hat, e.g. [tnt, lava_bucket].")
    public List<String> hatBlocked = List.of();

    @Setting(path = "trash.rows", desc = "Rows of the /trash menu, including the bottom bar.", min = 2, max = 6)
    public int trashRows = 4;

    @Setting(path = "trash.warn-custom-items", desc = "Tell the player in chat when custom items (tools, notes, keys) were destroyed.")
    public boolean trashWarnCustom = true;

    @Setting(path = "sudo.blocked-commands", desc = "Commands /sudo may never run (first word, without /).")
    public List<String> sudoBlocked = List.of("sudo", "op", "deop", "lp", "luckperms", "perm", "perms", "permissions",
            "stop", "restart", "reload", "rl", "pl", "plugins", "skyblock", "sb", "eco", "pay", "trade", "ah", "withdraw");

    @Setting(path = "freerank.enabled", desc = "/freerank: claim the free lowest rank once after enough playtime (owner RW4).")
    public boolean freerankEnabled = true;

    @Setting(path = "freerank.min-playtime-minutes", desc = "Total playtime needed before /freerank works.", min = 0, max = 10_000)
    public int freerankMinPlaytimeMinutes = 30;

    @Setting(path = "freerank.group", desc = "LuckPerms group /freerank gives (server/permissions/base.txt creates 'ember'). Kept forever.",
            level = Setting.Level.OWNER)
    public String freerankGroup = "ember";

    @Setting(path = "freerank.tags", desc = "The 3 tags the player picks 1 from (ids from tags-titles.yml). Kept forever.")
    public List<String> freerankTags = List.of("ember", "spark", "ash");

    @Override
    public String fileName() {
        return "perks.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock perks: /craft, /ec, /hat, /nick, /trash (rank perks), /freerank and /repair, /sudo, /realname (staff).
                Edit, then run /skyblock reload perks. Wrong values are reported in the console and the old value stays active.""";
    }
}
