package net.skybornmc.skyblock.npcs;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the NPC and hologram module ({@code npcs.yml}). Positions come from the applied builds
 * ({@code build-points.yml}, lists {@code npcs}, {@code holograms}, {@code leaderboards}); texts from
 * {@code messages/npcs.yml}. Mapping lists use {@code "key: value"} lines so they stay editable in-game.
 */
public final class NpcsConfig extends ModuleConfig {

    /** What the NPC body is. */
    public enum Body { MANNEQUIN, VILLAGER }

    // ------------------------------------------------------------------ NPCs

    @Setting(path = "npcs.enabled", desc = "Spawn the NPCs at the builds' npc pads.")
    public boolean npcsEnabled = true;

    @Setting(path = "npcs.body", desc = "NPC body: mannequin (player-shaped, Paper 26.3) or villager (no AI). Use villager if Bedrock players can't see mannequins.")
    public Body body = Body.MANNEQUIN;

    @Setting(path = "npcs.name-height", desc = "Height of the name text above the NPC's feet (blocks).", min = 1.0, max = 4.0)
    public double nameHeight = 2.05;

    @Setting(path = "npcs.name-view-range", desc = "How far the name text is visible (1.0 = 64 blocks).", min = 0.1, max = 4.0)
    public double nameViewRange = 0.5;

    @Setting(path = "npcs.look-at-players", desc = "NPCs turn their head to the nearest player.")
    public boolean lookAtPlayers = true;

    @Setting(path = "npcs.look-radius", desc = "Players closer than this are looked at (blocks).", min = 1.0, max = 16.0)
    public double lookRadius = 6.0;

    @Setting(path = "npcs.look-ticks", desc = "How often NPC heads are turned (ticks). Higher = cheaper, less smooth.", min = 2, max = 100)
    public int lookTicks = 10;

    @Setting(path = "npcs.click-cooldown-ms", desc = "Minimum time between two clicks of one player on NPCs and holograms (milliseconds).", min = 100, max = 5000)
    public int clickCooldownMs = 600;

    @Setting(path = "npcs.left-click-acts", desc = "Hitting an NPC or hologram acts like right-clicking it (Bedrock touch players tap = hit). Off: only right-click acts; the texts say Click.")
    public boolean leftClickActs = true;

    @Setting(path = "npcs.sneak-for-help", desc = "Sneak + click on an NPC with an action shows its help text instead of running the action.")
    public boolean sneakForHelp = true;

    @Setting(path = "npcs.roles", desc = "NPC id -> role, as 'id: role' lines (NPCs whose id is already a role need no line; a 'role' key in the build metadata wins).")
    public List<String> npcRoles = List.of(
            "quartermaster: forge",
            "blacksmith: forge",
            "help: info",
            "ah: auction",
            "depths_forge: forge",
            "depths_sell: sell",
            "depths_guide: dungeon",
            "mine_foreman: foreman");

    @Setting(path = "npcs.actions", desc = "Role -> what a click does, as 'role: command <command>' (runs the command as the player, help on sneak-click) or 'role: help [command]' (help text, with an [Open] button for the command). Roles without a line show their help.")
    public List<String> roleActions = List.of(
            "guide: help menu",
            "info: help help",
            "forge: command forge",
            "enchanter: command enchanter",
            "wardrobe: command wardrobe",
            "bank: command bank",
            "shop: command shop",
            "sell: command sell",
            "auction: command ah",
            "dungeon: command dungeon",
            "foreman: help dungeon",
            "crates: command crates",
            "black_market: help",
            "quests: help",
            "daily: command daily",
            "vote: help");

    /** {@code npcs.actions} up to v2: crates and daily were help-only ("opens soon"). */
    static final List<String> ACTIONS_V2 = List.of(
            "guide: help menu",
            "info: help help",
            "forge: command forge",
            "enchanter: command enchanter",
            "wardrobe: command wardrobe",
            "bank: command bank",
            "shop: command shop",
            "sell: command sell",
            "auction: command ah",
            "dungeon: command dungeon",
            "foreman: help dungeon",
            "crates: help",
            "black_market: help",
            "quests: help",
            "daily: help",
            "vote: help");

