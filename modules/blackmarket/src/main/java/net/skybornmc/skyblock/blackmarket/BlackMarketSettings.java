package net.skybornmc.skyblock.blackmarket;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the Black Market ({@code blackmarket.yml}, PHASE-10-REWARDS.md M10.11). Every number is a default
 * the owner can change; texts and offer names are in {@code messages/blackmarket.yml}. Principle 0 (fun first, less is
 * more): offers whose system is not built yet stay in the list as {@code coming-soon} and are never rolled.
 */
public final class BlackMarketSettings extends ModuleConfig {

    @Setting(path = "enabled", desc = "Black Market on: /blackmarket opens, the Black Market NPC opens it, rotations are announced.")
    public boolean enabled = true;

    // ------------------------------------------------------------------ rotation

    @Setting(path = "rotation.zone", desc = "Time zone of the restock times (P10-35: Europe/Oslo).", level = Setting.Level.OWNER)
    public String zone = "Europe/Oslo";

    @Setting(path = "rotation.times", desc = "Restock times every day, local time (P10-35: 00:00 and 12:00 = every 12 h). "
            + "Every 6 h would be 00:00, 06:00, 12:00, 18:00.", level = Setting.Level.OWNER)
    public List<String> times = List.of("00:00", "12:00");

    @Setting(path = "rotation.headline-slots", desc = "Headline offers per rotation (P10-35: 1).", min = 0, max = 1,
            level = Setting.Level.OWNER)
    public int headlineSlots = 1;

    @Setting(path = "rotation.rare-slots", desc = "Rare offers per rotation (P10-35: 3).", min = 0, max = 7, level = Setting.Level.OWNER)
    public int rareSlots = 3;

    @Setting(path = "rotation.common-slots", desc = "Common offers per rotation (P10-35: 4).", min = 0, max = 7,
            level = Setting.Level.OWNER)
    public int commonSlots = 4;

