package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the crates module ({@code crates-settings.yml}). The crates and reward tables live in
 * {@code crates.yml}; texts in {@code messages/crates.yml}. Every number is a default the owner can change
 * (PHASE-10-REWARDS.md M10.1).
 */
public final class CratesSettings extends ModuleConfig {

    @Setting(path = "open.bulk-amount", desc = "Opens of the 'Open 10' button and shift-click (PHASE-10: 10).", min = 2, max = 64)
    public int bulkAmount = 10;

    @Setting(path = "open.max-per-click", desc = "Most opens one click of 'Open all' does (PHASE-10: up to 64 per click).",
            min = 1, max = 256)
    public int maxPerClick = 64;

    @Setting(path = "open.summary-lines", desc = "Reward lines in the chat summary of a bulk open (the rest is counted in "
            + "one 'and N more' line; the summary screen shows everything).", min = 1, max = 30)
    public int summaryLines = 8;

    @Setting(path = "open.retries", desc = "How often an open is tried again when the keys or pity changed while it ran "
            + "(another open, a key arriving). Nothing is taken until it succeeds.", min = 0, max = 5)
    public int retries = 2;

    @Setting(path = "open.save-player-data", desc = "Save the player's data right after reward items are handed over, so a "
            + "crash can't roll the inventory back to before the open (SECFIX1 PlayerSaves).")
    public boolean savePlayerData = true;

    @Setting(path = "open.sound", desc = "Sound (sounds.yml key) after an open.")
    public String openSound = "success";

    @Setting(path = "open.top-sound", desc = "Sound (sounds.yml key) when an open wins a top-band reward.")
    public String topSound = "unlock";

    @Setting(path = "keys.bound-sources", desc = "Key sources whose keys are BOUND (never withdrawn or traded; REVIEW3 "
            + "R3-2 and PHASE-10 RX6): votes, the AFK pool, the daily calendar, playtime, rank claims, the tutorial. A source "
            + "in neither list is treated as bound (safest).", level = Setting.Level.OWNER)
    public List<String> boundSources = List.of("afk_pool", "vote", "vote_streak", "vote_party", "daily", "playtime", "rank",
            "tutorial");

    @Setting(path = "keys.tradeable-sources", desc = "Key sources whose keys are TRADEABLE (withdraw as items in M10.4): "
            + "quests, the battle pass, events, weekly top, key fragments, crate chains.", level = Setting.Level.OWNER)
    public List<String> tradeableSources = List.of("quest", "quest_daily", "quest_weekly", "pass", "event", "weekly_top",
            "fragment", "koth");

    @Setting(path = "fragments.enabled", desc = "R3-18: the Key Fragments button in /crates turns fragments (Depths tier 11+ "
            + "drops) into keys.")
    public boolean fragmentsEnabled = true;

    @Setting(path = "fragments.per-key", desc = "R3-18: fragments for one key (owner: 3).", min = 1, max = 64,
            level = Setting.Level.OWNER)
    public int fragmentsPerKey = 3;

    @Setting(path = "fragments.crate", desc = "R3-18: crate whose key the fragments make (owner: phoenix).", level = Setting.Level.OWNER)
    public String fragmentsCrate = "phoenix";

    @Setting(path = "fragments.item", desc = "Custom item id of a fragment (the Depths drop item).")
    public String fragmentsItem = "depths_key_fragment";

    @Setting(path = "fragments.max-keys-per-click", desc = "Most keys one 'Combine all' click makes.", min = 1, max = 1_000)
    public int fragmentsMaxKeysPerClick = 64;

    @Setting(path = "key-items.enabled", desc = "M10.4: tradeable keys can be withdrawn in a crate's preview as key items "
            + "(unique, dupe-checked, tradeable on /trade and the AH). USE a key item to put it back on your active profile. "
            + "Bound keys can never be withdrawn. Off = no withdraw buttons; key items already out can still be used.")
    public boolean keyItemsEnabled = true;

    @Setting(path = "key-items.material", desc = "Vanilla item of a key item (the resource pack can retexture it later). Read at server start.",
            restart = true)
    public String keyItemMaterial = "TRIPWIRE_HOOK";

