package net.skybornmc.skyblock.moderation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P6 without a server: raid lockdown rules (13.9: only new accounts, staff and ranked never, manual by default, lengths,
 * auto-expiry, cleared state, join lockdown never refuses returning players, detection only alerts, the auto-lock limits,
 * Bedrock = Java), suggestion details (13.3: dedupe appends max 5 lines), the network scope filter (13.12, enabled false
 * and true) and the P6 config keys.
 */
class P6LogicTest {

    private static final long MIN = Duration.ofMinutes(1).toMillis();
    private static final long HOUR = TestData.HOUR;
    private static final Lockdown.Settings S = Lockdown.Settings.DEFAULT;
    private static final Path MESSAGES = Path.of("../../plugin/src/main/resources/messages/moderation.yml");
    private static final Path TRACKED_MESSAGES = Path.of("../../server/plugins/PhoenixSkyblock/messages/moderation.yml");
    private static final Path BUNDLED = Path.of("src/main/resources/punishments.yml");
    private static final Path TRACKED = Path.of("../../server/plugins/PhoenixSkyblock/punishments.yml");

    // ================================================================== 13.9: who is "new"

    @Test
    void newMeansLowPlaytimeOrYoungAccount() {
        long now = 1_000 * HOUR;
        assertTrue(Lockdown.isNew(null, 0, now, S), "never joined = new");
        assertTrue(Lockdown.isNew(now - 5 * MIN, 5 * MIN, now, S), "5 minutes old, 5 minutes played");
        assertTrue(Lockdown.isNew(now - 72 * HOUR, 29 * MIN, now, S), "old account but under 30m playtime");
        assertTrue(Lockdown.isNew(now - 2 * HOUR, 2 * HOUR, now, S), "lots of playtime but first join under 24h ago");
        assertFalse(Lockdown.isNew(now - 25 * HOUR, 30 * MIN, now, S), "30m played and older than 24h = not new");
        assertFalse(Lockdown.isNew(now - 400 * HOUR, 100 * HOUR, now, S), "regular player");
    }

    @Test
    void chatLockdownOnlyHitsNewAccountsAndNeverStaffOrRanks() {
        long now = 10 * HOUR;
        Lockdown.Active chat = new Lockdown.Active(Lockdown.Kind.CHAT, now - MIN, now + 9 * MIN, null, "Alex", false);
        assertTrue(Lockdown.chatBlocked(chat, now, false, true), "new player, no bypass: blocked");
        assertFalse(Lockdown.chatBlocked(chat, now, false, false), "existing player: never");
        assertFalse(Lockdown.chatBlocked(chat, now, true, true), "staff / ranked (bypass) even when new: never");
        assertFalse(Lockdown.chatBlocked(null, now, false, true), "no lockdown: never");
        assertFalse(Lockdown.chatBlocked(chat, now + 9 * MIN, false, true), "ended by time: never");
    }

    @Test
    void sameRulesForBedrockAndJava() {
        // The rules take no platform input at all: a Floodgate account with the same playtime/first join gets the same answer.
        long now = 50 * HOUR;
        boolean java = Lockdown.isNew(now - HOUR, 10 * MIN, now, S);
        boolean bedrock = Lockdown.isNew(now - HOUR, 10 * MIN, now, S);
        assertEquals(java, bedrock);
    }

    @Test
    void joinLockdownNeverRefusesReturningPlayers() {
        long now = 10 * HOUR;
        Lockdown.Active join = new Lockdown.Active(Lockdown.Kind.JOIN, now, now + 5 * MIN, null, "Alex", false);
        assertTrue(Lockdown.joinBlocked(join, now, false), "never joined before: refused");
        assertFalse(Lockdown.joinBlocked(join, now, true), "joined before (even new by playtime): always in");
        assertFalse(Lockdown.joinBlocked(null, now, false), "no join lockdown: everyone in");
        assertFalse(Lockdown.joinBlocked(join, now + 5 * MIN, false), "ended by time");
    }

