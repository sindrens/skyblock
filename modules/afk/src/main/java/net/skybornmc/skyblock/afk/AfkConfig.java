package net.skybornmc.skyblock.afk;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of the AFK pool ({@code afk.yml}). Owner decisions O10 (fuel + small money + keys, loot improves the
 * longer you stay, max 8 h per day, balanced, boosters count toward the global cap O11) and RW3 (small area at spawn,
 * rewards every 15 min, keys at 1 h / 3 h / 6 h / 8 h, no movement needed, one reward stream per IP).
 * Money defaults: docs/ECONOMY_BALANCE.md §7.5 (tools/economy_model.py, table {@code afk}).
 */
public final class AfkConfig extends ModuleConfig {

    // ------------------------------------------------------------------ the pool area

    @Setting(path = "pool.build-region", desc = "Id of a region in the applied builds' metadata (regions list, e.g. in the spawn build) that is the AFK pool. Used first when it exists; empty = never.")
    public String buildRegion = "afk_pool";

    @Setting(path = "pool.use-config-area", desc = "Use the box below when the build region does not exist. Off and no build region = the AFK pool is closed.")
    public boolean useConfigArea = false;

    @Setting(path = "pool.world", desc = "World of the box below.")
    public String world = "spawn";

    @Setting(path = "pool.min-x", desc = "Box corner 1, x (blocks, inclusive).")
    public int minX = 0;

    @Setting(path = "pool.min-y", desc = "Box corner 1, y.")
    public int minY = 0;

    @Setting(path = "pool.min-z", desc = "Box corner 1, z.")
    public int minZ = 0;

    @Setting(path = "pool.max-x", desc = "Box corner 2, x (blocks, inclusive; corners may be given in any order).")
    public int maxX = 0;

    @Setting(path = "pool.max-y", desc = "Box corner 2, y.")
    public int maxY = 0;

    @Setting(path = "pool.max-z", desc = "Box corner 2, z.")
    public int maxZ = 0;

    @Setting(path = "pool.hide-players", desc = "Players inside the pool can't see each other (owner R3-36). Staff that is not vanished stays visible.")
    public boolean hidePlayers = true;

    // ------------------------------------------------------------------ not earning (owner R3-35: no idle kick)

    @Setting(path = "idle.send-home", desc = "A player in the pool who is not earning (daily limit reached, or paused because another account "
            + "on their connection earns) is sent to their island after the time below (owner R3-35). Nobody is ever kicked for being idle.")
    public boolean sendHome = true;

    @Setting(path = "idle.send-home-seconds", desc = "Seconds of not earning in the pool before the player is sent to their island.", min = 10, max = 3600)
    public int sendHomeSeconds = 60;

    @Setting(path = "idle.fallback-world", desc = "Players without an island are sent to this world's spawn instead.")
    public String fallbackWorld = "spawn";

    // ------------------------------------------------------------------ time

    @Setting(path = "time.check-seconds", desc = "How often players in the pool are checked, credited and shown the action bar.", min = 1, max = 10)
    public int checkSeconds = 2;

    @Setting(path = "time.save-seconds", desc = "Credited time of online players is saved this often (and on leaving, quitting and shutdown). A crash loses at most this much.", min = 5, max = 300)
    public int saveSeconds = 30;

    @Setting(path = "time.reward-minutes", desc = "Credited minutes per reward (owner RW3: 15).", min = 1, max = 120, level = Setting.Level.OWNER)
    public int rewardMinutes = 15;

    @Setting(path = "time.daily-cap-minutes", desc = "Most minutes credited per account per day (owner O10: 8 h = 480).", min = 15, max = 1440, level = Setting.Level.OWNER)
    public int dailyCapMinutes = 480;

    @Setting(path = "time.timezone", desc = "Time zone of the day reset (midnight). Empty = core.yml display-timezone (Europe/Oslo).")
    public String timezone = "";

    // ------------------------------------------------------------------ anti-abuse (RW3)

    @Setting(path = "anti-abuse.one-per-ip", desc = "Only one account per IP address earns at a time; the others in the pool see why they are paused (owner RW3).")
    public boolean onePerIp = true;

    @Setting(path = "anti-abuse.ip-daily-cap-minutes", desc = "Most minutes credited per IP address per day, all accounts together (stops alts taking turns). 0 = off. Kept in memory: a restart resets it.", min = 0, max = 1440, level = Setting.Level.OWNER)
    public int ipDailyCapMinutes = 480;

    @Setting(path = "anti-abuse.family-groups", desc = "Staff family whitelist (owner R3-3): accounts that may earn together on one connection. "
            + "One entry per family: 'name: uuid, uuid, ...' (the name is only a label). Each member also gets their own per-connection daily limit. "
            + "An account outside the family on the same connection still waits its turn.", level = Setting.Level.OWNER)
    public List<String> familyGroups = List.of();

    @Setting(path = "anti-abuse.bedrock-ip-check", desc = "Warn in the console when Bedrock players seem to share the proxy's IP (Floodgate not passing "
            + "real IPs): then every Bedrock player would count as one connection. See docs/commands/afk.md, Bedrock IPs.")
    public boolean bedrockIpCheck = true;

