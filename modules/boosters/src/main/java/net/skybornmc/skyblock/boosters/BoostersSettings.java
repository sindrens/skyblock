package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Typed settings of the boosters module ({@code boosters.yml}). Texts live in {@code messages/boosters.yml}. Every
 * number is a default the owner can change (PHASE-10-REWARDS.md M10.2).
 */
public final class BoostersSettings extends ModuleConfig {

    // ------------------------------------------------------------------ personal boosters

    @Setting(path = "personal.max-minutes", desc = "Most booster time one type can hold at once (RX39: 3 h). Starting an item "
            + "that would pass it is refused and the item is kept.", min = 10, max = 1_440, level = Setting.Level.OWNER)
    public int personalMaxMinutes = 180;

    @Setting(path = "personal.min-percent", desc = "Lowest strength a personal booster item may have (percent).", min = 1, max = 300,
            level = Setting.Level.OWNER)
    public int personalMinPercent = 5;

    @Setting(path = "personal.max-percent", desc = "Highest strength a personal booster item may have (PHASE-10: 25 / 50 / 100%). "
            + "The family caps still apply on top (sell: +300% in total, O11).", min = 1, max = 300, level = Setting.Level.OWNER)
    public int personalMaxPercent = 100;

    @Setting(path = "personal.max-item-minutes", desc = "Longest time one personal booster item may give (PHASE-10: 10-60 min).",
            min = 1, max = 1_440, level = Setting.Level.OWNER)
    public int personalMaxItemMinutes = 120;

    @Setting(path = "personal.bank-max-minutes", desc = "Most banked time (not started yet, e.g. the AFK pool's sell booster) one "
            + "type can hold when the giver sets no own limit.", min = 1, max = 1_440, level = Setting.Level.OWNER)
    public int bankMaxMinutes = 180;

    @Setting(path = "personal.save-seconds", desc = "How often the time used by running boosters is saved. A crash loses at most "
            + "this much counting (in the player's favour).", min = 5, max = 300)
    public int saveSeconds = 30;

    // ------------------------------------------------------------------ global boosters

    @Setting(path = "global.enabled", desc = "Global booster items can be started (everyone online gets the bonus).")
    public boolean globalEnabled = true;

    @Setting(path = "global.max-percent", desc = "Highest strength a global booster item may have (PHASE-10: +50%).", min = 1, max = 300,
            level = Setting.Level.OWNER)
    public int globalMaxPercent = 100;

    @Setting(path = "global.max-item-minutes", desc = "Longest time one global booster item may give (PHASE-10: 30-60 min).", min = 1,
            max = 1_440, level = Setting.Level.OWNER)
    public int globalMaxItemMinutes = 120;

    @Setting(path = "global.max-queue", desc = "Most global boosters of one type that may wait behind the running one. More are "
            + "refused and the item is kept.", min = 0, max = 50)
    public int globalMaxQueue = 5;

    @Setting(path = "global.always-bound", desc = "Global booster items are always bound to the profile that won them (safest: "
            + "never traded). Off = only items from bound sources are bound.", level = Setting.Level.OWNER)
    public boolean globalAlwaysBound = true;

    @Setting(path = "global.boss-bar", desc = "Show a boss bar countdown of every running global booster to everyone online "
            + "(players can hide it in /settings).")
    public boolean bossBar = true;

    @Setting(path = "global.boss-bar-color", desc = "Boss bar color: pink, blue, red, green, yellow, purple or white.")
    public String bossBarColor = "yellow";

    @Setting(path = "global.save-seconds", desc = "How often the time left of running global boosters is saved.", min = 5, max = 300)
    public int globalSaveSeconds = 30;

    @Setting(path = "global.announce", desc = "Tell everyone online when a global booster starts, is queued and ends.")
    public boolean announce = true;

    // ------------------------------------------------------------------ thanks

    @Setting(path = "thanks.enabled", desc = "Players can thank the starter of a global booster with /thanks or by saying thanks "
            + "in chat (RW2).")
    public boolean thanksEnabled = true;

    @Setting(path = "thanks.credits", desc = "Earned credits one thanks gives the starter (RW2: 1, once per booster per player; "
            + "earned credits can't be traded, C1).", min = 0, max = 100, level = Setting.Level.OWNER)
    public int thanksCredits = 1;

    @Setting(path = "thanks.max-credits-per-booster", desc = "Most thanks credits one global booster pays its starter (0 = no "
            + "limit). Thanks after that are still shown, without a credit.", min = 0, max = 10_000, level = Setting.Level.OWNER)
    public int thanksMaxCredits = 100;

