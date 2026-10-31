package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of voting ({@code votes.yml}, PHASE-10-REWARDS.md M10.5, owner 129, 320, P10-13, P10-15). Secrets (the
 * RSA key pair and the v2 tokens) are not here: they live in {@code plugins/PhoenixSkyblock/votifier/} so no editor shows
 * them. Texts are in {@code messages/rewards.yml}.
 */
public final class VotesSettings extends ModuleConfig {

    // ------------------------------------------------------------------ menu

    @Setting(path = "votes.enabled", desc = "Voting on: /vote menu, rewards for votes, streak and vote party. The receiver has its "
            + "own switch (receiver.enabled).")
    public boolean enabled = true;

    @Setting(path = "votes.page-url", desc = "The vote page with every site (owner 320). Shown in /vote and to the console.")
    public String pageUrl = "https://skybornmc.net/vote";

    @Setting(path = "votes.npc-role", desc = "NPC role whose click opens /vote (sneak-click keeps the help text).")
    public String npcRole = "vote";

    // ------------------------------------------------------------------ receiver

    @Setting(path = "receiver.enabled", desc = "Run our own Votifier receiver. OFF until the server is registered on the vote sites "
            + "(open the port in the firewall first). Keys and tokens: plugins/PhoenixSkyblock/votifier/.", level = Setting.Level.OWNER)
    public boolean receiverEnabled = false;

    @Setting(path = "receiver.host", desc = "Address the receiver listens on (0.0.0.0 = every address of the machine).",
            level = Setting.Level.OWNER)
    public String host = "0.0.0.0";

    @Setting(path = "receiver.port", desc = "TCP port of the receiver (the Votifier port you give the vote sites; NuVotifier's "
            + "default is 8192). Must be open in the firewall.", min = 1, max = 65_535, level = Setting.Level.OWNER)
    public int port = 8192;

    @Setting(path = "receiver.protocol-v1", desc = "Accept protocol v1 votes (RSA public key). Most sites still use v1.",
            level = Setting.Level.OWNER)
    public boolean v1 = true;

    @Setting(path = "receiver.protocol-v2", desc = "Accept protocol v2 votes (one token per site, votifier/tokens.txt).",
            level = Setting.Level.OWNER)
    public boolean v2 = true;

    @Setting(path = "receiver.max-packet-bytes", desc = "Largest packet read (a vote is about 300-600 bytes).", min = 256, max = 65_535)
    public int maxPacketBytes = 4_096;

    @Setting(path = "receiver.timeout-ms", desc = "A connection must deliver its whole vote within this time, then it is closed.",
            min = 500, max = 60_000)
    public int timeoutMs = 5_000;

    @Setting(path = "receiver.max-per-minute", desc = "Connections one address may open per minute (a site sends one per vote).",
            min = 1, max = 10_000)
    public int maxPerMinute = 60;

    @Setting(path = "receiver.max-open", desc = "Most connections handled at the same time; more are closed at once.", min = 1, max = 256)
    public int maxOpen = 16;

    @Setting(path = "receiver.reject-log-seconds", desc = "Rejected connections are written to the console at most once per this "
            + "many seconds (the line counts the others).", min = 0, max = 3_600)
    public int rejectLogSeconds = 60;

    // ------------------------------------------------------------------ sites

    @Setting(path = "sites", desc = "Vote sites, one per line: '<id> | <name> | <service names, comma separated> | <vote link>'. The "
            + "service name is what the site sends with each vote (case and dots don't matter). Unknown service names are logged so "
            + "you can add them. Replace each link with your server's page after registering (owner 320: the vote page links them "
            + "all).", level = Setting.Level.OWNER)
    public List<String> sites = List.of(
            "planetminecraft | PlanetMinecraft | PlanetMinecraft.com, PlanetMinecraft | https://www.planetminecraft.com/servers/",
            "minecraft-mp | Minecraft-MP | Minecraft-MP.com, MinecraftMP | https://minecraft-mp.com/",
            "minecraftservers | MinecraftServers.org | MinecraftServers.org, MinecraftServers | https://minecraftservers.org/",
            "topg | TopG | TopG.org, TopG | https://topg.org/minecraft-servers/",
            "minecraft-server-list | Minecraft-Server-List | Minecraft-Server-List.com, MinecraftServerList | "
                    + "https://minecraft-server-list.com/");

    // ------------------------------------------------------------------ rewards