    @Test
    void blockedCommandsCoverPrivateMessages() {
        List<String> blocked = S.blockedCommands();
        for (String label : List.of("msg", "MSG", "tell", "w", "r", "reply", "mail", "skyblock:msg")) {
            assertTrue(Lockdown.blockedCommand(label, blocked), label);
        }
        for (String label : List.of("spawn", "is", "help", "", "message")) {
            assertFalse(Lockdown.blockedCommand(label, blocked), label);
        }
        assertFalse(Lockdown.blockedCommand(null, blocked));
    }

    // ================================================================== 13.9: lengths, manual start, expiry, restart

    @Test
    void lengthsUseDefaultsAndNeverPassTheMax() {
        assertEquals(Duration.ofMinutes(10), Lockdown.length(Lockdown.Kind.CHAT, null, S).duration());
        assertEquals(Duration.ofMinutes(5), Lockdown.length(Lockdown.Kind.JOIN, null, S).duration());
        assertEquals(Duration.ofMinutes(60), Lockdown.length(Lockdown.Kind.CHAT, Duration.ofMinutes(60), S).duration());
        assertEquals("lockdown.too-long", Lockdown.length(Lockdown.Kind.CHAT, Duration.ofMinutes(61), S).error());
        assertEquals("lockdown.too-long", Lockdown.length(Lockdown.Kind.JOIN, Duration.ofMinutes(16), S).error());
        assertTrue(Lockdown.length(Lockdown.Kind.JOIN, Duration.ofMinutes(15), S).ok());
        assertEquals("lockdown.bad-length", Lockdown.length(Lockdown.Kind.CHAT, Duration.ZERO, S).error());
    }

    @Test
    void stateStartsStopsAndExpiresOnce() {
        Lockdown.State state = new Lockdown.State();
        long now = 100 * HOUR;
        assertFalse(state.any(now), "nothing at start (a restart is a new, empty state)");
        Lockdown.Active chat = new Lockdown.Active(Lockdown.Kind.CHAT, now, now + 10 * MIN, null, "Alex", false);
        assertTrue(state.start(chat, now).isEmpty());
        assertTrue(state.live(Lockdown.Kind.CHAT, now).isPresent());
        assertTrue(state.live(Lockdown.Kind.JOIN, now).isEmpty(), "a chat lockdown is not a join lockdown");
        Lockdown.Active longer = new Lockdown.Active(Lockdown.Kind.CHAT, now + MIN, now + 30 * MIN, null, "Bob", false);
        assertEquals(chat, state.start(longer, now + MIN).orElseThrow(), "starting again replaces it (new end)");
        assertTrue(state.expire(now + 29 * MIN).isEmpty(), "not over yet");
        assertEquals(List.of(longer), state.expire(now + 30 * MIN), "ends by itself");
        assertTrue(state.expire(now + 31 * MIN).isEmpty(), "reported once");
        assertFalse(state.any(now + 31 * MIN));

        Lockdown.Active join = new Lockdown.Active(Lockdown.Kind.JOIN, now, now + 5 * MIN, null, "Alex", false);
        state.start(join, now);
        state.start(chat, now);
        assertEquals(2, state.stopAll(now + MIN).size(), "/lockdown off (and shutdown) ends both");
        assertFalse(state.any(now + MIN));
        assertTrue(state.stop(Lockdown.Kind.CHAT, now + MIN).isEmpty(), "nothing left to stop");
    }