    @Setting(path = "key-items.glint", desc = "Key items shine (enchantment glint). Read at server start.", restart = true)
    public boolean keyItemGlint = true;

    @Setting(path = "key-items.max-per-withdraw", desc = "Most key items one withdraw makes ('Withdraw an amount'; overflow "
            + "goes to /box).", min = 1, max = 256)
    public int keyItemMaxPerWithdraw = 64;

    @Setting(path = "tier.stage-skills", desc = "Skills whose highest level decides the opener's tier for money and crystal "
            + "pouches (the same rule the AFK pool uses).")
    public List<String> stageSkills = List.of("mining", "foraging", "combat");

    @Setting(path = "tier.tier-levels", desc = "Skill level that unlocks tiers 1-12 (the Depths unlock levels, "
            + "ECONOMY_BALANCE §3.4).", level = Setting.Level.OWNER)
    public List<String> tierLevels = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58", "66");

    @Setting(path = "pouch.ref-per-minute", desc = "Reference income per minute (REF(t)/60, ECONOMY_BALANCE §1.2) for tiers "
            + "1-12; a Money Pouch of N minutes pays N x this at the opener's tier. The last value is used above.",
            level = Setting.Level.OWNER)
    public List<String> refPerMinute = List.of("827", "1467", "2560", "4507", "7920", "13867", "24347", "42667", "122133",
            "349333", "1000000", "2853333");

    @Setting(path = "pouch.crystal-growth", desc = "Crystal Pouch growth per tier: base x growth^(tier-1) (crystals grow "
            + "x1.30 per tier, ECONOMY_BALANCE §9.1). Also scales the crystal value of coming-soon rewards.",
            min = 1, max = 3, level = Setting.Level.OWNER)
    public double crystalGrowth = 1.3;

    @Setting(path = "pity.season-id", desc = "Current season number for pity counters: raise it at the season reset and "
            + "every pity counter starts at 0 again (PHASE-10: counters reset at the season reset).", min = 0,
            level = Setting.Level.OWNER)
    public int seasonId = 1;

    @Setting(path = "physical.enabled", desc = "Crate blocks on the crate yard pedestals (build points 'crates', M10.3): "
            + "the crate is placed as a real vanilla block with a hologram above it. Click opens 1 (with the in-world "
            + "animation), sneak-click shows the rewards; no key = the rewards + how to get keys.")
    public boolean physicalEnabled = true;

    @Setting(path = "physical.points", desc = "Crate yard build point id -> crate id ('point:crate'). The yard has 6 "
            + "pedestals; Conqueror stays /crates-only until the builder adds a 7th pedestal: then add its line here, "
            + "e.g. 'conqueror:conqueror'.")
    public List<String> physicalPoints = List.of("common:ember", "uncommon:cinder", "rare:blaze", "epic:inferno",
            "legendary:phoenix", "monthly:eclipse");

    @Setting(path = "physical.y-offsets", desc = "Blocks above a crate build point that count as the crate when clicked "
            + "(0 = the point itself, where the crate block is placed; 1 = the block on top of it).")
    public List<String> physicalOffsets = List.of("0", "1");

    @Setting(path = "physical.blocks", desc = "Vanilla block of each crate on its pedestal ('crate:block data', RX11). A "
            + "crate without a line uses its crates.yml icon. The resource pack retextures them later.")
    public List<String> physicalBlocks = List.of("ember:barrel[facing=up]", "cinder:chest[facing=west]",
            "blaze:copper_chest[facing=west]", "inferno:ender_chest[facing=west]", "phoenix:vault[facing=west]",
            "eclipse:vault[facing=west,ominous=true]", "conqueror:trial_spawner[ominous=true]");

    @Setting(path = "physical.replace", desc = "Blocks the crate block may replace on a pedestal. Anything else there is "
            + "left alone and reported once in the log (never breaks a build).")
    public List<String> physicalReplace = List.of("air", "cave_air", "void_air", "light", "barrel", "chest", "trapped_chest",
            "copper_chest", "ender_chest", "vault", "trial_spawner");

    @Setting(path = "physical.click-cooldown-ms", desc = "Clicks on a crate block within this time after the last one are "
            + "ignored (holding the mouse button sends many clicks).", min = 0, max = 5_000)
    public int clickCooldownMs = 400;

