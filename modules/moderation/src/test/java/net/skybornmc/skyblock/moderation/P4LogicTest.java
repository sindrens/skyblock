package net.skybornmc.skyblock.moderation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4, pure logic: approval rules + the approval stage + the request state machine (13.4), probation (13.7, also combined
 * with offence expiry and approval), the "mute ended" timers (13.13) and the watchlist join recipients (13.5).
 */
class P4LogicTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = TestData.DAY;
    private static final UUID STEVE = TestData.STEVE;
    private static final UUID ALEX = TestData.ALEX;
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    // ================================================================ 13.4 approval rules

    @Test
    void rulesParseAndMatch() {
        Approval.Rule banPerm = Approval.Rule.parse("ban perm");
        assertTrue(banPerm.matches(Punishment.Type.BAN, false, null));
        assertTrue(banPerm.matches(Punishment.Type.BAN, true, null), "an IP-wide perm ban is a perm ban too");
        assertFalse(banPerm.matches(Punishment.Type.BAN, false, Duration.ofDays(30)));
        assertFalse(banPerm.matches(Punishment.Type.MUTE, false, null));

        Approval.Rule ipban = Approval.Rule.parse("ipban *");
        assertTrue(ipban.matches(Punishment.Type.BAN, true, Duration.ofHours(1)));
        assertFalse(ipban.matches(Punishment.Type.BAN, false, null), "an account ban is not an ipban");

        Approval.Rule long30 = Approval.Rule.parse("ban >= 30d");
        assertTrue(long30.matches(Punishment.Type.BAN, false, Duration.ofDays(30)));
        assertTrue(long30.matches(Punishment.Type.BAN, false, Duration.ofDays(60)));
        assertTrue(long30.matches(Punishment.Type.BAN, false, null), "perm is longer than any time");
        assertFalse(long30.matches(Punishment.Type.BAN, false, Duration.ofDays(14)));

        Approval.Rule longer = Approval.Rule.parse("mute > 7d");
        assertFalse(longer.matches(Punishment.Type.MUTE, false, Duration.ofDays(7)));
        assertTrue(longer.matches(Punishment.Type.MUTE, false, Duration.ofDays(8)));
        Approval.Rule exact = Approval.Rule.parse("MUTE 7d");
        assertTrue(exact.matches(Punishment.Type.MUTE, false, Duration.ofDays(7)));
        assertFalse(exact.matches(Punishment.Type.MUTE, false, Duration.ofDays(8)));
        assertEquals("mute 7d", exact.text());

        for (String bad : List.of("", "kick *", "ban", "ban >= ", "ban => 30d", "ban 0d", "ban >= soon", "warn perm")) {
            assertThrows(IllegalArgumentException.class, () -> Approval.Rule.parse(bad), bad);
        }
        assertEquals(Approval.Rule.BAD_KIND, assertThrows(IllegalArgumentException.class,
                () -> Approval.Rule.parse("bann perm")).getMessage());
    }

    private static PunishDecision.Actor actor(Duration max, boolean approver) {
        return new PunishDecision.Actor("Alex", false, Set.of(Punishment.Type.values()), true, true, max, false, ALEX, false,
                approver);
    }

    private static PunishDecision.Input in(PunishDecision.Actor a, Punishment.Type type, Duration d) {
        return new PunishDecision.Input(a, type, d, false, false, false, false, true, null, null, NOW);
    }

    private static final PunishDecision PIPELINE = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT);

    @Test
    void bigPunishmentsBecomeRequestsForNonApprovers() {
        PunishDecision.Actor mod = actor(Duration.ofDays(30), false);
        PunishDecision.Verdict v = PIPELINE.decide(in(mod, Punishment.Type.BAN, null));
        assertEquals(PunishDecision.Outcome.REQUEST, v.outcome(), "ban perm needs approval");
        assertEquals("ban perm", v.args().get("rule"));
        assertEquals(PunishDecision.Outcome.REQUEST, PIPELINE.decide(in(mod, Punishment.Type.BAN, Duration.ofDays(30))).outcome(),
                "ban >= 30d");
        assertEquals(PunishDecision.Outcome.APPLY, PIPELINE.decide(in(mod, Punishment.Type.BAN, Duration.ofDays(7))).outcome(),
                "a 7d ban needs nobody");
        assertEquals(PunishDecision.Outcome.APPLY, PIPELINE.decide(in(mod, Punishment.Type.WARN, null)).outcome());
        assertEquals(PunishDecision.Outcome.REQUEST, PIPELINE.decide(in(mod, Punishment.Type.MUTE, null)).outcome(), "mute perm");
        // longer than the rank limit: a request instead of the old "too long" error
        PunishDecision.Actor helper = actor(Duration.ofDays(1), false);
        PunishDecision.Verdict tooLong = PIPELINE.decide(in(helper, Punishment.Type.MUTE, Duration.ofDays(3)));
        assertEquals(PunishDecision.Outcome.REQUEST, tooLong.outcome());
        assertEquals("limit", tooLong.args().get("rule"));
    }

    @Test
    void approversApplyButTheirRankLimitStaysTheHardMaximum() {
        PunishDecision.Actor admin = actor(null, true);
        assertEquals(PunishDecision.Outcome.APPLY, PIPELINE.decide(in(admin, Punishment.Type.BAN, null)).outcome());
        PunishDecision.Actor limitedApprover = actor(Duration.ofDays(30), true);
        PunishDecision.Verdict v = PIPELINE.decide(in(limitedApprover, Punishment.Type.BAN, null));
        assertEquals(PunishDecision.Outcome.BLOCKED, v.outcome(), "limits from moderation.yml still enforced");
        assertEquals("length-limit", v.stage());
        // the system (automatic rules) never needs approval
        assertEquals(PunishDecision.Outcome.APPLY, PIPELINE.decide(in(PunishDecision.Actor.system("System"),
                Punishment.Type.BAN, null)).outcome());
    }

    @Test
    void approvalOffOrNothingToChangeGivesTheOldVerdicts() {
        PunishDecision off = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DISABLED);
        PunishDecision.Verdict v = off.decide(in(actor(Duration.ofDays(1), false), Punishment.Type.MUTE, Duration.ofDays(3)));
        assertEquals(PunishDecision.Outcome.BLOCKED, v.outcome());
        assertEquals("error.too-long", v.key());
        // already banned for good: a perm ban request would change nothing, so never-shorten reports it
        Punishment active = TestData.punishment(5, Punishment.Type.BAN, NOW - DAY, null, null, null);
        PunishDecision.Verdict skipped = PIPELINE.decide(in(actor(Duration.ofDays(30), false), Punishment.Type.BAN, null)
                .withActive(null, active));
        assertEquals(PunishDecision.Outcome.SKIPPED, skipped.outcome());
        // the permission stage runs first: no request for a type you can't give at all
        PunishDecision.Actor mutesOnly = new PunishDecision.Actor("Helper", false, Set.of(Punishment.Type.MUTE), false, false,
                Duration.ofDays(1), false, ALEX, false, false);
        assertEquals("permission", PIPELINE.decide(in(mutesOnly, Punishment.Type.BAN, null)).stage());
    }

    private static PunishRequest request(PunishRequest.Status status, long expires, UUID requester) {
        return new PunishRequest(1, STEVE, "Steve", "hacking", Punishment.Type.BAN, null, false, null, "Hacked client",
                "hacking", 1, 10, false, null, requester, "Alex", NOW - 1000, expires, status, null, null, null, null, null, 0,
                false);
    }

    @Test
    void requestStateMachine() {
        PunishRequest pending = request(PunishRequest.Status.PENDING, NOW + DAY, ALEX);
        assertEquals(Approval.Check.OK, Approval.check(pending, BOB, true, false, NOW));
        assertEquals(Approval.Check.OWN_REQUEST, Approval.check(pending, ALEX, true, false, NOW), "never your own");
        assertEquals(Approval.Check.OK, Approval.check(pending, ALEX, true, true, NOW), "self-approve: true");
        assertEquals(Approval.Check.OK, Approval.check(pending, ALEX, false, false, NOW), "denying your own = withdraw");
        assertEquals(Approval.Check.EXPIRED, Approval.check(request(PunishRequest.Status.PENDING, NOW, ALEX), BOB, true, false, NOW));
        for (PunishRequest.Status done : List.of(PunishRequest.Status.APPROVED, PunishRequest.Status.DENIED,
                PunishRequest.Status.SKIPPED)) {
            assertEquals(Approval.Check.ALREADY_HANDLED, Approval.check(request(done, NOW + DAY, ALEX), BOB, true, false, NOW));
        }
        assertEquals(Approval.Check.NOT_FOUND, Approval.check(null, BOB, true, false, NOW));
        assertEquals("approval.result.expired", PunishmentService.resultKey(request(PunishRequest.Status.PENDING, NOW, ALEX), NOW));
        assertEquals("approval.result.denied", PunishmentService.resultKey(request(PunishRequest.Status.DENIED, NOW, ALEX), NOW));
    }

    @Test
    void mergeKeyIsTheReasonIdOrTypeAndText() {
        assertEquals("hacking", Approval.mergeKey("Hacking", Punishment.Type.BAN, "Hacked client"));
        assertEquals("ban:cheating", Approval.mergeKey(null, Punishment.Type.BAN, " Cheating "));
        assertEquals(48, Approval.mergeKey(null, Punishment.Type.MUTE, "x".repeat(100)).length());
    }

    // ================================================================ 13.7 probation

    private static Punishment ended(Punishment.Type type, long endedAt, String template, Long removedAt) {
        return new Punishment(1, type, STEVE, "Steve", null, false, "x", template, 0, ALEX, "Alex", endedAt - DAY, endedAt,
                false, removedAt, null, null, null, null);
    }

    @Test
    void probationStartsOnlyOnANaturalEnd() {
        Probation.Settings s = Probation.Settings.DEFAULT;
        long end = NOW - DAY;
        assertEquals(end + 14 * DAY, Probation.until(List.of(ended(Punishment.Type.MUTE, end, "spam", null)), t -> true, s, NOW));
        assertEquals(end + 14 * DAY, Probation.until(List.of(ended(Punishment.Type.BAN, end, null, null)), t -> true, s, NOW),
                "direct commands too");
        assertNull(Probation.until(List.of(ended(Punishment.Type.MUTE, end, "spam", end - 1000)), t -> true, s, NOW),
                "pardoned / undone: no probation");
        assertNull(Probation.until(List.of(ended(Punishment.Type.MUTE, NOW + DAY, "spam", null)), t -> true, s, NOW),
                "still running");
        assertNull(Probation.until(List.of(ended(Punishment.Type.WARN, end, "spam", null)), t -> true, s, NOW));
        assertNull(Probation.until(List.of(TestData.punishment(1, Punishment.Type.BAN, NOW - DAY, null, null, null)),
                t -> true, s, NOW), "perm never ends");
        assertNull(Probation.until(List.of(ended(Punishment.Type.MUTE, NOW - 15 * DAY, "spam", null)), t -> true, s, NOW),
                "over after 14 days");
        assertNull(Probation.until(List.of(ended(Punishment.Type.BAN, end, "afk-farming", null)),
                t -> !"afk-farming".equals(t), s, NOW), "category with probation: false");
        assertNull(Probation.until(List.of(ended(Punishment.Type.MUTE, end, "spam", null)), t -> true,
                Probation.Settings.DISABLED, NOW));
        // the newest end wins
        assertEquals(NOW - 1000 + 14 * DAY, Probation.until(List.of(ended(Punishment.Type.MUTE, end, "spam", null),
                ended(Punishment.Type.BAN, NOW - 1000, "hacking", null)), t -> true, s, NOW));
    }

    @Test
    void stepBonusIsCapped() {
        assertEquals(1, Probation.raise(0, 1, 4));
        assertEquals(3, Probation.raise(3, 1, 4), "never past the last step");
        assertEquals(3, Probation.raise(1, 5, 4));
        assertEquals(0, Probation.raise(0, 0, 4));
        assertTrue(Probation.applies(NOW + 1, 1, true, NOW));
        assertFalse(Probation.applies(NOW + 1, 1, false, NOW));
        assertFalse(Probation.applies(NOW, 1, true, NOW));
        assertFalse(Probation.applies(NOW + 1, 0, true, NOW));
    }

    private static final List<Templates.Step> SPAM = List.of(Templates.Step.parse("warn"), Templates.Step.parse("mute 30m"),
            Templates.Step.parse("mute 6h"), Templates.Step.parse("mute 3d"));

    private static PunishDecision.Input ladderInput(PunishDecision.Actor a, int index, Long probationUntil, int bonus) {
        Templates.Step step = SPAM.get(index);
        return in(a, step.type(), step.duration()).withEscalation(new PunishDecision.Escalation(SPAM, index, false,
                probationUntil, bonus));
    }

    @Test
    void probationRaisesTheStepInThePipeline() {
        PunishDecision.Actor admin = actor(null, true);
        // offence #1 + probation = step 2 (spec 13.16)
        PunishDecision.Verdict v = PIPELINE.decide(ladderInput(admin, 0, NOW + DAY, 1));
        assertTrue(v.applied());
        assertEquals(Punishment.Type.MUTE, v.input().type());
        assertEquals(Duration.ofMinutes(30), v.input().duration());
        assertTrue(v.input().escalation().raised());
        assertEquals(1, v.input().escalation().index());
        // capped at the last step
        PunishDecision.Verdict last = PIPELINE.decide(ladderInput(admin, 3, NOW + DAY, 1));
        assertEquals(Duration.ofDays(3), last.input().duration());
        assertFalse(last.input().escalation().raised());
        // no probation, ended probation, or a reason with probation off (builder passes null): normal step
        assertEquals(Punishment.Type.WARN, PIPELINE.decide(ladderInput(admin, 0, null, 1)).input().type());
        assertEquals(Punishment.Type.WARN, PIPELINE.decide(ladderInput(admin, 0, NOW - 1, 1)).input().type());
        // the system never gets a bonus
        assertEquals(Punishment.Type.WARN, PIPELINE.decide(ladderInput(PunishDecision.Actor.system("System"), 0, NOW + DAY, 1))
                .input().type());
    }

    @Test
    void aRaisedStepStillNeedsApprovalAndPermission() {
        List<Templates.Step> hacking = List.of(Templates.Step.parse("ban 14d"), Templates.Step.parse("ban perm"));
        PunishDecision.Actor mod = actor(Duration.ofDays(30), false);
        PunishDecision.Input first = in(mod, Punishment.Type.BAN, Duration.ofDays(14))
                .withEscalation(new PunishDecision.Escalation(hacking, 0, false, NOW + DAY, 1));
        PunishDecision.Verdict v = PIPELINE.decide(first);
        assertEquals(PunishDecision.Outcome.REQUEST, v.outcome(), "probation made it ban perm, which needs approval");
        assertNull(v.input().duration());
        PunishDecision.Actor mutesOnly = new PunishDecision.Actor("Helper", false, Set.of(Punishment.Type.WARN,
                Punishment.Type.MUTE), false, false, Duration.ofDays(1), false, ALEX, false, false);
        List<Templates.Step> ladder = List.of(Templates.Step.parse("warn"), Templates.Step.parse("ban 1d"));
        PunishDecision.Verdict blocked = PIPELINE.decide(in(mutesOnly, Punishment.Type.WARN, null)
                .withEscalation(new PunishDecision.Escalation(ladder, 0, false, NOW + DAY, 1)));
        assertEquals("permission", blocked.stage(), "the raised step needs the permission for a ban");
    }

    @Test
    void probationAndOffenceExpiryAreIndependent() {
        // two old spam offences expired (offence-expire 90d), so this is offence #1 again: index 0, + probation = step 2
        long old = NOW - 100 * DAY;
        List<Punishment> history = List.of(
                new Punishment(1, Punishment.Type.WARN, STEVE, "Steve", null, false, "x", "spam", 0, ALEX, "Alex", old, null,
                        false, null, null, null, null, null),
                new Punishment(2, Punishment.Type.MUTE, STEVE, "Steve", null, false, "x", "spam", 1, ALEX, "Alex", old + DAY,
                        old + DAY + Duration.ofMinutes(30).toMillis(), false, null, null, null, null, null),
                ended(Punishment.Type.MUTE, NOW - DAY, "toxicity", null));
        int previous = Offences.counting(history, "spam", Offences.cutoff(Duration.ofDays(90), NOW));
        assertEquals(0, previous);
        Long until = Probation.until(history, t -> true, Probation.Settings.DEFAULT, NOW);
        assertNotNull(until);
        PunishDecision.Verdict v = PIPELINE.decide(ladderInput(actor(null, true), previous, until, 1));
        assertEquals(Duration.ofMinutes(30), v.input().duration());
    }

    // ================================================================ 13.13 "mute ended" timers

    /** A fake scheduler: tasks run when the test advances the clock. */
    private static final class FakeScheduler implements EndTimers.Scheduler {
        record Task(long at, Runnable action, boolean[] cancelled) {
        }

        final AtomicLong clock = new AtomicLong(NOW);
        final List<Task> tasks = new ArrayList<>();

        @Override
        public Runnable schedule(long delayMillis, Runnable action) {
            Task t = new Task(clock.get() + delayMillis, action, new boolean[1]);
            tasks.add(t);
            return () -> t.cancelled()[0] = true;
        }

        /** Runs every task due at {@code at} (the scheduler may fire up to {@code early} ms before, like a lagging server). */
        void advance(long to, long early) {
            clock.set(to);
            for (Task t : new ArrayList<>(tasks)) {
                if (!t.cancelled()[0] && t.at() - early <= to) {
                    tasks.remove(t);
                    t.action().run();
                }
            }
        }

        long live() {
            return tasks.stream().filter(t -> !t.cancelled()[0]).count();
        }
    }

    @Test
    void timerFiresOnceAtTheEnd() {
        FakeScheduler s = new FakeScheduler();
        List<Long> ended = new ArrayList<>();
        EndTimers timers = new EndTimers(s, s.clock::get, (player, id) -> ended.add(id));
        timers.track(STEVE, 7, NOW + 60_000);
        timers.track(STEVE, 7, NOW + 60_000); // same mute again: one timer
        assertEquals(1, s.live());
        s.advance(NOW + 30_000, 0);
        assertTrue(ended.isEmpty());
        s.advance(NOW + 60_000, 0);
        assertEquals(List.of(7L), ended);
        s.advance(NOW + 120_000, 0);
        assertEquals(List.of(7L), ended, "never repeats");
        assertFalse(timers.tracking(STEVE));
    }

    @Test
    void timerIsRescheduledOnChangesAndCancelledOnQuit() {
        FakeScheduler s = new FakeScheduler();
        List<Long> ended = new ArrayList<>();
        EndTimers timers = new EndTimers(s, s.clock::get, (player, id) -> ended.add(id));
        timers.track(STEVE, 7, NOW + 60_000);
        timers.track(STEVE, 8, NOW + 120_000); // a longer mute replaced it
        assertEquals(1, s.live());
        s.advance(NOW + 60_000, 0);
        assertTrue(ended.isEmpty(), "the old timer is gone");
        s.advance(NOW + 120_000, 0);
        assertEquals(List.of(8L), ended);

        timers.track(ALEX, 9, NOW + 200_000);
        timers.cancel(ALEX); // quit
        s.advance(NOW + 300_000, 0);
        assertEquals(List.of(8L), ended);
        timers.track(BOB, 10, NOW + 400_000);
        timers.track(BOB, 10, null); // made permanent / unmuted
        assertEquals(0, timers.size());
        timers.track(BOB, 11, NOW + 500_000);
        timers.clear();
        assertEquals(0, s.live());
    }

    @Test
    void earlyFireWaitsForTheRest() {
        FakeScheduler s = new FakeScheduler();
        List<Long> ended = new ArrayList<>();
        EndTimers timers = new EndTimers(s, s.clock::get, (player, id) -> ended.add(id));
        timers.track(STEVE, 7, NOW + 60_000);
        s.advance(NOW + 55_000, 10_000); // ticks ran ahead of the wall clock: fires 5 s early
        assertTrue(ended.isEmpty(), "not ended yet by the clock");
        assertTrue(timers.tracking(STEVE), "rescheduled for the rest");
        s.advance(NOW + 60_000, 0);
        assertEquals(List.of(7L), ended);
    }

    // ================================================================ 13.5 watchlist

    @Test
    void joinLineGoesOnlyToStaffNeverToThePlayer() {
        List<Watchlist.Viewer> online = List.of(new Watchlist.Viewer(STEVE, true), new Watchlist.Viewer(ALEX, true),
                new Watchlist.Viewer(BOB, false));
        assertEquals(List.of(ALEX), Watchlist.recipients(online, STEVE), "watched staff member is not told either");
        assertEquals(List.of(STEVE, ALEX), Watchlist.recipients(online, BOB));
    }

    @Test
    void watchEntriesExpire() {
        Watchlist.Entry e = new Watchlist.Entry(1, STEVE, "Steve", ALEX, "Alex", "macros", NOW, NOW + DAY, false, null, null);
        assertTrue(e.active(NOW));
        assertFalse(e.active(NOW + DAY));
        assertTrue(new Watchlist.Entry(1, STEVE, "Steve", ALEX, "Alex", null, NOW, null, false, null, null).active(NOW + 999 * DAY));
        assertFalse(new Watchlist.Entry(1, STEVE, "Steve", ALEX, "Alex", null, NOW, null, true, NOW, "Bob").active(NOW));
        assertNull(Watchlist.cleanReason("   ", 100));
        assertEquals("abc", Watchlist.cleanReason("  abc ", 100));
        assertEquals(10, Watchlist.cleanReason("x".repeat(50), 10).length());
    }

    // ================================================================ config + texts

    private static final Path BUNDLED = Path.of("src/main/resources/punishments.yml");
    private static final Path TRACKED = Path.of("../../server/plugins/PhoenixSkyblock/punishments.yml");

    @Test
    void shippedConfigHasTheP4Defaults() throws Exception {
        for (Path file : List.of(BUNDLED, TRACKED)) {
            PunishConfigParser.Result r = PunishConfigParser.parse(Files.readString(file), TestData.ICONS);
            assertTrue(r.ok(), file + ": " + r.errors());
            PunishConfig.Extras x = r.config().extras();
            assertTrue(x.approval().enabled());
            assertEquals(Duration.ofHours(24), x.approval().expire());
            assertFalse(x.approval().selfApprove());
            assertEquals(List.of("ban perm", "ipban *", "mute perm", "ban >= 30d"),
                    x.approval().rules().stream().map(Approval.Rule::text).toList());
            assertEquals(Duration.ofDays(30), x.watchlist().expire());
            assertEquals(new Probation.Settings(true, Duration.ofDays(14), 1), x.probation());
            assertTrue(x.muteEnded().enabled());
            Templates t = new Templates();
            t.use(r.config());
            assertFalse(t.template("inappropriate-name").orElseThrow().probation(), "small stuff: off");
            assertFalse(t.template("afk-farming").orElseThrow().probation());
            assertTrue(t.template("spam").orElseThrow().probation());
            assertTrue(t.probation(null), "direct commands start probation");
            assertFalse(t.probation("inappropriate-skin"));
        }
    }

    @Test
    void badP4KeysAreRejected() throws Exception {
        String base = Files.readString(BUNDLED);
        PunishConfigParser.Result r = PunishConfigParser.parse(base.replace("\"ban >= 30d\"]", "\"bann >= 30d\"]"), TestData.ICONS);
        assertFalse(r.ok());
        ConfigProblem p = r.errors().getFirst();
        assertEquals("config.bad-approval-rule", p.key());
        assertEquals("ban", p.arg("suggestion"));
        assertTrue(p.line() > 0, "with a line number");
        assertFalse(PunishConfigParser.parse(base.replace("step-bonus: 1", "step-bonus: 9"), TestData.ICONS).ok());
        assertFalse(PunishConfigParser.parse(base.replace("duration: 14d", "duration: soon"), TestData.ICONS).ok());
        assertFalse(PunishConfigParser.parse(base.replace("    probation: false                # small",
                "    probation: maybe                # small"), TestData.ICONS).ok());
        assertTrue(PunishConfigParser.parse(base.replace("expire: 30d               # perm", "expire: perm              # perm"),
                TestData.ICONS).config().extras().watchlist().expire() == null, "watchlist perm = until removed");
    }

    @Test
    void everyP4TextExists() {
        for (Path file : List.of(Path.of("../../plugin/src/main/resources/messages/moderation.yml"),
                Path.of("../../server/plugins/PhoenixSkyblock/messages/moderation.yml"))) {
            YamlConfiguration msg = YamlConfiguration.loadConfiguration(file.toFile());
            List<String> keys = new ArrayList<>(List.of("approval.requested", "approval.merged", "approval.notify",
                    "approval.approve-button", "approval.approve-hover", "approval.deny-button", "approval.deny-hover",
                    "approval.approved", "approval.denied", "approval.skipped", "approval.own", "approval.already",
                    "approval.expired", "approval.not-found", "approval.no-permission", "approval.deny-empty",
                    "approval.deny-prompt", "approval.join-header", "approval.join-line", "approval.join-more",
                    "approval.menu.title", "approval.menu.info.name", "approval.menu.approve.name", "approval.menu.deny.name",
                    "approval.deny-menu.title", "approval.deny-menu.reason.name", "approval.deny-menu.type.name",
                    "punish.request", "punish.request-limit", "punish.reason.probation", "punish.summary.probation",
                    "punish.summary.watch", "punish.summary.request", "punish.summary.request-wait", "menu.history.approved",
                    "ended.mute", "ended.mute-probation", "ended.ban", "ended.ban-probation", "stats.requests",
                    "stats.approvals", "watch.usage", "watch.too-long", "watch.added", "watch.updated", "watch.removed",
                    "watch.not-watched", "watch.no-reason", "watch.notify", "watch.list-header", "watch.list-none",
                    "watch.list-line", "watch.menu.title", "watch.menu.info", "watch.menu.empty.name", "watch.menu.entry.name",
                    "watch.menu.entry.lore", "watch.menu.entry.click-punish", "watch.menu.entry.click-history",
                    "config.bad-approval-rules", "config.bad-approval-rule", "config.bad-list"));
            for (PunishRequest.Status s : PunishRequest.Status.values()) {
                keys.add("approval.status." + s.name().toLowerCase(java.util.Locale.ROOT));
                keys.add("approval.result." + s.name().toLowerCase(java.util.Locale.ROOT));
            }
            keys.add("approval.status.expired");
            keys.add("approval.result.expired");
            for (String id : Approval.Settings.DEFAULT.denyReasons()) {
                keys.add("approval.deny-reasons." + id);
            }
            for (String key : keys) {
                assertTrue(msg.contains(key), file + ": missing " + key);
            }
        }
    }
}