    @Test
    void manualByDefaultAndAutoLockLimits() {
        long now = 1000 * HOUR;
        Lockdown.Raid raid = Lockdown.Raid.DEFAULT;
        assertFalse(raid.autoLock(), "manual by default");
        assertEquals(Lockdown.AutoOutcome.OFF, Lockdown.auto(raid, S, null, null, now).outcome(), "detection only alerts");

        Lockdown.Raid on = new Lockdown.Raid(true, 15, Duration.ofSeconds(30), true, Duration.ofMinutes(20), Duration.ofMinutes(1));
        Lockdown.Auto first = Lockdown.auto(on, S, null, null, now);
        assertEquals(Lockdown.AutoOutcome.START, first.outcome());
        assertEquals(Duration.ofMinutes(5), first.duration(), "never more than 5 minutes, whatever the config says");
        assertEquals(Lockdown.AutoOutcome.COOLDOWN, Lockdown.auto(on, S, null, now, now + 29 * MIN).outcome(),
                "at most once per 30 minutes (a shorter cooldown is raised to 30m)");
        assertEquals(Lockdown.AutoOutcome.START, Lockdown.auto(on, S, null, now, now + 30 * MIN).outcome());
        Lockdown.Active running = new Lockdown.Active(Lockdown.Kind.CHAT, now, now + 10 * MIN, null, "Alex", false);
        assertEquals(Lockdown.AutoOutcome.ALREADY, Lockdown.auto(on, S, running, null, now + MIN).outcome(),
                "never replaces a running chat lockdown");
        Lockdown.Settings shortMax = new Lockdown.Settings(S.newPlayerPlaytime(), S.newPlayerAge(), Duration.ofMinutes(2),
                Duration.ofMinutes(3), S.joinDefault(), S.joinMax(), S.staffReminder(), S.blockedCommands());
        assertEquals(Duration.ofMinutes(3), Lockdown.auto(on, shortMax, null, null, now).duration(), "never past chat-max");
        // the automatic start remembers its time for the cooldown; a manual start does not
        Lockdown.State state = new Lockdown.State();
        state.start(new Lockdown.Active(Lockdown.Kind.CHAT, now, now + 10 * MIN, UUID.randomUUID(), "Alex", false), now);
        assertNull(state.lastAuto());
        state.start(new Lockdown.Active(Lockdown.Kind.CHAT, now + MIN, now + 6 * MIN, null, null, true), now + MIN);
        assertEquals(now + MIN, state.lastAuto());
    }

    // ================================================================== 13.9: raid detection

    @Test
    void raidDetectionAlertsOnceWhenOverTheLimit() {
        Lockdown.Raid raid = new Lockdown.Raid(true, 3, Duration.ofSeconds(30), false, Lockdown.AUTO_MAX, Lockdown.AUTO_COOLDOWN_MIN);
        Lockdown.RaidDetector d = new Lockdown.RaidDetector();
        long now = 10_000_000L;
        UUID a = UUID.randomUUID();
        assertTrue(d.record(a, now, raid).isEmpty());
        assertTrue(d.record(a, now + 1000, raid).isEmpty(), "the same account twice counts once");
        assertTrue(d.record(UUID.randomUUID(), now + 2000, raid).isEmpty());
        assertTrue(d.record(UUID.randomUUID(), now + 3000, raid).isEmpty(), "exactly the limit (3): no alert");
        assertEquals(Optional.of(4), d.record(UUID.randomUUID(), now + 4000, raid), "more than the limit: alert");
        assertTrue(d.record(UUID.randomUUID(), now + 5000, raid).isEmpty(), "one raid = one alert");
        assertEquals(5, d.count(now + 5000, raid.window()));
        // all hits leave the window: a new wave alerts again
        long later = now + 60_000;
        for (int i = 0; i < 3; i++) {
            assertTrue(d.record(UUID.randomUUID(), later + i, raid).isEmpty());
        }
        assertEquals(Optional.of(4), d.record(UUID.randomUUID(), later + 10, raid));
    }

    @Test
    void slowJoinsNeverLookLikeARaid() {
        Lockdown.Raid raid = new Lockdown.Raid(true, 3, Duration.ofSeconds(30), false, Lockdown.AUTO_MAX, Lockdown.AUTO_COOLDOWN_MIN);
        Lockdown.RaidDetector d = new Lockdown.RaidDetector();
        long now = 0;
        for (int i = 0; i < 50; i++) {
            assertTrue(d.record(UUID.randomUUID(), now + i * 11_000L, raid).isEmpty(), "one every 11s never passes 3 in 30s");
        }
    }

