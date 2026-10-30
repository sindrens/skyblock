package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/**
 * Typed settings of the achievements module ({@code achievements-settings.yml}). The achievements themselves are in
 * {@code achievements.yml} (read by {@link AchievementRegistry}); that file holds only the two catalog sections.
 */
public final class AchievementsConfig extends ModuleConfig {

    // ------------------------------------------------------------------ tracking

    @Setting(path = "tracking.gauge-poll-seconds", desc = "How often skill levels, sets, tools and balances of online players are read for achievements.", min = 5, max = 600)
    public int gaugePollSeconds = 30;

    @Setting(path = "tracking.island-poll-seconds", desc = "How often island stats (blocks mined, logs, crops, placed, mobs, visitors) are read for achievements. One read per island.", min = 30, max = 3600)
    public int islandPollSeconds = 300;

    @Setting(path = "tracking.playtime", desc = "Count minutes played per profile for the playtime achievements.")
    public boolean playtime = true;

    @Setting(path = "tracking.hotbar-tools", desc = "Look at the hotbar for the best custom tool level and prestige (every gauge poll).")
    public boolean hotbarTools = true;

    // ------------------------------------------------------------------ storage

    @Setting(path = "storage.save-interval-seconds", desc = "Changed progress of online players is saved in one batch this often (and always on quit, profile switch and shutdown).", min = 5, max = 600)
    public int saveIntervalSeconds = 60;

    // ------------------------------------------------------------------ unlock toast

    @Setting(path = "toast.title", desc = "Show a screen title when an achievement tier unlocks.")
    public boolean toastTitle = true;

    @Setting(path = "toast.chat", desc = "Send a chat line when an achievement tier unlocks.")
    public boolean toastChat = true;

    @Setting(path = "toast.sound", desc = "Sound name from sounds.yml played on an unlock (empty = none).")
    public String toastSound = "unlock";

    @Setting(path = "toast.max-lines", desc = "Most unlock lines shown at once (e.g. the first join after achievements came out); the rest is one '+N more' line.", min = 1, max = 20)
    public int toastMaxLines = 3;

    // ------------------------------------------------------------------ menu and titles

    @Setting(path = "menu.bar-length", desc = "Segments of the progress bars in the achievements menu.", min = 5, max = 20)
    public int barLength = 10;

    @Setting(path = "menu.hidden-icon", desc = "Item shown for secret achievements that are not unlocked yet.")
    public String hiddenIcon = "gray_dye";

    @Setting(path = "titles.add-missing", desc = "Add reward titles that have a display in achievements.yml to tags-titles.yml when they are missing there (existing titles are never changed).")
    public boolean addMissingTitles = true;

    @Override
    public String fileName() {
        return "achievements-settings.yml";
    }

    @Override
    public String header() {
        return """
                Achievements (F3): settings. The achievements and categories are in achievements.yml; texts in messages/achievements.yml.
                Progress is per profile; reward titles are account-wide (owner decision PR1).
                Reload: /skyblock reload achievements""";
    }
}
