package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P5 pure logic: bulk selection and limits (13.8), the appeal rules and state machine (13.10), log-to-punish (13.11). */
class P5LogicTest {

    private static final UUID STEVE = TestData.STEVE;
    private static final UUID ALEX = TestData.ALEX;
    private static final long DAY = TestData.DAY;
    private static final long HOUR = TestData.HOUR;

    // ================================================================ 13.8 bulk

    @Test
    void bulkNamesAreUniqueKeepOrderAndRespectTheMax() {
        BulkSelection.Names n = BulkSelection.parse("Steve alex, Notch  steve ALEX", 20);
        assertEquals(List.of("Steve", "alex", "Notch"), n.names());
        assertEquals(List.of("Steve", "alex"), n.duplicates());
        assertFalse(n.tooMany());
        assertTrue(BulkSelection.parse("   ", 20).empty());
        assertTrue(BulkSelection.parse(null, 20).empty());

        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            many.append("p").append(i).append(' ');
        }
        BulkSelection.Names over = BulkSelection.parse(many.toString(), 20);
        assertTrue(over.tooMany(), "21 > max 20 is refused, never cut silently");
        assertEquals(21, over.names().size());
        assertFalse(BulkSelection.parse(many.toString().replace("p20 ", ""), 20).tooMany(), "exactly 20 is fine");
    }

    @Test
    void bulkPickerToggleStopsAtTheMax() {
        Set<Integer> selected = new LinkedHashSet<>();
        assertTrue(BulkSelection.toggle(selected, 1, 2));
        assertTrue(BulkSelection.toggle(selected, 2, 2));
        assertFalse(BulkSelection.toggle(selected, 3, 2), "full");
        assertEquals(Set.of(1, 2), selected);
        assertTrue(BulkSelection.toggle(selected, 1, 2), "unselect always works");
        assertTrue(BulkSelection.toggle(selected, 3, 2));
        assertEquals(List.of(2, 3), List.copyOf(selected));
    }

    private static PunishDecision.Input input(Punishment.Type type, Duration d) {
        return new PunishDecision.Input(TestData.staff(null), type, d, false, false, false, false, true, null, null, 0);
    }

    @Test
    void bulkLinesComeFromThePipelineVerdict() {
        Templates.Step planned = Templates.Step.parse("mute 30m");
        PunishDecision.Verdict apply = PunishDecision.Verdict.apply(input(Punishment.Type.MUTE, Duration.ofMinutes(30)));
        BulkSelection.Line a = BulkSelection.line("Steve", apply, planned);
        assertEquals(BulkSelection.Kind.APPLY, a.kind());
        assertEquals(Duration.ofMinutes(30), a.step().duration());
        assertNull(a.reason());

        PunishDecision.Verdict request = new PunishDecision.Verdict(PunishDecision.Outcome.REQUEST, "approval", "approval.requested",
                Map.of(), input(Punishment.Type.BAN, null));
        BulkSelection.Line r = BulkSelection.line("Alex", request, Templates.Step.parse("ban perm"));
        assertEquals(BulkSelection.Kind.REQUEST, r.kind(), "ban perm in bulk needs approval per player (13.4)");

        PunishDecision.Verdict exempt = new PunishDecision.Verdict(PunishDecision.Outcome.BLOCKED, "exempt", "error.exempt",
                Map.of(), input(Punishment.Type.MUTE, Duration.ofMinutes(30)));
        PunishDecision.Verdict longer = new PunishDecision.Verdict(PunishDecision.Outcome.SKIPPED, "never-shorten",
                "error.already-muted", Map.of("left", "2h"), input(Punishment.Type.MUTE, Duration.ofMinutes(30)));
        PunishDecision.Verdict dup = new PunishDecision.Verdict(PunishDecision.Outcome.BLOCKED, "duplicate", "error.duplicate",
                Map.of(), input(Punishment.Type.MUTE, Duration.ofMinutes(30)));
        assertEquals("exempt", BulkSelection.line("A", exempt, planned).reason());
        assertEquals("never-shorten", BulkSelection.line("B", longer, planned).reason());
        assertEquals(BulkSelection.Kind.SKIP, BulkSelection.line("C", dup, planned).kind(), "no 'click again' in bulk");

        List<BulkSelection.Line> lines = List.of(a, r, BulkSelection.line("A", exempt, planned),
                new BulkSelection.Line("Nobody", BulkSelection.Kind.SKIP, null, BulkSelection.UNKNOWN));
        BulkSelection.Summary s = BulkSelection.summary(lines);
        assertEquals(new BulkSelection.Summary(1, 1, 2), s);
        assertEquals(4, s.total());
    }

    @Test
    void bulkLineShowsTheStepProbationRaisedTo() {
        List<Templates.Step> ladder = List.of(Templates.Step.parse("mute 30m"), Templates.Step.parse("mute 6h"),
                Templates.Step.parse("mute 1d"));
        PunishDecision.Escalation raised = new PunishDecision.Escalation(ladder, 1, false, 1L, 1, true);
        PunishDecision.Input in = input(Punishment.Type.MUTE, Duration.ofHours(6)).withEscalation(raised);
        BulkSelection.Line l = BulkSelection.line("Steve", PunishDecision.Verdict.apply(in), ladder.getFirst());
        assertEquals(Duration.ofHours(6), l.step().duration(), "each player gets their own step");
    }

    @Test
    void bulkGroupAndSuggestions() {
        String g = BulkSelection.group(1_700_000_000_000L, 77);
        assertTrue(g.startsWith(BulkSelection.SOURCE_PREFIX));
        assertTrue(g.length() <= 48, "fits the source column");
        assertTrue(BulkSelection.isBulk(g));
        assertFalse(BulkSelection.isBulk("auto:warn-rule:2"));
        assertFalse(BulkSelection.isBulk(null));

        List<String> known = List.of("Steve", "Alex", "Notch", "Sam");
        assertEquals(List.of("Steve", "Sam"), BulkSelection.suggestions("s", known, 20));
        assertEquals(List.of("Steve Alex", "Steve Notch", "Steve Sam"), BulkSelection.suggestions("Steve ", known, 20),
                "names typed already are not offered again");
        assertEquals(List.of(), BulkSelection.suggestions("Steve Alex ", known, 2), "full: nothing more");
    }

    // ================================================================ 13.10 appeals

    private static final PunishConfig.Appeals CFG = PunishConfig.Appeals.DEFAULT;

    private static Punishment mute(long id, long created, Long expires) {
        return TestData.mute(id, created, expires, null);
    }

    private static Appeal appeal(long id, long pid, Appeal.Status status, long created, Long handled) {
        return new Appeal(id, pid, Punishment.Type.MUTE, STEVE, "Steve", ALEX, "Alex", "please let me talk again", status,
                status == Appeal.Status.OPEN ? null : ALEX, status == Appeal.Status.OPEN ? null : "Alex", null, null, null,
                created, handled, false);
    }

    @Test
    void appealStateMachineOnlyMovesFromOpenOnce() {
        for (Appeal.Status to : Appeal.Status.values()) {
            assertEquals(to != Appeal.Status.OPEN, Appeal.Status.OPEN.canMoveTo(to));
        }
        for (Appeal.Status from : List.of(Appeal.Status.ACCEPTED, Appeal.Status.REDUCED, Appeal.Status.DENIED, Appeal.Status.CLOSED)) {
            assertTrue(from.terminal());
            for (Appeal.Status to : Appeal.Status.values()) {
                assertFalse(from.canMoveTo(to), from + " never changes");
            }
        }
        assertFalse(Appeal.Status.OPEN.terminal());
    }

    @Test
    void whoMayAppealWhat() {
        long now = 10 * DAY;
        Punishment p = mute(1, now - HOUR, now + DAY);
        assertEquals(Optional.empty(), AppealRules.canAppeal(p, STEVE, List.of(), true, CFG, now));
        assertEquals(Optional.of(AppealRules.Problem.NOT_YOURS), AppealRules.canAppeal(p, ALEX, List.of(), true, CFG, now));
        assertEquals(Optional.of(AppealRules.Problem.NOT_FOUND), AppealRules.canAppeal(null, STEVE, List.of(), true, CFG, now));
        assertEquals(Optional.of(AppealRules.Problem.REASON), AppealRules.canAppeal(p, STEVE, List.of(), false, CFG, now),
                "appealable: false (alt abuse, RMT)");
        assertEquals(Optional.of(AppealRules.Problem.NOT_ACTIVE), AppealRules.canAppeal(mute(2, 0, now - 1), STEVE, List.of(),
                true, CFG, now));
        Punishment warn = TestData.warn(3, now - HOUR);
        assertEquals(Optional.of(AppealRules.Problem.TYPE), AppealRules.canAppeal(warn, STEVE, List.of(), true, CFG, now),
                "default types: mute, ban");
        PunishConfig.Appeals off = new PunishConfig.Appeals(false, CFG.types(), CFG.cooldown(), 20, 500, CFG.reminder(), 365,
                CFG.denyReasons(), false, "appeals");
        assertEquals(Optional.of(AppealRules.Problem.DISABLED), AppealRules.canAppeal(p, STEVE, List.of(), true, off, now));
    }

    @Test
    void oneOpenAppealPerPunishmentAndACooldownAfterADeny() {
        long now = 10 * DAY;
        Punishment p = TestData.punishment(1, Punishment.Type.BAN, now - DAY, null, null, null);
        assertEquals(Optional.of(AppealRules.Problem.ALREADY_OPEN), AppealRules.canAppeal(p, STEVE,
                List.of(appeal(1, 1, Appeal.Status.OPEN, now - HOUR, null)), true, CFG, now));
        List<Appeal> denied = List.of(appeal(1, 1, Appeal.Status.DENIED, now - 2 * DAY, now - DAY));
        assertEquals(Optional.of(AppealRules.Problem.COOLDOWN), AppealRules.canAppeal(p, STEVE, denied, true, CFG, now));
        assertEquals(now - DAY + 7 * DAY, AppealRules.cooldownUntil(denied, CFG.cooldown()));
        assertEquals(Optional.empty(), AppealRules.canAppeal(p, STEVE, denied, true, CFG, now + 6 * DAY + 1), "after 7d");
        List<Appeal> closed = List.of(appeal(1, 1, Appeal.Status.CLOSED, now - 2 * DAY, now - DAY));
        assertEquals(Optional.empty(), AppealRules.canAppeal(p, STEVE, closed, true, CFG, now), "closed: no decision, no cooldown");
        List<Appeal> reduced = List.of(appeal(1, 1, Appeal.Status.REDUCED, now - 2 * DAY, now - DAY));
        assertEquals(Optional.of(AppealRules.Problem.COOLDOWN), AppealRules.canAppeal(p, STEVE, reduced, true, CFG, now));
    }

    @Test
    void appealTextLimitsAndArguments() {
        assertEquals(Optional.of(AppealRules.Problem.TOO_SHORT), AppealRules.checkText("too short", CFG));
        assertEquals(Optional.of(AppealRules.Problem.TOO_SHORT), AppealRules.checkText("   a      b        c       d   ", CFG),
                "whitespace runs count once");
        assertEquals(Optional.empty(), AppealRules.checkText("x".repeat(20), CFG));
        assertEquals(Optional.empty(), AppealRules.checkText("x".repeat(500), CFG));
        assertEquals(Optional.of(AppealRules.Problem.TOO_LONG), AppealRules.checkText("x".repeat(501), CFG));
        assertEquals("a b", AppealRules.clean(" a\n\tb "));

        assertEquals(new AppealRules.Args(12L, "I did nothing wrong at all"), AppealRules.parse("12 I did nothing wrong at all"));
        assertEquals(new AppealRules.Args(12L, "text here"), AppealRules.parse("#12 text here"));
        assertEquals(new AppealRules.Args(null, "I did nothing wrong"), AppealRules.parse("I did nothing wrong"));
        assertEquals(new AppealRules.Args(null, "12"), AppealRules.parse("12"), "an id alone is not an appeal");
    }

    @Test
    void appealWithoutIdPicksTheActiveMuteFirst() {
        long now = 10 * DAY;
        Punishment oldBan = TestData.punishment(1, Punishment.Type.BAN, now - 3 * DAY, null, null, null);
        Punishment shortMute = mute(2, now - HOUR, now + HOUR);
        Punishment longMute = mute(3, now - 2 * HOUR, now + DAY);
        Punishment ended = mute(4, now - DAY, now - 1);
        assertEquals(3, AppealRules.pick(List.of(oldBan, shortMute, longMute, ended), STEVE, CFG, t -> true, now).id());
        assertEquals(1, AppealRules.pick(List.of(oldBan, ended), STEVE, CFG, t -> true, now).id());
        assertNull(AppealRules.pick(List.of(ended), STEVE, CFG, t -> true, now));
        assertNull(AppealRules.pick(List.of(longMute), STEVE, CFG, t -> false, now), "not appealable reasons are not picked");
    }

    @Test
    void reduceRangeIsAlwaysShorter() {
        long now = 10 * DAY;
        AppealRules.ReduceRange perm = AppealRules.reduceRange(TestData.punishment(1, Punishment.Type.BAN, now, null, null, null), 365);
        assertEquals(AppealRules.Unit.DAYS, perm.unit());
        assertEquals(365, perm.max());
        AppealRules.ReduceRange week = AppealRules.reduceRange(mute(2, now, now + 7 * DAY), 365);
        assertEquals(AppealRules.Unit.DAYS, week.unit());
        assertEquals(6, week.max(), "strictly shorter than 7d");
        AppealRules.ReduceRange hours = AppealRules.reduceRange(mute(3, now, now + 6 * HOUR), 365);
        assertEquals(AppealRules.Unit.HOURS, hours.unit());
        assertEquals(5, hours.max());
        AppealRules.ReduceRange minutes = AppealRules.reduceRange(mute(4, now, now + 30 * 60_000L), 365);
        assertEquals(AppealRules.Unit.MINUTES, minutes.unit());
        assertEquals(29, minutes.max());
        assertNull(AppealRules.reduceRange(mute(5, now, now + 60_000L), 365), "1 minute can't be reduced");

        Punishment m = mute(6, now - DAY, now + 6 * DAY);
        assertEquals(now - DAY + 3 * DAY, AppealRules.reducedEnd(m, AppealRules.Unit.DAYS, 3, now));
        assertNull(AppealRules.reducedEnd(m, AppealRules.Unit.DAYS, 7, now), "not shorter");
        assertEquals(now, AppealRules.reducedEnd(m, AppealRules.Unit.HOURS, 1, now), "already served: ends now");
        assertNull(AppealRules.reducedEnd(m, AppealRules.Unit.DAYS, 0, now));
    }

    @Test
    void historySummaryCountsTypes() {
        List<Punishment> h = List.of(TestData.warn(1, 0), TestData.warn(2, 0), mute(3, 0, 1L),
                TestData.punishment(4, Punishment.Type.BAN, 0, null, null, null));
        assertEquals(new AppealRules.HistorySummary(1, 1, 2, 0), AppealRules.summary(h));
    }

    @Test
    void acceptingAnotherStaffMembersPunishmentNamesThem() {
        Appeal a = appeal(1, 1, Appeal.Status.OPEN, 0, null);
        assertTrue(a.otherStaff(UUID.randomUUID()));
        assertFalse(a.otherStaff(ALEX), "your own punishment");
    }

    @Test
    void discordAppealPayloadHasNoIp() {
        Punishment p = new Punishment(7, Punishment.Type.BAN, STEVE, "Steve", "10.1.2.3", true, "Ban evasion", null, null, ALEX,
                "Alex", 0, null, false, null, null, null, null, null);
        Appeal a = new Appeal(3, 7, Punishment.Type.BAN, STEVE, "Steve", ALEX, "Alex", "I share the wifi with my brother",
                Appeal.Status.OPEN, null, null, null, null, null, 0, null, false);
        String json = DiscordPayload.json(PunishEmbeds.appealNew(a, p, new AppealRules.HistorySummary(1, 0, 2, 0),
                PunishConfig.Discord.DEFAULT, key -> key));
        assertFalse(json.contains("10.1.2.3"), "never the IP");
        assertTrue(json.contains("#3"));
        assertTrue(json.contains("I share the wifi"));
        Appeal denied = new Appeal(3, 7, Punishment.Type.BAN, STEVE, "Steve", ALEX, "Alex", "x", Appeal.Status.DENIED, ALEX,
                "Bob", "The evidence is clear", null, null, 0, 5L, false);
        String decision = DiscordPayload.json(PunishEmbeds.appealDecision(denied, PunishConfig.Discord.DEFAULT, key -> key));
        assertTrue(decision.contains("The evidence is clear"));
        assertTrue(decision.contains("titles.appeal-denied"));
    }

    private static final java.nio.file.Path BUNDLED = java.nio.file.Path.of("src/main/resources/punishments.yml");
    private static final java.nio.file.Path TRACKED = java.nio.file.Path.of("../../server/plugins/PhoenixSkyblock/punishments.yml");

    @Test
    void shippedConfigHasTheP5Defaults() throws Exception {
        for (java.nio.file.Path file : List.of(BUNDLED, TRACKED)) {
            PunishConfigParser.Result r = PunishConfigParser.parse(java.nio.file.Files.readString(file), TestData.ICONS);
            assertTrue(r.ok(), () -> file + ": " + r.errors());
            PunishConfig.Extras x = r.config().extras();
            assertEquals(20, x.bulk().max());
            assertTrue(x.appeal().enabled());
            assertEquals(Set.of(Punishment.Type.MUTE, Punishment.Type.BAN), x.appeal().types());
            assertEquals(Duration.ofDays(7), x.appeal().cooldown());
            assertEquals(20, x.appeal().minLength());
            assertEquals(500, x.appeal().maxLength());
            assertEquals(Duration.ofHours(48), x.appeal().reminder());
            assertFalse(x.appeal().discordEnabled(), "Discord is postponed: off by default");
            assertEquals("appeals", x.appeal().discordChannel());
            Templates t = new Templates();
            t.use(r.config());
            assertFalse(t.appealable("alt-abuse"), "owner spec: no appeals for alt abuse");
            assertFalse(t.appealable("rmt"), "owner spec: no appeals for RMT");
            assertTrue(t.appealable("spam"));
            assertTrue(t.appealable(null), "direct commands can be appealed");
        }
    }

    @Test
    void badP5KeysAreRejected() throws Exception {
        String base = java.nio.file.Files.readString(BUNDLED);
        assertTrue(PunishConfigParser.parse(base.replace("appealable-types: [mute, ban]", "appealable-types: [mute, warn]"),
                TestData.ICONS).ok());
        assertFalse(PunishConfigParser.parse(base.replace("appealable-types: [mute, ban]", "appealable-types: [mute, kick]"),
                TestData.ICONS).ok(), "a kick is over at once");
        assertFalse(PunishConfigParser.parse(base.replace("    max: 20 ", "    max: 500 "), TestData.ICONS).ok());
        assertFalse(PunishConfigParser.parse(base.replace("cooldown: 7d", "cooldown: soon"), TestData.ICONS).ok());
        assertFalse(PunishConfigParser.parse(base.replace("channel: appeals", "url: https://discord.example/x"),
                TestData.ICONS).ok(), "a webhook URL never belongs in the file");
        assertFalse(PunishConfigParser.parse(base.replace("appealable: false           # owner spec 13.10: no appeals for alt",
                "appealable: maybe           # owner spec 13.10: no appeals for alt"), TestData.ICONS).ok());
    }

    @Test
    void bundledDefaultsShipAppealsOnAndDiscordOff() {
        PunishConfig.Appeals d = PunishConfig.Appeals.DEFAULT;
        assertTrue(d.enabled());
        assertFalse(d.discordEnabled(), "Discord is postponed: no network calls by default");
        assertEquals(Set.of(Punishment.Type.MUTE, Punishment.Type.BAN), d.types());
        assertEquals(20, PunishConfig.Bulk.DEFAULT.max());
    }

    // ================================================================ 13.11 punish from logs

    private static AuditEntry chat(long id, UUID who, String name, String action, AuditCategory category, String message) {
        return new AuditEntry(id, 1_700_000_000_000L, 0, "phoenix", category, action, new Actor(who, name), null, null, null,
                "islands", 1, 64, 1, Map.of("message", message));
    }

    @Test
    void onlyTheTargetsOwnChatLinesCanBeEvidence() {
        assertEquals(Optional.empty(), LogEvidence.check(chat(5, STEVE, "Steve", "chat", AuditCategory.CHAT, "hi"), STEVE));
        assertEquals(Optional.empty(), LogEvidence.check(chat(5, STEVE, "Steve", "chat_blocked", AuditCategory.CHAT, "hi"), STEVE),
                "a line the filter blocked is evidence too");
        assertEquals(Optional.of(LogEvidence.Problem.OTHER_PLAYER),
                LogEvidence.check(chat(5, ALEX, "Alex", "chat", AuditCategory.CHAT, "hi"), STEVE));
        assertEquals(Optional.of(LogEvidence.Problem.NOT_CHAT),
                LogEvidence.check(chat(5, STEVE, "Steve", "pay", AuditCategory.ECONOMY, "hi"), STEVE));
        assertEquals(Optional.of(LogEvidence.Problem.NOT_CHAT),
                LogEvidence.check(chat(5, STEVE, "Steve", "command", AuditCategory.CHAT, "hi"), STEVE));
        assertEquals(Optional.of(LogEvidence.Problem.NOT_FOUND), LogEvidence.check(null, STEVE));
        assertEquals("log-evidence.other-player", LogEvidence.Problem.OTHER_PLAYER.key());
    }

    @Test
    void anchorPreviewHidesIpsAndIsCut() {
        AuditEntry e = chat(42, STEVE, "Steve", "chat", AuditCategory.CHAT, "join 1.2.3.4:25565 now\nfree stuff " + "x".repeat(80));
        LogEvidence.Anchor a = LogEvidence.anchor(e, ZoneId.of("Europe/Oslo"), 60, "[IP]");
        assertEquals(42, a.id());
        assertEquals(STEVE, a.player());
        assertFalse(a.line().contains("1.2.3.4"));
        assertTrue(a.line().contains("[IP]"));
        assertFalse(a.line().contains("\n"));
        assertTrue(a.line().length() <= 60);
        assertTrue(a.line().startsWith("[23:13] Steve: "), a.line());
    }

    @Test
    void evidenceFlagIsParsedFromThePunishFlags() {
        ModerationModule.Reason r = ModerationModule.Reason.parse("-s --evidence 1234");
        assertTrue(r.silent());
        assertEquals(1234L, r.evidence());
        assertFalse(r.badEvidence());
        assertEquals("", r.text());
        assertEquals(99L, ModerationModule.Reason.parse("--evidence #99 -force").evidence());
        assertTrue(ModerationModule.Reason.parse("--evidence").badEvidence(), "flag without id");
        assertTrue(ModerationModule.Reason.parse("--evidence abc").badEvidence());
        assertTrue(ModerationModule.Reason.parse("--evidence 1 --evidence 2").badEvidence(), "two different lines");
        assertNull(ModerationModule.Reason.parse("Spam -s").evidence());
        assertNull(LogEvidence.parseId("0"));
        assertNull(LogEvidence.parseId("-5"));
        assertEquals(7L, LogEvidence.parseId(" 7 "));
    }

    @Test
    void evidenceFlagIsOfferedButNoIdsAfterIt() {
        List<String> flags = Flags.allowed(perm -> true);
        assertTrue(flags.contains(LogEvidence.FLAG));
        assertTrue(Flags.suggestions("-", flags).contains(LogEvidence.FLAG));
        assertEquals(List.of(), Flags.suggestions("--evidence ", flags), "tab completion only for the flag itself");
        assertNotNull(Flags.suggestions("--evidence 12 ", flags));
        assertFalse(Flags.suggestions("--evidence 12 ", flags).isEmpty(), "other flags after the id");
    }
}
