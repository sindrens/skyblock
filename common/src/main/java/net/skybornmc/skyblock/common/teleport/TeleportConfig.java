package net.skybornmc.skyblock.common.teleport;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;
import java.util.Map;

/**
 * Typed settings of {@code teleport.yml} (F4): warmup and cooldown per {@link TeleportReason}, what cancels a warmup and
 * the staff {@code /back}. Reload: {@code /skyblock reload core}. Defaults keep the behaviour from before F4.
 */
public final class TeleportConfig extends ModuleConfig {

    @Setting(path = "spawn.warmup-seconds", desc = "/spawn: seconds to stand still before the teleport (0 = instant, owner decision).", min = 0, max = 30)
    public int spawnWarmupSeconds = 0;

    @Setting(path = "spawn.cooldown-seconds", desc = "/spawn: seconds between two uses.", min = 0, max = 3600)
    public int spawnCooldownSeconds = 5;

    @Setting(path = "tpa.warmup-seconds", desc = "/tpa and /tpahere: seconds to stand still after the request is accepted (0 = instant; danger.* still applies).", min = 0, max = 30)
    public int tpaWarmupSeconds = 0;

    @Setting(path = "tpa.cooldown-seconds", desc = "/tpa and /tpahere: seconds between two accepted teleports (the request cooldown is in essentials.yml).", min = 0, max = 3600)
    public int tpaCooldownSeconds = 0;

    @Setting(path = "island-home.warmup-seconds", desc = "/is home, personal homes and the Home button: seconds to stand still (0 = instant).", min = 0, max = 30)
    public int islandHomeWarmupSeconds = 0;

    @Setting(path = "island-home.cooldown-seconds", desc = "/is home: seconds between two uses.", min = 0, max = 3600)
    public int islandHomeCooldownSeconds = 0;

    @Setting(path = "island-visit.warmup-seconds", desc = "/is visit: seconds to stand still before the teleport (0 = instant).", min = 0, max = 30)
    public int islandVisitWarmupSeconds = 0;

    @Setting(path = "island-visit.cooldown-seconds", desc = "/is visit: seconds between two uses.", min = 0, max = 3600)
    public int islandVisitCooldownSeconds = 0;

    @Setting(path = "island-warp.warmup-seconds", desc = "/is warp and warp signs: seconds to stand still before the teleport (0 = instant).", min = 0, max = 30)
    public int islandWarpWarmupSeconds = 0;

    @Setting(path = "island-warp.cooldown-seconds", desc = "/is warp: seconds between two uses.", min = 0, max = 3600)
    public int islandWarpCooldownSeconds = 0;

    @Setting(path = "dungeon.warmup-seconds", desc = "/dungeon: seconds to stand still before the teleport (0 = instant; skyblock.depths.bypass skips it).", min = 0, max = 30)
    public int dungeonWarmupSeconds = 0;

    @Setting(path = "dungeon.cooldown-seconds", desc = "/dungeon: seconds between two teleports.", min = 0, max = 3600)
    public int dungeonCooldownSeconds = 0;

    @Setting(path = "danger.warmup-seconds", desc = "Owner answers 285/341/345: teleports are instant, but while combat-tagged or "
            + "inside a combat zone (danger.worlds, the Depths boss arena and combat tiers, events) every player teleport "
            + "(/spawn, /tpa, homes, visits, warps, /dungeon) waits this many seconds. /spawn and /tpa are never blocked there.",
            min = 0, max = 30)
    public int dangerWarmupSeconds = 3;

    @Setting(path = "danger.when-combat-tagged", desc = "A combat tag (hitting or being hit, core.yml combat.*) counts as danger.")
    public boolean dangerWhenTagged = true;

    @Setting(path = "danger.worlds", desc = "Worlds that count as a combat zone everywhere (the warzone, event worlds).")
    public List<String> dangerWorlds = List.of("warzone");

    @Setting(path = "portal-only-worlds", desc = "Worlds players only enter through their portal (owner answer 413): /tpa, "
            + "/tpahere and other player teleports into them are refused (the staff teleport bypass skips this).")
    public List<String> portalOnlyWorlds = List.of("warzone");

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

    /** Version 2 (owner answers round 2, 285/341): visit, warp, tpa and dungeon warmups 3 → 0 (danger.* adds 3 s in combat). */
    @Override
    public int version() {
        return 2;
    }

    @Override
    public Map<Integer, Map<String, ValueChange>> valueChanges() {
        return Map.of(2, Map.of(
                "tpa.warmup-seconds", ValueChange.of(3, 0),
                "island-visit.warmup-seconds", ValueChange.of(3, 0),
                "island-warp.warmup-seconds", ValueChange.of(3, 0),
                "dungeon.warmup-seconds", ValueChange.of(3, 0)));
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