    @Test
    void detectionOffNeverAlerts() {
        Lockdown.Raid off = new Lockdown.Raid(false, 1, Duration.ofSeconds(30), true, Lockdown.AUTO_MAX, Lockdown.AUTO_COOLDOWN_MIN);
        Lockdown.RaidDetector d = new Lockdown.RaidDetector();
        for (int i = 0; i < 10; i++) {
            assertTrue(d.record(UUID.randomUUID(), i, off).isEmpty());
        }
    }

    // ================================================================== 13.3: suggestion details

    @Test
    void repeatedSuggestionsAppendDetailsMaxFiveLines() {
        String d = null;
        for (int vl = 1; vl <= 7; vl++) {
            d = ApiSuggestions.appendDetails(d, "Grim: Reach vl " + vl, 5, 255);
        }
        assertEquals("Grim: Reach vl 3 | Grim: Reach vl 4 | Grim: Reach vl 5 | Grim: Reach vl 6 | Grim: Reach vl 7", d,
                "newest 5 lines");
        assertEquals("a", ApiSuggestions.appendDetails("", "a", 5, 255));
        assertEquals("a", ApiSuggestions.appendDetails("a", "  ", 5, 255), "an empty detail adds nothing");
        String longLine = "x".repeat(200);
        String two = ApiSuggestions.appendDetails(longLine, "y".repeat(100), 5, 255);
        assertEquals("y".repeat(100), two, "oldest lines are dropped until it fits the column");
        assertTrue(ApiSuggestions.appendDetails(null, "z".repeat(400), 5, 255).length() <= 255, "one long line is cut");
    }

    // ================================================================== 13.12: network scope filter

    @Test
    void scopeFilterOffChangesNothing() {
        NetworkScope.Settings off = NetworkScope.Settings.DEFAULT;
        assertFalse(off.enabled());
        assertTrue(NetworkScope.applies("server", "lobby", off), "with the filter off every row counts");
        assertTrue(NetworkScope.applies(null, null, off));
        assertEquals("", NetworkScope.sql(off));
        assertNull(NetworkScope.parameter(off));
    }

    @Test
    void scopeFilterOnUsesScopeAndServer() {
        NetworkScope.Settings on = new NetworkScope.Settings(true, "phoenix", "network");
        assertTrue(NetworkScope.applies("network", "lobby", on), "network rows count everywhere");
        assertTrue(NetworkScope.applies(null, null, on), "rows from before P6 count as network");
        assertTrue(NetworkScope.applies("server", "phoenix", on), "server rows count where they were given");
        assertFalse(NetworkScope.applies("server", "lobby", on), "... and nowhere else");
        assertFalse(NetworkScope.applies("server", null, on));
        assertTrue(NetworkScope.sql(on).contains("server_id = ?"));
        assertEquals("phoenix", NetworkScope.parameter(on));
    }

    @Test
    void bansAndMutesUseTheDefaultScopeWarnsAndKicksStayLocal() {
        NetworkScope.Settings s = new NetworkScope.Settings(false, "phoenix", "network");
        assertEquals("network", NetworkScope.scopeFor(Punishment.Type.BAN, s));
        assertEquals("network", NetworkScope.scopeFor(Punishment.Type.MUTE, s));
        assertEquals("server", NetworkScope.scopeFor(Punishment.Type.WARN, s));
        assertEquals("server", NetworkScope.scopeFor(Punishment.Type.KICK, s));
        NetworkScope.Settings local = new NetworkScope.Settings(false, "phoenix", "server");
        assertEquals("server", NetworkScope.scopeFor(Punishment.Type.BAN, local));
        assertEquals(Optional.of("network"), NetworkScope.parseScope(" Network "));
        assertTrue(NetworkScope.parseScope("global").isEmpty());
    }

    // ================================================================== config

