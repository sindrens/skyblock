package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the join tutorial ({@code tutorial.yml}, TUTORIAL.md). Every rule here is a default the owner can
 * change (the TQ answers): steps, locks, counts, targets, guides, reward and the starter chest.
 */
public final class TutorialConfig extends ModuleConfig {

    // ------------------------------------------------------------------ flow

    @Setting(path = "enabled", desc = "Run the tutorial for new players. Off = nobody starts it (running ones pause) and every lock is open.", level = Setting.Level.OWNER)
    public boolean enabled = true;

    @Setting(path = "flow.steps", desc = "Core steps in order (TQ1). Known ids: portal, arrive, chest, crop, menu, depths, mine, sell, forge, help, bank. Removing a step skips it; players mid-way continue at their first step not done.", level = Setting.Level.OWNER)
    public List<String> steps = List.of("arrive", "chest", "crop", "menu", "depths", "mine", "sell", "forge", "bank");

    /** The default before v6 (the spawn portal start, the help step). */
    static final List<String> STEPS_V1 = List.of("portal", "arrive", "chest", "crop", "menu", "depths", "mine", "sell", "forge", "help", "bank");

    @Setting(path = "start.auto-island", desc = "Owner 3 Nov 2026: a new player gets their own island on the first join and starts the tutorial there (no spawn portal walk). Off = the old start at spawn.")
    public boolean autoIsland = true;

    @Setting(path = "flow.version", desc = "Raise after changing flow.steps; stored with each player's progress (finished players stay finished).", min = 1, max = 1000)
    public int flowVersion = 2;

    @Setting(path = "flow.early-steps-count", desc = "Steps done before they are reached count and are skipped later (TQ21).")
    public boolean earlySteps = true;

    // ------------------------------------------------------------------ locks

    @Setting(path = "gates", desc = "Feature locks (TQ4) as 'feature: step'. The feature opens when that step and every step before it is done; 'finish' = when the tutorial is finished. Features: island_team, depths, auction, pay, withdraw, trade, warzone, profile_create, outposts, events. A feature not listed is never locked.", level = Setting.Level.OWNER)
    public List<String> gates = List.of("island_team: crop", "depths: menu", "auction: sell", "pay: bank", "withdraw: bank",
            "trade: finish", "warzone: finish", "profile_create: finish", "outposts: finish", "events: finish");

    @Setting(path = "locked.chat-cooldown-seconds", desc = "The 'Unlocks at tutorial step N' line goes to chat at most this often (the action bar shows it every time).", min = 0, max = 300)
    public int lockedChatCooldownSeconds = 10;

    // ------------------------------------------------------------------ steps

    @Setting(path = "steps.arrive.seconds", desc = "Length of the arrival step when the island camera can't play (cameras off, no island home): a title on the island.", min = 1, max = 30)
    public int arriveSeconds = 6;

    @Setting(path = "steps.chest.need-tool", desc = "The chest step is done when the starter chest was opened and this tool kind is in the inventory (pickaxe, axe, hoe, sword, rod; empty = opening is enough).")
    public String chestTool = "pickaxe";

    @Setting(path = "steps.crop.count", desc = "Crops to plant on your island for the first-crop step.", min = 1, max = 64)
    public int cropCount = 3;

    @Setting(path = "steps.crop.blocks", desc = "Crop blocks that count (block ids).")
    public List<String> cropBlocks = List.of("wheat");

    @Setting(path = "steps.menu.menu-ids", desc = "Menus whose opening completes the menu step (hub = /menu, the Nether Star; "
            + "UX review: the step teaches /menu, then points at The Depths).")
    public List<String> menuIds = List.of("hub");

    @Setting(path = "steps.depths.zone", desc = "Depths zone the 'go to the Depths' step waits for.")
    public String depthsZone = "mine_t01";

    @Setting(path = "steps.mine.count", desc = "Mining XP gains in the Depths for the mine step (one per block, TQ29).", min = 1, max = 500)
    public int mineCount = 10;

    @Setting(path = "steps.sell.sources", desc = "Sale sources that count for the sell step (satchel, hand, all, gui...); empty = any sale.")
    public List<String> sellSources = List.of();

    @Setting(path = "steps.forge.menu-ids", desc = "Menus whose opening also completes the Forge step (owner 3 Nov 2026: empty, the step is done by forging; the first forge is free during it).")
    public List<String> forgeMenuIds = List.of();

    @Setting(path = "steps.help.npc-roles", desc = "NPC roles whose click completes the help step (TUT-FIX2: the help NPCs only, "
            + "so the Forge click no longer counts); empty = any NPC.")
    public List<String> helpRoles = List.of("dungeon", "info");

    @Setting(path = "steps.help.menu-ids", desc = "Menus whose opening also completes the help step (help = /help).")
    public List<String> helpMenuIds = List.of("help");