    @Setting(path = "anti-abuse.bedrock-shared-ip-players", desc = "Bedrock players online on the same IP that trigger the warning above "
            + "(a loopback address always does).", min = 2, max = 50)
    public int bedrockSharedIpPlayers = 3;

    // ------------------------------------------------------------------ money

    @Setting(path = "money.enabled", desc = "Give money with every reward.")
    public boolean moneyEnabled = true;

    @Setting(path = "money.per-reward-by-tier", desc = "Money per reward at 100% for stage tiers 1-12 (whole dollars; 2.5% of the reference income per hour / 4, ECONOMY_BALANCE §7.5). The last value is used above.", level = Setting.Level.OWNER)
    public List<String> moneyByTier = List.of("310", "550", "960", "1690", "2970", "5200", "9130", "16000", "45800", "131000", "375000", "1070000");

    @Setting(path = "money.stage-skills", desc = "Skills whose highest level decides the player's stage tier (mining, foraging, combat, farming, fishing).")
    public List<String> stageSkills = List.of("mining", "foraging", "combat");

    @Setting(path = "money.tier-levels", desc = "Skill level that unlocks stage tiers 1-12 (the Depths unlock levels, ECONOMY_BALANCE §3.4).", level = Setting.Level.OWNER)
    public List<String> tierLevels = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58", "66");

    @Setting(path = "money.session-steps", desc = "Reward size by minutes stayed in the pool without leaving: 'minutes:percent'. Leaving resets the session (not the daily total).", level = Setting.Level.OWNER)
    public List<String> sessionSteps = List.of("0:100", "30:110", "60:125", "120:140", "240:160", "360:180");

    // ------------------------------------------------------------------ island fuel

    @Setting(path = "fuel.enabled", desc = "Give island fuel with every reward (to the player's island; nothing without an island).")
    public boolean fuelEnabled = true;

    @Setting(path = "fuel.minutes-per-reward", desc = "Minutes of island fuel per reward. Kept for later while Island Fuel does not exist yet.", min = 0, max = 1440, level = Setting.Level.OWNER)
    public int fuelMinutes = 15;

    // ------------------------------------------------------------------ crate keys

    @Setting(path = "keys.enabled", desc = "Give crate keys when the daily total reaches the milestones below.")
    public boolean keysEnabled = true;

    @Setting(path = "keys.milestones", desc = "'minutes:crate:amount' per day (owner RW3: 1 h, 3 h, 6 h, 8 h; R3-15: AFK never gives Blaze keys, the 8 h milestone is a Cinder key).", level = Setting.Level.OWNER)
    public List<String> keyMilestones = List.of("60:ember:1", "180:ember:1", "360:cinder:1", "480:cinder:1");

    // ------------------------------------------------------------------ personal sell booster

    @Setting(path = "booster.enabled", desc = "A reward can bank a short personal sell booster. It starts when the player is outside the pool and counts toward the +300% sell cap (O11).")
    public boolean boosterEnabled = true;

    @Setting(path = "booster.chance-percent", desc = "Chance per reward (percent).", min = 0, max = 100, level = Setting.Level.OWNER)
    public double boosterChance = 2.0;

    @Setting(path = "booster.min-session-minutes", desc = "Only rewards after this many minutes in one session can roll the booster.", min = 0, max = 480)
    public int boosterMinSession = 60;

    @Setting(path = "booster.percent", desc = "Sell bonus of the booster (percent, ECONOMY_BALANCE §2.7: personal booster 50%).", min = 1, max = 300, level = Setting.Level.OWNER)
    public int boosterPercent = 50;

    @Setting(path = "booster.minutes", desc = "Minutes of booster banked per win.", min = 1, max = 120, level = Setting.Level.OWNER)
    public int boosterMinutes = 10;

    @Setting(path = "booster.max-banked-minutes", desc = "Most booster minutes one profile can bank.", min = 1, max = 600, level = Setting.Level.OWNER)
    public int boosterMaxBanked = 60;

    // ------------------------------------------------------------------ display

    @Setting(path = "display.action-bar", desc = "Show the next-reward countdown and today's total on the action bar inside the pool.")
    public boolean actionBar = true;

    @Setting(path = "display.reward-chat", desc = "Send a chat line for every reward.")
    public boolean rewardChat = true;

    @Setting(path = "display.reward-sound", desc = "Sound name from sounds.yml played on a reward (empty = none).")
    public String rewardSound = "money";

    @Setting(path = "display.key-sound", desc = "Sound name from sounds.yml played when a key milestone is reached (empty = none).")
    public String keySound = "unlock";

    @Override
    public String fileName() {
        return "afk.yml";
    }

    @Override
    public String header() {
        return """
                AFK pool (O10, RW3): stand in the pool at spawn, no movement needed. Every reward-minutes: money (grows with
                the session), island fuel; keys at daily milestones; a small chance of a personal sell booster.
                Max daily-cap-minutes per account per day, one earning account per IP. Texts: messages/afk.yml.
                Reload: /skyblock reload afk""";
    }
}
