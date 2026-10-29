package net.skybornmc.skyblock.essentials;

import org.bukkit.GameMode;

import java.util.Locale;

/** Permission nodes of the essentials module ({@code skyblock.<group>.<cmd>}, {@code .others} = on other players). */
final class Perms {

    static final String SPAWN = "skyblock.player.spawn";
    static final String TPA = "skyblock.player.tpa";

    static final String TELEPORT_BYPASS = "skyblock.staff.teleport.bypass";
    static final String TP = "skyblock.staff.tp";
    static final String TP_OTHERS = "skyblock.staff.tp.others";
    static final String TPHERE = "skyblock.staff.tphere";
    static final String TPPOS = "skyblock.staff.tppos";
    static final String TOP = "skyblock.staff.top";
    static final String BACK = "skyblock.staff.back";
    static final String HOME = "skyblock.staff.home";

    static final String GAMEMODE = "skyblock.staff.gamemode";
    static final String GAMEMODE_OTHERS = "skyblock.staff.gamemode.others";
    static final String FLY = "skyblock.staff.fly";
    static final String FLY_OTHERS = "skyblock.staff.fly.others";
    static final String SPEED = "skyblock.staff.speed";
    static final String GOD = "skyblock.staff.god";
    static final String GOD_OTHERS = "skyblock.staff.god.others";
    static final String HEAL = "skyblock.staff.heal";
    static final String HEAL_OTHERS = "skyblock.staff.heal.others";
    static final String FEED = "skyblock.staff.feed";
    static final String FEED_OTHERS = "skyblock.staff.feed.others";
    static final String CLEAR = "skyblock.staff.clear";
    static final String CLEAR_OTHERS = "skyblock.staff.clear.others";
    static final String INVSEE = "skyblock.staff.invsee";
    static final String INVSEE_EDIT = "skyblock.staff.invsee.edit";
    static final String ECSEE = "skyblock.staff.ecsee";
    static final String ECSEE_EDIT = "skyblock.staff.ecsee.edit";
    static final String VANISH = "skyblock.staff.vanish";
    static final String VANISH_OTHERS = "skyblock.staff.vanish.others";
    static final String VANISH_SEE = "skyblock.staff.vanish.see";
    static final String GIVE = "skyblock.staff.give";
    static final String TIME = "skyblock.staff.time";
    static final String WEATHER = "skyblock.staff.weather";
    static final String SKULL = "skyblock.staff.skull";
    static final String MORE = "skyblock.staff.more";
    static final String NOTIFY = "skyblock.staff.notify";

    private Perms() {
    }

    /** Per-mode gamemode permission, e.g. {@code skyblock.staff.gamemode.creative}. */
    static String gamemode(GameMode mode) {
        return GAMEMODE + "." + mode.name().toLowerCase(Locale.ROOT);
    }
}