    @Setting(path = "steps.bank.count-island-bank", desc = "Also count a deposit into the island bank (off = only the personal bank).")
    public boolean bankCountsIsland = false;

    @Setting(path = "steps.stuck.skip-after", desc = "After this many 'I'm stuck' presses on one step the player may skip it (only steps in steps.stuck.skippable).", min = 1, max = 20)
    public int stuckSkipAfter = 3;

    @Setting(path = "steps.stuck.skippable", desc = "Steps a stuck player may skip (never sell or bank: they open money features).")
    public List<String> stuckSkippable = List.of("arrive", "depths", "forge");

    // ------------------------------------------------------------------ targets

    @Setting(path = "targets.portal", desc = "Where the portal step points: 'world:x,y,z', 'npc:<id>', 'role:<npc role>' or 'build:<category>:<id>'.")
    public String portalTarget = "spawn:0.5,104,-96.5";

    @Setting(path = "targets.forge", desc = "Where the Forge step points (same formats).")
    public String forgeTarget = "npc:depths_forge";

    @Setting(path = "targets.forge-role", desc = "Fallback NPC role for the Forge step when the target is not found.")
    public String forgeRole = "forge";

    @Setting(path = "targets.help", desc = "Where the help step points (same formats).")
    public String helpTarget = "npc:depths_guide";

    @Setting(path = "targets.help-role", desc = "Fallback NPC role for the help step.")
    public String helpRole = "dungeon";

    @Setting(path = "trail.nodes", desc = "Fallback route of the portal step when no walkable path is found, as 'world:x,y,z' points (spawn dais to the Island portal, TQ12).")
    public List<String> trailNodes = List.of("spawn:0.5,100,0.5", "spawn:0,100,-13", "spawn:0,100,-50", "spawn:0,102,-74", "spawn:0,103,-95");

    // ------------------------------------------------------------------ guides

    @Setting(path = "guide.update-ticks", desc = "How often the boss bar compass and action bar update (ticks).", min = 2, max = 40)
    public int updateTicks = 5;

    @Setting(path = "guide.bossbar", desc = "Show the boss bar compass.")
    public boolean bossbar = true;

    @Setting(path = "guide.bossbar-color", desc = "Boss bar color: pink, blue, red, green, yellow, purple, white. Yellow by default; red stays for event and boss bars (A3-R1-02).")
    public String bossbarColor = "yellow";

    @Setting(path = "guide.bossbar-overlay", desc = "Boss bar style: progress (smooth), notched-6, notched-10, notched-12 or notched-20. Bedrock may ignore the notches.")
    public Overlay bossbarOverlay = Overlay.PROGRESS;

    /** The vanilla boss bar overlays (A3-R1-02). Written {@code progress}, {@code notched-6} ...; {@code notched_6} is read too. */
    public enum Overlay {
        PROGRESS(net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS),
        NOTCHED_6(net.kyori.adventure.bossbar.BossBar.Overlay.NOTCHED_6),
        NOTCHED_10(net.kyori.adventure.bossbar.BossBar.Overlay.NOTCHED_10),
        NOTCHED_12(net.kyori.adventure.bossbar.BossBar.Overlay.NOTCHED_12),
        NOTCHED_20(net.kyori.adventure.bossbar.BossBar.Overlay.NOTCHED_20);

        private final net.kyori.adventure.bossbar.BossBar.Overlay adventure;

        Overlay(net.kyori.adventure.bossbar.BossBar.Overlay adventure) {
            this.adventure = adventure;
        }

        net.kyori.adventure.bossbar.BossBar.Overlay adventure() {
            return adventure;
        }
    }

    @Setting(path = "guide.compass.ahead-degrees", desc = "Within this angle the target counts as straight ahead.", min = 5, max = 90)
    public int aheadDegrees = 30;

    @Setting(path = "guide.compass.behind-degrees", desc = "From this angle the target counts as behind you.", min = 90, max = 180)
    public int behindDegrees = 135;

    @Setting(path = "guide.action-bar-hint", desc = "TUT-FIX2 one place per message: also show the step hint on the action bar every 2 s (off = the boss bar carries the instruction).")
    public boolean actionBarHint = false;

    @Setting(path = "guide.hint-after-seconds", desc = "After this long on a step with nothing done, the step hint is sent once as one chat line. 0 = never.", min = 0, max = 600)
    public int hintAfterSeconds = 45;

    @Setting(path = "guide.scoreboard-objective", desc = "Publish the current step for the scoreboard Next row (placeholders objective, tutorial_next).")
    public boolean scoreboardObjective = true;

    @Setting(path = "guide.particles", desc = "Show the per-player guide trail, the target marker and the dust on farm spots and ore.")
    public boolean particles = true;

    @Setting(path = "guide.particle-ticks", desc = "How often particles are drawn (ticks).", min = 2, max = 40)
    public int particleTicks = 10;