    @Setting(path = "physical.place-per-tick", desc = "PERF10: most crate pedestals (crate block + hologram) set up per tick "
            + "after a start, reload or chunk load (at least one per tick).", min = 1, max = 64)
    public int placePerTick = 2;

    @Setting(path = "physical.place-budget-ms", desc = "PERF10: stop setting up crate pedestals for the current tick after this "
            + "many milliseconds (at least one per tick).", min = 1, max = 50)
    public int placeBudgetMs = 2;

    @Setting(path = "hologram.enabled", desc = "Hologram above each crate block: its name in its rarity color and how to "
            + "open it.")
    public boolean hologramEnabled = true;

    @Setting(path = "hologram.height", desc = "Height of the hologram's bottom above the top of the crate block (blocks).",
            min = 0, max = 3)
    public double hologramHeight = 0.35;

    @Setting(path = "hologram.view-range", desc = "How far the crate holograms and the reward float are visible "
            + "(1.0 = 64 blocks).", min = 0.1, max = 4.0)
    public double hologramViewRange = 0.5;

    @Setting(path = "hologram.background-alpha", desc = "Darkness of the hologram background (0 = none, 255 = black).",
            min = 0, max = 255)
    public int hologramBackgroundAlpha = 64;

    @Setting(path = "displays.enabled", desc = "Resource pack viewers see each crate as a custom model (a body and a lid that "
            + "swings open) on the pedestal; everyone else, and Bedrock, keeps the vanilla block. Needs the pack module.")
    public boolean displaysEnabled = true;

    @Setting(path = "displays.scale", desc = "Size of the crate model (1.0 = one block; a little more hides the block behind it).",
            min = 0.5, max = 2.0)
    public double displayScale = 1.05;

    @Setting(path = "displays.y-offset", desc = "Lift of the model above the pedestal block's floor (blocks), so it does not "
            + "flicker against the floor.", min = -0.5, max = 1.0)
    public double displayYOffset = 0.02;

    @Setting(path = "displays.view-range", desc = "How far the crate models are visible (1.0 = 64 blocks).", min = 0.1, max = 4.0)
    public double displayViewRange = 0.8;

    @Setting(path = "displays.default-facing", desc = "Side the crate front faces (north, east, south or west) when the crate's "
            + "block has no horizontal facing and displays.facings has no line for it. Point it at the walkway.")
    public String displayDefaultFacing = "west";

    @Setting(path = "displays.facings", desc = "Side the front of a crate faces ('crate:north|east|south|west'). A crate "
            + "without a line faces like its physical.blocks block, else displays.default-facing.")
    public List<String> displayFacings = List.of();

    @Setting(path = "displays.hide-vanilla-block", desc = "Pack viewers get the crate's block replaced by an invisible one "
            + "(displays.hide-block-as) on their screen, in case the vanilla chest shows through the model. Clicks still work. "
            + "Off by default.")
    public boolean displayHideBlock = false;

    @Setting(path = "displays.hide-block-as", desc = "Block pack viewers see instead of the crate block when "
            + "displays.hide-vanilla-block is on (BARRIER is invisible and clickable).")
    public String displayHideBlockAs = "BARRIER";

    @Setting(path = "displays.brightness", desc = "Light level (0-15) the models are drawn with; -1 = the world's light.",
            min = -1, max = 15)
    public int displayBrightness = -1;

    @Setting(path = "lid.angle", desc = "How far the lid swings open (degrees).", min = 20, max = 150)
    public double lidAngle = 100;

    @Setting(path = "lid.open-ticks", desc = "Ticks the lid takes to swing open.", min = 2, max = 40)
    public int lidOpenTicks = 8;

    @Setting(path = "lid.close-ticks", desc = "Ticks the lid takes to close again.", min = 2, max = 40)
    public int lidCloseTicks = 10;

    @Setting(path = "lid.steps", desc = "Key frames of a swing (more = a truer arc, a few more packets).", min = 1, max = 10)
    public int lidSteps = 4;

    @Setting(path = "lid.overshoot", desc = "How far the lid swings past open and settles back (0 = none, 1.7 = about 10%).",
            min = 0, max = 4)
    public double lidOvershoot = 1.2;