    @Setting(path = "npcs.help-once", desc = "A plain click on an NPC with an action runs it and, the first time each session, also "
            + "prints the NPC's short help in chat (owner U5: one click talks; no sneak-click needed). Off: help only on sneak-click.")
    public boolean helpOnce = true;

    @Setting(path = "npcs.skins", desc = "NPC id or role -> Minecraft name whose skin the mannequin wears, as 'id: name' lines. Empty = default skins (no skin lookups).")
    public List<String> skins = List.of();

    @Setting(path = "npcs.held-items", desc = "NPC id or role -> item in the NPC's hand, as 'id: material' lines.")
    public List<String> heldItems = List.of(
            "blacksmith: iron_pickaxe",
            "depths_forge: iron_pickaxe",
            "quartermaster: wooden_sword",
            "enchanter: enchanted_book",
            "bank: gold_ingot");

    // ------------------------------------------------------------------ holograms

    @Setting(path = "spawning.per-tick", desc = "Most NPCs, holograms and leaderboards spawned per tick; the rest follow in the next ticks (spreads the cost of start-up and chunk loads).", min = 1, max = 200)
    public int spawnsPerTick = 8;

    @Setting(path = "spawning.budget-ms", desc = "Stop spawning for the current tick after this many milliseconds (at least one is always spawned per tick).", min = 1, max = 50)
    public int spawnBudgetMs = 2;

    @Setting(path = "spawning.removals-per-tick", desc = "Reload: most old NPCs, holograms and leaderboards removed per tick (within spawning.budget-ms) before the new ones spawn.", min = 1, max = 500)
    public int removalsPerTick = 24;

    @Setting(path = "holograms.enabled", desc = "Spawn the text holograms at the builds' hologram points (text: messages/npcs.yml -> holograms.<id>).")
    public boolean hologramsEnabled = true;

    @Setting(path = "holograms.skip", desc = "Hologram ids not spawned here (live texts another module writes later: boss timers, event timers, the rush banner).")
    public List<String> hologramSkip = List.of("miniboss_timer", "event_timer_board", "rush_banner");

    @Setting(path = "holograms.view-range", desc = "How far holograms and leaderboards are visible (1.0 = 64 blocks).", min = 0.1, max = 4.0)
    public double viewRange = 1.0;

    @Setting(path = "holograms.line-width", desc = "Text wraps after this many pixels.", min = 50, max = 1000)
    public int lineWidth = 220;

    @Setting(path = "holograms.line-height", desc = "Height of one text line at scale 1 (blocks); used to fit a text into the board size.", min = 0.1, max = 1.0)
    public double lineHeight = 0.27;

    @Setting(path = "holograms.fill", desc = "Part of the board height the text may use (0.8 = 80%).", min = 0.2, max = 1.0)
    public double fill = 0.8;

    @Setting(path = "holograms.min-scale", desc = "Smallest text scale.", min = 0.1, max = 5.0)
    public double minScale = 0.5;

    @Setting(path = "holograms.max-scale", desc = "Largest text scale (one-line signposts).", min = 0.1, max = 5.0)
    public double maxScale = 2.0;

    @Setting(path = "holograms.wall-offset", desc = "How far the text sits from the point toward the wall behind it (blocks; the point is the air cell in front of the board).", min = 0.0, max = 1.0)
    public double wallOffset = 0.45;

    @Setting(path = "holograms.yaw-offset", desc = "Added to every board's facing (degrees). Use 180 if boards show their back.", min = -360, max = 360)
    public double yawOffset = 0;

    @Setting(path = "holograms.background-alpha", desc = "Darkness of the text background (0 = none, 255 = black).", min = 0, max = 255)
    public int backgroundAlpha = 0;

    @Setting(path = "holograms.clickable", desc = "Holograms with a help text (hologram-help.<id>) get an invisible click box that shows it.")
    public boolean clickable = true;

    @Setting(path = "holograms.click-max-width", desc = "Widest click box (blocks).", min = 0.5, max = 8.0)
    public double clickMaxWidth = 3.0;