    @Setting(path = "guide.spawn-retry-millis", desc = "When Pip or a guide hologram could not be spawned (refused here), wait this long before trying again (ms).", min = 100, max = 60000)
    public int spawnRetryMillis = 1000;

    @Setting(path = "guide.trail.spacing", desc = "Blocks between two dots of the guide trail.", min = 1, max = 8)
    public double trailSpacing = 1.5;

    @Setting(path = "guide.trail.ahead", desc = "How far ahead of the player the trail is drawn (blocks); dots fade out toward the end.", min = 4, max = 64)
    public double trailAhead = 20.0;

    @Setting(path = "guide.trail.steps", desc = "Steps that draw the walkable guide trail from the player to the target (TUT-FIX2).")
    public List<String> trailSteps = List.of("portal", "chest", "crop", "depths", "forge", "help");

    @Setting(path = "guide.trail.max-particles", desc = "Most trail dots drawn per update for one player.", min = 2, max = 64)
    public int trailMaxParticles = 14;

    @Setting(path = "guide.trail.dot-size", desc = "Size of the nearest trail dot (vanilla dust 0.5-2); farther dots are smaller.", min = 0.5, max = 2)
    public double trailDotSize = 1.1;

    @Setting(path = "guide.trail.color", desc = "Trail colour ('#RRGGBB') of steps not listed in guide.trail.colors.")
    public String trailColor = "#FFD21A";

    @Setting(path = "guide.trail.colors", desc = "Trail colour per step as 'step: #RRGGBB' (travel yellow, island green, NPC visits sky blue).")
    public List<String> trailColors = List.of("portal: #FFD21A", "depths: #FFD21A", "chest: #4DFF88", "crop: #4DFF88",
            "forge: #5AB4E5", "help: #5AB4E5");

    @Setting(path = "guide.trail.animate", desc = "The dots move slowly toward the target.")
    public boolean trailAnimate = true;

    @Setting(path = "guide.trail.min-distance", desc = "No trail when the player is this close to the target (blocks).", min = 1, max = 32)
    public double trailMinDistance = 5;

    @Setting(path = "guide.trail.max-distance", desc = "No path search beyond this distance to the target (blocks); the compass and Pip lead.", min = 16, max = 256)
    public int trailMaxDistance = 128;

    @Setting(path = "guide.trail.max-nodes", desc = "Most blocks one path search may look at (main thread budget).", min = 200, max = 20000)
    public int trailMaxNodes = 4000;

    @Setting(path = "guide.trail.paths-per-run", desc = "Most path searches per guide update for all players together.", min = 1, max = 10)
    public int trailPathsPerRun = 1;

    @Setting(path = "guide.trail.repath-distance", desc = "A player this far off the trail gets a new one (blocks).", min = 2, max = 16)
    public double trailRepathDistance = 4;

    @Setting(path = "guide.trail.repath-seconds", desc = "At least this long between two path searches for one player.", min = 1, max = 30)
    public int trailRepathSeconds = 3;

    @Setting(path = "guide.trail.cache-seconds", desc = "A found path is shared with players starting from the same spot for this long (launch bursts).", min = 0, max = 600)
    public int trailCacheSeconds = 60;

    @Setting(path = "guide.column.enabled", desc = "A small dust column in the trail colour over the target.")
    public boolean column = true;

    @Setting(path = "guide.column.height", desc = "Height of the dust column over the target (blocks).", min = 1, max = 20)
    public int columnHeight = 3;

    @Setting(path = "guide.markers.crop-radius", desc = "Radius around your island home searched for farm spots to mark (blocks).", min = 2, max = 16)
    public int cropRadius = 6;

    @Setting(path = "guide.markers.mine-radius", desc = "Radius around you searched for ore to mark in the mine step.", min = 2, max = 10)
    public int mineRadius = 5;

    @Setting(path = "guide.markers.mine-count", desc = "How many nearby blocks are marked in the mine step.", min = 1, max = 20)
    public int mineMarkers = 5;

    @Setting(path = "guide.markers.mine-blocks", desc = "Block ids marked in the mine step ('*_ore' = every ore).")
    public List<String> mineBlocks = List.of("*_ore", "stone", "cobblestone", "deepslate");

    @Setting(path = "guide.holograms", desc = "Show per-player holograms over targets.")
    public boolean holograms = true;

    @Setting(path = "guide.hologram-steps", desc = "Steps that show a target hologram (TUT-FIX2: not over NPCs or portals, they have their own label).")
    public List<String> hologramSteps = List.of("chest");

    @Setting(path = "guide.holograms-bedrock", desc = "Hologram for Bedrock viewers: text-display or armor-stand (a name tag, TQ32).")
    public String hologramsBedrock = "armor-stand";

    @Setting(path = "guide.hologram-height", desc = "Height of a hologram over its target (blocks).", min = 0.5, max = 5)
    public double hologramHeight = 2.4;

    @Setting(path = "guide.hologram-range", desc = "Holograms show only within this distance (blocks).", min = 4, max = 128)
    public int hologramRange = 48;