    private static final String BASE = """
            categories:
              cheating:
                icon: DIAMOND_SWORD
                reasons:
                  hacking:
                    ladder: ["ban 30d", "ban perm"]
                  macro:
                    ladder: ["ban 7d"]
                  xray:
                    ladder: ["ban 14d"]
            menu:
              summary-slot: 4
              bans:
                hacking: 14
                macro: 15
                xray: 16
            warn-rules:
              enabled: false
            extras:
              undo-window: 2m
            """;

    private static PunishConfigParser.Result parse(String text) {
        return PunishConfigParser.parse(text, TestData.ICONS);
    }

    @Test
    void shippedFilesHaveTheP6Defaults() throws Exception {
        for (Path file : List.of(BUNDLED, TRACKED)) {
            PunishConfigParser.Result r = parse(Files.readString(file));
            assertTrue(r.ok(), file + ": " + r.errors());
            PunishConfig.Extras x = r.config().extras();
            assertEquals(List.of("hacking", "xray", "macro"), x.neverAuto());
            assertEquals(Lockdown.Settings.DEFAULT, x.lockdown());
            assertEquals(Lockdown.Raid.DEFAULT, x.raid());
            assertFalse(x.raid().autoLock(), "manual by default");
            assertEquals(NetworkScope.Settings.DEFAULT, x.network());
            assertFalse(x.network().enabled(), "one server: filter off");
        }
    }

    @Test
    void missingP6KeysUseDefaults() {
        PunishConfigParser.Result r = parse(BASE);
        assertTrue(r.ok(), () -> r.errors().toString());
        assertEquals(Lockdown.Settings.DEFAULT, r.config().extras().lockdown());
        assertEquals(PunishConfig.Extras.NEVER_AUTO, r.config().extras().neverAuto());
    }

    private static List<String> errorKeys(String extras) {
        return parse(BASE + extras).errors().stream().map(ConfigProblem::key).toList();
    }

    @Test
    void badP6ValuesAreRejected() {
        assertTrue(errorKeys("  never-auto: [hacking, flying]\n").contains("config.unknown-reason"), "never-auto ids must exist");
        assertTrue(errorKeys("  lockdown:\n    chat-max: 12h\n").contains("config.duration-too-long"), "chat never above 6h");
        assertTrue(errorKeys("  lockdown:\n    join-max: 2h\n").contains("config.duration-too-long"), "join never above 1h");
        assertTrue(errorKeys("  lockdown:\n    chat-default: 30m\n    chat-max: 20m\n").contains("config.duration-too-long"),
                "default above max");
        assertTrue(errorKeys("  lockdown:\n    staff-reminder: 2s\n").contains("config.bad-duration"));
        assertTrue(errorKeys("  lockdown:\n    blocked-commands: [\"msg me\"]\n").contains("config.bad-id"));
        assertTrue(errorKeys("  raid:\n    auto-duration: 10m\n").contains("config.duration-too-long"), "auto max 5m");
        assertTrue(errorKeys("  raid:\n    auto-cooldown: 5m\n").contains("config.duration-too-short"), "auto cooldown min 30m");
        assertTrue(errorKeys("  raid:\n    accounts: 1\n").contains("config.bad-number"));
        assertTrue(errorKeys("  network:\n    default-scope: global\n").contains("config.unknown-key"));
        assertTrue(errorKeys("  network:\n    server-id: \"Phoenix One\"\n").contains("config.bad-id"));
        assertTrue(errorKeys("  network:\n    enabled: maybe\n").contains("config.bad-boolean"));
    }