    @Setting(path = "lid.shake-degrees", desc = "The crate shakes by this much before the lid opens (0 = no shake).", min = 0,
            max = 15)
    public double lidShakeDegrees = 2.5;

    @Setting(path = "lid.shake-ticks", desc = "Ticks of the shake before the lid opens.", min = 3, max = 20)
    public int lidShakeTicks = 6;

    @Setting(path = "lid.open-sound", desc = "Sound when the lid opens ('namespace:sound volume pitch'; empty = none).")
    public String lidOpenSound = "minecraft:block.chest.open 0.6 0.9";

    @Setting(path = "lid.close-sound", desc = "Sound when the lid closes ('namespace:sound volume pitch'; empty = none).")
    public String lidCloseSound = "minecraft:block.chest.close 0.5 0.9";

    @Setting(path = "ambient.enabled", desc = "One light task for the whole yard: rarity particles above the rarer crates and the "
            + "approach chime, only while a player is near. Also keeps the crate models in step with the pack status.")
    public boolean ambientEnabled = true;

    @Setting(path = "ambient.interval-ticks", desc = "Ticks between two ambient checks.", min = 5, max = 100)
    public int ambientInterval = 15;

    @Setting(path = "ambient.min-rarity", desc = "Rarest-or-above rarity that gets particles (common, uncommon, rare, epic, "
            + "legendary, mythic, special). Epic is Inferno.")
    public String ambientMinRarity = "epic";

    @Setting(path = "ambient.particle-range", desc = "Particles only appear while a player is within this many blocks.",
            min = 4, max = 48)
    public double ambientParticleRange = 16;

    @Setting(path = "ambient.particles", desc = "Dust particles per crate per check (0 = none).", min = 0, max = 10)
    public int ambientParticles = 2;

    @Setting(path = "ambient.particle-size", desc = "Size of the dust particles.", min = 0.3, max = 2)
    public double ambientParticleSize = 0.9;

    @Setting(path = "approach.enabled", desc = "A quiet chime for a player who walks up to a crate.")
    public boolean approachEnabled = true;

    @Setting(path = "approach.range", desc = "Blocks from a crate that count as coming close.", min = 1, max = 24)
    public double approachRange = 6;

    @Setting(path = "approach.reset-range", desc = "The player must go farther than this (blocks) before the chime can play "
            + "again for that crate.", min = 2, max = 32)
    public double approachResetRange = 8;

    @Setting(path = "approach.cooldown-seconds", desc = "Least seconds between two chimes of one crate for one player.", min = 0,
            max = 3_600)
    public int approachCooldownSeconds = 30;

    @Setting(path = "approach.pack-sound", desc = "Chime for pack viewers ('namespace:sound volume pitch').")
    public String approachPackSound = "phoenix:crate.chime 0.35 1.0";

    @Setting(path = "approach.fallback-sound", desc = "Chime for everyone else (no pack, Bedrock).")
    public String approachFallbackSound = "minecraft:block.amethyst_block.chime 0.35 1.3";

    @Setting(path = "key-board.enabled", desc = "Write the crate yard key board (build hologram 'key_board'): how to get "
            + "keys and the top-reward chance of every crate (the npcs module places it).")
    public boolean keyBoardEnabled = true;

    @Setting(path = "key-board.hologram", desc = "Build hologram id of the key board.")
    public String keyBoardHologram = "key_board";

    @Setting(path = "spinner.enabled", desc = "B40 spinner for 'Open 1' in the menus: rewards scroll through slots 10-16 and "
            + "slow down onto slot 13. The reward is decided and saved before it starts; click to skip.")
    public boolean spinnerEnabled = true;

    @Setting(path = "spinner.duration-ticks", desc = "Length of the spin (20 ticks = 1 s; plan: ~3 s).", min = 10, max = 200)
    public int spinnerTicks = 60;

    @Setting(path = "spinner.frames", desc = "Steps of the spin on Java (each step moves the row by one slot).", min = 5,
            max = 60)
    public int spinnerFrames = 26;