    // ------------------------------------------------------------------ leaderboards

    @Setting(path = "leaderboards.enabled", desc = "Spawn the leaderboard holograms at the builds' leaderboard points.")
    public boolean leaderboardsEnabled = true;

    @Setting(path = "leaderboards.refresh-minutes", desc = "How often leaderboards are read again (minutes; read off the main thread).", min = 1, max = 60)
    public int refreshMinutes = 5;

    @Setting(path = "leaderboards.size", desc = "Players listed per board.", min = 3, max = 15)
    public int size = 10;

    @Setting(path = "leaderboards.sources", desc = "Leaderboard id -> data, as 'id: money' (or shards, credits; crystals are never on a public list, C1-R1-28), 'id: skill <mining|foraging|combat|farming|fishing>', 'id: island <stat> [all_time|this_week|last_week|today] [all|solo|team]', 'id: island_level [all|solo|team]', 'id: island_value [all|solo|team]', 'id: island_gain this_week [all|solo|team|cycle]', 'id: pillar_gain this_week [all|solo|team|cycle]' (weekly boards; cycle switches Solo/Team) or 'id: none' (shows Coming soon).")
    public List<String> sources = List.of(
            "island_top_solo: island_level solo",
            "island_top_team: island_level team",
            "weekly_island_gain: island_gain this_week cycle",
            "weekly_pillar_gain: pillar_gain this_week cycle",
            "boss_damage: none",
            "mining: skill mining",
            "foraging: skill foraging",
            "combat: skill combat",
            "farming: skill farming",
            "fishing: skill fishing",
            "baltop: money",
            "money: money");

    /** The default from PHASE-5 M5.2 until M5.7 (version 2/3): the weekly boards still showed "Coming soon". */
    static final List<String> SOURCES_V2 = List.of(
            "island_top_solo: island_level solo",
            "island_top_team: island_level team",
            "weekly_island_gain: none",
            "weekly_pillar_gain: none",
            "boss_damage: none",
            "mining: skill mining",
            "foraging: skill foraging",
            "combat: skill combat",
            "farming: skill farming",
            "fishing: skill fishing",
            "baltop: money",
            "money: money");

    /** The four boards that showed "Coming soon" before PHASE-5 M5.2 (version 1 default). */
    static final List<String> SOURCES_V1 = List.of(
            "island_top_solo: none",
            "island_top_team: none",
            "weekly_island_gain: none",
            "weekly_pillar_gain: none",
            "boss_damage: none",
            "mining: skill mining",
            "foraging: skill foraging",
            "combat: skill combat",
            "farming: skill farming",
            "fishing: skill fishing",
            "baltop: money",
            "money: money");

    @Override
    public int version() {
        return 4;
    }

    /**
     * v2 (PHASE-5 M5.2): the island top boards show island levels. v3 (GUI-BUGS): the Crate Keeper and Daily Rewards NPCs
     * open /crates and /daily (they exist now). v4 (PHASE-5 M5.7): the two weekly Hall of Heroes boards show the weekly
     * island and pillar gain, switching Solo/Team. An owner-edited list is kept.
     */
    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of("leaderboards.sources", ValueChange.of(SOURCES_V1, sources())),
                3, java.util.Map.of("npcs.actions", ValueChange.of(ACTIONS_V2, new NpcsConfig().roleActions)),
                4, java.util.Map.of("leaderboards.sources", ValueChange.of(SOURCES_V2, sources())));
    }

    private static List<String> sources() {
        return new NpcsConfig().sources;
    }

    @Override
    public String fileName() {
        return "npcs.yml";
    }

    @Override
    public String header() {
        return """
                NPCs, holograms and leaderboards (NPCHOLO) at the points of the applied builds (build-points.yml, written by
                /skyblock builds apply). NPCs are Paper mannequins with a name text above; holograms are text displays with an
                optional click box for help; leaderboards refresh off the main thread. Texts: messages/npcs.yml.
                Reload (respawns everything): /skyblock reload npcs""";
    }
}