    @Setting(path = "rotation.headline-stock", desc = "Server-wide stock of the headline per rotation (P10-37: 25). 0 = no shared "
            + "stock.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int headlineStock = 25;

    @Setting(path = "rotation.announce", desc = "Tell everyone online the new headline when the stock rotates (one chat line).")
    public boolean announce = true;

    @Setting(path = "rotation.check-seconds", desc = "How often the rotation clock is checked (seconds).", min = 5, max = 300)
    public int checkSeconds = 15;

    @Setting(path = "season-id", desc = "Current season for 'per season' limits (Phoenix Regalia 1 per account per season) and the "
            + "Island Charter. Raise it at the season reset.", min = 0, max = 10_000, level = Setting.Level.OWNER)
    public int seasonId = 0;

    // ------------------------------------------------------------------ offers

    @Setting(path = "offers", desc = "Offers, one per line: '<id>: slot=<headline|rare|common|fixed> price=<shards> weight=<n> "
            + "reward=<kind>[:<value>] [amount=<n>] [level=<n>] [limit=<n>/<rotation|day|month|season>] [icon=<item>]'. Kinds: "
            + "set-token:<set>, enchant-book:<rarity>, scroll, key:<crate>, spawner:<tier 1-5>, item:<item>, tag:<tag>, coming-soon "
            + "(listed, never rolled). Names and lore: messages/blackmarket.yml offers.<id>.", level = Setting.Level.OWNER)
    public List<String> offers = List.of(
            "scroll: slot=fixed price=8 reward=scroll limit=10/rotation icon=paper",
            "regalia: slot=headline price=400 weight=1 reward=set-token:phoenix limit=1/season",
            "voidforged: slot=headline price=250 weight=2 reward=set-token:voidforged limit=1/rotation",
            "mythic_book: slot=headline price=200 weight=2 reward=enchant-book:mythic limit=1/rotation",
            "coinmaster: slot=rare price=120 weight=2 reward=set-token:coinmaster limit=1/rotation",
            "chunk_collector: slot=rare price=100 weight=2 reward=coming-soon icon=hopper",
            "autosell_chest: slot=rare price=120 weight=2 reward=coming-soon icon=chest",
            "eclipse_key: slot=rare price=75 weight=2 reward=key:eclipse limit=1/month",
            "legendary_book: slot=rare price=60 weight=3 reward=enchant-book:legendary limit=1/rotation",
            "phoenix_key: slot=rare price=50 weight=3 reward=key:phoenix limit=2/rotation",
            "spawner_t5: slot=rare price=40 weight=3 reward=spawner:5 limit=1/rotation",
            "blueprint: slot=common price=40 weight=3 reward=coming-soon icon=map",
            "bm_tag: slot=common price=60 weight=2 reward=tag:black_market limit=1/rotation",
            "elytra: slot=common price=30 weight=3 reward=item:elytra limit=1/rotation",
            "sell_wand: slot=common price=25 weight=3 reward=coming-soon icon=blaze_rod",
            "wardrobe_tome: slot=common price=20 weight=3 reward=coming-soon icon=book");

    @Setting(path = "items.bound", desc = "Items and keys bought here are bound to the buyer's profile (no trade, no auction). "
            + "Safest default; keys are always bound (the 'blackmarket' key source is not tradeable in crates.yml).",
            level = Setting.Level.OWNER)
    public boolean bound = true;

    @Setting(path = "tag.define", desc = "Add the Black Market tag (tag:black_market) to tags-titles.yml once, if it does not exist "
            + "(name in messages/blackmarket.yml tag.display).")
    public boolean defineTag = true;

    // ------------------------------------------------------------------ Island Charter

    @Setting(path = "charter.enabled", desc = "Island Charter in the fixed row: island sizes above the normal maximum, one step at a "
            + "time (owner 157, N2-2).", level = Setting.Level.OWNER)
    public boolean charterEnabled = true;

    @Setting(path = "charter.from-size", desc = "Island size (chunks) the island must reach with normal /is upgrade first (owner "
            + "157: 9 = 9x9; match islands.yml size.normal-max-chunks).", min = 3, max = 31, level = Setting.Level.OWNER)
    public int fromSize = 9;

    @Setting(path = "charter.steps", desc = "Sizes sold here: '<size>: <shards> [gems]' (P10-38: 11x11 150 shards / 250 gems, 13x13 "
            + "300 / 500, 15x15 450 / 750; REVIEW3 F6: gems too). 0 = not sold for that currency. Never above islands.yml "
            + "size.max-chunks.", level = Setting.Level.OWNER)
    public List<String> charterSteps = List.of("11: 150 250", "13: 300 500", "15: 450 750");

    // ------------------------------------------------------------------ NPC and sounds

    @Setting(path = "npc.role", desc = "NPC role whose click opens /blackmarket (the Market Square alley trader; sneak-click keeps "
            + "its help text).")
    public String npcRole = "black_market";

    @Setting(path = "sounds.buy", desc = "Sound (sounds.yml) after a buy (empty = none).")
    public String buySound = "unlock";

    // ------------------------------------------------------------------ delivery

    @Setting(path = "delivery.retry-seconds", desc = "How often waiting hand-outs are tried again (seconds).", min = 10, max = 3_600)
    public int retrySeconds = 60;

    @Setting(path = "delivery.save-player-data", desc = "Save the player's data right after items are handed over, so a crash "
            + "can't take them back while the buy stays paid.")
    public boolean savePlayerData = true;

    // ------------------------------------------------------------------ staff

    @Setting(path = "admin.history-lines", desc = "Buys /blackmarket admin view shows.", min = 1, max = 100)
    public int historyLines = 15;

    @Override
    public String fileName() {
        return "blackmarket.yml";
    }

    @Override
    public String header() {
        return "Black Market (PHASE-10 M10.11, /blackmarket). Texts: messages/blackmarket.yml.\n"
                + "Reload: /skyblock reload blackmarket";
    }
}