    @Setting(path = "rewards.key-crate", desc = "Crate of the bound key every vote gives (P10-13: ember).", level = Setting.Level.OWNER)
    public String keyCrate = "ember";

    @Setting(path = "rewards.keys", desc = "Keys every vote gives (P10-13: 1).", min = 0, max = 64, level = Setting.Level.OWNER)
    public int keys = 1;

    @Setting(path = "rewards.gems", desc = "Gems every vote gives (P10-13, ECONOMY_BALANCE §9.2: 3).", min = 0, max = 1_000,
            level = Setting.Level.OWNER)
    public long gems = 3;

    @Setting(path = "rewards.pass-xp", desc = "Battle pass XP every vote gives (P10-13: 25). Only once the pass exists.", min = 0,
            max = 100_000, level = Setting.Level.OWNER)
    public long passXp = 25;

    @Setting(path = "rewards.key-fallback", desc = "Crystal base (tier-scaled) paid instead of a key when the crates module is not there.",
            min = 0, max = 100_000, level = Setting.Level.OWNER)
    public long keyFallback = 20;

    @Setting(path = "rewards.cooldown-hours", desc = "One reward per site per account in this many hours (site rules: 24).", min = 1,
            max = 168, level = Setting.Level.OWNER)
    public int cooldownHours = 24;

    @Setting(path = "rewards.accounts-per-address", desc = "Vote rewards for at most this many accounts per voter address per day "
            + "(owner rule 292, RX16). 0 = no limit. Only a hash of the address is stored.", min = 0, max = 100,
            level = Setting.Level.OWNER)
    public int accountsPerAddress = 3;

    @Setting(path = "rewards.unknown-name-days", desc = "A vote for a name that never joined waits this many days for that name.",
            min = 1, max = 90, level = Setting.Level.OWNER)
    public int unknownNameDays = 7;

    @Setting(path = "rewards.offline-days", desc = "A vote for a known player who stays offline waits this many days, then it expires.",
            min = 1, max = 365, level = Setting.Level.OWNER)
    public int offlineDays = 60;

    @Setting(path = "rewards.keep-days", desc = "Days vote rows are kept (replay protection and history; /logs keeps its own).",
            min = 30, max = 3_650)
    public int keepDays = 90;

    // ------------------------------------------------------------------ streak

    @Setting(path = "streak.rewards", desc = "Vote streak rewards (days in a row with at least one vote): '<day>: <crate>' (RX14: 7 "
            + "Cinder, 14 Blaze, 21 Cinder, 30 Inferno). Bound keys.", level = Setting.Level.OWNER)
    public List<String> streakRewards = List.of("7: cinder", "14: blaze", "21: cinder", "30: inferno");

    @Setting(path = "streak.cycle-days", desc = "After this many streak days the rewards repeat (day 37 = day 7 ...). 0 = no repeat.",
            min = 0, max = 3_650, level = Setting.Level.OWNER)
    public int cycleDays = 30;

    // ------------------------------------------------------------------ vote party

    @Setting(path = "party.enabled", desc = "Vote party: a server-wide counter; when it fills, every online player who voted today, "
            + "finished the tutorial and is not AFK gets the party reward.", level = Setting.Level.OWNER)
    public boolean partyEnabled = true;

    @Setting(path = "party.size", desc = "Votes that fill the party (P10-15: 500, about 3 parties a day at our size).", min = 1,
            max = 1_000_000, level = Setting.Level.OWNER)
    public int partySize = 500;

    @Setting(path = "party.crate", desc = "Crate of the bound party key (P10-15: blaze).", level = Setting.Level.OWNER)
    public String partyCrate = "blaze";

    @Setting(path = "party.hologram", desc = "Build hologram that shows the party progress (Tavern vote board). Empty = none.")
    public String partyHologram = "vote_party";

    @Setting(path = "party.hologram-seconds", desc = "At most one hologram update per this many seconds.", min = 1, max = 600)
    public int hologramSeconds = 10;

    @Override
    public String fileName() {
        return "votes.yml";
    }

    @Override
    public String header() {
        return "Voting (PHASE-10 M10.5): /vote menu, our own Votifier receiver, vote rewards, streak and vote party.\n"
                + "Secrets (RSA key pair, v2 tokens) are in plugins/PhoenixSkyblock/votifier/, never here.\n"
                + "Texts: messages/rewards.yml. Reload: /skyblock reload rewards";
    }
}