    @Test
    void validP6ValuesAreRead() {
        PunishConfigParser.Result r = parse(BASE + """
                  never-auto: [hacking]
                  lockdown:
                    new-player-playtime: 1h
                    chat-default: 5m
                    chat-max: 30m
                    blocked-commands: [/msg, Tell]
                  raid:
                    accounts: 20
                    window: 1m
                    auto-lock: true
                    auto-duration: 3m
                    auto-cooldown: 1h
                  network:
                    enabled: true
                    server-id: lobby
                    default-scope: server
                """);
        assertTrue(r.ok(), () -> r.errors().toString());
        PunishConfig.Extras x = r.config().extras();
        assertEquals(List.of("hacking"), x.neverAuto());
        assertEquals(Duration.ofHours(1), x.lockdown().newPlayerPlaytime());
        assertEquals(Duration.ofMinutes(30), x.lockdown().chatMax());
        assertEquals(List.of("msg", "tell"), x.lockdown().blockedCommands());
        assertEquals(new Lockdown.Raid(true, 20, Duration.ofMinutes(1), true, Duration.ofMinutes(3), Duration.ofHours(1)), x.raid());
        assertEquals(new NetworkScope.Settings(true, "lobby", "server"), x.network());
    }

    @Test
    void everyP6TextExists() throws Exception {
        for (Path file : List.of(MESSAGES, TRACKED_MESSAGES)) {
            YamlConfiguration msg = YamlConfiguration.loadConfiguration(file.toFile());
            for (String key : List.of("config.duration-too-long", "config.duration-too-short", "lockdown.kinds.chat",
                    "lockdown.kinds.join", "lockdown.what.chat", "lockdown.what.join", "lockdown.system",
                    "lockdown.chat-blocked", "lockdown.join-screen", "lockdown.started", "lockdown.changed",
                    "lockdown.auto-started", "lockdown.ended", "lockdown.expired", "lockdown.none-active", "lockdown.too-long",
                    "lockdown.bad-length", "lockdown.no-permission", "lockdown.reminder", "lockdown.reminder-part",
                    "lockdown.status-header", "lockdown.status-on", "lockdown.status-auto", "lockdown.status-off",
                    "lockdown.status-raid", "lockdown.status-raid-auto", "lockdown.status-raid-off", "lockdown.status-new",
                    "lockdown.placeholder.chat", "lockdown.placeholder.join", "lockdown.placeholder.separator",
                    "lockdown.raid.alert", "lockdown.raid.alert-auto", "lockdown.raid.alert-running",
                    "lockdown.raid.alert-cooldown", "lockdown.raid.start-button", "lockdown.raid.start-hover",
                    "lockdown.raid.ignore-button", "lockdown.raid.ignore-hover", "lockdown.raid.ignored", "lockdown.raid.already",
                    "lockdown.menu.title", "lockdown.menu.chat-off.name", "lockdown.menu.chat-on.name",
                    "lockdown.menu.join-off.name", "lockdown.menu.join-on.name", "lockdown.menu.status-off.name",
                    "lockdown.menu.status-on.name", "psuggest.created", "psuggest.refreshed", "discord.titles.lockdown-start",
                    "discord.titles.lockdown-stop", "discord.titles.lockdown-expired", "discord.titles.lockdown-restart",
                    "discord.fields.lockdown", "discord.fields.started-by", "discord.lockdown-kinds.chat",
                    "discord.lockdown-kinds.join", "discord.lockdown-auto")) {
                assertTrue(msg.contains(key), file + ": missing " + key);
            }
        }
    }

    @Test
    void lockdownEmbedHasNoPlayerData() {
        long now = 5 * HOUR;
        Lockdown.Active a = new Lockdown.Active(Lockdown.Kind.CHAT, now, now + 10 * MIN, null, null, true);
        net.skybornmc.skyblock.common.discord.DiscordSink.Embed e = PunishEmbeds.lockdown(a, "start", null, now,
                PunishConfig.Discord.DEFAULT, k -> k);
        assertEquals("titles.lockdown-start", e.title());
        assertEquals(0xE91E63, e.color());
        assertTrue(e.fields().stream().anyMatch(f -> f.value().equals("10m")), "length shown");
        assertTrue(e.fields().stream().anyMatch(f -> f.value().equals("system")), "no staff = system");
        assertTrue(e.fields().stream().anyMatch(f -> f.value().equals("lockdown-auto")), "automatic marked");
    }
}
