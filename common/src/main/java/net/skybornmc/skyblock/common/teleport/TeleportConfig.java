package net.skybornmc.skyblock.common.teleport;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/**
 * Typed settings of {@code teleport.yml} (F4): warmup and cooldown per {@link TeleportReason}, what cancels a warmup and
 * the staff {@code /back}. Reload: {@code /skyblock reload core}. Defaults keep the behaviour from before F4.
 */
public final class TeleportConfig extends ModuleConfig {

    @Setting(path = "spawn.warmup-seconds", desc = "/spawn: seconds to stand still before the teleport (0 = instant, owner decision).", min = 0, max = 30)
    public int spawnWarmupSeconds = 0;

    @Setting(path = "spawn.cooldown-seconds", desc = "/spawn: seconds between two uses.", min = 0, max = 3600)
    public int spawnCooldownSeconds = 5;

    @Setting(path = "tpa.warmup-seconds", desc = "/tpa and /tpahere: seconds to stand still after the request is accepted (0 = instant).", min = 0, max = 30)
    public int tpaWarmupSeconds = 3;

    @Setting(path = "tpa.cooldown-seconds", desc = "/tpa and /tpahere: seconds between two accepted teleports (the request cooldown is in essentials.yml).", min = 0, max = 3600)
    public int tpaCooldownSeconds = 0;

    @Setting(path = "island-home.warmup-seconds", desc = "/is home, personal homes and the Home button: seconds to stand still (0 = instant).", min = 0, max = 30)
    public int islandHomeWarmupSeconds = 0;

    @Setting(path = "island-home.cooldown-seconds", desc = "/is home: seconds between two uses.", min = 0, max = 3600)
    public int islandHomeCooldownSeconds = 0;

    @Setting(path = "island-visit.warmup-seconds", desc = "/is visit: seconds to stand still before the teleport (0 = instant).", min = 0, max = 30)
    public int islandVisitWarmupSeconds = 3;

    @Setting(path = "island-visit.cooldown-seconds", desc = "/is visit: seconds between two uses.", min = 0, max = 3600)
    public int islandVisitCooldownSeconds = 0;

    @Setting(path = "island-warp.warmup-seconds", desc = "/is warp and warp signs: seconds to stand still before the teleport (0 = instant).", min = 0, max = 30)
    public int islandWarpWarmupSeconds = 3;

    @Setting(path = "island-warp.cooldown-seconds", desc = "/is warp: seconds between two uses.", min = 0, max = 3600)
    public int islandWarpCooldownSeconds = 0;

    @Setting(path = "dungeon.warmup-seconds", desc = "/dungeon: seconds to stand still before the teleport (0 = instant; skyblock.depths.bypass skips it).", min = 0, max = 30)
    public int dungeonWarmupSeconds = 3;

    @Setting(path = "dungeon.cooldown-seconds", desc = "/dungeon: seconds between two teleports.", min = 0, max = 3600)
    public int dungeonCooldownSeconds = 0;

    @Setting(path = "cancel.on-move", desc = "Moving away from the start spot cancels a warmup.")
    public boolean cancelOnMove = true;

    @Setting(path = "cancel.move-distance", desc = "Blocks a player may move from the start spot before the warmup is cancelled.", min = 0.1, max = 10)
    public double cancelMoveDistance = 1.0;

    @Setting(path = "cancel.on-damage", desc = "Taking damage cancels a warmup.")
    public boolean cancelOnDamage = true;

    @Setting(path = "cancel.on-combat", desc = "A new combat tag (hitting or being hit, core.yml combat.*) cancels a warmup.")
    public boolean cancelOnCombat = true;

    @Setting(path = "block-in-combat", desc = "Warmup teleports can't start while combat-tagged (the bypass permission skips this).")
    public boolean blockInCombat = false;

    @Setting(path = "back.remember-death", desc = "Staff /back also returns to the last death location.")
    public boolean backRememberDeath = true;

    @Override
    public String fileName() {
        return "teleport.yml";
    }

    @Override
    public String header() {
        return "Teleports (F4): warmup and cooldown per teleport type, and what cancels a warmup.\n"
                + "Texts are in messages/core.yml (teleport.*). Reload: /skyblock reload core";
    }

    public int warmupSeconds(TeleportReason reason) {
        return switch (reason) {
            case SPAWN -> spawnWarmupSeconds;
            case TPA -> tpaWarmupSeconds;
            case ISLAND_HOME -> islandHomeWarmupSeconds;
            case ISLAND_VISIT -> islandVisitWarmupSeconds;
            case ISLAND_WARP -> islandWarpWarmupSeconds;
            case DUNGEON -> dungeonWarmupSeconds;
            case PROFILE, STAFF -> 0;
        };
    }

    public int cooldownSeconds(TeleportReason reason) {
        return switch (reason) {
            case SPAWN -> spawnCooldownSeconds;
            case TPA -> tpaCooldownSeconds;
            case ISLAND_HOME -> islandHomeCooldownSeconds;
            case ISLAND_VISIT -> islandVisitCooldownSeconds;
            case ISLAND_WARP -> islandWarpCooldownSeconds;
            case DUNGEON -> dungeonCooldownSeconds;
            case PROFILE, STAFF -> 0;
        };
    }

    public TeleportRules rules() {
        return new TeleportRules(cancelOnMove, cancelMoveDistance, cancelOnDamage, cancelOnCombat, blockInCombat);
    }
}