    // ------------------------------------------------------------------ resume, idle

    @Setting(path = "rejoin.teleport", desc = "On rejoin mid-tutorial, teleport back to the step's place after rejoin.delay-ticks (sneak to stay, TQ20).")
    public boolean rejoinTeleport = true;

    @Setting(path = "rejoin.delay-ticks", desc = "Wait before the rejoin teleport (ticks).", min = 0, max = 400)
    public int rejoinDelayTicks = 40;

    @Setting(path = "idle.reminder-minutes", desc = "One reminder (one chat line + sound) after this many idle minutes on a step. 0 = off.", min = 0, max = 120)
    public int idleReminderMinutes = 5;

    // ------------------------------------------------------------------ reward

    @Setting(path = "reward.money", desc = "Money paid once per account at the finish, to the profile you finish on (TQ7).", min = 0, max = 1_000_000_000L, level = Setting.Level.OWNER)
    public long rewardMoney = 2500;

    @Setting(path = "reward.crystals", desc = "Crystals paid at the finish (TQ7).", min = 0, max = 1_000_000L, level = Setting.Level.OWNER)
    public long rewardCrystals = 25;

    @Setting(path = "reward.title", desc = "Title id granted at the finish (cosmetics); empty = none.", level = Setting.Level.OWNER)
    public String rewardTitle = "firstflight";

    // ------------------------------------------------------------------ starter chest and pack

    @Setting(path = "starter-chest.enabled", desc = "Fill new islands' starter chest with the tutorial contents below (off = islands.yml starter-chest.items).", level = Setting.Level.OWNER)
    public boolean starterChest = true;

    @Setting(path = "starter-chest.kit", desc = "Put the Forge starter kit (5 tools) in the chest; it counts as the profile's first starter kit claim (TQ15).", level = Setting.Level.OWNER)
    public boolean chestKit = true;

    @Setting(path = "starter-chest.kit-slots", desc = "Chest slots of the starter tools, in kit order (pickaxe, axe, hoe, sword, rod).")
    public List<String> kitSlots = List.of("10", "11", "12", "13", "14");

    @Setting(path = "starter-chest.book-slot", desc = "Chest slot of the Phoenix Field Guide (-1 = no book).", min = -1, max = 26)
    public int bookSlot = 4;

    @Setting(path = "starter-chest.items", desc = "Plain items as 'slot: item: amount' (TQ14, TQ17). Slots 0-26.", level = Setting.Level.OWNER)
    public List<String> chestItems = List.of("15: wheat_seeds: 8", "16: bone_meal: 8", "19: oak_sapling: 2", "20: lava_bucket: 1",
            "21: bread: 16", "22: crafting_table: 1", "23: chest: 1");

    @Setting(path = "starter-pack.enabled", desc = "New players who join a team get the Starter pack instead of a chest (TQ18).", level = Setting.Level.OWNER)
    public boolean starterPack = true;

    @Setting(path = "starter-pack.kit", desc = "Starter tools in the pack (only when that profile has no kit claim running).")
    public boolean packKit = true;

    @Setting(path = "starter-pack.book", desc = "Field Guide in the pack.")
    public boolean packBook = true;

    @Setting(path = "starter-pack.items", desc = "Plain items in the pack as 'item: amount'.", level = Setting.Level.OWNER)
    public List<String> packItems = List.of("bread: 16");

    // ------------------------------------------------------------------ cameras (TU3, TQ10, TQ11)

    @Setting(path = "cameras.enabled", desc = "Play the camera flyovers (spawn on the first join, island at arrival, Depths at Mine Tier 1). Off = titles only.")
    public boolean camerasEnabled = true;

    @Setting(path = "cameras.max-seconds", desc = "Longest camera (seconds); longer camera lines are cut (TUTORIAL §9.4: max 8 s each).", min = 1, max = 20)
    public int cameraMaxSeconds = 8;

    @Setting(path = "cameras.java-smooth", desc = "Java: smooth spectator flight along the shots. Off = Java uses the cut-and-fade shots like Bedrock.")
    public boolean cameraJavaSmooth = true;

    @Setting(path = "cameras.segment-ticks", desc = "Java: ticks per smooth camera move (a display entity smooths at most 59).", min = 5, max = 59)
    public int cameraSegmentTicks = 40;

    @Setting(path = "cameras.fade-ticks", desc = "Cut-and-fade (Bedrock): blindness fade at every cut (ticks).", min = 0, max = 40)
    public int cameraFadeTicks = 10;

    @Setting(path = "cameras.start-delay-ticks", desc = "Wait between moving the player to the first shot and following the camera (ticks; lets the client load the camera).", min = 1, max = 20)
    public int cameraStartDelayTicks = 6;

