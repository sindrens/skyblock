package net.skybornmc.skyblock.moderation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P10 without a server: monthly report month boundaries in Oslo time (also across daylight saving), when the scheduled
 * run is due, the report math on known data (totals, reasons, reversed split, appeals, reports, requests, staff ranking,
 * reoffence), no player data in the output, JSON and CSV; the clean streak conditions (days, account age, playtime per
 * period from daily samples, reversed punishments don't break it, a new one hides it); config and messages.
 */
class P10LogicTest {

    private static final Path BUNDLED = Path.of("src/main/resources/punishments.yml");
    private static final Path TRACKED = Path.of("../../server/plugins/PhoenixSkyblock/punishments.yml");
    private static final Path MESSAGES = Path.of("../../plugin/src/main/resources/messages/moderation.yml");
    private static final Path TRACKED_MESSAGES = Path.of("../../server/plugins/PhoenixSkyblock/messages/moderation.yml");
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final UUID STEVE = TestData.STEVE;
    private static final UUID ALEX = TestData.ALEX;
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    private static final UUID MALLORY = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final long DAY = TestData.DAY;
    private static final long HOUR = TestData.HOUR;
    private static final MonthlyReport.Settings RS = MonthlyReport.Settings.DEFAULT;

    private static long oslo(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, OSLO).toInstant().toEpochMilli();
    }

    // ================================================================ 14.8 month boundaries (Oslo)

    @Test
    void monthBoundariesAreOsloMidnightAcrossDaylightSaving() {
        // October 2026 starts in summer time (UTC+2) and ends in winter time (UTC+1, DST ends 25 October)
        assertEquals(Instant.parse("2026-09-30T22:00:00Z").toEpochMilli(), MonthlyReport.start(OCT, OSLO));
        assertEquals(Instant.parse("2026-10-31T23:00:00Z").toEpochMilli(), MonthlyReport.end(OCT, OSLO));
        assertEquals(31 * DAY + HOUR, MonthlyReport.end(OCT, OSLO) - MonthlyReport.start(OCT, OSLO), "October has an extra hour");
        YearMonth march = YearMonth.of(2026, 3);
        assertEquals(31 * DAY - HOUR, MonthlyReport.end(march, OSLO) - MonthlyReport.start(march, OSLO), "March loses an hour");
        assertEquals(MonthlyReport.end(OCT, OSLO), MonthlyReport.start(OCT.plusMonths(1), OSLO), "no gap between months");
    }

    @Test
    void momentsNearMidnightBelongToTheOsloMonth() {
        assertEquals(OCT, MonthlyReport.monthOf(Instant.parse("2026-09-30T22:30:00Z").toEpochMilli(), OSLO), "00:30 on 1 Oct in Oslo");
        assertEquals(YearMonth.of(2026, 11), MonthlyReport.monthOf(Instant.parse("2026-10-31T23:30:00Z").toEpochMilli(), OSLO));
        assertEquals(YearMonth.of(2026, 9), MonthlyReport.monthOf(Instant.parse("2026-09-30T21:59:59Z").toEpochMilli(), OSLO));
        // a punishment at 00:30 Oslo on 1 November counts for November, not October
        long late = Instant.parse("2026-10-31T23:30:00Z").toEpochMilli();
        MonthlyReport.Report r = MonthlyReport.compute(input(OCT, oslo(2026, 11, 1, 9, 0),
                List.of(p(Punishment.Type.WARN, STEVE, "spam", 0, ALEX, late, MonthlyReport.Reversal.NONE),
                        p(Punishment.Type.WARN, STEVE, "spam", 0, ALEX, MonthlyReport.start(OCT, OSLO), MonthlyReport.Reversal.NONE))), RS);
        assertEquals(1, r.totals().warns());
        assertFalse(r.partial());
    }

    @Test
    void scheduledRunIsDueAfterNineOnTheFirstAndCatchesUp() {
        Set<String> none = Set.of();
        assertEquals(Optional.empty(), MonthlyReport.due(oslo(2026, 11, 1, 8, 59), OSLO, RS, none));
        assertEquals(Optional.of(OCT), MonthlyReport.due(oslo(2026, 11, 1, 9, 0), OSLO, RS, none));
        assertEquals(Optional.of(OCT), MonthlyReport.due(oslo(2026, 11, 5, 3, 0), OSLO, RS, none), "after downtime");
        assertEquals(Optional.empty(), MonthlyReport.due(oslo(2026, 11, 5, 3, 0), OSLO, RS, Set.of("2026-10")), "announced once");
        MonthlyReport.Settings off = new MonthlyReport.Settings(false, 1, LocalTime.of(9, 0), Duration.ofDays(30), 10, 10, 15, false, true);
        assertEquals(Optional.empty(), MonthlyReport.due(oslo(2026, 11, 2, 12, 0), OSLO, off, none));
        // January: the December report of the previous year
        assertEquals(Optional.of(YearMonth.of(2026, 12)), MonthlyReport.due(oslo(2027, 1, 1, 9, 30), OSLO, RS, none));
        MonthlyReport.Settings late = new MonthlyReport.Settings(true, 28, LocalTime.of(23, 0), Duration.ofDays(30), 10, 10, 15, false, true);
        assertEquals(Optional.empty(), MonthlyReport.due(oslo(2027, 2, 28, 22, 59), OSLO, late, none));
        assertEquals(Optional.of(YearMonth.of(2027, 1)), MonthlyReport.due(oslo(2027, 2, 28, 23, 0), OSLO, late, none));
    }

    @Test
    void monthArgument() {
        assertEquals(Optional.of(OCT), MonthlyReport.parseMonth("2026-10"));
        assertEquals(Optional.of(YearMonth.of(2026, 1)), MonthlyReport.parseMonth(" 2026-1 "));
        assertEquals(Optional.empty(), MonthlyReport.parseMonth("2026-13"));
        assertEquals(Optional.empty(), MonthlyReport.parseMonth("october"));
        assertEquals(Optional.empty(), MonthlyReport.parseMonth(null));
        YearMonth now = YearMonth.of(2026, 11);
        assertEquals(MonthlyReport.Ask.PAST, MonthlyReport.ask(OCT, now));
        assertEquals(MonthlyReport.Ask.CURRENT, MonthlyReport.ask(now, now));
        assertEquals(MonthlyReport.Ask.FUTURE, MonthlyReport.ask(now.plusMonths(1), now));
        assertEquals(List.of("2026-10", "2026-11", "2026-09"), MonthlyReport.hints(now, List.of("2026-10", "2026-09")));
        assertEquals("2026-03", MonthlyReport.key(YearMonth.of(2026, 3)));
    }

    // ================================================================ 14.8 report math

    private static MonthlyReport.PRow p(Punishment.Type type, UUID target, String template, Integer step, UUID issuer, long created,
                                        MonthlyReport.Reversal reversal) {
        return new MonthlyReport.PRow(type, target, template, step, issuer, issuer == null ? null : name(issuer), created, reversal);
    }

    private static String name(UUID id) {
        if (id.equals(ALEX)) {
            return "Alex";
        }
        if (id.equals(BOB)) {
            return "Bob";
        }
        return id.equals(ADMIN) ? "Admin" : "Someone";
    }

    private static MonthlyReport.Input input(YearMonth month, long now, List<MonthlyReport.PRow> rows) {
        return new MonthlyReport.Input(month, OSLO, now, rows, List.of(), List.of(), List.of(), null, List.of(), List.of(), 0);
    }

    private static long oct(int day) {
        return oslo(2026, 10, day, 12, 0);
    }

    @Test
    void numbersMatchKnownTestData() {
        long made = oslo(2026, 11, 1, 9, 0);
        List<MonthlyReport.PRow> rows = List.of(
                p(Punishment.Type.WARN, STEVE, "spam", 0, ALEX, oct(2), MonthlyReport.Reversal.NONE),
                p(Punishment.Type.WARN, MALLORY, "spam", 0, ALEX, oct(3), MonthlyReport.Reversal.UNDO),
                p(Punishment.Type.MUTE, STEVE, "spam", 1, ALEX, oct(4), MonthlyReport.Reversal.NONE),
                p(Punishment.Type.BAN, MALLORY, "hacking", 0, BOB, oct(5), MonthlyReport.Reversal.APPEAL),
                p(Punishment.Type.BAN, STEVE, "hacking", 0, BOB, oct(6), MonthlyReport.Reversal.ROLLBACK),
                p(Punishment.Type.KICK, STEVE, null, null, BOB, oct(7), MonthlyReport.Reversal.NONE),
                p(Punishment.Type.MUTE, STEVE, "toxicity", 0, null, oct(8), MonthlyReport.Reversal.PARDON), // system
                p(Punishment.Type.ISLAND_BAN, STEVE, "griefing", 0, ALEX, oct(9), MonthlyReport.Reversal.NONE),
                p(Punishment.Type.REPORT_BLOCK, STEVE, "report-abuse", 1, ALEX, oct(10), MonthlyReport.Reversal.NONE),
                // outside October: never counted in the totals
                p(Punishment.Type.BAN, STEVE, "hacking", 0, BOB, oslo(2026, 9, 20, 12, 0), MonthlyReport.Reversal.NONE),
                p(Punishment.Type.BAN, STEVE, "hacking", 0, BOB, oslo(2026, 11, 2, 12, 0), MonthlyReport.Reversal.NONE));
        List<MonthlyReport.SanctionRow> sanctions = List.of(new MonthlyReport.SanctionRow(ADMIN, "Admin", oct(11)),
                new MonthlyReport.SanctionRow(ADMIN, "Admin", oslo(2026, 9, 11, 12, 0)));
        List<MonthlyReport.AppealRow> appeals = List.of(
                new MonthlyReport.AppealRow(ADMIN, "Admin", "ACCEPTED", oct(5), oct(5) + 2 * HOUR),
                new MonthlyReport.AppealRow(ADMIN, "Admin", "DENIED", oct(10), oct(10) + 4 * HOUR),
                new MonthlyReport.AppealRow(null, null, "OPEN", oct(30), null),
                // created in September, decided in October: a decision of October, not received in October
                new MonthlyReport.AppealRow(BOB, "Bob", "REDUCED", oslo(2026, 9, 29, 12, 0), oct(1)),
                // created in October, decided in November: received, not decided
                new MonthlyReport.AppealRow(ADMIN, "Admin", "DENIED", oct(31), oslo(2026, 11, 1, 1, 0)));
        List<MonthlyReport.RequestRow> requests = List.of(
                new MonthlyReport.RequestRow(ALEX, "Alex", true, ADMIN, "Admin", "APPROVED", oct(3), oct(4)),
                new MonthlyReport.RequestRow(ALEX, "Alex", true, ADMIN, "Admin", "DENIED", oct(4), oct(5)),
                new MonthlyReport.RequestRow(BOB, "Bob", false, null, null, "PENDING", oct(5), oct(6)),
                new MonthlyReport.RequestRow(BOB, "Bob", false, null, null, "PENDING", oct(31), made + DAY));
        List<MonthlyReport.ReportRow> reports = List.of(
                new MonthlyReport.ReportRow(oct(2), oct(2) + HOUR, "INVALID"),
                new MonthlyReport.ReportRow(oct(3), oct(3) + 3 * HOUR, "PUNISHED"),
                new MonthlyReport.ReportRow(oct(31), null, null));
        List<MonthlyReport.DryRow> dry = List.of(new MonthlyReport.DryRow(ALEX, "Alex", true, oct(2)),
                new MonthlyReport.DryRow(BOB, "Bob", false, oct(3)));
        List<MonthlyReport.GuardRow> guard = List.of(new MonthlyReport.GuardRow(BOB, "Bob", oct(6)));
        MonthlyReport.Report r = MonthlyReport.compute(new MonthlyReport.Input(OCT, OSLO, made, rows, sanctions, appeals,
                requests, reports, dry, guard, 2), RS);

        assertEquals(new MonthlyReport.Totals(2, 2, 2, 1, 1, 1, 1), r.totals());
        assertEquals(9, r.totals().punishments());
        assertEquals(10, r.totals().all());
        assertEquals(new MonthlyReport.Reversed(9, 1, 1, 1, 1), r.reversed());
        assertEquals(44, r.reversed().percent(), "4 of 9");
        assertEquals(new MonthlyReport.Count("spam", 3), r.topReasons().get(0));
        assertEquals(new MonthlyReport.Count("hacking", 2), r.topReasons().get(1));
        assertTrue(r.topReasons().contains(new MonthlyReport.Count("-", 1)), "typed reasons are grouped");

        MonthlyReport.Appeals a = r.appeals();
        assertEquals(4, a.received());
        assertEquals(1, a.accepted());
        assertEquals(1, a.reduced());
        assertEquals(1, a.denied());
        assertEquals(1, a.open());
        assertEquals(33, a.acceptedPercent());
        long expectedAvg = (2 * HOUR + 4 * HOUR + (oct(1) - oslo(2026, 9, 29, 12, 0))) / 3;
        assertEquals(expectedAvg, a.avgHandleMs());

        assertEquals(new MonthlyReport.Reports(3, 2, 1, 2 * HOUR), r.reports());
        assertEquals(new MonthlyReport.Requests(4, 1, 1, 0, 1, 1, 2), r.requests());
        assertEquals(1, r.guardEvents());
        assertEquals(2, r.falseReportSuggestions());
        assertEquals(2, r.dryRuns());
        assertEquals(1, r.traineeDryRuns());

        // staff ranking: most given first; system rows never count
        List<MonthlyReport.StaffLine> staff = r.staff();
        assertEquals(List.of("Alex", "Bob", "Admin"), staff.stream().map(MonthlyReport.StaffLine::name).toList());
        MonthlyReport.StaffLine alex = staff.get(0);
        assertEquals(5, alex.given());
        assertEquals(2, alex.other(), "island ban + report block");
        assertEquals(1, alex.reversed());
        assertEquals(20, alex.reversedPercent());
        assertEquals(2, alex.requestsMade());
        assertEquals(1, alex.requestsApproved());
        assertEquals(1, alex.requestsDenied());
        assertEquals(1, alex.dryRuns());
        assertTrue(alex.trainee());
        MonthlyReport.StaffLine bob = staff.get(1);
        assertEquals(3, bob.given());
        assertEquals(2, bob.reversed());
        assertEquals(1, bob.guardEvents());
        assertEquals(1, bob.appealsReduced());
        assertFalse(bob.trainee());
        MonthlyReport.StaffLine admin = staff.get(2);
        assertEquals(0, admin.given());
        assertEquals(1, admin.sanctions());
        assertEquals(1, admin.appealsAccepted());
        assertEquals(1, admin.appealsDenied(), "the November decision is not October's");
        assertEquals(1, admin.approvedForOthers());
    }

    @Test
    void reportsAreNullWithoutTheReportsModuleAndEmptyMonthsAreZero() {
        MonthlyReport.Report r = MonthlyReport.compute(input(OCT, oslo(2026, 10, 15, 12, 0), List.of()), RS);
        assertNull(r.reports());
        assertTrue(r.partial(), "made before the month ended");
        assertEquals(0, r.reversed().percent());
        assertEquals(0, r.appeals().acceptedPercent());
        assertTrue(r.topReasons().isEmpty());
        assertTrue(r.staff().isEmpty());
        assertEquals("2026-09", r.cohortMonth());
    }

    @Test
    void outputHoldsNoPlayerData() {
        List<MonthlyReport.PRow> rows = List.of(p(Punishment.Type.BAN, MALLORY, "hacking", 0, ALEX, oct(3), MonthlyReport.Reversal.NONE));
        MonthlyReport.Report r = MonthlyReport.compute(input(OCT, oslo(2026, 11, 1, 9, 0), rows), RS);
        String json = MonthlyReport.toJson(r);
        String csv = String.join("\n", MonthlyReport.csv(r));
        for (String out : List.of(json, csv)) {
            assertFalse(out.contains(MALLORY.toString()), "no player account ids");
            assertFalse(out.contains(ALEX.toString()), "no staff account ids either");
            assertFalse(out.matches("(?s).*\\d+\\.\\d+\\.\\d+\\.\\d+.*"), "no IP addresses");
        }
        assertTrue(json.contains("Alex"), "staff names only in the staff section");
        assertEquals(1, r.staff().size());
    }

    @Test
    void reoffenceRatePerReasonAndStep() {
        long sep = MonthlyReport.start(YearMonth.of(2026, 9), OSLO);
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        UUID p4 = UUID.randomUUID();
        UUID p5 = UUID.randomUUID();
        List<MonthlyReport.PRow> rows = new ArrayList<>(List.of(
                // spam step 2 in September: p1 again after 10 days (yes), p2 again after 40 days (no), p3 never (no),
                // p4 again only with a reversed punishment (no), p5 again with another reason (no)
                p(Punishment.Type.MUTE, p1, "spam", 1, ALEX, sep + DAY, MonthlyReport.Reversal.NONE),
                p(Punishment.Type.MUTE, p1, "spam", 2, ALEX, sep + 11 * DAY, MonthlyReport.Reversal.NONE),
                p(Punishment.Type.MUTE, p2, "spam", 1, ALEX, sep + 2 * DAY, MonthlyReport.Reversal.NONE),
                p(Punishment.Type.MUTE, p2, "spam", 2, ALEX, sep + 42 * DAY, MonthlyReport.Reversal.NONE),
                p(Punishment.Type.MUTE, p3, "spam", 1, ALEX, sep + 3 * DAY, MonthlyReport.Reversal.NONE),
                p(Punishment.Type.MUTE, p4, "spam", 1, ALEX, sep + 4 * DAY, MonthlyReport.Reversal.NONE),
                p(Punishment.Type.MUTE, p4, "spam", 2, ALEX, sep + 5 * DAY, MonthlyReport.Reversal.UNDO),
                p(Punishment.Type.MUTE, p5, "spam", 1, ALEX, sep + 6 * DAY, MonthlyReport.Reversal.NONE),
                p(Punishment.Type.MUTE, p5, "toxicity", 0, ALEX, sep + 7 * DAY, MonthlyReport.Reversal.NONE),
                // a reversed cohort row is no offence: not in the cohort
                p(Punishment.Type.WARN, p3, "advertising", 0, ALEX, sep + 8 * DAY, MonthlyReport.Reversal.PARDON)));
        MonthlyReport.Report r = MonthlyReport.compute(input(OCT, oslo(2026, 11, 20, 9, 0), rows), RS);
        assertEquals("2026-09", r.cohortMonth());
        MonthlyReport.Reoffence spam2 = r.reoffence().get(0);
        assertEquals(new MonthlyReport.Reoffence("spam", 2, 5, 1), spam2, "step 2 shown 1-based (stored index 1)");
        assertEquals(20, spam2.percent());
        assertTrue(r.reoffence().contains(new MonthlyReport.Reoffence("spam", 3, 1, 0)), "p1's step 3 in September");
        assertTrue(r.reoffence().stream().noneMatch(o -> o.reason().equals("advertising")));
        // only what happened before the report was made counts
        List<MonthlyReport.Reoffence> early = MonthlyReport.reoffence(rows, sep, MonthlyReport.start(OCT, OSLO),
                Duration.ofDays(30).toMillis(), sep + 5 * DAY);
        assertTrue(early.contains(new MonthlyReport.Reoffence("spam", 2, 5, 0)), early.toString());
    }

    @Test
    void reversalKindsOfOldRows() {
        assertEquals(MonthlyReport.Reversal.NONE, MonthlyReport.Reversal.of(null, "UNDO", null));
        assertEquals(MonthlyReport.Reversal.UNDO, MonthlyReport.Reversal.of(1L, "UNDO", null));
        assertEquals(MonthlyReport.Reversal.ROLLBACK, MonthlyReport.Reversal.of(1L, null, 7L));
        assertEquals(MonthlyReport.Reversal.PARDON, MonthlyReport.Reversal.of(1L, null, null));
        assertEquals(MonthlyReport.Reversal.PARDON, MonthlyReport.Reversal.of(1L, "weird", null));
    }

    @Test
    void jsonRoundTripAndBrokenJson() {
        List<MonthlyReport.PRow> rows = List.of(p(Punishment.Type.WARN, STEVE, "spam", 0, ALEX, oct(3), MonthlyReport.Reversal.UNDO));
        MonthlyReport.Report r = MonthlyReport.compute(new MonthlyReport.Input(OCT, OSLO, oslo(2026, 11, 1, 9, 0), rows, List.of(),
                List.of(), List.of(), List.of(new MonthlyReport.ReportRow(oct(1), oct(2), "INVALID")), List.of(), List.of(), 1), RS);
        assertEquals(Optional.of(r), MonthlyReport.fromJson(MonthlyReport.toJson(r)));
        assertEquals(Optional.empty(), MonthlyReport.fromJson("{not json"));
        assertEquals(Optional.empty(), MonthlyReport.fromJson(""));
        assertEquals(Optional.empty(), MonthlyReport.fromJson("{\"month\":\"nope\"}"));
    }

    @Test
    void csvFile() {
        MonthlyReport.Report r = MonthlyReport.compute(input(OCT, oslo(2026, 11, 1, 9, 0),
                List.of(p(Punishment.Type.WARN, STEVE, "spam", 0, ALEX, oct(3), MonthlyReport.Reversal.NONE))), RS);
        List<String> csv = MonthlyReport.csv(r);
        assertEquals("section,name,value", csv.get(0));
        assertTrue(csv.contains("totals,warns,1"));
        assertTrue(csv.contains("reason,spam,1"));
        assertTrue(csv.stream().anyMatch(l -> l.startsWith("Alex,1,1,0,0,0,0,0,0,0,")));
        assertEquals("punishments-2026-10.csv", MonthlyReport.csvName(OCT));
        assertEquals("\"a,b\"", MonthlyReport.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", MonthlyReport.cell("say \"hi\""));
        assertEquals("'=HYPERLINK(1)", MonthlyReport.cell("=HYPERLINK(1)"), "spreadsheet formulas are defused");
        assertEquals("-5", MonthlyReport.cell("-5"));
    }

    // ================================================================ 14.11 clean streak

    private static final CleanStreak.Settings CS = CleanStreak.Settings.DEFAULT;
    private static final long NOW = 1_800_000_000_000L;

    private static CleanStreak.Facts facts(long accountAgeDays, Long lastBreakingDaysAgo, long playedHours, boolean owns,
                                           CleanStreak.State state) {
        return new CleanStreak.Facts(NOW, NOW - accountAgeDays * DAY, lastBreakingDaysAgo == null ? null : NOW - lastBreakingDaysAgo * DAY,
                playedHours * HOUR, owns, state);
    }

    @Test
    void unlockNeedsCleanDaysAccountAgeAndPlaytime() {
        assertEquals(60, CS.days());
        assertEquals(Duration.ofHours(10), CS.minPlaytime());
        assertEquals(Duration.ofHours(6), CS.check());
        assertEquals(CleanStreak.Action.GRANT, CleanStreak.decide(CS, facts(100, null, 10, false, CleanStreak.State.NONE)));
        assertEquals(CleanStreak.Action.GRANT, CleanStreak.decide(CS, facts(100, 60L, 12, false, CleanStreak.State.NONE)), "exactly 60 days");
        assertEquals(CleanStreak.Action.NONE, CleanStreak.decide(CS, facts(100, 59L, 12, false, CleanStreak.State.NONE)), "59 days");
        assertEquals(CleanStreak.Action.NONE, CleanStreak.decide(CS, facts(59, null, 50, false, CleanStreak.State.NONE)), "account too new");
        assertEquals(CleanStreak.Action.NONE, CleanStreak.decide(CS, facts(400, null, 9, false, CleanStreak.State.NONE)), "9h played");
        CleanStreak.Settings off = new CleanStreak.Settings(false, 60, Duration.ofHours(10), Duration.ofHours(6), Duration.ofSeconds(10),
                "honorable", "unlock", true);
        assertEquals(CleanStreak.Action.NONE, CleanStreak.decide(off, facts(100, null, 10, false, CleanStreak.State.NONE)));
        // earned again after a loss, once the new punishment is 60 days old
        CleanStreak.State lost = new CleanStreak.State(false, NOW - 200 * DAY, NOW - 70 * DAY, 1);
        assertEquals(CleanStreak.Action.GRANT, CleanStreak.decide(CS, facts(400, 70L, 20, false, lost)));
        assertEquals(CleanStreak.Action.NONE, CleanStreak.decide(CS, facts(400, 30L, 20, false, lost)));
        // owned without being earned here (e.g. a title id changed in the config): never taken
        assertEquals(CleanStreak.Action.NONE, CleanStreak.decide(CS, facts(10, 1L, 0, true, CleanStreak.State.NONE)));
    }

    @Test
    void anEarnedTitleIsHiddenOnlyByANewPunishment() {
        CleanStreak.State earned = new CleanStreak.State(true, NOW - 10 * DAY, null, 1);
        assertEquals(CleanStreak.Action.NONE, CleanStreak.decide(CS, facts(400, 80L, 0, true, earned)), "playtime later doesn't matter");
        assertEquals(CleanStreak.Action.REVOKE, CleanStreak.decide(CS, facts(400, 2L, 50, true, earned)), "punished after earning");
        assertEquals(CleanStreak.Action.REGRANT_QUIET, CleanStreak.decide(CS, facts(400, 80L, 0, false, earned)), "title row missing");
        // reversed punishments are not "on record" (the store leaves them out): lastBreaking stays the old one
        assertEquals(CleanStreak.Action.NONE, CleanStreak.decide(CS, facts(400, null, 0, true, earned)));
        assertTrue(CleanStreak.breaks(Punishment.Type.WARN));
        assertTrue(CleanStreak.breaks(Punishment.Type.KICK));
        assertFalse(CleanStreak.breaks(Punishment.Type.ISLAND_BAN), "spec: warn, mute, ban or kick only");
        assertFalse(CleanStreak.breaks(Punishment.Type.REPORT_BLOCK));
    }

    /** SMALL2: the check decides off the main thread and decides again on it with the owned titles of that moment. */
    @Test
    void decidingAgainWithFreshOwnership() {
        CleanStreak.State earned = new CleanStreak.State(true, NOW - 10 * DAY, null, 1);
        CleanStreak.Facts missing = facts(400, 80L, 0, false, earned);
        assertEquals(CleanStreak.Action.REGRANT_QUIET, CleanStreak.decide(CS, missing));
        assertEquals(CleanStreak.Action.NONE, CleanStreak.decide(CS, missing.withOwns(true)), "granted meanwhile: nothing to do");
        assertEquals(missing.state(), missing.withOwns(true).state());
        assertEquals(missing.played(), missing.withOwns(true).played());
        assertEquals(CleanStreak.Action.NONE, CleanStreak.decide(CS, facts(30, null, 1, false, CleanStreak.State.NONE)),
                "the usual join: nothing to do, so no main-thread hop");
    }

    @Test
    void playtimeWithinThePeriodFromDailySamples() {
        long now = oslo(2026, 12, 31, 20, 0);
        int today = CleanStreak.day(now, OSLO);
        int start = CleanStreak.startDay(now, OSLO, CS);
        assertEquals(today - 60, start);
        NavigableMap<Integer, Long> samples = new TreeMap<>();
        samples.put(start - 5, 100 * HOUR);
        samples.put(start, 102 * HOUR);
        samples.put(start + 10, 105 * HOUR);
        assertEquals(10 * HOUR, CleanStreak.played(112 * HOUR, samples, start), "total now - total at the start of the period");
        assertEquals(start, CleanStreak.pruneBefore(samples, start), "older samples are no longer needed");
        // the samples began later (after the update): only what was seen counts, never more
        NavigableMap<Integer, Long> late = new TreeMap<>(java.util.Map.of(start + 30, 500 * HOUR));
        assertEquals(4 * HOUR, CleanStreak.played(504 * HOUR, late, start));
        // first check ever: today's sample is the baseline, nothing played yet
        NavigableMap<Integer, Long> first = CleanStreak.withToday(new TreeMap<>(), today, 900 * HOUR);
        assertEquals(0, CleanStreak.played(900 * HOUR, first, start));
        assertNull(CleanStreak.pruneBefore(new TreeMap<>(), start));
        // a day is an Oslo day: 23:30 UTC on 31 Dec is already 1 January in Oslo
        assertEquals(CleanStreak.day(oslo(2027, 1, 1, 0, 30), OSLO),
                CleanStreak.day(Instant.parse("2026-12-31T23:30:00Z").toEpochMilli(), OSLO));
    }

    // ================================================================ config + messages

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PunishConfigParser.Result parse(String yaml) {
        return PunishConfigParser.parse(yaml, TestData.ICONS);
    }

    @Test
    void shippedConfigHasTheP10Blocks() {
        for (Path file : List.of(BUNDLED, TRACKED)) {
            PunishConfigParser.Result r = parse(read(file));
            assertTrue(r.ok(), file + ": " + r.errors());
            assertEquals(MonthlyReport.Settings.DEFAULT, r.config().extras().report(), file.toString());
            assertEquals(CleanStreak.Settings.DEFAULT, r.config().extras().cleanStreak(), file.toString());
        }
        assertEquals(read(BUNDLED), read(TRACKED));
        assertFalse(MonthlyReport.Settings.DEFAULT.discord(), "Discord off by default");
    }

    @Test
    void badP10ConfigIsRejectedWithThePath() {
        String base = read(BUNDLED);
        assertProblem(base.replace("time: \"09:00\"", "time: \"25:00\""), "config.bad-clock", "extras.report.time");
        assertProblem(base.replace("day: 1                    # day", "day: 31                   # day"), "config.bad-number", "extras.report.day");
        assertProblem(base.replace("reoffence-window: 30d", "reoffence-window: 60d"), "config.duration-too-long", "extras.report.reoffence-window");
        assertProblem(base.replace("title: honorable", "title: Honorable!"), "config.bad-title-id", "extras.clean-streak.title");
        assertProblem(base.replace("check: 6h ", "check: 1m "), "config.duration-too-short", "extras.clean-streak.check");
        assertProblem(base.replace("days: 60 ", "days: 0 "), "config.bad-number", "extras.clean-streak.days");
        // an older file without the P10 blocks still loads with the defaults
        PunishConfigParser.Result old = parse(base.substring(0, base.indexOf("  # ---------- owner spec section 14 (P10)")));
        assertTrue(old.ok(), old.errors().toString());
        assertEquals(MonthlyReport.Settings.DEFAULT, old.config().extras().report());
        assertEquals(CleanStreak.Settings.DEFAULT, old.config().extras().cleanStreak());
        assertEquals(LocalTime.of(9, 30), PunishConfigParser.clock("9:30"));
        assertNull(PunishConfigParser.clock("9.30"));
    }

    private static void assertProblem(String yaml, String key, String path) {
        PunishConfigParser.Result r = parse(yaml);
        assertFalse(r.ok(), "expected " + key + " at " + path);
        assertTrue(r.errors().stream().anyMatch(p -> p.key().equals(key) && p.path().equals(path)), r.errors().toString());
    }

    @Test
    void everyP10MessageKeyExists() {
        List<String> keys = new ArrayList<>(List.of("config.bad-clock", "config.bad-title-id", "report.ready", "report.generating",
                "report.failed", "report.bad-month", "report.future", "report.custom-reason", "report.chat.header",
                "report.chat.header-partial", "report.chat.totals", "report.chat.reversed", "report.chat.appeals",
                "report.chat.reports", "report.chat.reports-none", "report.chat.requests", "report.chat.safety",
                "report.chat.reasons-header", "report.chat.reason", "report.chat.reoffence-header", "report.chat.reoffence",
                "report.chat.staff-header", "report.chat.staff", "report.chat.staff-trainee", "report.chat.empty",
                "report.menu.title", "report.menu.info", "report.menu.reason-line", "report.menu.reoffence-line",
                "report.menu.staff-line", "report.menu.staff-line-trainee", "report.menu.empty", "discord.report.title",
                "discord.report.values.reports-none", "discord.report.values.reason", "discord.footer",
                "clean-streak.unlocked", "clean-streak.lost", "clean-streak.title.display", "clean-streak.title.source"));
        for (String item : List.of("header", "header-partial", "totals", "reversed", "appeals", "reports", "reports-none",
                "requests", "safety", "reasons", "reoffence", "staff", "staff-locked", "chat", "previous", "next")) {
            keys.add("report.menu." + item + ".name");
            keys.add("report.menu." + item + ".lore");
        }
        for (String f : List.of("totals", "reversed", "appeals", "reports", "requests", "safety", "reasons")) {
            keys.add("discord.report.fields." + f);
        }
        for (String f : List.of("totals", "reversed", "appeals", "reports", "requests", "safety")) {
            keys.add("discord.report.values." + f);
        }
        for (int m = 1; m <= 12; m++) {
            keys.add("report.months." + m);
        }
        for (Path file : List.of(MESSAGES, TRACKED_MESSAGES)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            for (String key : keys) {
                assertTrue(yaml.contains(key), file + " misses " + key);
            }
        }
        assertEquals(read(MESSAGES), read(TRACKED_MESSAGES));
    }
}
