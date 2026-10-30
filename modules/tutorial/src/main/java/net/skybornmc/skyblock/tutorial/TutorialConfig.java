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
    public List<String> steps = List.of("portal", "arrive", "chest", "crop", "menu", "depths", "mine", "sell", "forge", "help", "bank");

    @Setting(path = "flow.version", desc = "Raise after changing flow.steps; stored with each player's progress (finished players stay finished).", min = 1, max = 1000)
    public int flowVersion = 1;

    @Setting(path = "flow.early-steps-count", desc = "Steps done before they are reached count and are skipped later (TQ21).")
    public boolean earlySteps = true;

    // ------------------------------------------------------------------ locks

    @Setting(path = "gates", desc = "Feature locks (TQ4) as 'feature: step'. The feature opens when that step and every step before it is done; 'finish' = when the tutorial is finished. Features: island_team, depths, auction, pay, withdraw, trade, warzone, profile_create, outposts, events. A feature not listed is never locked.", level = Setting.Level.OWNER)
    public List<String> gates = List.of("island_team: crop", "depths: menu", "auction: sell", "pay: bank", "withdraw: bank",
            "trade: finish", "warzone: finish", "profile_create: finish", "outposts: finish", "events: finish");

    @Setting(path = "locked.chat-cooldown-seconds", desc = "The 'Unlocks at tutorial step N' line goes to chat at most this often (the action bar shows it every time).", min = 0, max = 300)
    public int lockedChatCooldownSeconds = 10;

    // ------------------------------------------------------------------ steps

    @Setting(path = "steps.arrive.seconds", desc = "Length of the arrival step (title on the island; the camera comes in TU3).", min = 1, max = 30)
    public int arriveSeconds = 6;

    @Setting(path = "steps.chest.need-tool", desc = "The chest step is done when the starter chest was opened and this tool kind is in the inventory (pickaxe, axe, hoe, sword, rod; empty = opening is enough).")
    public String chestTool = "pickaxe";

    @Setting(path = "steps.crop.count", desc = "Crops to plant on your island for the first-crop step.", min = 1, max = 64)
    public int cropCount = 3;

    @Setting(path = "steps.crop.blocks", desc = "Crop blocks that count (block ids).")
    public List<String> cropBlocks = List.of("wheat");

    @Setting(path = "steps.menu.menu-ids", desc = "Menus whose opening completes the menu step (island = the island menu, from /menu or /is).")
    public List<String> menuIds = List.of("island");

    @Setting(path = "steps.depths.zone", desc = "Depths zone the 'go to the Depths' step waits for.")
    public String depthsZone = "mine_t01";

    @Setting(path = "steps.mine.count", desc = "Mining XP gains in the Depths for the mine step (one per block, TQ29).", min = 1, max = 500)
    public int mineCount = 15;

    @Setting(path = "steps.sell.sources", desc = "Sale sources that count for the sell step (satchel, hand, all, gui...); empty = any sale.")
    public List<String> sellSources = List.of();

    @Setting(path = "steps.forge.menu-ids", desc = "Menus whose opening completes the Forge step.")
    public List<String> forgeMenuIds = List.of("forge");

    @Setting(path = "steps.help.npc-roles", desc = "NPC roles whose click completes the help step; empty = any NPC.")
    public List<String> helpRoles = List.of();

    @Setting(path = "steps.bank.count-island-bank", desc = "Also count a deposit into the island bank (off = only the personal bank).")
    public boolean bankCountsIsland = false;

    @Setting(path = "steps.stuck.skip-after", desc = "After this many 'I'm stuck' presses on one step the player may skip it (only steps in steps.stuck.skippable).", min = 1, max = 20)
    public int stuckSkipAfter = 3;

    @Setting(path = "steps.stuck.skippable", desc = "Steps a stuck player may skip (never sell or bank: they open money features).")
    public List<String> stuckSkippable = List.of("arrive", "depths", "help");

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

    @Setting(path = "trail.nodes", desc = "Path of the flame trail for the portal step, as 'world:x,y,z' points (spawn dais to the Island portal, TQ12).")
    public List<String> trailNodes = List.of("spawn:0.5,100,0.5", "spawn:0,100,-13", "spawn:0,100,-50", "spawn:0,102,-74", "spawn:0,103,-95");

    // ------------------------------------------------------------------ guides

    @Setting(path = "guide.update-ticks", desc = "How often the boss bar compass and action bar update (ticks).", min = 2, max = 40)
    public int updateTicks = 5;

    @Setting(path = "guide.bossbar", desc = "Show the boss bar compass.")
    public boolean bossbar = true;

    @Setting(path = "guide.bossbar-color", desc = "Boss bar color: pink, blue, red, green, yellow, purple, white.")
    public String bossbarColor = "red";

    @Setting(path = "guide.compass.ahead-degrees", desc = "Within this angle the target counts as straight ahead.", min = 5, max = 90)
    public int aheadDegrees = 30;

    @Setting(path = "guide.compass.behind-degrees", desc = "From this angle the target counts as behind you.", min = 90, max = 180)
    public int behindDegrees = 135;

    @Setting(path = "guide.particles", desc = "Show the per-player flame trail, flame column and dust markers.")
    public boolean particles = true;

    @Setting(path = "guide.particle-ticks", desc = "How often particles are drawn (ticks).", min = 2, max = 40)
    public int particleTicks = 10;

    @Setting(path = "guide.trail.spacing", desc = "Blocks between two trail flames.", min = 1, max = 8)
    public double trailSpacing = 2.0;

    @Setting(path = "guide.trail.ahead", desc = "How far ahead of the player the trail is drawn (blocks).", min = 4, max = 64)
    public double trailAhead = 20.0;

    @Setting(path = "guide.column.height", desc = "Height of the flame column over the target (blocks).", min = 1, max = 20)
    public int columnHeight = 6;

    @Setting(path = "guide.markers.crop-radius", desc = "Radius around your island home searched for farm spots to mark (blocks).", min = 2, max = 16)
    public int cropRadius = 6;

    @Setting(path = "guide.markers.mine-radius", desc = "Radius around you searched for ore to mark in the mine step.", min = 2, max = 10)
    public int mineRadius = 5;

    @Setting(path = "guide.markers.mine-count", desc = "How many nearby blocks are marked in the mine step.", min = 1, max = 20)
    public int mineMarkers = 5;

    @Setting(path = "guide.markers.mine-blocks", desc = "Block ids marked in the mine step ('*_ore' = every ore).")
    public List<String> mineBlocks = List.of("*_ore", "stone", "cobblestone", "deepslate");

    @Setting(path = "guide.holograms", desc = "Show per-player holograms over targets (starter chest, NPCs).")
    public boolean holograms = true;

    @Setting(path = "guide.holograms-bedrock", desc = "Hologram for Bedrock viewers: text-display or armor-stand (a name tag, TQ32).")
    public String hologramsBedrock = "armor-stand";

    @Setting(path = "guide.hologram-height", desc = "Height of a hologram over its target (blocks).", min = 0.5, max = 5)
    public double hologramHeight = 1.6;

    @Setting(path = "guide.hologram-range", desc = "Holograms show only within this distance (blocks).", min = 4, max = 128)
    public int hologramRange = 48;

    // ------------------------------------------------------------------ resume, idle

    @Setting(path = "rejoin.teleport", desc = "On rejoin mid-tutorial, teleport back to the step's place after rejoin.delay-ticks (sneak to stay, TQ20).")
    public boolean rejoinTeleport = true;

    @Setting(path = "rejoin.delay-ticks", desc = "Wait before the rejoin teleport (ticks).", min = 0, max = 400)
    public int rejoinDelayTicks = 40;

    @Setting(path = "idle.reminder-minutes", desc = "One reminder (title + sound) after this many idle minutes on a step. 0 = off.", min = 0, max = 120)
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
