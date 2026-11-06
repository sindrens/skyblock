package net.skybornmc.skyblock.collection;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the collection log ({@code collection.yml}, PHASE-10-REWARDS.md M10.10). Every number is a default
 * the owner can change; texts are in {@code messages/collection.yml}. Only pages with a live source exist (principle 0:
 * fun first, less is more): the four grind areas (farm, ores, fish, mobs and bosses) and the crates.
 */
public final class CollectionSettings extends ModuleConfig {

    @Setting(path = "enabled", desc = "Collection log on: finds are recorded, /collection opens.")
    public boolean enabled = true;

    // ------------------------------------------------------------------ pages

    @Setting(path = "pages.order", desc = "The pages (tabs) in order. Known pages: farm, mine, fish, mobs, crates. Remove one to hide "
            + "it (its finds stay saved). A page with no entries is hidden and the whole log can't be completed while it is empty.",
            level = Setting.Level.OWNER)
    public List<String> pageOrder = PAGES_V2;

    static final List<String> PAGES_V1 = List.of("mine", "forest", "combat", "crates");
    static final List<String> PAGES_V2 = List.of("farm", "mine", "fish", "mobs", "crates");

    @Setting(path = "pages.farm", desc = "Farm page: crops harvested at /farm (vanilla item ids, in unlock order). Each counts "
            + "every harvest.", level = Setting.Level.OWNER)
    public List<String> farm = List.of("wheat", "carrot", "potato", "beetroot", "nether_wart");

    @Setting(path = "pages.mine", desc = "Ores page: drops mined at /mine (vanilla item ids, in unlock order).", level = Setting.Level.OWNER)
    public List<String> mine = List.of("coal", "raw_iron", "raw_gold", "redstone", "lapis_lazuli", "diamond", "emerald");

    @Setting(path = "pages.fish", desc = "Fish page: catches at /fish (vanilla item ids, in water order).", level = Setting.Level.OWNER)
    public List<String> fish = List.of("cod", "salmon", "ink_sac", "tropical_fish", "pufferfish", "prismarine_crystals",
            "glow_ink_sac", "nautilus_shell", "heart_of_the_sea");

    @Setting(path = "pages.mobs", desc = "Mobs page: the /pve ring mobs (vanilla mob ids, ring 1 first). Each mob also gives its "
            + "ring's boss an entry (/pve boss; names in messages/collection.yml bosses.<ring>).", level = Setting.Level.OWNER)
    public List<String> mobs = List.of("zombie", "husk", "skeleton", "spider", "stray", "drowned", "piglin_brute", "blaze",
            "wither_skeleton", "ravager");

    @Setting(path = "pages.crates", desc = "Crates whose rewards are on the Crates page, in order. Eclipse (new rewards every month) "
            + "and Conqueror (KOTH, not built) are left out so the page can be finished.", level = Setting.Level.OWNER)
    public List<String> crates = List.of("ember", "cinder", "blaze", "inferno", "phoenix");

    @Setting(path = "pages.crates-top-only", desc = "Crates page shows only the star (top) rewards of each crate (the rare ones), "
            + "not pouches and boosters. 'Coming soon' rewards are never on it.", level = Setting.Level.OWNER)
    public boolean cratesTopOnly = true;

    // ------------------------------------------------------------------ milestones and rewards

    @Setting(path = "milestones.percents", desc = "Page milestones in percent (PHASE-10: 25/50/75/100). Each one reached is a "
            + "collection milestone for the island level.", level = Setting.Level.OWNER)
    public List<String> percents = List.of("25", "50", "75", "100");

    @Setting(path = "milestones.level-points", desc = "Island level points per page milestone of every island member (PLAN5: 1; "
            + "the 'Collections' source, capped at collections-percent in island-level.yml).", min = 0, max = 100,
            level = Setting.Level.OWNER)
    public double levelPoints = 1;

    @Setting(path = "rewards.page-titles", desc = "A full page gives its title (collection_<page>; names in messages/collection.yml "
            + "titles.*). Titles are account-wide.", level = Setting.Level.OWNER)
    public boolean pageTitles = true;

