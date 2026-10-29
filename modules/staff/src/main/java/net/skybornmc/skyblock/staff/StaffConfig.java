package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Typed settings of {@code staff.yml}. */
public final class StaffConfig extends ModuleConfig {

    public enum LogoutAction { ALERT, BAN }

    // ------------------------------------------------------------------ staff mode

    @Setting(path = "mode.vanish", desc = "Vanish when entering staff mode.")
    public boolean modeVanish = true;

    @Setting(path = "mode.night-vision", desc = "Night vision in staff mode.")
    public boolean modeNightVision = true;

    @Setting(path = "mode.random-tp-skip-staff", desc = "Random teleport skips other staff members.")
    public boolean randomTpSkipStaff = true;

    @Setting(path = "mode.inspect-range", desc = "How far the Inspect/Freeze/Punish tools reach when you look at a player (blocks).", min = 2, max = 64)
    public int toolRange = 16;

    @Setting(path = "mode.inspect-tool-material", desc = "Item of the Inspect tool. Use one that does nothing on its own: "
            + "a spyglass zooms on the player's screen and that can't be stopped by the server.")
    public String inspectToolMaterial = "ender_eye";

    // ------------------------------------------------------------------ freeze

    @Setting(path = "freeze.allowed-commands", desc = "Commands a frozen player may still use (without /). Empty = none. "
            + "Chat always goes to staff only; don't add /msg, /r or /tell unless frozen players may message others.")
    public List<String> freezeAllowedCommands = List.of();

    @Setting(path = "freeze.allowed-teleport-causes", desc = "Teleports that may still move a frozen player (staff /tp, /tphere, "
            + "plugins). Others (ender-pearl, chorus-fruit, consumable-effect, nether-portal, end-portal, end-gateway, spectate, "
            + "exit-bed, dismount) are blocked.")
    public List<String> freezeAllowedTeleportCauses = List.of("command", "plugin");

    @Setting(path = "freeze.slowness-level", desc = "Slowness effect level shown to frozen players as a hint (0 = none). "
            + "Only visual: the freeze itself holds them in place.", min = 0, max = 10)
    public int freezeSlownessLevel = 3;

    @Setting(path = "freeze.block-jump", desc = "Take away the jump of frozen players (visual hint; the freeze itself holds them in place).")
    public boolean freezeBlockJump = true;

    @Setting(path = "freeze.check-ticks", desc = "How often (ticks) frozen players are checked: back to the freeze spot, out of "
            + "vehicles, flight kept. Movement is also stopped on every move.", min = 5, max = 100)
    public int freezeCheckTicks = 10;

    @Setting(path = "freeze.fall-grace-seconds", desc = "After an unfreeze, fall damage is ignored this long (frozen players "
            + "hover, so someone frozen in the air would otherwise fall when released).", min = 0, max = 60)
    public int freezeFallGraceSeconds = 10;

    @Setting(path = "freeze.reminder-seconds", desc = "How often a frozen player gets the title and chat instructions again.", min = 3, max = 120)
    public int freezeReminderSeconds = 10;

    @Setting(path = "freeze.logout-action", desc = "What happens when a frozen player logs out: alert (staff alert + note) or ban (runs the ban command).")
    public LogoutAction freezeLogoutAction = LogoutAction.ALERT;

    @Setting(path = "freeze.ban-command", desc = "Console command for logout-action ban (<player> is replaced).")
    public String freezeBanCommand = "ban <player> Logged out while frozen";

    @Setting(path = "freeze.keep-on-rejoin", desc = "A frozen player who logs out is still frozen when they come back "
            + "(also after a restart). Off: logging out ends the freeze.")
    public boolean freezeKeepOnRejoin = true;

    // ------------------------------------------------------------------ reports

    @Setting(path = "reports.cooldown-seconds", desc = "Seconds between two reports from the same player.", min = 0, max = 3600)
    public int reportCooldownSeconds = 60;

    @Setting(path = "reports.max-open-per-player", desc = "How many open reports one player can have at once.", min = 1, max = 20)
    public int reportMaxOpen = 3;

    @Setting(path = "reports.min-playtime-minutes", desc = "Minutes a player must have played before they can report (anti-abuse; 0 = no limit).", min = 0, max = 600)
    public int reportMinPlaytimeMinutes = 0;

    @Setting(path = "reports.details-max-length", desc = "Maximum length of the free-text details.", min = 10, max = 255)
    public int reportDetailsMaxLength = 120;

    @Setting(path = "reports.reasons", desc = "Report reasons shown in the picker (the last one asks for free text).")
    public List<String> reportReasons = List.of("Hacking", "X-ray", "Toxicity", "Spam", "Advertising", "Exploiting",
            "Inappropriate build", "Inappropriate name", "Other");

    @Setting(path = "reports.closed-shown", desc = "How many recently closed reports /reports shows.", min = 10, max = 500)
    public int closedShown = 100;

    @Override
    public String fileName() {
        return "staff.yml";
    }

    @Override
    public String header() {
        return """
                Staff tools: staff mode (/staff), /freeze, /inspect, /report and /reports.
                Texts are in messages/staff.yml. Reload: /skyblock reload staff""";
    }
}
