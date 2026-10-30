package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Typed settings of {@code moderation.yml}. Templates and ladders live in {@code punishments.yml}. */
public final class ModerationConfig extends ModuleConfig {

    @Setting(path = "announce.public", desc = "Tell all players about bans and mutes (unless -s silent is used).")
    public boolean announcePublic = true;

    @Setting(path = "announce.show-staff", desc = "Show which staff member punished (public message and ban screen).")
    public boolean showStaff = false;

    @Setting(path = "limits.max-duration", desc = "Longest punishment per tier: '<tier>=<time|perm>'. A staff member with "
            + "skyblock.staff.punish.max.<tier> gets that tier (the longest wins).")
    public List<String> maxDurations = List.of("helper=1d", "mod=30d", "admin=perm");

    @Setting(path = "limits.default-max-duration", desc = "Longest punishment for staff without any max tier (perm = no limit).")
    public String defaultMaxDuration = "1d";

    @Setting(path = "alts.alert", desc = "Alert staff when an account sharing an IP with a banned or muted player joins.")
    public boolean altAlert = true;

    @Setting(path = "alts.auto-ban", desc = "Automatically ban accounts that share an IP with a banned player (owner decision: off).")
    public boolean altAutoBan = false;

    @Setting(path = "vanilla-sync", desc = "Also keep the vanilla ban list in sync (safety net if the plugin fails to load).")
    public boolean vanillaSync = true;

    @Setting(path = "appeal-url", desc = "Appeal link on the ban screen. Empty = the Discord link from social.yml.")
    public String appealUrl = "";

    @Setting(path = "appeal-wait.perm-ban-days", desc = "Owner answer 423: a permanent ban can be appealed this many days after it "
            + "was given (shown on the ban screen; 0 = any time).", min = 0, max = 3650)
    public int appealWaitPermBanDays = 30;

    @Setting(path = "appeal-wait.cheating-days", desc = "Owner answer 423: the wait for permanent bans whose reason is in a "
            + "cheating category (appeal-wait.cheating-categories).", min = 0, max = 3650)
    public int appealWaitCheatingDays = 90;

    @Setting(path = "appeal-wait.cheating-categories", desc = "punishments.yml categories that count as cheating for the longer wait.")
    public List<String> appealWaitCheatingCategories = List.of("cheating");

    @Setting(path = "history.page-size", desc = "Punishments per page in /history (chat).", min = 3, max = 20)
    public int historyPageSize = 8;

    @Override
    public String fileName() {
        return "moderation.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock moderation (punishments). Templates and escalation ladders: punishments.yml.
                Texts (ban screen, notifications): messages/moderation.yml. Reload: /skyblock reload moderation""";
    }
}