    @Setting(path = "thanks.same-ip-pays", desc = "A thanks from a player on the starter's IP address pays a credit too (off = no "
            + "credit: alts can't farm credits for their main).", level = Setting.Level.OWNER)
    public boolean thanksSameIp = false;

    @Setting(path = "thanks.grace-seconds", desc = "Seconds after a global booster ends during which it can still be thanked.",
            min = 0, max = 900)
    public int thanksGraceSeconds = 120;

    @Setting(path = "thanks.chat-phrases", desc = "Chat lines containing one of these as whole words count as a thanks (only while "
            + "a global booster runs that you have not thanked yet).")
    public List<String> chatPhrases = List.of("thanks", "thank you", "thank u", "thx", "ty", "tysm", "tyvm", "tks", "thnx", "ty all");

    @Setting(path = "thanks.chat-max-length", desc = "Longer chat lines never count as a thanks.", min = 2, max = 256)
    public int chatMaxLength = 60;

    // ------------------------------------------------------------------ items

    @Setting(path = "items.materials", desc = "Vanilla item of each personal booster type ('type:material'; the resource pack "
            + "retextures them later). Pick items that do nothing on right-click.", restart = true)
    public List<String> materials = List.of("sell:gold_ingot", "skill_xp:lapis_lazuli", "tool_xp:echo_shard", "crystals:prismarine_crystals");

    @Setting(path = "items.global-material", desc = "Vanilla item of global boosters (all types).", restart = true)
    public String globalMaterial = "nether_star";

    @Setting(path = "items.save-player-data", desc = "Save the player's data right after a booster item was used up, so a crash "
            + "can't bring the item back (it could not start twice anyway).")
    public boolean savePlayerData = true;

    @Setting(path = "join-delay-ticks", desc = "Ticks after joining before boosters load (the profile module loads the active "
            + "profile first).", min = 1, max = 1_200)
    public int joinDelayTicks = 40;

    @Setting(path = "sounds.start", desc = "Sound (sounds.yml key) when a booster starts.")
    public String startSound = "success";

    @Setting(path = "sounds.end", desc = "Sound (sounds.yml key) when a personal booster ends.")
    public String endSound = "click";

    @Setting(path = "sounds.global", desc = "Sound (sounds.yml key) everyone hears when a global booster starts.")
    public String globalSound = "unlock";

    /** Version 2 (card C1): thanks pay credits (earned) instead of gems; the gem keys are moved. */
    @Override
    public int version() {
        return 2;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, String>> migrations() {
        return java.util.Map.of(2, java.util.Map.of("thanks.gems", "thanks.credits",
                "thanks.max-gems-per-booster", "thanks.max-credits-per-booster"));
    }

    @Override
    public String fileName() {
        return "boosters.yml";
    }

    @Override
    public String header() {
        return "Boosters (PHASE-10 M10.2): personal booster items, banked time (AFK pool), global boosters and /thanks.\n"
                + "Texts: messages/boosters.yml. Reload: /skyblock reload boosters";
    }

    long personalMaxMillis() {
        return personalMaxMinutes * BoosterRules.MINUTE;
    }

    /** True when an item with these numbers may exist. */
    boolean valid(int percent, int minutes, boolean global) {
        if (minutes < 1 || percent < personalMinPercent) {
            return false;
        }
        return global ? percent <= globalMaxPercent && minutes <= globalMaxItemMinutes
                : percent <= personalMaxPercent && minutes <= personalMaxItemMinutes && minutes <= personalMaxMinutes;
    }

    /** {@code items.materials} as a map; broken entries are reported and the default item of that type is used. */
    static Map<BoosterType, Material> materials(List<String> raw, Consumer<String> warn) {
        Map<BoosterType, Material> out = new EnumMap<>(BoosterType.class);
        out.put(BoosterType.SELL, Material.GOLD_INGOT);
        out.put(BoosterType.SKILL_XP, Material.LAPIS_LAZULI);
        out.put(BoosterType.TOOL_XP, Material.ECHO_SHARD);
        out.put(BoosterType.CRYSTALS, Material.PRISMARINE_CRYSTALS);
        for (String entry : raw == null ? List.<String>of() : raw) {
            String[] parts = entry == null ? new String[0] : entry.split(":", 2);
            if (parts.length != 2) {
                warn.accept("boosters.yml → items.materials: '" + entry + "' is not 'type:material', skipped");
                continue;
            }
            var type = BoosterType.byId(parts[0]);
            Material m = Material.matchMaterial(parts[1].trim().toUpperCase(Locale.ROOT));
            if (type.isEmpty() || m == null || !m.isItem() || m.isAir()) {
                warn.accept("boosters.yml → items.materials: '" + entry + "' has an unknown type or item, skipped");
                continue;
            }
            out.put(type.get(), m);
        }
        return out;
    }
}
