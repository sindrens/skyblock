package net.skybornmc.skyblock.common.config;

import net.skybornmc.skyblock.common.util.Format;

/** Global plugin settings ({@code core.yml}). */
public final class CoreConfig extends ModuleConfig {

    @Setting(desc = "Extra debug output in the console. Keep off on the live server.")
    public boolean debug = false;

    @Setting(desc = "Main-thread work slower than this (milliseconds) is logged as a warning with its name.",
            min = 1, max = 1000)
    public int slowOperationMs = 5;

    @Setting(desc = "Show config problems to staff with skyblock.admin.reload when they join.")
    public boolean notifyStaffOfConfigProblems = true;

    @Setting(desc = "Time zone of every date and time shown to players and staff (owner: CET, Europe/Oslo). "
            + "Stored times stay UTC; only the display changes.")
    public String displayTimezone = Format.DEFAULT_ZONE.getId();

    @Override
    public String fileName() {
        return "core.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock core settings.
                Edit, then run /skyblock reload core. A wrong value is reported in the console and the old value stays active.
                New settings are added here automatically on updates; your values are never overwritten.""";
    }
}
