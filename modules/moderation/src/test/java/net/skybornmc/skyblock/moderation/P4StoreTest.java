package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4 against a real (H2, MySQL mode) database: approval requests (created not applied, merged, approve/deny, own request,
 * expiry, first click wins, [Apply] on a suggestion, /pstats counts), probation raising a stored step, the watchlist and
 * the "mute ended" notice claim (once, never repeats, never for pardons).
 */
class P4StoreTest {

    private static final UUID ALEX = TestData.ALEX;
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final long DAY = TestData.DAY;

    @TempDir
    Path dir;

    private Database db;
    private PunishmentStore store;
    private final PunishDecision decision = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT);
    private long now;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "moderation-p4-test";
        config.poolSize = 8;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("moderation-p4-test"));
        db.start();
        new Migrations(db, Logger.getLogger("moderation-p4-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("moderation-p4-test")).migrate("moderation", getClass().getClassLoader());
        store = new PunishmentStore(db);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static PunishDecision.Actor staff(String name, UUID id, Duration max, boolean approver) {
        return new PunishDecision.Actor(name, false, Set.of(Punishment.Type.values()), true, true, max, false, id, false, approver);
    }

    private PunishmentStore.IssueResult ban(UUID target, PunishDecision.Actor actor, Duration length, String template, long at) {
        Punishment draft = new Punishment(0, Punishment.Type.BAN, target, "Steve", null, false, "Hacked client", template, 0,
                actor.id(), actor.name(), at, null, false, null, null, null, null, null);
        PunishDecision.Input in = new PunishDecision.Input(actor, Punishment.Type.BAN, length, false, false, false, false, false,
                null, null, at);
        return store.issue(draft, in, decision, null, Duration.ofHours(24)).join();
    }

    private PunishmentStore.RequestResult approve(long id, PunishDecision.Actor approver, boolean selfApprove, long at) {
        return store.approve(id, r -> new PunishDecision.Input(approver, r.type(), r.duration(), r.ipWide(), r.silent(), false,
                false, false, null, null, at), decision, approver.id(), approver.name(), selfApprove, at).join();
    }

    // ================================================================ 13.4 approval

    @Test
    void requestIsCreatedNotApplied() {
        UUID steve = UUID.randomUUID();
        PunishmentStore.IssueResult r = ban(steve, staff("Alex", ALEX, Duration.ofDays(30), false), null, "hacking", now);
        assertEquals(PunishDecision.Outcome.REQUEST, r.verdict().outcome());
        assertNull(r.punishment());
        assertNotNull(r.request());
        assertFalse(r.merged());
        assertTrue(store.history(steve).join().isEmpty(), "nothing applied, the player is not punished");
        PunishRequest req = r.request();
        assertEquals(PunishRequest.Status.PENDING, req.status());
        assertEquals(ALEX, req.requester());
        assertNull(req.durationMs(), "perm");
        assertEquals("hacking", req.reasonKey());
        assertEquals(now + Duration.ofHours(24).toMillis(), req.expires());
        assertEquals(List.of(req.id()), store.pendingRequests(steve, now).join().stream().map(PunishRequest::id).toList());
    }

    @Test
    void aSecondRequestForTheSamePlayerAndReasonIsMerged() {
        UUID steve = UUID.randomUUID();
        PunishRequest first = ban(steve, staff("Alex", ALEX, Duration.ofDays(30), false), null, "hacking", now).request();
        PunishmentStore.IssueResult second = ban(steve, staff("Bob", BOB, Duration.ofDays(30), false), null, "hacking", now + 1);
        assertTrue(second.merged());
        assertEquals(first.id(), second.request().id());
        assertEquals(1, second.request().merges());
        assertEquals(1, store.pendingRequests(steve, now).join().size());
        PunishmentStore.IssueResult other = ban(steve, staff("Bob", BOB, Duration.ofDays(30), false), null, "xray", now + 2);
        assertFalse(other.merged(), "another reason is its own request");
    }

    @Test
    void approverAppliesItAsTheRequesterAndCanNotApproveTheirOwn() {
        UUID steve = UUID.randomUUID();
        PunishRequest req = ban(steve, staff("Alex", ALEX, Duration.ofDays(30), false), null, "hacking", now).request();
        PunishmentStore.RequestResult own = approve(req.id(), staff("Alex", ALEX, null, true), false, now + 10);
        assertEquals(PunishmentStore.RequestKind.OWN_REQUEST, own.kind());
        assertTrue(store.history(steve).join().isEmpty());

        PunishmentStore.RequestResult ok = approve(req.id(), staff("Bob", BOB, null, true), false, now + 20);
        assertEquals(PunishmentStore.RequestKind.APPROVED, ok.kind());
        Punishment p = ok.punishment();
        assertEquals(ALEX, p.issuer(), "by = the requester");
        assertEquals("Alex", p.issuerName());
        assertEquals(BOB, p.approvedBy());
        assertEquals("Bob", p.approvedByName());
        assertNull(p.expires(), "ban perm");
        Punishment stored = store.history(steve).join().getFirst();
        assertEquals("Bob", stored.approvedByName(), "approved_by stored");
        assertEquals("hacking", stored.template());
        assertEquals(PunishRequest.Status.APPROVED, ok.request().status());
        assertEquals(p.id(), ok.request().punishmentId());
        assertEquals(PunishmentStore.RequestKind.ALREADY_HANDLED, approve(req.id(), staff("Carl", UUID.randomUUID(), null, true),
                false, now + 30).kind(), "first click wins");
    }

    @Test
    void approversOwnLimitIsTheHardMaximum() {
        UUID steve = UUID.randomUUID();
        PunishRequest req = ban(steve, staff("Alex", ALEX, Duration.ofDays(1), false), null, "hacking", now).request();
        PunishmentStore.RequestResult blocked = approve(req.id(), staff("Bob", BOB, Duration.ofDays(30), true), false, now + 1);
        assertEquals(PunishmentStore.RequestKind.BLOCKED, blocked.kind());
        assertEquals("length-limit", blocked.verdict().stage());
        assertTrue(store.request(req.id()).join().orElseThrow().pending(now + 2), "stays pending for someone who may");
    }

    @Test
    void denyNeedsAReasonAndTheRequesterIsToldOnce() {
        UUID steve = UUID.randomUUID();
        PunishRequest req = ban(steve, staff("Alex", ALEX, Duration.ofDays(30), false), null, "hacking", now).request();
        PunishmentStore.RequestResult denied = store.deny(req.id(), BOB, "Bob", "Not enough evidence", now + 5).join();
        assertEquals(PunishmentStore.RequestKind.DENIED, denied.kind());
        assertEquals("Not enough evidence", denied.request().denyReason());
        assertEquals("Bob", denied.request().handlerName());
        assertTrue(store.history(steve).join().isEmpty());
        assertEquals(PunishmentStore.RequestKind.ALREADY_HANDLED, approve(req.id(), staff("Bob", BOB, null, true), false,
                now + 6).kind());
        List<PunishRequest> told = store.requesterUpdates(ALEX, now + 7).join();
        assertEquals(List.of(req.id()), told.stream().map(PunishRequest::id).filter(id -> id == req.id()).toList());
        assertTrue(store.requesterUpdates(ALEX, now + 8).join().stream().noneMatch(r -> r.id() == req.id()), "told once");
    }

    @Test
    void requestsExpire() {
        UUID steve = UUID.randomUUID();
        PunishRequest req = ban(steve, staff("Alex", ALEX, Duration.ofDays(30), false), null, "hacking", now).request();
        long later = now + Duration.ofHours(25).toMillis();
        assertEquals(PunishmentStore.RequestKind.EXPIRED, approve(req.id(), staff("Bob", BOB, null, true), false, later).kind());
        assertTrue(store.pendingRequests(steve, later).join().isEmpty());
        assertEquals(PunishmentStore.RequestKind.EXPIRED, store.deny(req.id(), BOB, "Bob", "late", later).join().kind());
        UUID requester = UUID.randomUUID();
        PunishRequest mine = ban(UUID.randomUUID(), staff("Dan", requester, Duration.ofDays(30), false), null, "hacking", now)
                .request();
        assertTrue(store.requesterUpdates(requester, now + 1).join().isEmpty(), "still waiting");
        List<PunishRequest> expired = store.requesterUpdates(requester, later).join();
        assertEquals(List.of(mine.id()), expired.stream().map(PunishRequest::id).toList());
        assertEquals("approval.result.expired", PunishmentService.resultKey(expired.getFirst(), later));
        // a new request after the expiry is not merged into the expired one
        assertFalse(ban(steve, staff("Bob", BOB, Duration.ofDays(30), false), null, "hacking", later).merged());
    }

    @Test
    void alreadyPunishedForLongerMarksTheRequestSkipped() {
        UUID steve = UUID.randomUUID();
        PunishRequest req = ban(steve, staff("Alex", ALEX, Duration.ofDays(30), false), Duration.ofDays(30), "hacking", now)
                .request();
        assertNotNull(req, "ban >= 30d needs approval");
        ban(steve, staff("Bob", BOB, null, true), null, "xray", now + 1); // an approver bans perm meanwhile
        PunishmentStore.RequestResult r = approve(req.id(), staff("Bob", BOB, null, true), false, now + 2);
        assertEquals(PunishmentStore.RequestKind.SKIPPED, r.kind());
        assertEquals(PunishRequest.Status.SKIPPED, r.request().status());
        assertEquals(1, store.history(steve).join().size(), "nothing extra applied");
    }

    @Test
    void twoApproversAtOnceApplyItOnce() throws Exception {
        UUID steve = UUID.randomUUID();
        PunishRequest req = ban(steve, staff("Alex", ALEX, Duration.ofDays(30), false), null, "hacking", now).request();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Callable<PunishmentStore.RequestKind>> clicks = List.of(1, 2, 3, 4).stream().map(i -> (Callable<PunishmentStore.RequestKind>) () -> {
                go.await();
                return approve(req.id(), staff("Admin" + i, UUID.randomUUID(), null, true), false, now + 5).kind();
            }).toList();
            List<Future<PunishmentStore.RequestKind>> futures = clicks.stream().map(pool::submit).toList();
            go.countDown();
            long approved = 0;
            for (Future<PunishmentStore.RequestKind> f : futures) {
                if (f.get() == PunishmentStore.RequestKind.APPROVED) {
                    approved++;
                }
            }
            assertEquals(1, approved);
            assertEquals(1, store.history(steve).join().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void applyOnASuggestionByANonApproverBecomesARequest() {
        UUID steve = UUID.randomUUID();
        Templates.Step perm = Templates.Step.parse("ban perm");
        PunishmentStore.ApiSuggestion s = store.suggestApi(steve, "Steve", "hacking", perm, "Hacked client", "Grim: Reach",
                now, now + DAY).join();
        PunishDecision.Actor mod = staff("Alex", ALEX, Duration.ofDays(30), false);
        PunishmentStore.HandleResult r = store.apply(s.suggestion().id(), x -> new PunishDecision.Input(mod, perm.type(),
                perm.duration(), false, false, false, false, false, null, null, now), decision, ALEX, "Alex", now,
                Duration.ofHours(24)).join();
        assertEquals(PunishmentStore.HandleKind.REQUESTED, r.kind());
        assertEquals(Suggestion.Status.REQUESTED, r.suggestion().status());
        assertEquals(Suggestion.NOTE_REQUEST + r.request().id(), r.suggestion().note());
        assertTrue(store.history(steve).join().isEmpty());
        assertEquals("hacking", r.request().template(), "the API reason id is kept, so it counts as an offence once approved");
        assertTrue(store.apiSuggestions(steve).join().isEmpty(), "handled");
    }

    @Test
    void requestCountsForPstats() {
        UUID steve = UUID.randomUUID();
        PunishDecision.Actor alex = staff("Alex", ALEX, Duration.ofDays(30), false);
        PunishRequest a = ban(steve, alex, null, "hacking", now).request();
        PunishRequest b = ban(steve, alex, null, "xray", now).request();
        ban(steve, alex, null, "macro", now);
        approve(a.id(), staff("Bob", BOB, null, true), false, now + 1);
        store.deny(b.id(), BOB, "Bob", "evidence", now + 2).join();
        PunishmentStore.RequestCounts alexCounts = store.requestCounts(ALEX, now - 1000, now + 3).join();
        assertEquals(new PunishmentStore.RequestCounts(3, 1, 1, 1, 0, 0, 0), alexCounts);
        PunishmentStore.RequestCounts bobCounts = store.requestCounts(BOB, now - 1000, now + 3).join();
        assertEquals(1, bobCounts.handledApproved());
        assertEquals(1, bobCounts.handledDenied());
        assertEquals(PunishmentStore.RequestCounts.NONE, store.requestCounts(UUID.randomUUID(), 0, now).join());
    }

    // ================================================================ 13.7 probation: the raised step is stored

    @Test
    void probationRaisedStepIsStoredWithItsLadderIndex() {
        UUID steve = UUID.randomUUID();
        List<Templates.Step> spam = List.of(Templates.Step.parse("warn"), Templates.Step.parse("mute 30m"));
        PunishDecision.Actor admin = staff("Bob", BOB, null, true);
        Punishment draft = new Punishment(0, Punishment.Type.WARN, steve, "Steve", null, false, "Spam", "spam", 0, BOB, "Bob",
                now, null, false, null, null, null, null, null);
        PunishDecision.Input in = new PunishDecision.Input(admin, Punishment.Type.WARN, null, false, false, false, false, true,
                null, null, now).withEscalation(new PunishDecision.Escalation(spam, 0, false, now + DAY, 1));
        Punishment p = store.issue(draft, in, decision, null).join().punishment();
        assertEquals(Punishment.Type.MUTE, p.type());
        assertEquals(1, p.ladderStep());
        assertEquals(now + Duration.ofMinutes(30).toMillis(), p.expires());
    }

    // ================================================================ 13.5 watchlist

    @Test
    void watchAddRenewRemoveAndExpire() {
        UUID steve = UUID.randomUUID();
        PunishmentStore.WatchSaved a = store.watch(steve, "Steve", ALEX, "Alex", "suspected macros", now, now + 30 * DAY).join();
        assertNull(a.before());
        assertEquals("suspected macros", a.entry().reason());
        PunishmentStore.WatchSaved b = store.watch(steve, "Steve", BOB, "Bob", "alt?", now + 1, now + 31 * DAY).join();
        assertNotNull(b.before(), "renewed, not a second entry");
        assertEquals(a.entry().id(), b.entry().id());
        assertEquals("Bob", b.entry().authorName());
        assertEquals("alt?", b.entry().reason());
        assertEquals(1, store.watchlist(now).join().size());
        assertEquals("alt?", store.activeWatch(steve, now).join().orElseThrow().reason());
        assertTrue(store.activeWatch(steve, now + 32 * DAY).join().isEmpty(), "expired");
        assertTrue(store.watchlist(now + 32 * DAY).join().isEmpty());

        Optional<Watchlist.Entry> removed = store.unwatch(steve, ALEX, "Alex", now + 2).join();
        assertTrue(removed.isPresent());
        assertTrue(store.activeWatch(steve, now + 3).join().isEmpty());
        assertTrue(store.unwatch(steve, ALEX, "Alex", now + 4).join().isEmpty(), "not watched anymore");
        long kept = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM watchlist WHERE target_uuid = ? AND removed = TRUE",
                rs -> rs.getLong(1), Sql.toBytes(steve)).orElse(0L));
        assertEquals(1, kept, "the row is kept as removed");
        assertNull(store.watch(UUID.randomUUID(), "Alex", null, "CONSOLE", null, now, null).join().entry().expires(),
                "until removed");
    }

    // ================================================================ 13.13 mute ended

    private Punishment insert(UUID target, Punishment.Type type, long created, Long expires) {
        Punishment draft = new Punishment(0, type, target, "Steve", null, false, "x", null, null, BOB, "Bob", created, null,
                false, null, null, null, null, null);
        PunishDecision.Input in = new PunishDecision.Input(staff("Bob", BOB, null, true), type,
                expires == null ? null : Duration.ofMillis(expires - created), false, false, false, false, true, null, null, created);
        return store.issue(draft, in, PunishDecision.standard(), null).join().punishment();
    }

    @Test
    void endNoticeIsClaimedOnceAndNeverForPardons() {
        UUID steve = UUID.randomUUID();
        Punishment mute = insert(steve, Punishment.Type.MUTE, now - 2 * DAY, now - DAY);
        Punishment ban = insert(steve, Punishment.Type.BAN, now - 3 * DAY, now - 2 * DAY);
        assertEquals(List.of(mute.id()), store.claimEndNotices(steve, now, true, false).join().stream().map(Punishment::id).toList());
        assertTrue(store.claimEndNotices(steve, now, true, false).join().isEmpty(), "never repeats");
        assertEquals(List.of(ban.id()), store.claimEndNotices(steve, now, true, true).join().stream().map(Punishment::id).toList());
        assertTrue(store.claimEndNotices(steve, now, true, true).join().isEmpty());

        UUID alex = UUID.randomUUID();
        insert(alex, Punishment.Type.MUTE, now - 2000, now + DAY);
        assertTrue(store.claimEndNotices(alex, now, true, true).join().isEmpty(), "still running");
        store.pardon(Punishment.Type.MUTE, alex, null, BOB, "Bob", "mistake", false).join();
        assertTrue(store.claimEndNotices(alex, now + 2 * DAY, true, true).join().isEmpty(), "pardoned: no 'mute ended'");
        insert(alex, Punishment.Type.MUTE, now - 3 * DAY, null);
        assertTrue(store.claimEndNotices(alex, now + 2 * DAY, true, true).join().isEmpty(), "perm never ends");
        assertTrue(store.claimEndNotices(alex, now, false, false).join().isEmpty());
    }

    @Test
    void migrationMarksOldEndedPunishmentsAsTold() {
        // V005 ran in start(): rows that ended before it are marked, so an update never sends old "mute ended" lines
        UUID steve = UUID.randomUUID();
        Punishment old = insert(steve, Punishment.Type.MUTE, now - 2 * DAY, now - DAY);
        db.blocking(c -> Sql.update(c, "UPDATE punishments SET end_notice_sent = TRUE WHERE expires IS NOT NULL "
                + "AND expires <= UNIX_TIMESTAMP() * 1000.0"));
        assertTrue(store.claimEndNotices(steve, now, true, true).join().isEmpty(), "the backfill statement works on H2: " + old.id());
    }
}
