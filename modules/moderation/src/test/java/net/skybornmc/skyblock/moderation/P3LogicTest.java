package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.discord.DiscordSink;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.skybornmc.skyblock.moderation.TestData.ALEX;
import static net.skybornmc.skyblock.moderation.TestData.DAY;
import static net.skybornmc.skyblock.moderation.TestData.HOUR;
import static net.skybornmc.skyblock.moderation.TestData.STEVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3 pure logic (spec 12.14): 5 evidence, 6 notes, 7 acknowledgement, 8 weights, 9 suggestion API (plan), 12 Discord
 * payload, 13 /mypunishments, plus the P3 config keys and their texts.
 */
class P3LogicTest {

    private static final Path TRACKED = Path.of("../../server/plugins/PhoenixSkyblock/punishments.yml");
    private static final Path MESSAGES = Path.of("../../plugin/src/main/resources/messages/moderation.yml");
    private static final Path TRACKED_MESSAGES = Path.of("../../server/plugins/PhoenixSkyblock/messages/moderation.yml");
    private static final long T0 = 1_800_000_000_000L;

    private static Punishment warn(long id, long created, int weight) {
        return TestData.warn(id, created).withWeight(weight);
    }

    // ================================================================ 12.7 weights (item 8)

    @Test
    void halfPlusHalfPlusOneIsExactlyTwo() {
        List<Punishment> warns = List.of(warn(1, T0, Weights.parse("0.5")), warn(2, T0 + 1, Weights.parse("0.5")),
                warn(3, T0 + 2, Weights.parse("1")));
        assertEquals(20, WarnRules.tenths(warns), "5 + 5 + 10 tenths");
        assertTrue(Weights.reaches(20, 2), "a rule at 2 fires at exactly 2");
        assertFalse(Weights.reaches(19, 2));
        assertEquals("2", Weights.text(20));
        assertEquals("1.5", Weights.text(15));
        assertEquals("0.5", Weights.text(5));
    }

    @Test
    void ruleFiresWhenTheWeightedSumReachesAt() {
        WarnRules.Settings rules = TestData.rules(WarnRules.Mode.NOTIFY, true);
        Punishment a = warn(1, T0, 5);
        Punishment b = warn(2, T0 + HOUR, 5);
        assertTrue(WarnRules.evaluate(b, List.of(a, b), List.of(), rules, T0 + HOUR).isEmpty(), "0.5 + 0.5 = 1 < 2");
        Punishment c = warn(3, T0 + 2 * HOUR, 10);
        var t = WarnRules.evaluate(c, List.of(a, b, c), List.of(), rules, T0 + 2 * HOUR);
        assertTrue(t.isPresent(), "0.5 + 0.5 + 1 = 2 fires the rule at 2");
        assertEquals(20, t.get().tenths());
        assertEquals(2, t.get().count());
        WarnRules.Counter counter = WarnRules.counter(List.of(a, b), List.of(), rules, T0 + HOUR);
        assertEquals("1", counter.text());
        assertEquals(2, counter.next().at(), "the next rule is still at 2");
        Punishment heavy = warn(4, T0 + 3 * HOUR, 20);
        assertTrue(WarnRules.evaluate(heavy, List.of(heavy), List.of(), rules, T0 + 3 * HOUR).isPresent(),
                "one serious warn (weight 2) is enough");
    }

