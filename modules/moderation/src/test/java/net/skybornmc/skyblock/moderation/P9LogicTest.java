package net.skybornmc.skyblock.moderation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P9 without a server: the staff guard (14.1: limits per tier, block refuses the one over the limit, alert only alerts,
 * locks, allowances, the sliding window, bulk counting per player, read-only paths untouched), rollback selection (only
 * that staff member's rows in the period, never kicks, preview numbers, period limits, confirm matching), false reports
 * (14.5: only invalid counts, threshold, weekly cap, report-block step falls to the next step when unavailable) and the
 * purge rules (14.10: what is kept, anonymised and deleted). Plus the shipped config and messages.
 */
class P9LogicTest {

    private static final Path BUNDLED = Path.of("src/main/resources/punishments.yml");
    private static final Path TRACKED = Path.of("../../server/plugins/PhoenixSkyblock/punishments.yml");
    private static final Path MESSAGES = Path.of("../../plugin/src/main/resources/messages/moderation.yml");
    private static final Path TRACKED_MESSAGES = Path.of("../../server/plugins/PhoenixSkyblock/messages/moderation.yml");
    private static final UUID ALEX = TestData.ALEX;
    private static final UUID STEVE = TestData.STEVE;
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final long MIN = 60_000L;
    private static final long NOW = 1_800_000_000_000L;

    private static PunishConfigParser.Result parse(String yaml) {
        return PunishConfigParser.parse(yaml, TestData.ICONS);
    }

    private static final Guard.Settings S = Guard.Settings.DEFAULT;

    // ================================================================ 14.1 guard: limits per tier

    @Test
    void defaultTiersMatchTheSpecProposals() {
        assertEquals(new Guard.Tier(Guard.Mode.BLOCK, 5, 15, 0, 25), S.tier("helper"));
        assertEquals(new Guard.Tier(Guard.Mode.BLOCK, 10, 25, 3, 40), S.tier("mod"));
        assertEquals(new Guard.Tier(Guard.Mode.ALERT, 30, 60, 10, 100), S.tier("admin"));
        assertEquals(Duration.ofMinutes(5), S.window());
    }

    @Test
    void tierIsTheHighestHeldOrTheDefault() {
        assertEquals("mod", Guard.tierOf(S, List.of("helper", "mod")));
        assertEquals("admin", Guard.tierOf(S, List.of("admin", "helper")));
        assertEquals("helper", Guard.tierOf(S, List.of()), "staff without a max tier get the default tier");
        assertEquals("helper", Guard.tierOf(S, List.of("unknown")));
    }

    @Test
    void blockModeRefusesTheEleventhBanAndBlocks() {
        Guard.Counts tenBans = new Guard.Counts(10, 0, 0, 10);
        Guard.Decision d = Guard.decide(S, "mod", null, tenBans, Guard.Kind.BAN, NOW);
        assertEquals(Guard.Verdict.BLOCK_NOW, d.verdict());
        assertEquals(new Guard.Hit(Guard.Limit.BANS, 11, 10), d.hit());
        assertTrue(d.refused());
        // the tenth is still fine
        assertEquals(Guard.Verdict.OK, Guard.decide(S, "mod", null, new Guard.Counts(9, 0, 0, 9), Guard.Kind.BAN, NOW).verdict());
        // mutes have their own limit: 10 bans don't stop a mute
        assertEquals(Guard.Verdict.OK, Guard.decide(S, "mod", null, tenBans, Guard.Kind.MUTE, NOW).verdict());
    }

    @Test
    void totalLimitCountsEverything() {
        Guard.Counts c = new Guard.Counts(0, 0, 0, 40);
        Guard.Decision warn = Guard.decide(S, "mod", null, c, Guard.Kind.OTHER, NOW);
        assertEquals(Guard.Verdict.BLOCK_NOW, warn.verdict());
        assertEquals(Guard.Limit.TOTAL, warn.hit().limit());
    }

    @Test
    void zeroMeansNoneAllowedAndMinusOneMeansNoLimit() {
        assertEquals(Guard.Verdict.BLOCK_NOW, Guard.decide(S, "helper", null, Guard.Counts.ZERO, Guard.Kind.SANCTION, NOW).verdict(),
                "helpers: max-sanctions 0");
        Guard.Settings open = new Guard.Settings(true, Duration.ofMinutes(5),
                Map.of("x", new Guard.Tier(Guard.Mode.BLOCK, -1, -1, -1, -1)), "x", Duration.ofMinutes(30), Duration.ofHours(1));
        assertEquals(Guard.Verdict.OK, Guard.decide(open, "x", null, new Guard.Counts(999, 999, 999, 9999), Guard.Kind.BAN, NOW).verdict());
    }

    @Test
    void alertModeOnlyAlerts() {
        Guard.Decision d = Guard.decide(S, "admin", null, new Guard.Counts(30, 0, 0, 30), Guard.Kind.BAN, NOW);
        assertEquals(Guard.Verdict.ALERT, d.verdict());
        assertFalse(d.refused(), "a legit mass cleanup by an admin is never blocked");
    }

    @Test
    void blockedAndLockedAreRefusedAllowedOnlyAlerts() {
        Guard.Entry blocked = new Guard.Entry(ALEX, "Alex", Guard.State.BLOCKED, null, "bans 11/10", "guard", NOW);
        assertEquals(Guard.Verdict.BLOCKED, Guard.decide(S, "mod", blocked, Guard.Counts.ZERO, Guard.Kind.OTHER, NOW).verdict());
        Guard.Entry locked = new Guard.Entry(ALEX, "Alex", Guard.State.LOCKED, null, null, "Admin", NOW);
        assertEquals(Guard.Verdict.LOCKED, Guard.decide(S, "mod", locked, Guard.Counts.ZERO, Guard.Kind.OTHER, NOW).verdict());
        assertEquals(Guard.Verdict.LOCKED, Guard.decide(S, null, locked, Guard.Counts.ZERO, Guard.Kind.OTHER, NOW).verdict(),
                "a lock holds even for staff without a tier");
        Guard.Settings off = new Guard.Settings(false, S.window(), S.tiers(), S.defaultTier(), S.allowDefault(), S.alertRollback());
        assertEquals(Guard.Verdict.LOCKED, Guard.decide(off, "mod", locked, Guard.Counts.ZERO, Guard.Kind.OTHER, NOW).verdict(),
                "a lock holds with the guard off");
        assertEquals(Guard.Verdict.OK, Guard.decide(off, "mod", blocked, new Guard.Counts(99, 0, 0, 99), Guard.Kind.BAN, NOW).verdict());
        Guard.Entry allowed = new Guard.Entry(ALEX, "Alex", Guard.State.ALLOWED, NOW + 30 * MIN, null, "Admin", NOW);
        assertEquals(Guard.Verdict.ALERT, Guard.decide(S, "mod", allowed, new Guard.Counts(10, 0, 0, 10), Guard.Kind.BAN, NOW).verdict());
        Guard.Entry ended = new Guard.Entry(ALEX, "Alex", Guard.State.ALLOWED, NOW - 1, null, "Admin", NOW - 31 * MIN);
        assertEquals(Guard.Verdict.BLOCK_NOW, Guard.decide(S, "mod", ended, new Guard.Counts(10, 0, 0, 10), Guard.Kind.BAN, NOW).verdict(),
                "after the allowed time the limits block again");
    }

    @Test
    void noTierMeansNeverLimited() {
        assertEquals(Guard.Verdict.OK, Guard.decide(S, null, null, new Guard.Counts(500, 500, 500, 1500), Guard.Kind.BAN, NOW).verdict());
    }

    @Test
    void windowSlides() {
        Guard.Window w = new Guard.Window();
        Duration five = Duration.ofMinutes(5);
        for (int i = 0; i < 10; i++) {
            w.record(ALEX, Guard.Kind.BAN, NOW + i * 10_000L);
        }
        w.record(ALEX, Guard.Kind.MUTE, NOW + 100_000L);
        w.record(BOB, Guard.Kind.BAN, NOW);
        assertEquals(new Guard.Counts(10, 1, 0, 11), w.counts(ALEX, five, NOW + 100_000L));
        // 5 minutes after the first ban, it falls out (exactly at the edge: out)
        assertEquals(new Guard.Counts(9, 1, 0, 10), w.counts(ALEX, five, NOW + 5 * MIN));
        assertEquals(new Guard.Counts(0, 1, 0, 1), w.counts(ALEX, five, NOW + 5 * MIN + 95_000L));
        assertEquals(Guard.Counts.ZERO, w.counts(ALEX, five, NOW + 20 * MIN));
        assertEquals(new Guard.Counts(1, 0, 0, 1), w.counts(BOB, five, NOW + MIN), "per staff member");
        w.clear(BOB);
        assertEquals(Guard.Counts.ZERO, w.counts(BOB, five, NOW + MIN));
    }

    @Test
    void bulkCountsPerPlayer() {
        // a bulk action is one punishment per player through the same pipeline: 11 players = 11 bans in the window
        Guard.Window w = new Guard.Window();
        Guard.Decision last = null;
        int applied = 0;
        for (int player = 0; player < 11; player++) {
            last = Guard.decide(S, "mod", null, w.counts(ALEX, S.window(), NOW), Guard.Kind.BAN, NOW);
            if (!last.refused()) {
                w.record(ALEX, Guard.Kind.BAN, NOW);
                applied++;
            }
        }
        assertEquals(10, applied);
        assertEquals(Guard.Verdict.BLOCK_NOW, last.verdict());
    }

    private static PunishDecision.Actor staff(String tier) {
        return new PunishDecision.Actor("Alex", false, Set.of(Punishment.Type.values()), true, true, null, false, ALEX, false,
                true, false, false, tier);
    }

    private static PunishDecision.Input input(PunishDecision.Actor actor, Punishment.Type type) {
        return new PunishDecision.Input(actor, type, Duration.ofDays(1), false, false, false, false, true, null, null, NOW);
    }

    @Test
    void guardStageStopsOnlyRefusedVerdictsAndSkipsTheSystem() {
        Map<String, Guard.Decision> answers = new LinkedHashMap<>();
        PunishDecision.GuardView view = (staff, tier, kind, now) -> answers.getOrDefault(kind.name(), Guard.Decision.OK);
        PunishDecision d = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT, Training.Settings.DEFAULT, view);
        assertTrue(d.slots().contains(PunishDecision.Slot.GUARD));
        answers.put("BAN", new Guard.Decision(Guard.Verdict.BLOCK_NOW, new Guard.Hit(Guard.Limit.BANS, 11, 10)));
        PunishDecision.Verdict v = d.decide(input(staff("mod"), Punishment.Type.BAN));
        assertEquals(PunishDecision.Outcome.BLOCKED, v.outcome());
        assertEquals("guard", v.stage());
        assertEquals("guard.blocked", v.key());
        assertEquals("block", v.args().get("trigger"));
        assertEquals("bans", v.args().get("limit"));
        assertEquals("11", v.args().get("count"));
        // a mute is fine
        assertEquals(PunishDecision.Outcome.APPLY, d.decide(input(staff("mod"), Punishment.Type.MUTE)).outcome());
        // alert = applied
        answers.put("MUTE", new Guard.Decision(Guard.Verdict.ALERT, new Guard.Hit(Guard.Limit.MUTES, 61, 60)));
        assertEquals(PunishDecision.Outcome.APPLY, d.decide(input(staff("admin"), Punishment.Type.MUTE)).outcome());
        // locked: refused, not a new block
        answers.put("OTHER", new Guard.Decision(Guard.Verdict.LOCKED, null));
        PunishDecision.Verdict locked = d.decide(input(staff("mod"), Punishment.Type.WARN));
        assertEquals("guard.locked", locked.key());
        assertNull(locked.args().get("trigger"));
        // the system rules and the console are never limited
        PunishDecision.Input system = input(PunishDecision.Actor.system("System"), Punishment.Type.BAN);
        assertEquals(PunishDecision.Outcome.APPLY, d.decide(system).outcome());
    }

    @Test
    void guardRunsBeforeTrainingSoALockedTraineeCantEvenRequest() {
        PunishDecision.GuardView locked = (staff, tier, kind, now) -> new Guard.Decision(Guard.Verdict.LOCKED, null);
        PunishDecision d = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT, Training.Settings.DEFAULT, locked);
        PunishDecision.Actor trainee = staff("helper").withTrainee(true);
        PunishDecision.Verdict v = d.decide(input(trainee, Punishment.Type.BAN));
        assertEquals(PunishDecision.Outcome.BLOCKED, v.outcome());
        assertEquals("guard", v.stage());
    }

    @Test
    void guardKindOfTypes() {
        assertEquals(Guard.Kind.BAN, Guard.Kind.of(Punishment.Type.BAN));
        assertEquals(Guard.Kind.MUTE, Guard.Kind.of(Punishment.Type.MUTE));
        assertEquals(Guard.Kind.OTHER, Guard.Kind.of(Punishment.Type.WARN));
        assertEquals(Guard.Kind.OTHER, Guard.Kind.of(Punishment.Type.KICK));
        assertEquals(Guard.Kind.OTHER, Guard.Kind.of(Punishment.Type.REPORT_BLOCK));
        assertEquals("bans 11/10", Guard.hitText(new Guard.Hit(Guard.Limit.BANS, 11, 10)));
    }

    // ================================================================ 14.1 rollback

    private static Punishment row(long id, Punishment.Type type, UUID issuer, long created, Long expires, Long removedAt) {
        return new Punishment(id, type, STEVE, "Steve", null, false, "x", "spam", 0, issuer, issuer == ALEX ? "Alex" : "Bob",
                created, expires, false, removedAt, null, null, null, null);
    }

    @Test
    void rollbackSelectsOnlyThatStaffMembersRowsInThePeriod() {
        long since = NOW - 60 * MIN;
        List<Punishment> rows = List.of(
                row(1, Punishment.Type.BAN, ALEX, NOW - 10 * MIN, null, null),          // yes
                row(2, Punishment.Type.MUTE, ALEX, NOW - 59 * MIN, NOW + MIN, null),     // yes
                row(3, Punishment.Type.MUTE, ALEX, NOW - 61 * MIN, NOW + MIN, null),     // before the period
                row(4, Punishment.Type.BAN, BOB, NOW - 10 * MIN, null, null),            // other staff
                row(5, Punishment.Type.KICK, ALEX, NOW - 10 * MIN, null, null),          // kicks never
                row(6, Punishment.Type.WARN, ALEX, NOW - 10 * MIN, null, NOW - 5 * MIN), // already pardoned
                row(7, Punishment.Type.MUTE, ALEX, NOW - 30 * MIN, NOW - 20 * MIN, null), // ended by time: reversed, not lifted
                row(8, Punishment.Type.ISLAND_KICK, ALEX, NOW - 10 * MIN, null, null),   // never
                row(9, Punishment.Type.COOP_REMOVE, ALEX, NOW - 10 * MIN, null, null),   // never
                row(10, Punishment.Type.REPORT_BLOCK, ALEX, NOW - 10 * MIN, NOW + MIN, null), // yes
                row(11, Punishment.Type.WARN, ALEX, NOW + MIN, null, null));             // after the preview
        List<Punishment> picked = RollbackRules.select(rows, ALEX, since, NOW);
        assertEquals(List.of(1L, 2L, 7L, 10L), picked.stream().map(Punishment::id).toList());
        RollbackRules.Counts c = RollbackRules.count(picked, List.of(), NOW);
        assertEquals(new RollbackRules.Counts(1, 2, 0, 1, 0, 3), c);
        assertEquals(4, c.punishments());
        assertFalse(RollbackRules.selects(row(1, Punishment.Type.BAN, ALEX, NOW, null, null), null, since, NOW));
    }

    @Test
    void rollbackSanctionsOnlyActiveOrHeld() {
        long since = NOW - 60 * MIN;
        Sanction freeze = new Sanction(1, Sanction.Kind.FREEZE, STEVE, "Steve", null, ALEX, "Alex", "r", NOW - MIN, NOW + MIN,
                null, null, null, Sanction.State.ACTIVE, null, null, null, null);
        Sanction ranOut = new Sanction(2, Sanction.Kind.TRADE, STEVE, "Steve", null, ALEX, "Alex", "r", NOW - 10 * MIN, NOW - MIN,
                null, null, null, Sanction.State.ACTIVE, null, null, null, null);
        Sanction money = new Sanction(3, Sanction.Kind.MONEY, STEVE, "Steve", ALEX, ALEX, "Alex", "r", NOW - MIN, null, 500L,
                "money", null, Sanction.State.ACTIVE, null, null, null, null);
        Sanction restored = new Sanction(4, Sanction.Kind.MONEY, STEVE, "Steve", ALEX, ALEX, "Alex", "r", NOW - MIN, null, 5L,
                "money", null, Sanction.State.RESTORED, null, null, null, null);
        Sanction bobs = new Sanction(5, Sanction.Kind.ITEM, STEVE, "Steve", ALEX, BOB, "Bob", "r", NOW - MIN, null, null, null,
                "x", Sanction.State.ACTIVE, null, null, null, null);
        Sanction pending = new Sanction(6, Sanction.Kind.ITEM, STEVE, "Steve", ALEX, ALEX, "Alex", "r", NOW - MIN, null, null,
                null, "x", Sanction.State.PENDING, null, null, null, null);
        assertEquals(List.of(1L, 3L), RollbackRules.selectSanctions(List.of(freeze, ranOut, money, restored, bobs, pending),
                ALEX, since, NOW, NOW).stream().map(Sanction::id).toList());
    }

    @Test
    void rollbackPeriodLimits() {
        RollbackRules.Settings s = RollbackRules.Settings.DEFAULT;
        assertEquals(RollbackRules.PeriodProblem.TOO_SHORT, RollbackRules.check(s, Duration.ofMinutes(14)));
        assertNull(RollbackRules.check(s, Duration.ofMinutes(15)));
        assertNull(RollbackRules.check(s, Duration.ofDays(7)));
        assertEquals(RollbackRules.PeriodProblem.TOO_LONG, RollbackRules.check(s, Duration.ofDays(7).plusSeconds(1)));
        assertEquals(RollbackRules.PeriodProblem.TOO_SHORT, RollbackRules.check(s, null));
    }

    @Test
    void rollbackConfirmMatchesThePreviewOnly() {
        Duration h = Duration.ofHours(1);
        Duration w = Duration.ofSeconds(60);
        assertTrue(RollbackRules.confirms(ALEX, h, NOW, ALEX, h, NOW + 59_000, w));
        assertFalse(RollbackRules.confirms(ALEX, h, NOW, ALEX, h, NOW + 61_000, w), "too late");
        assertFalse(RollbackRules.confirms(ALEX, h, NOW, BOB, h, NOW + 1_000, w), "other staff member");
        assertFalse(RollbackRules.confirms(ALEX, h, NOW, ALEX, Duration.ofHours(2), NOW + 1_000, w), "other period");
        assertFalse(RollbackRules.confirms(null, null, 0, ALEX, h, NOW, w), "no preview");
    }

    // ================================================================ 14.5 false reports

    @Test
    void onlyInvalidCountsAndTheThreshold() {
        assertTrue(FalseReportRules.counts("INVALID"));
        assertFalse(FalseReportRules.counts("NO_EVIDENCE"));
        assertFalse(FalseReportRules.counts("DUPLICATE"));
        assertFalse(FalseReportRules.counts("PUNISHED"));
        FalseReportRules.Settings s = FalseReportRules.Settings.DEFAULT;
        assertEquals(FalseReportRules.Step.NOTHING, FalseReportRules.step(s, 2));
        assertEquals(FalseReportRules.Step.SUGGEST, FalseReportRules.step(s, 3));
        assertEquals(FalseReportRules.Step.SUGGEST, FalseReportRules.step(s, 7));
        FalseReportRules.Settings off = new FalseReportRules.Settings(false, 3, s.window(), s.cap(), s.reason());
        assertEquals(FalseReportRules.Step.NOTHING, FalseReportRules.step(off, 9));
    }

    @Test
    void weeklyCap() {
        FalseReportRules.Settings s = FalseReportRules.Settings.DEFAULT;
        assertFalse(FalseReportRules.capped(null, s, NOW));
        assertTrue(FalseReportRules.capped(NOW - Duration.ofDays(6).toMillis(), s, NOW));
        assertTrue(FalseReportRules.capped(NOW - Duration.ofDays(7).toMillis(), s, NOW), "exactly 7 days: still capped");
        assertFalse(FalseReportRules.capped(NOW - Duration.ofDays(7).toMillis() - 1, s, NOW));
    }

    @Test
    void reportBlockStepParsesAndFallsToTheNextStepWhenUnavailable() {
        Templates.Step step = Templates.Step.parse("report-block 3d");
        assertEquals(Punishment.Type.REPORT_BLOCK, step.type());
        assertEquals(Duration.ofDays(3), step.duration());
        assertEquals("report-block 3d", step.describe());
        assertNull(Templates.Step.parse("report-block perm").duration());
        assertTrue(Punishment.Type.REPORT_BLOCK.timed());
        assertFalse(Punishment.Type.REPORT_BLOCK.instant());
        assertFalse(Punishment.Type.REPORT_BLOCK.island());
        List<Templates.Step> ladder = List.of(Templates.Step.parse("warn"), Templates.Step.parse("report-block 3d"),
                Templates.Step.parse("report-block 14d"), Templates.Step.parse("mute 1d"));
        IslandSteps.Context without = IslandSteps.Context.UNAVAILABLE;
        IslandSteps.Context with = IslandSteps.Context.UNAVAILABLE.withReports(true);
        assertEquals(Templates.Step.parse("mute 1d"), IslandSteps.replacement(ladder, 1, without), "not available: next possible step");
        assertEquals(IslandSteps.Why.NO_REPORTS, IslandSteps.replaced(ladder, 2, without));
        assertEquals(ladder, IslandSteps.effective(ladder, with));
        assertNull(IslandSteps.problem(Templates.Step.parse("report-block 3d"), with));
        // the island steps are untouched by report availability
        assertEquals(IslandSteps.Why.UNAVAILABLE, IslandSteps.problem(Templates.Step.parse("island-ban 1d"), with));
    }

    @Test
    void falseReportSuggestionUsesTheLadderWithReportBlocks() {
        PunishConfig c = parse(read(BUNDLED)).config();
        Templates t = new Templates();
        t.use(c);
        Templates.Template abuse = t.template("report-abuse").orElseThrow();
        assertEquals(List.of("warn", "report-block 3d", "report-block 2w", "mute 1d"),
                abuse.ladder().stream().map(Templates.Step::describe).toList());
        List<Punishment> history = new ArrayList<>();
        assertEquals("warn", ApiSuggestions.plan(abuse, history, "Reports", "3 invalid reports in 30d", NOW, null, true)
                .step().describe());
        history.add(new Punishment(1, Punishment.Type.WARN, STEVE, "Steve", null, false, "False reports", "report-abuse", 0,
                ALEX, "Alex", NOW - MIN, null, false, null, null, null, null, null));
        assertEquals("report-block 3d", ApiSuggestions.plan(abuse, history, "Reports", "", NOW, null, true).step().describe());
        assertEquals("mute 1d", ApiSuggestions.plan(abuse, history, "Reports", "", NOW, null, false).step().describe(),
                "without the reports module the next possible step is suggested");
        assertEquals("Reports: 3 invalid reports in 30d",
                ApiSuggestions.plan(abuse, List.of(), "Reports", "3 invalid reports in 30d", NOW, null, true).details());
    }

    // ================================================================ 14.10 purge

    @Test
    void purgeKeepsWhatIsInForceAndTheLedgerForAYear() {
        PurgeRules.Settings s = PurgeRules.Settings.DEFAULT;
        long day = TestData.DAY;
        Punishment permBan = row(1, Punishment.Type.BAN, ALEX, NOW - 900 * day, null, null);
        Punishment oldMute = row(2, Punishment.Type.MUTE, ALEX, NOW - 400 * day, NOW - 399 * day, null);
        Punishment recentWarn = row(3, Punishment.Type.WARN, ALEX, NOW - 30 * day, null, null);
        Punishment oldWarn = row(4, Punishment.Type.WARN, ALEX, NOW - 500 * day, null, null);
        Punishment liftedBan = row(5, Punishment.Type.BAN, ALEX, NOW - 800 * day, null, NOW - 10 * day);
        Punishment activeReportBlock = row(6, Punishment.Type.REPORT_BLOCK, ALEX, NOW - day, NOW + day, null);
        Punishment oldKick = row(7, Punishment.Type.KICK, ALEX, NOW - 366 * day, null, null);
        assertEquals(PurgeRules.Fate.KEEP_ACTIVE, PurgeRules.fate(permBan, s, NOW), "a ban in force always stays");
        assertEquals(PurgeRules.Fate.DELETE, PurgeRules.fate(oldMute, s, NOW));
        assertEquals(PurgeRules.Fate.KEEP_LEDGER, PurgeRules.fate(recentWarn, s, NOW));
        assertEquals(PurgeRules.Fate.DELETE, PurgeRules.fate(oldWarn, s, NOW));
        assertEquals(PurgeRules.Fate.KEEP_LEDGER, PurgeRules.fate(liftedBan, s, NOW), "ended 10 days ago (pardon time counts)");
        assertEquals(PurgeRules.Fate.KEEP_ACTIVE, PurgeRules.fate(activeReportBlock, s, NOW));
        assertEquals(PurgeRules.Fate.DELETE, PurgeRules.fate(oldKick, s, NOW));
        PurgeRules.Ledger l = PurgeRules.ledger(List.of(permBan, oldMute, recentWarn, oldWarn, liftedBan, activeReportBlock,
                oldKick), s, NOW);
        assertEquals(new PurgeRules.Ledger(2, 2, 3, true), l);
        // keep-ledger 0: only what is in force stays
        PurgeRules.Settings none = new PurgeRules.Settings(Duration.ZERO, s.confirmWindow());
        assertEquals(PurgeRules.Fate.DELETE, PurgeRules.fate(recentWarn, none, NOW));
        assertEquals(PurgeRules.Fate.KEEP_ACTIVE, PurgeRules.fate(permBan, none, NOW));
    }

    @Test
    void purgeAnonymisesAndKeepsTheIpOnlyForAnIpBanInForce() {
        UUID id = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0");
        assertEquals("0f1e2d3c4b5a69788796a5b4c3d2e1f0", PurgeRules.anonymousName(id));
        assertEquals(32, PurgeRules.anonymousName(id).length(), "fits the 32 character name columns");
        Punishment ipban = new Punishment(1, Punishment.Type.BAN, STEVE, "Steve", "1.2.3.4", true, "x", "alt-abuse", 0, ALEX,
                "Alex", NOW - MIN, null, false, null, null, null, null, null);
        assertTrue(PurgeRules.keepsIp(ipban, NOW));
        Punishment ended = new Punishment(2, Punishment.Type.BAN, STEVE, "Steve", "1.2.3.4", true, "x", "alt-abuse", 0, ALEX,
                "Alex", NOW - 10 * MIN, NOW - MIN, false, null, null, null, null, null);
        assertFalse(PurgeRules.keepsIp(ended, NOW));
        assertEquals("alt-abuse", PurgeRules.ledgerReason(ipban));
        Punishment free = new Punishment(3, Punishment.Type.WARN, STEVE, "Steve", null, false, "my secret words", null, null,
                ALEX, "Alex", NOW, null, false, null, null, null, null, null);
        assertEquals("purged", PurgeRules.ledgerReason(free), "a free-text reason never survives");
    }

    @Test
    void purgeConfirmNeedsAFreshPreviewOfTheSamePlayer() {
        Duration w = Duration.ofSeconds(60);
        assertTrue(PurgeRules.confirms(STEVE, NOW, STEVE, NOW + 30_000, w));
        assertFalse(PurgeRules.confirms(STEVE, NOW, STEVE, NOW + 61_000, w));
        assertFalse(PurgeRules.confirms(STEVE, NOW, ALEX, NOW + 1_000, w));
        assertFalse(PurgeRules.confirms(null, 0, STEVE, NOW, w));
    }

    // ================================================================ config + messages

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void shippedConfigHasTheP9Blocks() {
        for (Path file : List.of(BUNDLED, TRACKED)) {
            PunishConfigParser.Result r = parse(read(file));
            assertTrue(r.ok(), file + ": " + r.errors());
            PunishConfig.Extras x = r.config().extras();
            assertEquals(Guard.Settings.DEFAULT.tiers(), x.guard().tiers(), file.toString());
            assertEquals("helper", x.guard().defaultTier());
            assertEquals(Duration.ofMinutes(30), x.guard().allowDefault());
            assertEquals(RollbackRules.Settings.DEFAULT, x.rollback());
            assertEquals(FalseReportRules.Settings.DEFAULT, x.falseReports());
            assertEquals(PurgeRules.Settings.DEFAULT, x.purge());
        }
        assertEquals(read(BUNDLED), read(TRACKED), "the tracked punishments.yml equals the bundled default");
    }

    @Test
    void badP9ConfigIsRejectedWithThePath() {
        String base = read(BUNDLED);
        assertProblem(base.replace("window: 5m                # what each", "window: 5h                # what each"),
                "config.duration-too-long", "extras.guard.window");
        assertProblem(base.replace("helper: { mode: block,", "helper: { mode: blok,"), "config.unknown-key", "extras.guard.tiers.helper.mode");
        assertProblem(base.replace("default-tier: helper", "default-tier: trainee"), "config.unknown-key", "extras.guard.default-tier");
        assertProblem(base.replace("max-period: 7d", "max-period: 90d"), "config.duration-too-long", "extras.rollback.max-period");
        assertProblem(base.replace("reason: report-abuse      # reason id above", "reason: report-abus"),
                "config.unknown-reason", "extras.false-reports.reason");
        assertProblem(base.replace("threshold: 3              # this many", "threshold: 0              # this many"),
                "config.bad-number", "extras.false-reports.threshold");
        PunishConfigParser.Result zero = parse(base.replace("keep-ledger: 365d", "keep-ledger: 0"));
        assertTrue(zero.ok(), zero.errors().toString());
        assertEquals(Duration.ZERO, zero.config().extras().purge().keepLedger());
        // an older file without the P9 blocks (and without report-abuse) still loads with the defaults
        PunishConfigParser.Result old = parse(base.substring(0, base.indexOf("  # ---------- owner spec section 14 (P9)"))
                .replace("""
                              report-abuse:                 # P9 (spec 14.5): false reports. Only ever SUGGESTED (3 invalid reports in 30d), never
                                icon: BELL                  # automatic. report-block = can't use /report for that time. No rule number yet (owner).
                                ladder: ["warn", "report-block 3d", "report-block 14d", "mute 1d"]
                                probation: false
                        """, "").replace("    report-abuse: 42                # P9: false reports (normally applied from the suggestion after 3 invalid reports)\n", ""));
        assertTrue(old.ok(), old.errors().toString());
        assertEquals(Guard.Settings.DEFAULT, old.config().extras().guard());
    }

    private static void assertProblem(String yaml, String key, String path) {
        PunishConfigParser.Result r = parse(yaml);
        assertFalse(r.ok(), "expected " + key + " at " + path);
        assertTrue(r.errors().stream().anyMatch(p -> p.key().equals(key) && p.path().equals(path)), r.errors().toString());
    }

    @Test
    void everyP9MessageKeyExists() {
        YamlConfiguration m = YamlConfiguration.loadConfiguration(MESSAGES.toFile());
        List<String> keys = new ArrayList<>(List.of("steps.report-block", "reasons.report-abuse", "types.report_block",
                "notify.report_block", "discord.types.report_block", "discord.titles.report_block", "discord.titles.guard-alert", "discord.titles.guard-blocked",
                "discord.titles.rollback", "discord.fields.limit", "discord.fields.applied", "discord.fields.window",
                "discord.fields.rolled-back-staff", "discord.fields.period", "discord.fields.punishments",
                "discord.fields.sanctions", "island-steps.why.no_reports", "undo.player-report", "stats.rolled-back",
                "stats.report-blocks", "dry-run.why.guard", "bulk.skip.guard", "guard.locked", "guard.blocked", "guard.alert-alert",
                "guard.alert-blocked", "guard.allowed", "guard.allow-locked", "guard.self", "guard.already-locked",
                "guard.locked-notify", "guard.you-locked", "guard.not-limited", "guard.unlocked", "guard.you-unlocked",
                "guard.bad-length", "guard.status.header", "guard.status.header-off", "guard.status.blocked",
                "guard.status.locked", "guard.status.allowed", "guard.status.counts", "guard.status.empty", "rollback.reason",
                "rollback.too-short", "rollback.too-long", "rollback.preview-first", "rollback.nothing", "rollback.preview",
                "rollback.confirm", "rollback.done", "rollback.notify", "report-block.you", "report-block.apply-failed",
                "false-reports.source", "false-reports.details", "purge.preview-first", "purge.no-ip-table", "purge.discord",
                "purge.active-ban", "purge.audit-kept", "purge.keep-none", "purge.confirm"));
        for (Guard.Limit l : Guard.Limit.values()) {
            keys.add("guard.limits." + l.id());
            keys.add("discord.guard-limits." + l.id());
        }
        for (String part : List.of("player-mute", "player-mute-still", "player-warn", "player-island", "player-report")) {
            keys.add("rollback." + part);
            keys.add("undo." + part);
        }
        for (String prefix : List.of("purge.preview", "purge.done")) {
            for (String part : List.of("header", "removed", "ips", "kept")) {
                keys.add(prefix + "." + part);
            }
        }
        for (String k : keys) {
            assertNotNull(m.getString(k), "messages/moderation.yml is missing " + k);
        }
        assertEquals(read(MESSAGES), read(TRACKED_MESSAGES), "the tracked messages equal the bundled ones");
        // Bedrock-safe: no arrows or other untested symbols in the new texts
        for (String k : keys) {
            String v = m.getString(k);
            assertFalse(v.contains("→") || v.contains("←") || v.contains("⇒"), k + " uses an arrow");
        }
    }
}