    @Setting(path = "cameras.bedrock-cuts", desc = "Bedrock: true = cut-and-fade (the player hovers at each shot); false = titles only, nothing about the player changes (safe default).")
    public boolean cameraBedrockCuts = false;

    @Setting(path = "cameras.input-grace-ticks", desc = "A move key or jump skips a camera once it ran this long (ticks; a key held from before does not skip at once). Sneak always skips.", min = 0, max = 40)
    public int cameraInputGraceTicks = 10;

    @Setting(path = "cameras.move-skip-blocks", desc = "Cut-and-fade: moving this far from where the camera holds you skips it (blocks).", min = 0.3, max = 5)
    public double cameraMoveSkipBlocks = 1.0;

    @Setting(path = "cameras.clearance", desc = "Room the camera keeps from every block (blocks); a shot that would come closer is moved or cut.", min = 0, max = 1)
    public double cameraClearance = 0.3;

    @Setting(path = "cameras.max-raise", desc = "A blocked shot may be raised up to this many blocks before it is moved closer, back or played still.", min = 0, max = 20)
    public int cameraMaxRaise = 6;

    @Setting(path = "cameras.start-fade-ticks", desc = "Short blindness when a camera takes the player (hides the jump and any arrival pop-in). 0 = off.", min = 0, max = 40)
    public int cameraStartFadeTicks = 12;

    @Setting(path = "cameras.end-fade-ticks", desc = "Short blindness when the camera gives the player back. 0 = off.", min = 0, max = 40)
    public int cameraEndFadeTicks = 8;

    @Setting(path = "cameras.menu-wait-seconds", desc = "The Depths and Forge cameras wait this long for an open menu to close; then they are left out.", min = 0, max = 60)
    public int cameraMenuWaitSeconds = 10;

    @Setting(path = "cameras.watchdog-seconds", desc = "A camera still running this long after its own length is ended and the player is given back (seconds).", min = 2, max = 60)
    public int cameraWatchdogSeconds = 5;

    @Setting(path = "cameras.island-wait-seconds", desc = "The arrival step waits this long for the player to stand on their island before it plays the island camera; then it finishes with a title.", min = 1, max = 60)
    public int cameraIslandWaitSeconds = 10;

    @Setting(path = "cameras.welcome-new-profile", desc = "A new island on a 2nd or 3rd profile gets the island camera too (no tutorial, TQ31).")
    public boolean cameraWelcomeNewProfile = true;

    @Setting(path = "cameras.spawn", desc = "Spawn flyover before step 1 (first join). Lines: '<from> [to] look=<point> ticks=<n> [text=<key>]'; points 'world:x,y,z', '@home:dx,dy,dz', '@zone:dx,dy,dz', '@forge:dx,dy,dz', '@help:dx,dy,dz', '@self:dx,dy,dz'. Texts: messages cameras.spawn.<key>.")
    public List<String> cameraSpawn = List.of(
            // TUT-FIX2 (owner: "the camera zooms into the roof"): the courtyard from above (open sky), then through the
            // Gatehouse gate (passage x -3..3, y 102-106) into the portal hall at eye height and a slow push to the
            // Island portal (frame z -96, trigger z -97); never above the hall roof (y 107+). Checked against the
            // spawn_gatehouse_v1 / keep / statue builds; spawn coordinates = build coordinates (origin 0,100,0).
            "spawn:0.5,121,12 spawn:0.5,124,4 look=spawn:0.5,115,-23 ticks=50 text=one",
            "spawn:0.5,105.5,-70 spawn:0.5,104.8,-80 look=spawn:0.5,104.5,-97 ticks=45 text=two",
            "spawn:0.5,104.8,-80 spawn:0.5,104.2,-89.5 look=spawn:0.5,104.2,-97 ticks=55 text=three");

    @Setting(path = "cameras.island", desc = "Island camera at the arrival step (starts the moment you arrive; around your island home, same line format).")
    public List<String> cameraIsland = List.of(
            // TUT-FIX2: the old 2nd shot looked through the tree (log: shot 2 blocked); now a high orbit from the
            // south-east to the south, then a glide down behind the player's own view of the shed.
            "@home:12,12,12 @home:-4,10,14 look=@home:0,1,-2 ticks=60 text=one",
            "@home:-4,10,14 @home:0,3.5,6 look=@home:0,1.5,-2 ticks=40 text=two");

    @Setting(path = "cameras.depths", desc = "Depths camera when you reach Mine Tier 1 (around the zone spawn of steps.depths.zone, same line format).")
    public List<String> cameraDepths = List.of(
            // TUT-FIX2: the old shots went through the hoist tower (log: shots 1 and 2 blocked); now over the quarry from
            // the south, then down to the arrival pad looking west over the ore field (depths_mine_t01_coal_v1).
            "@zone:-6,13,16 @zone:-6,11,-6 look=@zone:-28,-2,0 ticks=45 text=one",
            "@zone:-6,11,-6 @zone:2,4,0 look=@zone:-20,0,4 ticks=35 text=two");