    @Test
    void weightParsingAndSteps() {
        assertEquals(5, Weights.parse("0.5"));
        assertEquals(10, Weights.parse("1"));
        assertEquals(10, Weights.parse("1.0"));
        assertEquals(100, Weights.parse("10"));
        for (String bad : List.of("0", "-1", "0.05", "10.1", "abc", "", "1e9")) {
            assertThrows(IllegalArgumentException.class, () -> Weights.parse(bad), bad);
        }
        assertEquals(10, Weights.fromDb(null), "rows from before P3 count 1");
        assertEquals(15, Weights.fromDb(new BigDecimal("1.5")));
        Templates.Step half = Templates.Step.parse("warn 0.5");
        assertEquals(Punishment.Type.WARN, half.type());
        assertEquals(5, half.weight());
        assertEquals("warn 0.5", half.describe());
        assertEquals("warn", Templates.Step.parse("warn").describe());
        assertEquals(10, Templates.Step.parse("mute 1h").weight());
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("warn 0"));
        assertThrows(IllegalArgumentException.class, () -> Templates.Step.parse("kick 1"));
    }

    @Test
    void warnButtonWeightIsParsedAndValidated() {
        String yaml = """
                categories:
                  chat:
                    reasons:
                      spam:
                        ladder: ["warn 0.5", "mute 1h"]
                menu:
                  warns:
                    light: { slot: 27, weight: 0.5 }
                    serious: { slot: 28, weight: 2 }
                    plain: { slot: 29 }
                """;
        PunishConfigParser.Result r = PunishConfigParser.parse(yaml, TestData.ICONS);
        assertTrue(r.ok(), String.valueOf(r.errors()));
        Map<String, Integer> weights = new java.util.HashMap<>();
        r.config().menu().warns().forEach(w -> weights.put(w.id(), w.weight()));
        assertEquals(Map.of("light", 5, "serious", 20, "plain", 10), weights);
        assertEquals(5, r.config().categories().getFirst().templates().getFirst().ladder().getFirst().weight());

        PunishConfigParser.Result bad = PunishConfigParser.parse(yaml.replace("weight: 2", "weight: 0.25"), TestData.ICONS);
        assertFalse(bad.ok());
        assertTrue(bad.errors().stream().anyMatch(p -> p.key().equals("config.bad-weight")
                && p.path().equals("menu.warns.serious.weight")), String.valueOf(bad.errors()));
    }

    // ================================================================ 12.4 evidence (item 5)

    private static Evidence.Line line(long t, UUID who, String name, String text) {
        return new Evidence.Line(t, who, name, text, false);
    }

    @Test
    void evidenceKeepsTheTargetsLastLinesPlusContextOldestFirst() {
        UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000000c");
        List<Evidence.Line> all = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            all.add(line(T0 + i * 1000L, STEVE, "Steve", "msg " + i));
        }
        for (int i = 0; i < 8; i++) {
            all.add(line(T0 + i * 1000L + 500, bob, "Bob", "reply " + i));
        }
        all.add(line(T0 + 60_000, STEVE, "Steve", "after the punishment"));
        PunishConfig.Evidence cfg = new PunishConfig.Evidence(true, 10, 5, Duration.ofDays(180), 200, 6000);
        List<String> out = Evidence.build(all, STEVE, T0 + 30_000, cfg, ZoneId.of("UTC"), "[IP hidden]", "(blocked)");
        assertEquals(15, out.size(), "10 own lines + 5 context lines");
        assertEquals(10, out.stream().filter(s -> s.contains("Steve: ")).count());
        assertEquals(5, out.stream().filter(s -> s.contains("Bob: ")).count());
        assertTrue(out.getLast().endsWith("Steve: msg 14"), "newest own line last: " + out.getLast());
        assertTrue(out.stream().noneMatch(s -> s.contains("msg 4")), "only the last 10 own lines");
        assertTrue(out.stream().noneMatch(s -> s.contains("after the punishment")), "lines after the punishment are ignored");
        assertTrue(out.getFirst().startsWith("["), "time stamp first: " + out.getFirst());
    }

    @Test
    void evidenceNeverStoresIpAddresses() {
        for (String msg : List.of("join 192.168.1.20 now", "ip: 10.0.0.1:25565", "spaced 85 . 23 . 1 . 9 lol",
                "v6 2001:db8:85a3::8a2e:370:7334 here", "loop ::1 x", "full fe80:0:0:0:200:f8ff:fe21:67cf")) {
            String r = Evidence.redact(msg, "[IP hidden]");
            assertTrue(r.contains("[IP hidden]"), msg + " -> " + r);
            assertFalse(r.matches(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*"), msg + " -> " + r);
        }
        assertEquals("meet at 12:30 ok", Evidence.redact("meet at 12:30 ok", "[IP hidden]"), "times stay");
        assertEquals("version 1.21 is out", Evidence.redact("version 1.21 is out", "[IP hidden]"));
        List<String> out = Evidence.build(List.of(line(T0, STEVE, "Steve", "my ip is 1.2.3.4")), STEVE, T0 + 1,
                PunishConfig.Evidence.DEFAULT, ZoneId.of("UTC"), "[IP hidden]", "(blocked)");
        assertFalse(out.getFirst().contains("1.2.3.4"));
    }

    @Test
    void evidenceIsOneLineEachAndSizeLimited() {
        PunishConfig.Evidence cfg = new PunishConfig.Evidence(true, 10, 0, Duration.ofDays(1), 30, 200);
        List<Evidence.Line> all = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            all.add(line(T0 + i, STEVE, "Steve", "line\nwith newline " + i + " " + "x".repeat(50)));
        }
        all.add(new Evidence.Line(T0 + 20, STEVE, "Steve", "bad word", true));
        List<String> out = Evidence.build(all, STEVE, T0 + 100, cfg, ZoneId.of("UTC"), "[IP hidden]", "(blocked)");
        assertTrue(out.stream().allMatch(s -> s.length() <= 30 && !s.contains("\n")), out.toString());
        assertTrue(Evidence.join(out).length() <= 200, "max-chars");
        assertTrue(out.getLast().contains("bad word") || out.getLast().endsWith("..."), "the newest (blocked) line is kept");
        assertEquals(out, Evidence.split(Evidence.join(out)), "stored form round-trips");
        assertEquals(List.of(), Evidence.split(""));
    }

    // ================================================================ 12.5 notes (item 6)

    @Test
    void notesAreCleanedLimitedAndCut() {
        assertEquals("a b c", Notes.clean("  a \n b\t\tc "));
        assertEquals(Notes.Problem.EMPTY, Notes.check(Notes.clean("   "), 200).orElseThrow());
        assertEquals(Notes.Problem.TOO_LONG, Notes.check("x".repeat(201), 200).orElseThrow());
        assertTrue(Notes.check("x".repeat(200), 200).isEmpty());
        assertEquals(40, Notes.cut("y".repeat(80), 40).length());
        assertTrue(Notes.cut("y".repeat(80), 40).endsWith("..."));
        List<Notes.Note> all = List.of(note(1, T0, false), note(2, T0 + 1, true), note(3, T0 + 2, false), note(4, T0 + 3, false),
                note(5, T0 + 4, false));
        assertEquals(List.of(5L, 4L, 3L), Notes.latest(all, 3).stream().map(Notes.Note::id).toList(), "newest active first");
        assertEquals(4, Notes.active(all).size(), "removed notes are hidden");
    }

    private static Notes.Note note(long id, long created, boolean removed) {
        return new Notes.Note(id, STEVE, "Steve", ALEX, "Alex", "note " + id, created, removed, removed ? created : null,
                removed ? ALEX : null, removed ? "Alex" : null);
    }

    // ================================================================ 12.6 acknowledgement (item 7)

    @Test
    void ackRemindsEveryRepeatUpToTheLimitAndStopsWhenAcknowledged() {
        AckTracker acks = new AckTracker();
        Duration repeat = Duration.ofSeconds(30);
        acks.warned(STEVE, T0, repeat);
        assertTrue(acks.pending(STEVE));
        assertEquals(List.of(), acks.due(T0 + 29_000, repeat, 3), "not before 30 s");
        assertEquals(List.of(STEVE), acks.due(T0 + 30_000, repeat, 3));
        assertEquals(List.of(), acks.due(T0 + 31_000, repeat, 3));
        assertEquals(List.of(STEVE), acks.due(T0 + 60_000, repeat, 3));
        assertEquals(List.of(STEVE), acks.due(T0 + 90_000, repeat, 3));
        assertEquals(List.of(), acks.due(T0 + 120_000, repeat, 3), "max-repeats 3");
        assertFalse(acks.reminding(STEVE));
        assertTrue(acks.pending(STEVE), "still owes it (join reminder, /mypunishments)");
        assertTrue(acks.acknowledged(STEVE));
        assertFalse(acks.pending(STEVE));
        assertFalse(acks.acknowledged(STEVE), "a second /ack has nothing to confirm");

        acks.warned(ALEX, T0, repeat);
        assertTrue(acks.acknowledged(ALEX), "click right away");
        assertEquals(List.of(), acks.due(T0 + 60_000, repeat, 3), "no reminders after acknowledging");
        acks.warned(ALEX, T0, repeat);
        acks.forget(ALEX);
        assertFalse(acks.pending(ALEX), "quit forgets (the database keeps the truth)");
    }

    @Test
    void ackNeverBlocksByDefault() {
        assertFalse(PunishConfig.Ack.DEFAULT.blockUntilAck(), "spec 12.6: block-until-ack false");
        assertFalse(PunishConfig.Extras.DEFAULT.ack().blockUntilAck());
        assertEquals(Duration.ofSeconds(30), PunishConfig.Ack.DEFAULT.repeat());
        assertEquals(3, PunishConfig.Ack.DEFAULT.maxRepeats());
    }

    // ================================================================ 12.8 suggestion API: plan (item 9)

    @Test
    void apiSuggestionUsesTheNextLadderStepAndNeverExpiredOffences() {
        Templates.Template spam = new Templates.Template("spam", "chat", null, org.bukkit.Material.PAPER,
                List.of(Templates.Step.parse("warn"), Templates.Step.parse("mute 30m"), Templates.Step.parse("mute 6h")),
                Duration.ofDays(90));
        long now = T0 + 200 * DAY;
        Punishment old = withTemplate(TestData.warn(1, T0), "spam");             // 200 days old: no longer counts
        Punishment recent = withTemplate(TestData.warn(2, now - DAY), "spam");
        ApiSuggestions.Plan plan = ApiSuggestions.plan(spam, List.of(old, recent), "chat filter", "3 spam hits in 10s", now,
                Duration.ofHours(24));
        assertEquals(Templates.Step.parse("mute 30m"), plan.step(), "1 counting offence -> 2nd step");
        assertEquals("chat filter: 3 spam hits in 10s", plan.details());
        assertEquals(now + Duration.ofHours(24).toMillis(), plan.expires());
        ApiSuggestions.Plan first = ApiSuggestions.plan(spam, List.of(), "chat filter", "", now, null);
        assertEquals(Templates.Step.parse("warn"), first.step());
        assertEquals("chat filter", first.details());
        assertNull(first.expires());
        String longText = "x".repeat(400);
        assertTrue(ApiSuggestions.plan(spam, List.of(), "a", longText, now, null).details().length() <= 255);
    }

    private static Punishment withTemplate(Punishment p, String template) {
        return new Punishment(p.id(), p.type(), p.target(), p.targetName(), p.ip(), p.ipWide(), p.reason(), template, 0,
                p.issuer(), p.issuerName(), p.created(), p.expires(), p.silent(), p.removedAt(), p.removedBy(), p.removedByName(),
                p.removedReason(), p.source());
    }

    // ================================================================ 12.11 Discord payload (item 12)

    private static final Map<String, String> LABELS = Map.ofEntries(Map.entry("fields.type", "Type"),
            Map.entry("fields.target", "Player"), Map.entry("fields.staff", "Staff"), Map.entry("fields.reason", "Reason"),
            Map.entry("fields.length", "Length"), Map.entry("fields.weight", "Counts"), Map.entry("fields.id", "ID"),
            Map.entry("fields.rule", "Rule"), Map.entry("fields.given-by", "Given by"), Map.entry("titles.ban", "Ban"),
            Map.entry("titles.mute", "Mute"), Map.entry("titles.warn", "Warning"), Map.entry("titles.kick", "Kick"),
            Map.entry("titles.pardon", "Pardon"), Map.entry("titles.undo", "Undone"), Map.entry("types.ban", "Ban"),
            Map.entry("types.ipban", "IP-ban"), Map.entry("types.mute", "Mute"), Map.entry("types.ipmute", "IP-mute"),
            Map.entry("types.warn", "Warning"), Map.entry("types.kick", "Kick"), Map.entry("system", "System"),
            Map.entry("permanent", "permanent"), Map.entry("footer", "Phoenix Skyblock"), Map.entry("rule-repeat", "repeat"));

    private static String label(String key) {
        if (key.startsWith(PunishEmbeds.RULE_COUNT)) {
            return key.substring(PunishEmbeds.RULE_COUNT.length()) + " warnings";
        }
        String v = LABELS.get(key);
        if (v == null) {
            throw new AssertionError("no label " + key);
        }
        return v;
    }

    @Test
    void discordEmbedHasTheFieldsAndNeverAnIp() {
        Punishment ipban = new Punishment(7, Punishment.Type.BAN, STEVE, "Steve", "203.0.113.9", true, "Ban evasion", "alt-abuse",
                0, ALEX, "Alex", T0, T0 + 7 * DAY, false, null, null, null, null, null);
        DiscordSink.Embed e = PunishEmbeds.applied(ipban, PunishConfig.Discord.DEFAULT, P3LogicTest::label);
        String json = DiscordPayload.json(e);
        assertFalse(json.contains("203.0.113.9"), "no IP address: " + json);
        assertTrue(json.contains("\"IP-ban\""));
        assertTrue(json.contains("\"Steve\"") && json.contains("\"Alex\"") && json.contains("\"#7\"") && json.contains("\"1w\""), json);
        assertTrue(json.contains("\"color\":" + 0xE74C3C));
        assertTrue(json.contains("\"allowed_mentions\":{\"parse\":[]}"), "nobody is pinged");

        Punishment auto = new Punishment(8, Punishment.Type.MUTE, STEVE, "Steve", null, false, "Automatic rule: 2 warnings",
                null, null, null, "System (auto)", T0, null, false, null, null, null, null, "auto:warn-rule:2");
        String autoJson = DiscordPayload.json(PunishEmbeds.applied(auto, PunishConfig.Discord.DEFAULT, P3LogicTest::label));
        assertTrue(autoJson.contains("\"System\""), "no uuid = System: " + autoJson);
        assertTrue(autoJson.contains("\"2 warnings\"") && autoJson.contains("\"permanent\""), autoJson);

        String pardon = DiscordPayload.json(PunishEmbeds.pardoned(List.of(ipban), "Steve", "Mod", "Appeal accepted", T0,
                PunishConfig.Discord.DEFAULT, P3LogicTest::label));
        assertTrue(pardon.contains("\"Pardon\"") && pardon.contains("Appeal accepted") && !pardon.contains("203.0.113.9"), pardon);
        String undo = DiscordPayload.json(PunishEmbeds.undone(ipban, "Mod", "Undone by Mod (mistake)", T0,
                PunishConfig.Discord.DEFAULT, P3LogicTest::label));
        assertTrue(undo.contains("\"Undone\"") && undo.contains("\"Given by\"") && !undo.contains("203.0.113.9"), undo);
    }

    @Test
    void discordJsonIsEscapedAndLimited() {
        DiscordSink.Embed e = new DiscordSink.Embed("T\"itle", 0x123456, List.of(new DiscordSink.Field("Reason",
                "line1\nline2 \"quoted\" \\ *bold* @everyone \u0001", false)), "", T0);
        String json = DiscordPayload.json(e);
        assertTrue(json.contains("\"T\\\"itle\""), json);
        assertTrue(json.contains("line1\\nline2 \\\"quoted\\\" \\\\\\\\ \\\\*bold\\\\* @everyone \\u0001"), json);
        assertTrue(json.contains("\"timestamp\":\"2027-01-15T08:00:00Z\""), json);
        List<DiscordSink.Field> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(new DiscordSink.Field("f" + i, "v".repeat(2000), true));
        }
        String big = DiscordPayload.json(new DiscordSink.Embed("x".repeat(500), 0, many, null, 0));
        assertEquals(DiscordPayload.MAX_FIELDS, big.split("\"name\":").length - 1, "25 fields at most");
        assertFalse(big.contains("v".repeat(DiscordPayload.MAX_VALUE + 1)));
        assertFalse(big.contains("x".repeat(DiscordPayload.MAX_TITLE + 1)));
        assertFalse(big.contains("timestamp"));
    }

    // ================================================================ 12.12 /mypunishments (item 13)

    @Test
    void playerViewShowsOnlyOwnRowsAndHidesStaff() {
        UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000000c");
        Punishment mine = TestData.warn(1, T0);
        Punishment other = new Punishment(2, Punishment.Type.BAN, bob, "Bob", null, false, "x", null, null, ALEX, "Alex", T0,
                null, false, null, null, null, null, null);
        assertEquals(List.of(mine), PlayerView.own(List.of(mine, other), STEVE));
        assertEquals("Staff", PlayerView.staff(mine, false, "Staff", "System"), "hidden by default");
        assertEquals("Alex", PlayerView.staff(mine, true, "Staff", "System"));
        Punishment auto = TestData.mute(3, T0, null, "auto:warn-rule:2");
        Punishment autoNoIssuer = new Punishment(4, auto.type(), STEVE, "Steve", null, false, "x", null, null, null, "System (auto)",
                T0, null, false, null, null, null, null, "auto:warn-rule:2");
        assertEquals("System", PlayerView.staff(autoNoIssuer, true, "Staff", "System"));
        List<Punishment> rows = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            rows.add(TestData.warn(10 + i, T0 + i * HOUR));
        }
        assertEquals(List.of(17L, 16L, 15L, 14L, 13L), PlayerView.last(rows, 5).stream().map(Punishment::id).toList());
        Punishment shortMute = TestData.mute(20, T0, T0 + HOUR, null);
        Punishment permMute = TestData.mute(21, T0, null, null);
        assertEquals(21, PlayerView.activeMute(List.of(shortMute, permMute), T0 + 1).id(), "the permanent one wins");
        assertNull(PlayerView.activeMute(List.of(shortMute), T0 + 2 * HOUR), "expired");
        assertEquals(PunishConfig.PlayerView.DEFAULT.showStaff(), false, "spec 12.12: staff hidden by default");
        assertEquals("https://discord.skybornmc.net", PlayerView.link("discord.skybornmc.net"));
        assertEquals("https://x.net/a", PlayerView.link("https://x.net/a"));
    }

    // ================================================================ config: P3 extras

    @Test
    void trackedExtrasMatchTheSpecDefaults() throws Exception {
        PunishConfigParser.Result r = PunishConfigParser.parse(Files.readString(TRACKED), TestData.ICONS);
        assertTrue(r.ok(), String.valueOf(r.errors()));
        PunishConfig.Extras x = r.config().extras();
        assertTrue(x.closeReports());
        assertEquals(new PunishConfig.Evidence(true, 10, 5, Duration.ofDays(180), 200, 6000), x.evidence());
        assertEquals(new PunishConfig.Notes(200, 3, 40), x.notes());
        assertEquals(new PunishConfig.Ack(Duration.ofSeconds(30), 3, false, true), x.ack());
        assertEquals("https://skybornmc.net/rules", x.rulesLink(), "notes #8: the website");
        assertFalse(x.discord().enabled(), "Discord is postponed: off");
        assertEquals("punishments", x.discord().channel());
        assertEquals(0xE74C3C, x.discord().color("ban"));
        assertFalse(x.playerView().showStaff());
        assertEquals(5, x.playerView().lastEntries());
        assertEquals(new PunishConfig.ChatSuggestion(3, Duration.ofSeconds(10), "spam"), x.chatSuggestions().get("spam"));
    }

    @Test
    void badP3ExtrasAreRejected() {
        String yaml = """
                categories:
                  chat:
                    reasons:
                      spam:
                        ladder: ["warn"]
                extras:
                  evidence:
                    chat-lines: 500
                  notes:
                    max-length: 9999
                  rules-link: "https://x.net/<b>"
                  discord:
                    webhook: "https://discord.com/api/webhooks/1/abc"
                    colors: { ban: "red", purple: "#ffffff" }
                  chat-suggestions:
                    caps: { hits: 3, within: 10s, reason: capslock }
                """;
        PunishConfigParser.Result r = PunishConfigParser.parse(yaml, TestData.ICONS);
        assertFalse(r.ok());
        List<String> keys = r.errors().stream().map(ConfigProblem::key).toList();
        for (String k : List.of("config.bad-number", "config.bad-link", "config.webhook-in-config", "config.bad-color",
                "config.unknown-key", "config.unknown-reason")) {
            assertTrue(keys.contains(k), k + " in " + keys);
        }
        assertEquals(2, keys.stream().filter("config.bad-number"::equals).count(), keys.toString());
    }

    @Test
    void everyP3TextLivesInTheMessagesFile() {
        for (Path file : List.of(MESSAGES, TRACKED_MESSAGES)) {
            YamlConfiguration msg = YamlConfiguration.loadConfiguration(file.toFile());
            List<String> keys = new ArrayList<>(List.of("steps.warn-weighted", "ack.prompt", "ack.reminder", "ack.join",
                    "ack.button", "ack.hover", "ack.done", "ack.nothing", "ack.blocked", "ack.menu.title", "ack.menu.button.name",
                    "evidence.ip-hidden", "evidence.blocked", "entry.info.name", "entry.info.lore", "entry.evidence-name",
                    "entry.evidence-line", "entry.evidence-more", "entry.evidence-click", "entry.evidence-none.name",
                    "entry.evidence-type.name", "entry.chat-header", "entry.chat-line", "notes.added", "notes.header",
                    "notes.none", "notes.line", "notes.removed", "notes.already-removed", "notes.not-found", "notes.bad-id",
                    "notes.empty", "notes.too-long", "reports.closed", "reports.open", "mine.header", "mine.no-mute", "mine.mute",
                    "mine.warnings", "mine.warning", "mine.ack", "mine.recent-header", "mine.none", "mine.entry",
                    "mine.entry-staff", "suggest.api-notify", "warns.api-pending", "warns.ack-yes", "warns.ack-no",
                    "menu.history.weight", "menu.history.ack-yes", "menu.history.ack-no", "menu.history.click",
                    "punish.summary.api-pending", "punish.summary.notes-header", "punish.summary.note",
                    "punish.summary.notes-more", "punish.summary.notes-none", "punish.warn-button.weight", "config.bad-weight",
                    "config.bad-link", "config.webhook-in-config", "config.unknown-key", "config.bad-color", "config.missing-key",
                    "discord.system", "discord.permanent", "discord.footer", "discord.rule-repeat", "discord.rule-count"));
            for (String kind : PunishConfig.Discord.KINDS) {
                keys.add("discord.titles." + kind);
            }
            for (String f : List.of("type", "target", "staff", "reason", "length", "weight", "id", "rule", "given-by")) {
                keys.add("discord.fields." + f);
            }
            for (String t : List.of("ban", "ipban", "mute", "ipmute", "warn", "kick")) {
                keys.add("discord.types." + t);
            }
            for (String key : keys) {
                assertTrue(msg.contains(key), file + ": missing " + key);
            }
        }
    }
}
