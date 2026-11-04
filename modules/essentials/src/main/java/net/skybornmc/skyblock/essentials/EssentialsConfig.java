package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Typed settings of {@code essentials.yml}. */
public final class EssentialsConfig extends ModuleConfig {

    @Setting(path = "hunger.disabled", desc = "Owner 3 Nov 2026: no hunger anywhere (the food bar stays full).")
    public boolean hungerDisabled = true;

    @Setting(path = "grind.enabled", desc = "Temporary /farm, /mine and /dungeon areas in one world (owner 3 Nov 2026), until the grind areas redesign.")
    public boolean grindEnabled = true;

    @Setting(path = "grind.world", desc = "World of the temporary areas (must be in worlds.yml).")
    public String grindWorld = "grind";

    @Setting(path = "grind.y", desc = "Ground height of the areas in that world.", min = -60, max = 300)
    public int grindY = 99;

    @Setting(path = "grind.farm-regrow-seconds", desc = "A broken wheat crop at /farm is ripe again after this many seconds.", min = 1, max = 600)
    public int grindFarmRegrowSeconds = 2;

    @Setting(path = "grind.mine-regen-seconds", desc = "A mined block at /mine comes back after this many seconds.", min = 1, max = 600)
    public int grindMineRegenSeconds = 3;

    @Setting(path = "grind.removed-world", desc = "Owner 3 Nov 2026: the Depths world is gone for players; anyone logging in there "
            + "is sent to spawn. Empty = off.")
    public String removedWorld = "depths";

    @Setting(path = "grind.bonus-percent", desc = "Money bonus per /farm harvest and /mine ore, in percent of the built-in amounts "
            + "(grind.farm-bonus and grind.mine-bonus). 0 = no bonus.", min = 0, max = 1000)
    public int grindBonusPercent = 100;

    @Setting(path = "grind.bonus-chance-percent", desc = "Owner 4 Nov 2026: the money bonus comes only now and then: percent chance per /farm "
            + "harvest and /mine ore (was every block).", min = 0, max = 100)
    public double grindBonusChance = 4;

    @Setting(path = "grind.farm-bonus", desc = "Money of one /farm bonus per crop tier (wheat, carrots, potatoes, beetroot): small at the start, bigger later.")
    public List<Integer> grindFarmBonus = List.of(40, 70, 120, 200);

    @Setting(path = "grind.mine-bonus", desc = "Money of one /mine bonus per ore tier (coal, copper, iron, gold, redstone, lapis, diamond, emerald).")
    public List<Integer> grindMineBonus = List.of(30, 50, 80, 120, 160, 240, 450, 800);

    @Setting(path = "grind.mine-unlocks", desc = "Owner 4 Nov 2026: blocks mined in total that turn the whole /mine into the next ore for you "
            + "(coal, copper, iron, gold, redstone, lapis, diamond, emerald; the first is always 0).")
    public List<Integer> grindMineUnlocks = List.of(0, 400, 1_200, 3_000, 6_000, 10_000, 16_000, 25_000);

    @Setting(path = "grind.dungeon-mobs", desc = "Monsters kept alive in the /dungeon arena while players are inside.", min = 0, max = 60)
    public int grindDungeonMobs = 12;

    // ------------------------------------------------------------------ start flow (owner 3 Nov 2026, replaces the tutorial)

    @Setting(path = "start.enabled", desc = "Start guide on: a boss bar 'Go to spawn and talk to <guide>' until the player talked "
            + "to the guide NPC at spawn (or used /start); the scoreboard 'Next:' line shows the same.")
    public boolean startEnabled = true;

    @Setting(path = "start.island-on-first-join", desc = "Brand-new players get their island created right after joining "
            + "(no portal walk). If the island can't be made, they stay at spawn and can use /is.")
    public boolean startIslandOnFirstJoin = true;

    @Setting(path = "start.island-for-islandless", desc = "Also players who already played but have no island (on the active "
            + "profile) get one created when they join.")
    public boolean startIslandForIslandless = true;

    @Setting(path = "start.island-delay-ticks", desc = "Ticks after joining (and after the icon pack answer) before the island "
            + "is created, so the profile and the welcome are ready (20 = 1 second).", min = 1, max = 400)
    public int startIslandDelayTicks = 40;

    @Setting(path = "start.guide-role", desc = "NPC role of the guide at spawn (npcs.yml): clicking it counts as talking to the "
            + "guide. Its name is messages/npcs.yml roles.<role>.name.")
    public String startGuideRole = "guide";

    @Setting(path = "start.bossbar-color", desc = "Boss bar colour: PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE.")
    public String startBarColor = "YELLOW";

    @Setting(path = "start.bossbar-overlay", desc = "Boss bar style: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12 or NOTCHED_20.")
    public String startBarOverlay = "PROGRESS";

    @Setting(path = "start.island-command", desc = "Command (without /) of the Island button in /start.")
    public String startIslandCommand = "is home";

    @Setting(path = "start.farm-command", desc = "Command (without /) of the Farm button in /start.")
    public String startFarmCommand = "farm";

    @Setting(path = "start.mine-command", desc = "Command (without /) of the Mine button in /start.")
    public String startMineCommand = "mine";

    @Setting(path = "hunger.pvp-worlds", desc = "Worlds where hunger still works (owner 3 Nov 2026: only PvP; off in dungeons too).")
    public java.util.List<String> hungerWorlds = java.util.List.of("warzone");

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

    @Setting(path = "join.privacy-delay-seconds", desc = "Seconds after the first join before the privacy line (chat and commands "
            + "are logged) is sent, so it is not on the first screen next to the welcome.", min = 1, max = 600)
    public int privacyDelaySeconds = 45;

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
            + "/fly and /heal only in staff mode (/s) or build mode (/build). Off = only the permissions count.")
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

    // ------------------------------------------------------------------ QUICK1 drop guard (QoL #10a)

    @Setting(path = "drop-guard.enabled", desc = "Custom and unique items (tools, books, notes, keys, spawners) need a second "
            + "drop (or sneak + drop) before they leave your inventory. Players can turn it off in /settings.")
    public boolean dropGuardEnabled = true;

    @Setting(path = "drop-guard.confirm-seconds", desc = "Seconds in which dropping the same item again throws it.", min = 1, max = 30)
    public int dropGuardConfirmSeconds = 3;

    // ------------------------------------------------------------------ QOL3 auto-refill (QoL #12)

    @Setting(path = "auto-refill.enabled", desc = "When the last block of a hotbar or off-hand stack is placed, the next stack "
            + "of the same plain block moves in from the inventory. Players can turn it off in /settings.")
    public boolean autoRefillEnabled = true;

    // ------------------------------------------------------------------ QOL3 "Did you mean" (QoL #9a)

    @Setting(path = "command-hints.enabled", desc = "A mistyped command suggests the closest command the player may use "
            + "(\"Did you mean /home?\"). Off = the vanilla unknown-command line.")
    public boolean commandHintsEnabled = true;

    @Setting(path = "command-hints.max-distance", desc = "How many typos (missing, extra, changed or swapped letters) a "
            + "suggestion may be away from what was typed. Short words get at most one less than their length.", min = 1, max = 3)
    public int commandHintsMaxDistance = 2;

    // ------------------------------------------------------------------ M1.1 menu item




    /** Dead-key rule (REVIEW3 §6): the menu item moved to menus.yml (menu-item.*); the old essentials keys are deleted on load. */
    @Override
    public java.util.Map<String, String> removedKeys() {
        return java.util.Map.of("menu-item", "the menu item is configured in menus.yml (menu-item.*) now");
    }

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