    @Setting(path = "cameras.forge", desc = "Forge camera at the start of the Forge step when you are in the Depths (around the Forge NPC targets.forge, same line format). Empty = none.")
    public List<String> cameraForge = List.of(
            // TUT-FIX2: from the gate court to the open front of the Forge hall (depths_gate_v1, NPC depths_forge).
            "@forge:16,6,10 @forge:9,4,6 look=@forge:0,1.6,0 ticks=30 text=one",
            "@forge:9,4,6 @forge:5,2.6,3 look=@forge:0,1.6,0 ticks=30 text=two");

    @Setting(path = "cameras.market", desc = "Market Square camera for the optional spawn tour (Show me, same line format).")
    public List<String> cameraMarket = List.of(
            "spawn:0.5,112,38 spawn:0.5,110,72 look=spawn:0.5,100,60 ticks=60 text=one",
            "spawn:0.5,110,72 spawn:12.5,106,56 look=spawn:20.5,100,56 ticks=40 text=two");

    // ------------------------------------------------------------------ Pip the companion (TU5, TQ9)

    @Setting(path = "pip.enabled", desc = "Pip, the small red parrot only you can see, leads you during the tutorial and says one short line per step.")
    public boolean pipEnabled = true;

    @Setting(path = "pip.steps", desc = "Steps Pip comes along for (owner 3 Nov 2026: Kindle guides you on the island and sends Pip with you to the Depths). Empty = every step.")
    public List<String> pipSteps = List.of("depths", "mine", "forge");

    @Setting(path = "pip.chat", desc = "Pip's lines also go to chat (off: only his name display shows them, TUT-FIX2 one place per message).")
    public boolean pipChat = false;

    @Setting(path = "pip.line-seconds", desc = "How long Pip's name display shows a step line (seconds), then his short callout.", min = 1, max = 20)
    public int pipLineSeconds = 4;

    @Setting(path = "pip.name-display", desc = "Pip's 2-line name: auto (text display on Java, name tag on Bedrock), text-display or name-tag. Texts: messages pip.name-line1, pip.callouts.")
    public String pipNameDisplay = "auto";

    @Setting(path = "pip.update-ticks", desc = "How often Pip moves (ticks; smaller = smoother flight).", min = 1, max = 10)
    public int pipUpdateTicks = 2;

    @Setting(path = "pip.lead-distance", desc = "How far ahead of you Pip flies along the trail (blocks).", min = 1, max = 12)
    public double pipLeadDistance = 4;

    @Setting(path = "pip.wait-distance", desc = "When you are this far behind, Pip stops and waits for you (blocks).", min = 3, max = 20)
    public double pipWaitDistance = 7;

    @Setting(path = "pip.return-distance", desc = "When you are this far behind, Pip flies back to you (blocks).", min = 5, max = 40)
    public double pipReturnDistance = 14;

    @Setting(path = "pip.bob-height", desc = "How much Pip bobs up and down while flying (blocks).", min = 0, max = 0.5)
    public double pipBobHeight = 0.12;

    @Setting(path = "pip.hop-seconds", desc = "Pip does a little hop this often while perched or idle (seconds). 0 = never.", min = 0, max = 30)
    public int pipHopSeconds = 3;

    @Setting(path = "pip.particles", desc = "Little hearts and notes around Pip (only you see them).")
    public boolean pipParticles = true;

    @Setting(path = "pip.heart-seconds", desc = "While perched on the target, one heart this often (seconds).", min = 1, max = 30)
    public int pipHeartSeconds = 3;

    @Setting(path = "pip.sounds", desc = "Pip's chirps (vanilla sounds, only you hear them).")
    public boolean pipSounds = true;

    @Setting(path = "pip.sound-say", desc = "Sound when Pip says a line or calls you (vanilla sound id).")
    public String pipSoundSay = "entity.parrot.ambient";

    @Setting(path = "pip.sound-happy", desc = "Sound when Pip lands on the target or you finish a step (vanilla sound id).")
    public String pipSoundHappy = "entity.allay.item_given";

    @Setting(path = "pip.sound-volume", desc = "Volume of Pip's sounds.", min = 0, max = 2)
    public double pipSoundVolume = 0.6;

    @Setting(path = "pip.perch-offsets", desc = "Where Pip lands per step, relative to the target, as 'step: dx,dy,dz' (above an NPC's name, in front of the portal). Other steps: on the target.")
    public List<String> pipPerchOffsets = List.of("portal: 0,1.2,2.5", "forge: 0,3.0,0", "help: 0,3.0,0");

    @Setting(path = "pip.variant", desc = "Parrot color: red, blue, green, cyan, gray.")
    public String pipVariant = "red";

    @Setting(path = "pip.side", desc = "How far from you Pip flies (blocks).", min = 0.5, max = 5)
    public double pipSide = 1.6;