    @Setting(path = "spinner.bedrock-frames", desc = "Steps of the spin on Bedrock (menu updates; PHASE-10: at most 20 "
            + "including the final screen, so this is capped at " + (SpinnerMath.BEDROCK_MAX_UPDATES - 1) + ").", min = 3,
            max = SpinnerMath.BEDROCK_MAX_UPDATES - 1)
    public int spinnerBedrockFrames = 14;

    @Setting(path = "spinner.tick-sound", desc = "Soft tick each step: 'namespace:sound volume pitch' or a sounds.yml key.")
    public String spinnerTickSound = "minecraft:block.note_block.hat 0.35 1.6";

    @Setting(path = "animation.fast-setting-default", desc = "Default of the /settings switch 'Fast crate opening' (on = "
            + "no spin and no build-up, the result at once).")
    public boolean fastDefault = false;

    @Setting(path = "animation.build-ticks", desc = "In-world build-up at a pedestal before the reward shows (rarity "
            + "particles around the crate).", min = 0, max = 100)
    public int buildTicks = 30;

    @Setting(path = "animation.float-ticks", desc = "How long the reward floats above the crate with its name (plan: 3 s).",
            min = 20, max = 200)
    public int floatTicks = 60;

    @Setting(path = "animation.float-rise", desc = "How high the reward rises above the crate (blocks).", min = 0.2, max = 3)
    public double floatRise = 0.9;

    @Setting(path = "animation.particles", desc = "Rarity particles per step of the build-up (0 = none).", min = 0, max = 20)
    public int particlesPerStep = 4;

    @Setting(path = "animation.burst-particles", desc = "Particles of the burst when the reward appears.", min = 0, max = 60)
    public int burstParticles = 24;

    @Setting(path = "animation.firework-top", desc = "A firework above the crate when a top-band reward is won.")
    public boolean fireworkTop = true;

    @Setting(path = "animation.title", desc = "Also show the result as a title to the opener at a pedestal (Bedrock "
            + "may not show the floating reward).")
    public boolean titleAtPedestal = true;

    @Setting(path = "broadcast.crates", desc = "Crates whose top-band wins are told in global chat (RX12: Inferno and "
            + "higher). All other wins only go to the opener.", level = Setting.Level.OWNER)
    public List<String> broadcastCrates = List.of("inferno", "phoenix", "eclipse", "conqueror");

    @Setting(path = "delivery.join-delay-ticks", desc = "Ticks after joining before the key summary is refreshed (the "
            + "profile module loads the active profile first).", min = 1, max = 1_200)
    public int joinDelayTicks = 40;

    @Setting(path = "admin.max-amount", desc = "Most keys one /crates admin give or take can move (P10-41).", min = 1,
            max = CrateKeysService.MAX_PER_CALL, level = Setting.Level.OWNER)
    public int adminMaxAmount = 1_000;

    @Setting(path = "admin.confirm-above", desc = "A /crates admin give or take of more keys than this asks to confirm first "
            + "(click or type it again with 'confirm').", min = 0, max = CrateKeysService.MAX_PER_CALL, level = Setting.Level.OWNER)
    public int adminConfirmAbove = 64;

    @Setting(path = "admin.confirm-seconds", desc = "Seconds a /crates admin confirmation stays valid (read at server start).",
            min = 5, max = 120, restart = true)
    public int adminConfirmSeconds = 15;

    @Setting(path = "admin.default-tradeable", desc = "Kind of keys /crates admin give adds when the type is left out: false = "
            + "bound (safest: never traded), true = tradeable.", level = Setting.Level.OWNER)
    public boolean adminDefaultTradeable = false;

    @Setting(path = "admin.notify-player", desc = "Tell an online player on that profile when staff give them keys.")
    public boolean adminNotify = true;

    @Override
    public String fileName() {
        return "crates-settings.yml";
    }

    @Override
    public String header() {
        return "Crates settings (PHASE-10 M10.1). Crates and reward tables: crates.yml. Texts: messages/crates.yml.\n"
                + "Reload: /skyblock reload crates";
    }

    /** Whether keys from {@code reason} are bound (anything not listed as tradeable is bound: the safe default). */
    public boolean bound(String reason) {
        String r = reason == null ? "" : reason.trim().toLowerCase(java.util.Locale.ROOT);
        if (boundSources.contains(r)) {
            return true;
        }
        return !tradeableSources.contains(r);
    }
}