    @Setting(path = "rewards.page-key-crate", desc = "Crate of the key a full page gives (P10-33: blaze). Empty = no key.",
            level = Setting.Level.OWNER)
    public String pageKeyCrate = "blaze";

    @Setting(path = "rewards.page-keys", desc = "Keys a full page gives (bound to the profile).", min = 0, max = 64,
            level = Setting.Level.OWNER)
    public int pageKeys = 1;

    @Setting(path = "rewards.log-title", desc = "Title for the whole log (P10-33: Keeper of Ashes, mythic). Empty = none.",
            level = Setting.Level.OWNER)
    public String logTitle = "keeper_of_ashes";

    @Setting(path = "rewards.log-key-crate", desc = "Crate of the key the whole log gives (P10-33: phoenix). Empty = no key.",
            level = Setting.Level.OWNER)
    public String logKeyCrate = "phoenix";

    @Setting(path = "rewards.log-keys", desc = "Keys the whole log gives (bound to the profile).", min = 0, max = 64,
            level = Setting.Level.OWNER)
    public int logKeys = 1;

    @Setting(path = "rewards.log-set", desc = "Wardrobe set whose token the whole log gives (SE2 secret set). Empty = none yet (no "
            + "secret set exists today).", level = Setting.Level.OWNER)
    public String logSet = "";

    // ------------------------------------------------------------------ notices

    @Setting(path = "notice.title", desc = "Show a short screen title on a new find and on a milestone.")
    public boolean noticeTitle = true;

    @Setting(path = "notice.chat", desc = "Send a chat line on a new find and on a milestone.")
    public boolean noticeChat = false; // owner 7 Nov 2026: the title says it (no chat line per find)

    @Setting(path = "notice.find-sound", desc = "Sound (sounds.yml) on a new find (empty = none).")
    public String findSound = "notify";

    @Setting(path = "notice.milestone-sound", desc = "Sound (sounds.yml) on a milestone (empty = none).")
    public String milestoneSound = "unlock";

    // ------------------------------------------------------------------ saving

    @Setting(path = "save.seconds", desc = "Finds are saved in batches every this many seconds (milestones are saved at once).",
            min = 1, max = 300)
    public int saveSeconds = 10;

    @Setting(path = "delivery.retry-seconds", desc = "How often waiting reward hand-outs are tried again (seconds).", min = 10,
            max = 3_600)
    public int retrySeconds = 60;

    @Setting(path = "delivery.keep-days", desc = "Days delivered hand-out rows are kept (history stays in /logs).", min = 1, max = 365)
    public int keepDays = 14;

    @Setting(path = "rewards.milestone-crystals", desc = "Crystals every page milestone (25/50/75/100%) gives (batch 9: 5, so a "
            + "page is worth 20). 0 = none.", min = 0, max = 1_000, level = Setting.Level.OWNER)
    public int milestoneCrystals = 5;

    @Override
    public String fileName() {
        return "collection.yml";
    }

    @Override
    public int version() {
        return 3;
    }

    /** v2 (batch 9, 7 Nov 2026, FX-20): the pages of the four grind areas replace the Depths pages. */
    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of("pages.order", ValueChange.of(PAGES_V1, PAGES_V2),
                "pages.mine", ValueChange.of(MINE_V1, new CollectionSettings().mine)),
                3, java.util.Map.of("notice.chat", ValueChange.of(true, false)));
    }

    /** The Depths mine page (before v2). */
    static final List<String> MINE_V1 = List.of("1: depths_coal", "2: depths_copper", "3: depths_iron", "4: depths_gold",
            "5: depths_redstone", "6: depths_lapis", "7: depths_diamond", "8: depths_emerald", "9: depths_amethyst", "10: depths_quartz",
            "11: depths_netherite", "12: depths_voidstone");

    @Override
    public java.util.Map<String, String> removedKeys() {
        return java.util.Map.of("pages.forest", "the Depths are gone (batch 9): no forest page",
                "pages.combat", "the Depths are gone (batch 9): the mobs page counts /pve kills");
    }

    @Override
    public String header() {
        return "Collection log (PHASE-10 M10.10, /collection). Texts: messages/collection.yml.\n"
                + "Reload: /skyblock reload collection";
    }
}