    @Setting(path = "pip.height", desc = "How high above your feet Pip flies (blocks).", min = 0.5, max = 4)
    public double pipHeight = 2.0;

    @Setting(path = "pip.perch-range", desc = "Within this distance of the step's target Pip lands on it (blocks).", min = 1, max = 16)
    public double pipPerchRange = 6;

    @Setting(path = "pip.step", desc = "How far Pip flies per 5 ticks (blocks; split over pip.update-ticks); farther than pip.snap he jumps.", min = 0.2, max = 5)
    public double pipStep = 1.8;

    @Setting(path = "pip.snap", desc = "Pip jumps straight to you from this distance (blocks).", min = 4, max = 64)
    public double pipSnap = 16;

    // ------------------------------------------------------------------ NPCs (TU5, TQ27)

    @Setting(path = "guide-npc.enabled", desc = "Clicking the spawn Guide NPC opens /tutorial instead of its help (TQ27; sneak-click still shows the help).")
    public boolean guideNpc = true;

    @Setting(path = "guide-npc.roles", desc = "NPC roles that open /tutorial.")
    public List<String> guideRoles = List.of("guide");

    @Setting(path = "npc-lines.enabled", desc = "During the tutorial, NPCs say a short line for your step when clicked (messages npc-lines.<step>.<role>).")
    public boolean npcLines = true;

    // ------------------------------------------------------------------ optional steps (TU6, TQ8, TQ26)

    @Setting(path = "optional.enabled", desc = "Optional steps O1-O9: count them, show their tips and list them in /tutorial.")
    public boolean optionalEnabled = true;

