package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Typed settings of {@code essentials.yml}. */
public final class EssentialsConfig extends ModuleConfig {

    @Setting(path = "spawn.world", desc = "World that /spawn teleports to (its spawn point is set by worlds.yml).")
    public String spawnWorld = "spawn";

    @Setting(path = "tpa.request-timeout-seconds", desc = "Seconds before a /tpa request expires.", min = 10, max = 600)
    public int tpaTimeoutSeconds = 60;

    @Setting(path = "tpa.cooldown-seconds", desc = "Seconds between two /tpa or /tpahere requests.", min = 0, max = 3600)
    public int tpaCooldownSeconds = 15;

    @Setting(path = "tpa.max-pending", desc = "How many open requests one player can receive at once.", min = 1, max = 20)
    public int tpaMaxPending = 5;

    @Setting(path = "homes.max", desc = "Maximum staff homes per staff member (/sethome).", min = 1, max = 50)
    public int maxHomes = 5;

    @Setting(path = "give.max-amount", desc = "Largest amount /i and /give hand out at once.", min = 1, max = 6400)
    public int giveMaxAmount = 2304;

    @Setting(path = "clear.confirm-seconds", desc = "Seconds to repeat /clear <player> to confirm clearing someone else.", min = 3, max = 60)
    public int clearConfirmSeconds = 10;

    @Setting(path = "vanish.night-vision", desc = "Give vanished staff night vision.")
    public boolean vanishNightVision = true;

    // ------------------------------------------------------------------ M1.1 join flow

    @Setting(path = "join.teleport-to-spawn", desc = "Teleport every player to spawn when they join (until islands exist).")
    public boolean joinTeleportToSpawn = true;

    @Setting(path = "join.first-join-broadcast", desc = "Announce new players to everyone with their join number.")
    public boolean firstJoinBroadcast = true;

    @Setting(path = "join.welcome-back-title", desc = "Show a short 'Welcome back' title to returning players.")
    public boolean welcomeBackTitle = true;

    @Setting(path = "join.message-permission", desc = "Only players with this permission get a join/quit message (ranks + staff).")
    public String joinMessagePermission = "skyblock.join.message";

    @Setting(path = "respawn.at-spawn", desc = "Players always respawn at spawn after dying, also with a bed or respawn anchor "
            + "(owner answer 160: never at the island home).")
    public boolean respawnAtSpawn = true;

    // ------------------------------------------------------------------ M1.1 spawn protection

    @Setting(path = "protection.worlds", desc = "Worlds with spawn protection (no building, damage, hunger, drops or mob spawning).")
    public java.util.List<String> protectedWorlds = java.util.List.of("spawn");

    @Setting(path = "protection.interact-allowed", desc = "Blocks players may still use in protected worlds (* = wildcard). "
            + "Spawn is also protected by worlds.yml protection.*: allow a block in both lists.")
    public java.util.List<String> interactAllowed = java.util.List.of("*_door", "*_button", "lever", "*_pressure_plate",
            "*_fence_gate", "ender_chest", "crafting_table", "*_sign", "*_hanging_sign");

    @Setting(path = "protection.void-y", desc = "Falling below this Y in a protected world teleports back to spawn.", min = -2048, max = 320)
    public int voidY = 0;

    // ------------------------------------------------------------------ ANS3 staff powers (owner answers 331-334)

    @Setting(path = "staff-powers.enabled", desc = "Owner answers 331/333: creative only in the creative worlds, and god mode, "
            + "/fly and /heal only in staff mode (/staffmode) or build mode (/build). Off = only the permissions count.")
    public boolean staffPowersEnabled = true;

    @Setting(path = "staff-powers.creative-worlds", desc = "Owner answer 331: worlds where builders and admins may use creative "
            + "mode (spawn, the Depths, builder areas). Leaving them switches a creative player to survival.")
    public java.util.List<String> creativeWorlds = java.util.List.of("spawn", "depths");

    @Setting(path = "staff-powers.check-seconds", desc = "Seconds between checks that take god mode and /fly away from players "
            + "who are no longer in staff mode or build mode.", min = 1, max = 60, restart = true)
    public int staffPowersCheckSeconds = 1;

    @Setting(path = "staff-powers.tag-staff-items", desc = "Owner answer 332: items from /give, /i, /more, /skull and the creative "
            + "inventory get a hidden staff tag; they can't be sold, traded, auctioned or sent to others. The tag also goes onto "
            + "crafting and smelting results made with them.", level = Setting.Level.OWNER)
    public boolean tagStaffItems = true;

    @Setting(path = "staff-powers.track-placed-blocks", desc = "Owner answer 332: a staff item placed as a block in survival "
            + "drops staff items again when mined (remembered in the chunk). Creative builds are not tracked.",
            level = Setting.Level.OWNER)
    public boolean trackStaffBlocks = true;

    // ------------------------------------------------------------------ M1.1 menu item




    @Override
    public String fileName() {
        return "essentials.yml";
    }

    @Override
    public String header() {
        return """
                Essentials: teleports (/spawn, /tpa, staff /tp; warmups and cooldowns are in teleport.yml) and staff tools (/gm, /fly, /vanish, /invsee, ...).
                Texts are in messages/essentials.yml. Reload: /skyblock reload essentials""";
    }
}
