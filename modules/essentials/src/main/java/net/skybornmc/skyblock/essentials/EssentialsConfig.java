package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Typed settings of {@code essentials.yml}. */
public final class EssentialsConfig extends ModuleConfig {

    @Setting(path = "spawn.world", desc = "World that /spawn teleports to (its spawn point is set by worlds.yml).")
    public String spawnWorld = "spawn";

    @Setting(path = "spawn.warmup-seconds", desc = "Seconds to stand still before /spawn teleports (0 = instant).", min = 0, max = 30)
    public int spawnWarmupSeconds = 3;

    @Setting(path = "spawn.cooldown-seconds", desc = "Seconds between two /spawn uses.", min = 0, max = 3600)
    public int spawnCooldownSeconds = 5;

    @Setting(path = "tpa.request-timeout-seconds", desc = "Seconds before a /tpa request expires.", min = 10, max = 600)
    public int tpaTimeoutSeconds = 60;

    @Setting(path = "tpa.warmup-seconds", desc = "Seconds to stand still after a request is accepted (0 = instant).", min = 0, max = 30)
    public int tpaWarmupSeconds = 3;

    @Setting(path = "tpa.cooldown-seconds", desc = "Seconds between two /tpa or /tpahere requests.", min = 0, max = 3600)
    public int tpaCooldownSeconds = 15;

    @Setting(path = "tpa.max-pending", desc = "How many open requests one player can receive at once.", min = 1, max = 20)
    public int tpaMaxPending = 5;

    @Setting(path = "warmup.cancel-on-move", desc = "Cancel a teleport warmup when the player moves a block.")
    public boolean cancelOnMove = true;

    @Setting(path = "warmup.cancel-on-damage", desc = "Cancel a teleport warmup when the player takes damage.")
    public boolean cancelOnDamage = true;

    @Setting(path = "back.remember-death", desc = "Staff /back also returns to the last death location.")
    public boolean backRememberDeath = true;

    @Setting(path = "homes.max", desc = "Maximum staff homes per staff member (/sethome).", min = 1, max = 50)
    public int maxHomes = 5;

    @Setting(path = "give.max-amount", desc = "Largest amount /i and /give hand out at once.", min = 1, max = 6400)
    public int giveMaxAmount = 2304;

    @Setting(path = "clear.confirm-seconds", desc = "Seconds to repeat /clear <player> to confirm clearing someone else.", min = 3, max = 60)
    public int clearConfirmSeconds = 10;

    @Setting(path = "vanish.night-vision", desc = "Give vanished staff night vision.")
    public boolean vanishNightVision = true;

    @Override
    public String fileName() {
        return "essentials.yml";
    }

    @Override
    public String header() {
        return """
                Essentials: teleports (/spawn, /tpa, staff /tp) and staff tools (/gm, /fly, /vanish, /invsee, ...).
                Texts are in messages/essentials.yml. Reload: /skyblock reload essentials""";
    }
}