    @Setting(path = "optional.reward-crystals", desc = "Crystals for each optional step, once per account (TQ8; a line can set reward=<n>).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public long optionalReward = 5;

    @Setting(path = "optional.during-core", desc = "Optional steps also count while the core tutorial runs (off = only after it is finished).")
    public boolean optionalDuringCore = false;

    @Setting(path = "optional.steps", desc = "Optional steps as '<id>: <kind> <args> [key=value]'. Kinds: menu <ids>, npc <roles>, break <block> count=<n> [where=island], visit <targets> [radius=<r>], near <target> [radius=<r>]. Keys: reward, tip (none, any, island, spawn, depths, near), goto (Show me teleport), camera (Show me camera), if-command, enabled. Texts: messages optional.<id>.", level = Setting.Level.OWNER)
    public List<String> optionalSteps = List.of(
            "generator: break cobblestone count=5 where=island tip=island",
            "spawn_tour: visit npc:shop npc:sell npc:bank npc:ah radius=5 tip=spawn goto=spawn:0.5,99,50 camera=market",
            "settings: menu settings tip=any",
            "skills: menu skills tip=any if-command=skills",
            "team: menu members tip=island",
            "upgrades: menu upgrades tip=island enabled=false",
            "enchanter: npc enchanter tip=near goto=npc:enchanter if-command=enchanter",
            "wardrobe: menu wardrobe tip=any if-command=wardrobe",
            "warzone: near spawn:-21.5,102,-93.5 radius=4 tip=near goto=spawn:-21.5,102.5,-90.5");

    @Setting(path = "optional.tips", desc = "Offer an optional step with a chat tip the first time you reach its place (TQ26; players turn tips off in /settings).")
    public boolean optionalTips = true;

    @Setting(path = "optional.tip-cooldown-minutes", desc = "At most one tip per this many minutes (also the wait after finishing or joining).", min = 0, max = 240)
    public int tipCooldownMinutes = 10;

    @Setting(path = "optional.tip-radius", desc = "'tip=near' shows within this distance of the step's place (blocks).", min = 2, max = 64)
    public int tipRadius = 12;

    @Setting(path = "optional.check-ticks", desc = "How often places (visit, near, tips) are checked (ticks).", min = 10, max = 200)
    public int optionalCheckTicks = 20;

    @Setting(path = "optional.spawn-world", desc = "World where 'tip=spawn' tips show.")
    public String spawnWorld = "spawn";

    // ------------------------------------------------------------------ analytics (TU7, TQ24)

    @Setting(path = "analytics.keep-days", desc = "Tutorial events (funnel, drop-off) are kept this many days, then deleted (checked at start and daily).", min = 7, max = 3650)
    public int keepDays = 180;

    @Setting(path = "analytics.drop-after-hours", desc = "A player still on a step with no tutorial event for this long counts as stopped there (drop-off).", min = 1, max = 720)
    public int dropAfterHours = 24;

    @Setting(path = "analytics.cache-seconds", desc = "The stats menu reuses a result this long.", min = 5, max = 3600)
    public int statsCacheSeconds = 60;

    @Setting(path = "analytics.max-rows", desc = "Most events read for one stats view (protects the database).", min = 1000, max = 2_000_000)
    public int statsMaxRows = 250_000;

    @Override
    public int version() {
        return 6;
    }

    /** Old camera lines (v1/v2) that v3 replaces while the owner has not edited them. */
    static final List<String> OLD_SPAWN = List.of(
            "spawn:0.5,107,8 spawn:0.5,111,-22 look=spawn:0.5,103,-50 ticks=40 text=one",
            "spawn:0.5,111,-22 spawn:0.5,113,-62 look=spawn:0.5,104,-97 ticks=40 text=two",
            "spawn:0.5,113,-62 spawn:5.5,108,-84 look=spawn:0.5,104,-97 ticks=40 text=three");
    static final List<String> OLD_ISLAND = List.of(
            "@home:12,7,0 @home:0,7,12 look=@home:0,1,0 ticks=30 text=one",
            "@home:0,7,12 @home:-12,7,0 look=@home:0,1,0 ticks=30",
            "@home:-12,7,0 @home:0,7,-12 look=@home:0,1,0 ticks=30 text=two",
            "@home:0,7,-12 @home:6,4,-4 look=@home:0,1,0 ticks=30");

    /** The v3/v4 camera lines that v5 replaces while the owner has not edited them. */
    static final List<String> V3_SPAWN = List.of(
            "spawn:0.5,121,12 spawn:0.5,124,4 look=spawn:0.5,115,-23 ticks=50 text=one",
            "spawn:0.5,178,-70 spawn:0.5,150,-80 look=spawn:0.5,104,-96 ticks=50 text=three");
    static final List<String> V3_ISLAND = List.of(
            "@home:16,10,0 @home:0,10,16 look=@home:0,1,0 ticks=50 text=one",
            "@home:0,10,16 @home:-12,10,8 look=@home:0,1,0 ticks=50 text=two");
    static final List<String> V1_DEPTHS = List.of(
            "@zone:0,14,-14 @zone:10,10,4 look=@zone:0,0,0 ticks=50 text=one",
            "@zone:10,10,4 @zone:2,4,2 look=@zone:0,0,0 ticks=50 text=two");

    /**
     * v2 (GUI-BUGS): the menu step completes when /menu opens (it used to need the island menu). v3 (TUT-FIX): shorter
     * camera lines that stay out of the spawn builds and the island, a longer camera start delay. v4 (A3-R1-02): the
     * boss bar moves from red to yellow. v5 (TUT-FIX2, owner 31 Oct "tutorial has not been fixed"): interior portal
     * shot, island and Depths cameras that clear the builds, help step only for help NPCs, Pip off chat and faster,
     * a quieter trail and target marker. v6 (owner 3 Nov 2026): own island first, Kindle guides, Pip only off the
     * island, no tools in the chest, a shorter flow. Owner-edited values are kept.
     */
    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        TutorialConfig d = new TutorialConfig(); // code defaults, never the loaded values
        return java.util.Map.of(
                2, java.util.Map.of("steps.menu.menu-ids", ValueChange.of(List.of("island"), List.of("hub"))),
                3, java.util.Map.of(
                        "cameras.spawn", ValueChange.of(OLD_SPAWN, d.cameraSpawn),
                        "cameras.island", ValueChange.of(OLD_ISLAND, d.cameraIsland),
                        "cameras.start-delay-ticks", ValueChange.of(3, d.cameraStartDelayTicks)),
                4, java.util.Map.of("guide.bossbar-color", ValueChange.of("red", "yellow")),
                5, java.util.Map.of(
                        "cameras.spawn", ValueChange.of(V3_SPAWN, d.cameraSpawn),
                        "cameras.island", ValueChange.of(V3_ISLAND, d.cameraIsland),
                        "cameras.depths", ValueChange.of(V1_DEPTHS, d.cameraDepths),
                        "steps.help.npc-roles", ValueChange.of(List.of(), d.helpRoles),
                        "pip.chat", ValueChange.of(true, false),
                        "pip.step", ValueChange.of(1.2, d.pipStep),
                        "guide.trail.spacing", ValueChange.of(2.0, d.trailSpacing),
                        "guide.column.height", ValueChange.of(6, d.columnHeight),
                        "guide.hologram-height", ValueChange.of(1.6, d.hologramHeight)),
                // v6 (owner 3 Nov 2026): start on your own island, Kindle guides, Pip only off the island, faster.
                6, java.util.Map.of(
                        "flow.steps", ValueChange.of(STEPS_V1, d.steps),
                        "flow.version", ValueChange.of(1, d.flowVersion),
                        "steps.mine.count", ValueChange.of(15, d.mineCount),
                        "steps.forge.menu-ids", ValueChange.of(List.of("forge"), d.forgeMenuIds)));
    }

    @Override
    public String fileName() {
        return "tutorial.yml";
    }

    @Override
    public String header() {
        return "Join tutorial (docs/plans/TUTORIAL.md): steps, feature locks, guides, reward and the starter chest.\n"
                + "Texts: messages/tutorial.yml. Reload: /skyblock reload tutorial";
    }
}
